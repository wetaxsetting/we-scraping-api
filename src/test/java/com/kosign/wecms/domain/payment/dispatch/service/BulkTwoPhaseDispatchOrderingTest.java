package com.kosign.wecms.domain.payment.dispatch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.domain.payment.dispatch.alert.OpsAlertService;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileData;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileMst;
import com.kosign.wecms.domain.payment.dispatch.enums.ProcStat;
import com.kosign.wecms.domain.payment.dispatch.file.FixedLengthCodec;
import com.kosign.wecms.domain.payment.dispatch.repository.BulkValidationCounts;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmBatApiTranRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmConfRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmFileDataRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmFileMstRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileDataRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileMstRepository;
import com.kosign.wecms.domain.payment.dispatch.sftp.SftpClient;
import com.kosign.wecms.domain.payment.dispatch.sftp.SftpProperties;
import com.kosign.wecms.domain.payment.dispatch.van.client.EfsApiClient;
import com.kosign.wecms.domain.payment.dispatch.van.client.EfsProperties;
import com.kosign.wecms.domain.payment.dispatch.van.client.VanApiKeyService;
import com.kosign.wecms.domain.payment.dispatch.van.crypto.EfsCryptoService;
import com.kosign.wecms.domain.setting.firmbankingsettings.entity.TbFirmConf;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Two-phase dispatch — the <b>ordering</b> invariant, which is the whole point of the change and was
 * not covered by any existing test.
 *
 * <h2>What was wrong</h2>
 * Legacy {@code FILE_DEPOSIT_TRANSFER_BATCH1} commits the firm-file row at {@code :696-697}, uploads
 * at {@code :700} and calls 00301 at {@code :721}. The refactor had inverted it — the commit happened
 * last, at the end of a per-file transaction that also spanned the SFTP transfer and the gateway call.
 * Three defects followed, and each is pinned below:
 * <ol>
 *   <li><b>The sibling barrier could see a short group.</b>
 *       {@code BulkExecutionResultService} defers finalization while any sibling is non-terminal, but
 *       it counts rows that EXIST. With the commit last, file 2's row did not exist while file 1 was
 *       being uploaded and dispatched, so a callback arriving in that window counted an incomplete
 *       group and could pass. Test 1.</li>
 *   <li><b>A 01301 could arrive before its own row committed.</b> Legacy's callback looks the row up
 *       by {@code FIRM_FILE_NM} and rejects with {@code CD_DB_ERR} when it finds none. Test 2 — the
 *       row is committed before the upload that triggers the callback.</li>
 *   <li><b>SFTP and HTTP ran inside a transaction.</b> Test 3.</li>
 * </ol>
 *
 * <p>Test 4 pins the failure semantics the reordering had to preserve: legacy breaks the per-file loop
 * on a phase-B failure and fails the shared pay-file, so later split files are never sent.
 *
 * @see BulkReceptionStrandedFailureTest the post-claim failure recording this ordering makes explicit
 * @see BulkSplitTotalReconciliationTest the split arithmetic itself
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BulkTwoPhaseDispatchOrderingTest {

    private static final String TRAN_DT = "20251023";
    private static final String BIZ_KEY = "VN_VNM0004_000001";
    private static final String FILE_SEQ_NO = "1";
    private static final String OUT_BANK = "970457";
    private static final String WDRL = "100001020691";
    private static final String PRTN = "WRB001";
    private static final String VAN = "GV00000001";

    @Mock EfsApiClient efsApiClient;
    @Mock VanApiKeyService vanApiKeyService;
    @Mock EfsProperties efsProperties;
    @Mock TbPayFileMstRepository payFileMstRepository;
    @Mock TbPayFileDataRepository payFileDataRepository;
    @Mock TbFirmConfRepository firmConfRepository;
    @Mock TbFirmFileMstRepository firmFileMstRepository;
    @Mock TbFirmFileDataRepository firmFileDataRepository;
    @Mock TbFirmBatApiTranRepository firmBatApiTranRepository;
    @Mock DispatchKeyGenerator dispatchKeyGenerator;
    @Mock SftpClient sftpClient;
    @Mock BulkTransferValidator bulkTransferValidator;
    @Mock OpsAlertService opsAlertService;
    @Mock OperatingHoursService operatingHoursService;
    /** Pass-through manager: phase A's template runs inline and still propagates exceptions. */
    @Mock org.springframework.transaction.PlatformTransactionManager txManager;

    private final EfsCryptoService crypto = new EfsCryptoService();
    private final FixedLengthCodec codec = spy(new FixedLengthCodec());
    private final SftpProperties sftpProperties = new SftpProperties();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Counts firm-file MST rows written at the moment the first SFTP upload happens. */
    private final AtomicInteger mstSavesAtFirstUpload = new AtomicInteger(-1);
    /** Counts firm-file DATA rows written at the moment the first SFTP upload happens. */
    private final AtomicInteger dataSavesAtFirstUpload = new AtomicInteger(-1);
    private final AtomicInteger mstSaves = new AtomicInteger();
    private final AtomicInteger dataSaves = new AtomicInteger();

    private BulkReceptionService service;

    @BeforeEach
    void setUp() {
        BulkDispatchClaimTxService claimTxService =
                new BulkDispatchClaimTxService(payFileMstRepository, firmFileMstRepository);
        service = new BulkReceptionService(
                efsApiClient, crypto, vanApiKeyService, efsProperties, objectMapper,
                payFileMstRepository, payFileDataRepository, firmConfRepository,
                firmFileMstRepository, firmFileDataRepository, firmBatApiTranRepository,
                dispatchKeyGenerator, codec, sftpClient, sftpProperties, bulkTransferValidator,
                opsAlertService, operatingHoursService, claimTxService,
                org.mockito.Mockito.mock(com.kosign.wecms.domain.payment.common.PaymentBlockedCharGuard.class),
                org.mockito.Mockito.mock(com.kosign.wecms.domain.payment.common.OutBankCdNormalizer.class),
                txManager);

        when(bulkTransferValidator.validate(any(), any())).thenReturn(Optional.empty());
        when(operatingHoursService.evaluateBulk(any(), any())).thenReturn(OperatingHoursDecision.DISPATCH);
        when(payFileMstRepository.claimForReception(any(), any(), any(), any())).thenReturn(1);
        when(payFileMstRepository.failClaimedPayFile(any(), any(), any(), any(), any())).thenReturn(1);
        when(firmFileMstRepository.failFirmFile(any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        when(payFileMstRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(firmBatApiTranRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(dispatchKeyGenerator.nextTranSeq(anyString(), anyString())).thenReturn("1");
        when(firmFileMstRepository.findByTranDtAndOutBankCdAndBankPrtnId(anyString(), anyString(), anyString()))
                .thenReturn(List.of());
        sftpProperties.setLocalUploadPath(System.getProperty("java.io.tmpdir"));

        // Instrumentation: the ordering assertions read these counters, not a Mockito InOrder — an
        // InOrder would prove "some MST save preceded the upload", which the OLD code also satisfied
        // (its file-1 save preceded its file-1 upload). What has to be proven is stronger: EVERY
        // file's rows exist before the FIRST upload.
        when(firmFileMstRepository.save(any())).thenAnswer(i -> {
            mstSaves.incrementAndGet();
            return i.getArgument(0);
        });
        when(firmFileDataRepository.save(any())).thenAnswer(i -> {
            dataSaves.incrementAndGet();
            return i.getArgument(0);
        });
        org.mockito.Mockito.doAnswer(i -> {
            mstSavesAtFirstUpload.compareAndSet(-1, mstSaves.get());
            dataSavesAtFirstUpload.compareAndSet(-1, dataSaves.get());
            return null;
        }).when(sftpClient).upload(anyString(), anyString());
    }

    /* ───────────────────────── fixtures ───────────────────────── */

    private TbPayFileMst payRow() {
        TbPayFileMst p = new TbPayFileMst();
        p.setTranDt(TRAN_DT);
        p.setBizKey(BIZ_KEY);
        p.setFileSeqNo(FILE_SEQ_NO);
        p.setOutBankCd(OUT_BANK);
        p.setWdrlActNo(WDRL);
        p.setBankPrtnId(PRTN);
        p.setFundTp("0");
        p.setTranTp("1");
        p.setProcStat(ProcStat.WAIT.code());
        return p;
    }

    private TbFirmConf conf(String branchDiv, int limit) {
        TbFirmConf c = new TbFirmConf();
        c.setBizKey(BIZ_KEY);
        c.setOutBankCd(OUT_BANK);
        c.setWdrlActNo(WDRL);
        c.setBankPrtnId(PRTN);
        c.setVanOrgCd(VAN);
        c.setFileType("WRB");
        c.setOrgStat("1");
        c.setBatTranBranchDiv(branchDiv);
        c.setBatTranBranchLimit(BigDecimal.valueOf(limit));
        c.setRegDttm("20251023000000");
        return c;
    }

    private TbPayFileData detail(int seq, String ownerNm) {
        TbPayFileData d = new TbPayFileData();
        d.setTranDt(TRAN_DT);
        d.setBizKey(BIZ_KEY);
        d.setFileSeqNo(FILE_SEQ_NO);
        d.setSeq(BigDecimal.valueOf(seq));
        d.setTarBankCd("01203001");
        d.setFincMdaDiv("Y");
        d.setFincMdaNo("068100000" + seq);
        d.setTrscCcyCd("VND");
        d.setTranAmt(new BigDecimal("1000"));
        d.setBankChnlDiv("T");
        d.setAntDivCd("C");
        d.setFincMdaOwnerNm(ownerNm);
        return d;
    }

    /** {@code n} details split one-per-file, so the group is {@code n} split files. */
    private void givenSplitInto(int n) {
        givenSplitInto(n, "NGUYEN VAN A");
    }

    private void givenSplitInto(int n, String ownerNm) {
        when(payFileMstRepository.findByTranDtAndBizKeyAndFileSeqNo(TRAN_DT, BIZ_KEY, FILE_SEQ_NO))
                .thenReturn(Optional.of(payRow()));
        when(firmConfRepository.findById(any())).thenReturn(Optional.of(conf("Y", 1)));
        List<TbPayFileData> details = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            details.add(detail(i, ownerNm));
        }
        when(payFileDataRepository.findByBizKeyAndTranDtAndFileSeqNo(BIZ_KEY, TRAN_DT, FILE_SEQ_NO))
                .thenReturn(details);
        when(payFileDataRepository.aggregateValidationCounts(anyString(), anyString(), anyString()))
                .thenReturn(org.mockito.Mockito.mock(BulkValidationCounts.class));
        when(vanApiKeyService.getApiKey(anyString())).thenReturn("webcashvietnam0002TEST$#%$&*$&^!");
    }

    /* ── 1. the sibling barrier sees the COMPLETE group ─────────────────────── */

    @Test
    @DisplayName("1: every split file's MST row is committed before the FIRST upload — the barrier "
            + "can never count a short group")
    void allFirmFileRowsExistBeforeAnyEgress() {
        givenSplitInto(5);
        BulkReceptionService spy = spy(service);
        doReturn(null).when(spy).issueReception(any());

        spy.receive(TRAN_DT, BIZ_KEY, FILE_SEQ_NO);

        assertThat(mstSavesAtFirstUpload.get())
                .as("all 5 TB_FIRM_FILE_MST rows must exist before the first byte leaves — a 01301 "
                        + "for file 1 that lands during file 2's upload must see the whole group")
                .isEqualTo(5);
        verify(sftpClient, times(5)).upload(anyString(), anyString());
    }

    @Test
    @DisplayName("1b: every split file's DATA rows are committed before the FIRST upload")
    void allFirmFileDataRowsExistBeforeAnyEgress() {
        givenSplitInto(4);
        BulkReceptionService spy = spy(service);
        doReturn(null).when(spy).issueReception(any());

        spy.receive(TRAN_DT, BIZ_KEY, FILE_SEQ_NO);

        // One detail per file, so 4 files = 4 TB_FIRM_FILE_DATA rows, all before the first upload.
        assertThat(dataSavesAtFirstUpload.get()).isEqualTo(4);
    }

    /* ── 2. a 01301 cannot beat its own row ─────────────────────────────────── */

    @Test
    @DisplayName("2: the single-file path also commits before the upload (legacy :696 then :700)")
    void singleFilePathCommitsBeforeUpload() {
        givenSplitInto(1);
        BulkReceptionService spy = spy(service);
        doReturn(null).when(spy).issueReception(any());

        spy.receive(TRAN_DT, BIZ_KEY, FILE_SEQ_NO);

        assertThat(mstSavesAtFirstUpload.get())
                .as("the row a 01301 looks up by FIRM_FILE_NM must exist before the upload that "
                        + "triggers the callback")
                .isEqualTo(1);
    }

    /* ── 3. encode is in phase A: over-width rejects with NOTHING committed ─── */

    @Test
    @DisplayName("3: an over-width field is rejected in phase A — nothing uploaded, nothing 00301'd")
    void overWidthFieldRejectsBeforeAnyEgress() {
        // The retained divergence: the codec REFUSES an over-width field rather than truncating it
        // (legacy writes a short, misaligned file to the bank with only a log line). Keeping encode in
        // phase A is what preserves "over-width ⇒ nothing committed" — deferring it to phase B would
        // have downgraded that to "committed rows that must now be marked FAIL".
        givenSplitInto(2, "X".repeat(500));   // far past the 150-byte receiver-name slot
        BulkReceptionService spy = spy(service);

        spy.receive(TRAN_DT, BIZ_KEY, FILE_SEQ_NO);

        verify(sftpClient, never()).upload(anyString(), anyString());
        verify(spy, never()).issueReception(any());
        // Phase A threw, so its transaction rolled back and the pay-file is recorded FAIL by the
        // outer guard — never abandoned at the claimed '6'.
        verify(payFileMstRepository).failClaimedPayFile(eq(BIZ_KEY), eq(TRAN_DT), eq(FILE_SEQ_NO),
                anyString(), anyString());
    }

    /* ── 4. legacy failure semantics preserved ──────────────────────────────── */

    @Test
    @DisplayName("4: file 3 of 5 fails in phase B — files 4 and 5 are never sent, pay-file FAIL "
            + "(legacy :741-768 break)")
    void phaseBFailureStopsTheBatchAtTheFailedFile() {
        givenSplitInto(5);
        // Files 1-2 upload; file 3's upload fails.
        doThrow(new RuntimeException("sftp down"))
                .when(sftpClient).upload(anyString(), org.mockito.ArgumentMatchers.contains("_003_"));

        BulkReceptionService spy = spy(service);
        doReturn(null).when(spy).issueReception(any());

        spy.receive(TRAN_DT, BIZ_KEY, FILE_SEQ_NO);

        // Legacy throws out of the per-file loop, so files 4 and 5 are never built or sent. Three
        // upload attempts: files 1 and 2 succeeded, file 3 threw.
        verify(sftpClient, times(3)).upload(anyString(), anyString());
        verify(spy, times(2)).issueReception(any());

        // The failed file AND the shared pay-file both carry a terminal FAIL.
        verify(firmFileMstRepository).failFirmFile(eq(BIZ_KEY), eq(TRAN_DT), eq(FILE_SEQ_NO),
                org.mockito.ArgumentMatchers.contains("_003_"),
                anyString(), anyString(), anyString());
        verify(payFileMstRepository).failClaimedPayFile(eq(BIZ_KEY), eq(TRAN_DT), eq(FILE_SEQ_NO),
                anyString(), anyString());

        // The operator must be able to see that 2 files already reached the bank — a partially
        // dispatched batch must never read as a clean retryable failure.
        org.mockito.ArgumentCaptor<String> alert = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(opsAlertService, org.mockito.Mockito.atLeastOnce()).notify(eq(VAN), alert.capture());
        assertThat(alert.getAllValues()).anySatisfy(body ->
                assertThat(body).contains("FILES_DISPATCHED : 2 / 5"));
    }

    @Test
    @DisplayName("4b: rows for the un-sent files still exist — they were committed in phase A")
    void unsentFilesStillHaveTheirRows() {
        givenSplitInto(5);
        doThrow(new RuntimeException("sftp down"))
                .when(sftpClient).upload(anyString(), org.mockito.ArgumentMatchers.contains("_003_"));

        BulkReceptionService spy = spy(service);
        doReturn(null).when(spy).issueReception(any());

        spy.receive(TRAN_DT, BIZ_KEY, FILE_SEQ_NO);

        // This is the one visible consequence of committing first: files 4 and 5 have TB_FIRM_FILE_MST
        // rows at PROC_STEP='0'/PROC_STAT='6' that were never dispatched, where the old ordering left
        // no trace of them at all. That is the correct trade — the barrier needs those rows to exist —
        // but it means the '6' detection sweep (StuckPayFileMonitorService) is what surfaces them, and
        // an operator reconciling this batch will see 5 rows and 2 sent files.
        assertThat(mstSaves.get()).isEqualTo(5);
        verify(sftpClient, times(3)).upload(anyString(), anyString());
    }

    /* ── guard: BusinessException from phase A is caught, never escapes receive() ── */

    @Test
    @DisplayName("a phase-A BusinessException is recorded, not rethrown to the caller")
    void phaseABusinessExceptionIsNotRethrown() {
        givenSplitInto(2);
        doThrow(new BusinessException("WBE_BLOCKED_CHARS", "field=RECIPIENT_NAME char=@"))
                .when(firmFileDataRepository).save(any());

        BulkReceptionService spy = spy(service);

        // receive() is entered from an afterCommit hook and a recovery sweep, neither of which can do
        // anything with a throw — so it must record and return.
        org.assertj.core.api.Assertions.assertThatCode(
                () -> spy.receive(TRAN_DT, BIZ_KEY, FILE_SEQ_NO)).doesNotThrowAnyException();
        verify(sftpClient, never()).upload(anyString(), anyString());
    }
}
