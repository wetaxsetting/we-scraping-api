package com.kosign.wecms.domain.payment.dispatch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.domain.setting.firmbankingsettings.entity.TbFirmConf;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmFileMst;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileData;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileMst;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmFileData;
import com.kosign.wecms.domain.payment.dispatch.enums.ProcStat;
import com.kosign.wecms.domain.payment.dispatch.enums.ProcStep;
import com.kosign.wecms.domain.payment.dispatch.file.DepositData;
import com.kosign.wecms.domain.payment.dispatch.file.DepositHeader;
import com.kosign.wecms.domain.payment.dispatch.file.DepositTrailer;
import com.kosign.wecms.domain.payment.dispatch.file.FixedLengthCodec;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link BulkReceptionService#receive} — the BATCH1 (00301) request-time
 * flow. The {@code issueReception} API leg is stubbed via a Mockito spy so these
 * tests exercise the orchestration (validate → file build → MST/DATA insert →
 * SFTP → audit → state guarantees) without the EFS gateway / crypto.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BulkReceptionReceiveTest {

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
    @Mock com.kosign.wecms.domain.payment.dispatch.alert.OpsAlertService opsAlertService;
    @Mock OperatingHoursService operatingHoursService;
    // Item 2: a mock PlatformTransactionManager makes the per-file REQUIRES_NEW TransactionTemplate
    // a synchronous pass-through (getTransaction→null, commit/rollback no-op) that still propagates
    // exceptions — so these unit tests exercise the real per-file orchestration + fail-stop.
    @Mock org.springframework.transaction.PlatformTransactionManager perFileTxManager;

    private final EfsCryptoService crypto = new EfsCryptoService();
    // Spy so split tests can capture the per-file encode(header, lines, trailer) calls
    // (real method still runs and produces the actual bytes).
    private final FixedLengthCodec codec = spy(new FixedLengthCodec());
    private final SftpProperties sftpProperties = new SftpProperties();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private BulkReceptionService service;

    @BeforeEach
    void setUp() {
        service = new BulkReceptionService(
                efsApiClient, crypto, vanApiKeyService, efsProperties, objectMapper,
                payFileMstRepository, payFileDataRepository, firmConfRepository,
                firmFileMstRepository, firmFileDataRepository, firmBatApiTranRepository,
                dispatchKeyGenerator, codec, sftpClient, sftpProperties, bulkTransferValidator,
                opsAlertService, operatingHoursService,
                new BulkDispatchClaimTxService(payFileMstRepository),
                org.mockito.Mockito.mock(com.kosign.wecms.domain.payment.common.PaymentBlockedCharGuard.class),
                org.mockito.Mockito.mock(com.kosign.wecms.domain.payment.common.OutBankCdNormalizer.class),
                perFileTxManager);

        // Pre-flight validation passes by default; the dedicated cases live in
        // BulkReceptionServiceValidationTest.
        when(bulkTransferValidator.validate(any(), any())).thenReturn(Optional.empty());
        // Operating-hours gate: open by default so the dispatch-path tests proceed.
        when(operatingHoursService.evaluateBulk(any(), any())).thenReturn(OperatingHoursDecision.DISPATCH);
        // H-4: the atomic '9'->'6' claim wins by default. Cases that must NOT dispatch (SKIP,
        // CUTOFF, NO_CRITERIA, not-WAIT) return before the claim, so this stub is not consulted.
        when(payFileMstRepository.claimForReception(any(), any(), any(), any())).thenReturn(1);
        when(firmFileMstRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(payFileMstRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(firmFileDataRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(firmBatApiTranRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(dispatchKeyGenerator.nextTranSeq(anyString(), anyString())).thenReturn("1");
        when(firmFileMstRepository.findByTranDtAndOutBankCdAndBankPrtnId(anyString(), anyString(), anyString()))
                .thenReturn(List.of());
    }

    private TbPayFileMst payRow(String procStat) {
        TbPayFileMst p = new TbPayFileMst();
        p.setTranDt(TRAN_DT);
        p.setBizKey(BIZ_KEY);
        p.setFileSeqNo(FILE_SEQ_NO);
        p.setOutBankCd(OUT_BANK);
        p.setWdrlActNo(WDRL);
        p.setBankPrtnId(PRTN);
        p.setFundTp("0");
        p.setTranTp("1");
        p.setProcStat(procStat);
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

    private TbPayFileData detail(int seq, String amt) {
        TbPayFileData d = new TbPayFileData();
        d.setTranDt(TRAN_DT);
        d.setBizKey(BIZ_KEY);
        d.setFileSeqNo(FILE_SEQ_NO);
        d.setSeq(BigDecimal.valueOf(seq));
        d.setTarBankCd("01203001");
        d.setFincMdaDiv("Y");
        d.setFincMdaNo("068100000" + seq);
        d.setTrscCcyCd("VND");
        d.setTranAmt(new BigDecimal(amt));
        d.setBankChnlDiv("T");
        d.setFincMdaOwnerNm("NGUYEN VAN A");
        return d;
    }

    /** Common WAIT pay-file + conf + one detail row so receive() reaches the operating-hours gate. */
    private void stubReadyFile() {
        when(payFileMstRepository.findByTranDtAndBizKeyAndFileSeqNo(TRAN_DT, BIZ_KEY, FILE_SEQ_NO))
                .thenReturn(Optional.of(payRow(ProcStat.WAIT.code())));
        when(firmConfRepository.findById(any())).thenReturn(Optional.of(conf("N", 2000)));
        when(payFileDataRepository.findByBizKeyAndTranDtAndFileSeqNo(BIZ_KEY, TRAN_DT, FILE_SEQ_NO))
                .thenReturn(List.of(detail(1, "1000.00")));
    }

    @Test
    void operatingHoursCutoff_failsFile_alerts_noDispatch() {
        stubReadyFile();
        when(operatingHoursService.evaluateBulk(any(), any())).thenReturn(OperatingHoursDecision.CUTOFF);

        service.receive(TRAN_DT, BIZ_KEY, FILE_SEQ_NO);

        ArgumentCaptor<TbPayFileMst> cap = ArgumentCaptor.forClass(TbPayFileMst.class);
        verify(payFileMstRepository).save(cap.capture());
        assertEquals(ProcStat.FAIL.code(), cap.getValue().getProcStat());
        ArgumentCaptor<String> msg = ArgumentCaptor.forClass(String.class);
        verify(opsAlertService).notify(any(), msg.capture());
        assertTrue(msg.getValue().startsWith("대량이체 Batch 거래 마감."));
        // No dispatch: no firm-file built.
        verify(firmFileMstRepository, never()).save(any());
    }

    @Test
    void operatingHoursNoCriteria_failsFile_alerts_noDispatch() {
        stubReadyFile();
        when(operatingHoursService.evaluateBulk(any(), any())).thenReturn(OperatingHoursDecision.NO_CRITERIA);

        service.receive(TRAN_DT, BIZ_KEY, FILE_SEQ_NO);

        ArgumentCaptor<TbPayFileMst> cap = ArgumentCaptor.forClass(TbPayFileMst.class);
        verify(payFileMstRepository).save(cap.capture());
        assertEquals(ProcStat.FAIL.code(), cap.getValue().getProcStat());
        assertTrue(cap.getValue().getRsMsg().contains("TB_BATCH_SCHEDULE_INFO"));
        ArgumentCaptor<String> msg = ArgumentCaptor.forClass(String.class);
        verify(opsAlertService).notify(any(), msg.capture());
        assertTrue(msg.getValue().startsWith("대량이체 Batch 수행 기준 없음."));
        verify(firmFileMstRepository, never()).save(any());
    }

    @Test
    void operatingHoursSkip_leavesFile_noAlert_noDispatch() {
        stubReadyFile();
        when(operatingHoursService.evaluateBulk(any(), any())).thenReturn(OperatingHoursDecision.SKIP);

        service.receive(TRAN_DT, BIZ_KEY, FILE_SEQ_NO);

        verify(payFileMstRepository, never()).save(any()); // left WAIT, untouched
        verify(opsAlertService, never()).notify(any(), any());
        verify(firmFileMstRepository, never()).save(any());
    }

    @Test
    void happyPath_insertsFileAtStep0Stat6_uploads_auditsBeforeCall_andDoesNotAdvance() {
        when(payFileMstRepository.findByTranDtAndBizKeyAndFileSeqNo(TRAN_DT, BIZ_KEY, FILE_SEQ_NO))
                .thenReturn(Optional.of(payRow(ProcStat.WAIT.code())));
        when(firmConfRepository.findById(any())).thenReturn(Optional.of(conf("N", 2000)));
        when(payFileDataRepository.findByBizKeyAndTranDtAndFileSeqNo(BIZ_KEY, TRAN_DT, FILE_SEQ_NO))
                .thenReturn(List.of(detail(1, "1000.00"), detail(2, "2000.00")));

        BulkReceptionService spy = spy(service);
        doReturn(null).when(spy).issueReception(any());

        spy.receive(TRAN_DT, BIZ_KEY, FILE_SEQ_NO);

        // FIRM_FILE_MST created at PROC_STEP=0 / PROC_STAT=6 and NOT advanced.
        ArgumentCaptor<TbFirmFileMst> mstCap = ArgumentCaptor.forClass(TbFirmFileMst.class);
        verify(firmFileMstRepository).save(mstCap.capture());
        TbFirmFileMst saved = mstCap.getValue();
        assertEquals(ProcStep.REQ.code(), saved.getProcStep());
        assertEquals(ProcStat.REQ.code(), saved.getProcStat());
        assertEquals("S_" + VAN + "_WRB_001_" + TRAN_DT, saved.getFirmFileNm());
        assertEquals(new BigDecimal("2"), saved.getReqCnt());

        // H-4: the pay-file '9'->'6' transition is now the ATOMIC conditional claim, committed
        // before any egress — not an unconditional save(). Verifying the claim, and the absence of
        // any save() on the happy path, is the regression guard that the old non-atomic
        // read-then-write does not come back.
        verify(payFileMstRepository).claimForReception(
                org.mockito.ArgumentMatchers.eq(BIZ_KEY),
                org.mockito.ArgumentMatchers.eq(TRAN_DT),
                org.mockito.ArgumentMatchers.eq(FILE_SEQ_NO),
                anyString());
        verify(payFileMstRepository, never()).save(any());

        // Two detail rows persisted; upload + API issued.
        verify(firmFileDataRepository, org.mockito.Mockito.times(2)).save(any());
        verify(sftpClient).upload(anyString(), anyString());
        verify(spy).issueReception(any());

        // Audit written BEFORE the call (00301) then updated with the result → 2 saves.
        ArgumentCaptor<com.kosign.wecms.domain.payment.dispatch.entity.TbFirmBatApiTran> auditCap =
                ArgumentCaptor.forClass(com.kosign.wecms.domain.payment.dispatch.entity.TbFirmBatApiTran.class);
        verify(firmBatApiTranRepository, org.mockito.Mockito.times(2)).save(auditCap.capture());
        assertEquals(com.kosign.wecms.domain.payment.dispatch.enums.ApiTranTp.BULK_RECEPTION.code(),
                auditCap.getAllValues().get(0).getApiTranTp());
    }

    @Test
    void duplicate_payNotWait_isSafeNoOp() {
        when(payFileMstRepository.findByTranDtAndBizKeyAndFileSeqNo(TRAN_DT, BIZ_KEY, FILE_SEQ_NO))
                .thenReturn(Optional.of(payRow(ProcStat.REQ.code()))); // already '6'

        service.receive(TRAN_DT, BIZ_KEY, FILE_SEQ_NO);

        verify(payFileMstRepository, never()).save(any());
        verify(firmFileMstRepository, never()).save(any());
        verify(sftpClient, never()).upload(anyString(), anyString());
    }

    @Test
    void sftpUploadFailure_marksBothFailed_andDoesNotCallApi() {
        when(payFileMstRepository.findByTranDtAndBizKeyAndFileSeqNo(TRAN_DT, BIZ_KEY, FILE_SEQ_NO))
                .thenReturn(Optional.of(payRow(ProcStat.WAIT.code())));
        when(firmConfRepository.findById(any())).thenReturn(Optional.of(conf("N", 2000)));
        when(payFileDataRepository.findByBizKeyAndTranDtAndFileSeqNo(BIZ_KEY, TRAN_DT, FILE_SEQ_NO))
                .thenReturn(List.of(detail(1, "1000.00")));
        doThrow(new RuntimeException("sftp down")).when(sftpClient).upload(anyString(), anyString());

        BulkReceptionService spy = spy(service);
        spy.receive(TRAN_DT, BIZ_KEY, FILE_SEQ_NO);

        // FIRM_FILE_MST ends FAIL (still step 0, never advanced); pay ends FAIL.
        ArgumentCaptor<TbFirmFileMst> mstCap = ArgumentCaptor.forClass(TbFirmFileMst.class);
        verify(firmFileMstRepository, org.mockito.Mockito.atLeastOnce()).save(mstCap.capture());
        TbFirmFileMst last = mstCap.getValue();
        assertEquals(ProcStep.REQ.code(), last.getProcStep());
        assertEquals(ProcStat.FAIL.code(), last.getProcStat());

        ArgumentCaptor<TbPayFileMst> payCap = ArgumentCaptor.forClass(TbPayFileMst.class);
        verify(payFileMstRepository, org.mockito.Mockito.atLeastOnce()).save(payCap.capture());
        assertEquals(ProcStat.FAIL.code(), payCap.getValue().getProcStat());

        verify(spy, never()).issueReception(any());
    }

    /** G. FILE_TYPE is used RAW (no trim) in the filename + stored FILE_TYPE (legacy BATCH1:533,548).
     *  Real data is CHAR(3) exactly-3-char (no padding), so this padded value only demonstrates the
     *  raw pass-through mechanism — with the old trim() the filename would be "..._WRB_..." instead. */
    @Test
    void fileType_isRawNotTrimmed_inFilenameAndMst() {
        TbFirmConf paddedConf = conf("N", 2000);
        paddedConf.setFileType("WRB ");   // simulated padded/raw value; legacy carries it verbatim
        when(payFileMstRepository.findByTranDtAndBizKeyAndFileSeqNo(TRAN_DT, BIZ_KEY, FILE_SEQ_NO))
                .thenReturn(Optional.of(payRow(ProcStat.WAIT.code())));
        when(firmConfRepository.findById(any())).thenReturn(Optional.of(paddedConf));
        when(payFileDataRepository.findByBizKeyAndTranDtAndFileSeqNo(BIZ_KEY, TRAN_DT, FILE_SEQ_NO))
                .thenReturn(List.of(detail(1, "1000.00")));
        BulkReceptionService spy = spy(service);
        doReturn(null).when(spy).issueReception(any());
        spy.receive(TRAN_DT, BIZ_KEY, FILE_SEQ_NO);

        ArgumentCaptor<TbFirmFileMst> mstCap = ArgumentCaptor.forClass(TbFirmFileMst.class);
        verify(firmFileMstRepository).save(mstCap.capture());
        assertEquals("WRB ", mstCap.getValue().getFileType());                               // raw, not trimmed
        assertEquals("S_" + VAN + "_WRB _001_" + TRAN_DT, mstCap.getValue().getFirmFileNm()); // padding carried raw
    }

    // ------------------------------------------------------------------ C5 branch-split

    private List<TbPayFileData> details(int n) {
        List<TbPayFileData> list = new ArrayList<>(n);
        for (int i = 1; i <= n; i++) {
            list.add(detail(i, String.valueOf(i * 1000) + ".00"));
        }
        return list;
    }

    /** All MST rows persisted, in save order. */
    private List<TbFirmFileMst> capturedMst(int expectedSaves) {
        ArgumentCaptor<TbFirmFileMst> cap = ArgumentCaptor.forClass(TbFirmFileMst.class);
        verify(firmFileMstRepository, times(expectedSaves)).save(cap.capture());
        return cap.getAllValues();
    }

    /** Group every persisted TB_FIRM_FILE_DATA row by its FIRM_FILE_NM (insertion order). */
    private Map<String, List<TbFirmFileData>> capturedDataByFile() {
        ArgumentCaptor<TbFirmFileData> cap = ArgumentCaptor.forClass(TbFirmFileData.class);
        verify(firmFileDataRepository, org.mockito.Mockito.atLeastOnce()).save(cap.capture());
        Map<String, List<TbFirmFileData>> byFile = new LinkedHashMap<>();
        for (TbFirmFileData d : cap.getAllValues()) {
            byFile.computeIfAbsent(d.getFirmFileNm(), k -> new ArrayList<>()).add(d);
        }
        return byFile;
    }

    /** 1. Split DISABLED (BAT_TRAN_BRANCH_DIV='N') → single file, current behavior unchanged. */
    @Test
    void splitDisabled_singleFile_currentBehaviorUnchanged() {
        when(payFileMstRepository.findByTranDtAndBizKeyAndFileSeqNo(TRAN_DT, BIZ_KEY, FILE_SEQ_NO))
                .thenReturn(Optional.of(payRow(ProcStat.WAIT.code())));
        when(firmConfRepository.findById(any())).thenReturn(Optional.of(conf("N", 2)));
        when(payFileDataRepository.findByBizKeyAndTranDtAndFileSeqNo(BIZ_KEY, TRAN_DT, FILE_SEQ_NO))
                .thenReturn(details(5)); // 5 rows, but split off

        BulkReceptionService spy = spy(service);
        doReturn(null).when(spy).issueReception(any());
        spy.receive(TRAN_DT, BIZ_KEY, FILE_SEQ_NO);

        List<TbFirmFileMst> mst = capturedMst(1);
        assertEquals("S_" + VAN + "_WRB_001_" + TRAN_DT, mst.get(0).getFirmFileNm());
        assertEquals(new BigDecimal("5"), mst.get(0).getReqCnt());
        verify(firmFileDataRepository, times(5)).save(any());
        verify(spy, times(1)).issueReception(any());
    }

    /** 2. Split ENABLED but count <= limit → single file, current behavior unchanged. */
    @Test
    void splitEnabled_countAtOrBelowLimit_singleFile() {
        when(payFileMstRepository.findByTranDtAndBizKeyAndFileSeqNo(TRAN_DT, BIZ_KEY, FILE_SEQ_NO))
                .thenReturn(Optional.of(payRow(ProcStat.WAIT.code())));
        when(firmConfRepository.findById(any())).thenReturn(Optional.of(conf("Y", 5)));
        when(payFileDataRepository.findByBizKeyAndTranDtAndFileSeqNo(BIZ_KEY, TRAN_DT, FILE_SEQ_NO))
                .thenReturn(details(3)); // 3 <= 5 → no split

        BulkReceptionService spy = spy(service);
        doReturn(null).when(spy).issueReception(any());
        spy.receive(TRAN_DT, BIZ_KEY, FILE_SEQ_NO);

        List<TbFirmFileMst> mst = capturedMst(1);
        assertEquals("S_" + VAN + "_WRB_001_" + TRAN_DT, mst.get(0).getFirmFileNm());
        assertEquals(new BigDecimal("3"), mst.get(0).getReqCnt());
        verify(firmFileDataRepository, times(3)).save(any());
        verify(spy, times(1)).issueReception(any());
    }

    /** 3. Split ENABLED and count > limit → multiple files created (ceil(5/2)=3). */
    @Test
    void splitEnabled_countAboveLimit_multipleFiles() {
        when(payFileMstRepository.findByTranDtAndBizKeyAndFileSeqNo(TRAN_DT, BIZ_KEY, FILE_SEQ_NO))
                .thenReturn(Optional.of(payRow(ProcStat.WAIT.code())));
        when(firmConfRepository.findById(any())).thenReturn(Optional.of(conf("Y", 2)));
        when(payFileDataRepository.findByBizKeyAndTranDtAndFileSeqNo(BIZ_KEY, TRAN_DT, FILE_SEQ_NO))
                .thenReturn(details(5)); // ceil(5/2) = 3 files

        BulkReceptionService spy = spy(service);
        doReturn(null).when(spy).issueReception(any());
        spy.receive(TRAN_DT, BIZ_KEY, FILE_SEQ_NO);

        List<TbFirmFileMst> mst = capturedMst(3);
        // Consecutive rounds 001 / 002 / 003, all sharing the one FILE_SEQ_NO.
        assertEquals("S_" + VAN + "_WRB_001_" + TRAN_DT, mst.get(0).getFirmFileNm());
        assertEquals("S_" + VAN + "_WRB_002_" + TRAN_DT, mst.get(1).getFirmFileNm());
        assertEquals("S_" + VAN + "_WRB_003_" + TRAN_DT, mst.get(2).getFirmFileNm());
        // H-4: claimed exactly ONCE before the loop (not once per split file), and never failed.
        // Claiming per split file would let a second driver win a later file of the same batch.
        verify(payFileMstRepository, times(1)).claimForReception(
                org.mockito.ArgumentMatchers.eq(BIZ_KEY),
                org.mockito.ArgumentMatchers.eq(TRAN_DT),
                org.mockito.ArgumentMatchers.eq(FILE_SEQ_NO),
                anyString());
        verify(payFileMstRepository, never()).save(any());
    }

    /** 4. Each split file has the correct header / data / trailer counts (2, 2, 1 for ceil(5/2)). */
    @Test
    void eachSplitFile_hasCorrectHeaderDataTrailerCount() {
        when(payFileMstRepository.findByTranDtAndBizKeyAndFileSeqNo(TRAN_DT, BIZ_KEY, FILE_SEQ_NO))
                .thenReturn(Optional.of(payRow(ProcStat.WAIT.code())));
        when(firmConfRepository.findById(any())).thenReturn(Optional.of(conf("Y", 2)));
        when(payFileDataRepository.findByBizKeyAndTranDtAndFileSeqNo(BIZ_KEY, TRAN_DT, FILE_SEQ_NO))
                .thenReturn(details(5));

        BulkReceptionService spy = spy(service);
        doReturn(null).when(spy).issueReception(any());
        spy.receive(TRAN_DT, BIZ_KEY, FILE_SEQ_NO);

        // Capture every encode(header, lines, trailer) — one per file.
        ArgumentCaptor<DepositHeader> headerCap = ArgumentCaptor.forClass(DepositHeader.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DepositData>> linesCap = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<DepositTrailer> trailerCap = ArgumentCaptor.forClass(DepositTrailer.class);
        verify(codec, times(3)).encode(headerCap.capture(), linesCap.capture(), trailerCap.capture(), any());

        int[] expectedCounts = {2, 2, 1};
        String[] expectedRounds = {"001", "002", "003"};
        for (int i = 0; i < 3; i++) {
            // Header carries this file's round in iterationInfo.
            assertEquals(expectedRounds[i], headerCap.getAllValues().get(i).iterationInfo());
            // Data record count matches the window size.
            assertEquals(expectedCounts[i], linesCap.getAllValues().get(i).size());
            // Trailer requestCnt matches the same window size (header/data/trailer agree).
            assertEquals(String.valueOf(expectedCounts[i]), trailerCap.getAllValues().get(i).requestCnt());
            // Per-file line sequence restarts at 1.
            assertEquals("1", linesCap.getAllValues().get(i).get(0).seq());
        }

        // MST REQ_CNT mirrors the per-file counts.
        List<TbFirmFileMst> mst = capturedMst(3);
        for (int i = 0; i < 3; i++) {
            assertEquals(BigDecimal.valueOf(expectedCounts[i]), mst.get(i).getReqCnt());
        }
    }

    /** 5. Each split file creates its own TB_FIRM_FILE_MST + TB_FIRM_FILE_DATA rows. */
    @Test
    void eachSplitFile_createsOwnMstAndDataRows() {
        when(payFileMstRepository.findByTranDtAndBizKeyAndFileSeqNo(TRAN_DT, BIZ_KEY, FILE_SEQ_NO))
                .thenReturn(Optional.of(payRow(ProcStat.WAIT.code())));
        when(firmConfRepository.findById(any())).thenReturn(Optional.of(conf("Y", 2)));
        when(payFileDataRepository.findByBizKeyAndTranDtAndFileSeqNo(BIZ_KEY, TRAN_DT, FILE_SEQ_NO))
                .thenReturn(details(5));

        BulkReceptionService spy = spy(service);
        doReturn(null).when(spy).issueReception(any());
        spy.receive(TRAN_DT, BIZ_KEY, FILE_SEQ_NO);

        // 3 distinct MST rows (one per file).
        List<TbFirmFileMst> mst = capturedMst(3);
        assertEquals(3, mst.stream().map(TbFirmFileMst::getFirmFileNm).distinct().count());

        // DATA rows grouped per file: 3 files with 2, 2, 1 rows; FILE_SEQ_NO shared, FILE_SEQ per-file.
        Map<String, List<TbFirmFileData>> byFile = capturedDataByFile();
        assertEquals(3, byFile.size());
        assertEquals(5, byFile.values().stream().mapToInt(List::size).sum());
        for (List<TbFirmFileData> rows : byFile.values()) {
            assertTrue(rows.stream().allMatch(r -> FILE_SEQ_NO.equals(r.getFileSeqNo())));
            // Each row's FILE_SEQ matches the round embedded in its file name.
            String fileNm = rows.get(0).getFirmFileNm();
            assertTrue(rows.stream().allMatch(r -> fileNm.contains("_" + r.getFileSeq() + "_")));
        }
    }

    /** 6. Each split file issues its own EFS 301 (one issueReception call per file). */
    @Test
    void eachSplitFile_issuesEfs301() {
        when(payFileMstRepository.findByTranDtAndBizKeyAndFileSeqNo(TRAN_DT, BIZ_KEY, FILE_SEQ_NO))
                .thenReturn(Optional.of(payRow(ProcStat.WAIT.code())));
        when(firmConfRepository.findById(any())).thenReturn(Optional.of(conf("Y", 2)));
        when(payFileDataRepository.findByBizKeyAndTranDtAndFileSeqNo(BIZ_KEY, TRAN_DT, FILE_SEQ_NO))
                .thenReturn(details(5));

        BulkReceptionService spy = spy(service);
        doReturn(null).when(spy).issueReception(any());
        spy.receive(TRAN_DT, BIZ_KEY, FILE_SEQ_NO);

        // 3 files → 3 EFS 301 calls; each file also SFTP-uploaded.
        verify(spy, times(3)).issueReception(any());
        verify(sftpClient, times(3)).upload(anyString(), anyString());
    }

    /** All original SEQ (ORG_SEQ) values grouped per split file, in file save order. */
    private List<List<Long>> orgSeqsPerFile() {
        List<List<Long>> out = new ArrayList<>();
        for (List<TbFirmFileData> rows : capturedDataByFile().values()) {
            out.add(rows.stream().map(r -> r.getOrgSeq().longValue()).toList());
        }
        return out;
    }

    /** F. Contiguous SEQ 1..5, limit 2 → value-range windows [1,2] [3,4] [5,6]:
     *  file1 SEQ {1,2}, file2 {3,4}, file3 {5} (legacy START_SEQ/END_SEQ parity). */
    @Test
    void split_partitionsBySeqValueRange_contiguous() {
        when(payFileMstRepository.findByTranDtAndBizKeyAndFileSeqNo(TRAN_DT, BIZ_KEY, FILE_SEQ_NO))
                .thenReturn(Optional.of(payRow(ProcStat.WAIT.code())));
        when(firmConfRepository.findById(any())).thenReturn(Optional.of(conf("Y", 2)));
        when(payFileDataRepository.findByBizKeyAndTranDtAndFileSeqNo(BIZ_KEY, TRAN_DT, FILE_SEQ_NO))
                .thenReturn(details(5)); // SEQ 1..5
        BulkReceptionService spy = spy(service);
        doReturn(null).when(spy).issueReception(any());
        spy.receive(TRAN_DT, BIZ_KEY, FILE_SEQ_NO);

        List<List<Long>> perFile = orgSeqsPerFile();
        assertEquals(List.of(1L, 2L), perFile.get(0));
        assertEquals(List.of(3L, 4L), perFile.get(1));
        assertEquals(List.of(5L), perFile.get(2));
    }

    /** F. Non-1 starting SEQ {2,3,4}, limit 2 → legacy value-range windows [1,2] [3,4]:
     *  SEQ 2 → file1, SEQ 3,4 → file2. List-index slicing would instead give {2,3} / {4};
     *  asserting {2} / {3,4} proves partition is by SEQ VALUE, not by list position. */
    @Test
    void split_partitionsBySeqValueRange_notListIndex_nonOneStart() {
        when(payFileMstRepository.findByTranDtAndBizKeyAndFileSeqNo(TRAN_DT, BIZ_KEY, FILE_SEQ_NO))
                .thenReturn(Optional.of(payRow(ProcStat.WAIT.code())));
        when(firmConfRepository.findById(any())).thenReturn(Optional.of(conf("Y", 2)));
        when(payFileDataRepository.findByBizKeyAndTranDtAndFileSeqNo(BIZ_KEY, TRAN_DT, FILE_SEQ_NO))
                .thenReturn(List.of(detail(2, "2000.00"), detail(3, "3000.00"), detail(4, "4000.00")));
        BulkReceptionService spy = spy(service);
        doReturn(null).when(spy).issueReception(any());
        spy.receive(TRAN_DT, BIZ_KEY, FILE_SEQ_NO);

        List<List<Long>> perFile = orgSeqsPerFile();
        assertEquals(List.of(2L), perFile.get(0));       // value window [1,2] → only SEQ 2
        assertEquals(List.of(3L, 4L), perFile.get(1));   // value window [3,4] → SEQ 3,4
    }

    // ------------------------------------------------------------------ Item 2: per-file tx boundary

    /**
     * Item 2 (audit M-7): a later split's EFS 00301 failure trips the sibling fail-stop —
     * the earlier file was fully built + dispatched (committed in its own REQUIRES_NEW tx) and
     * the remaining sibling is never attempted (R004 parity). The earlier file's work is not
     * undone by the later failure.
     */
    @Test
    void laterSplitFailure_stopsBatch_earlierFileStandsCommitted() {
        when(payFileMstRepository.findByTranDtAndBizKeyAndFileSeqNo(TRAN_DT, BIZ_KEY, FILE_SEQ_NO))
                .thenReturn(Optional.of(payRow(ProcStat.WAIT.code())));
        when(firmConfRepository.findById(any())).thenReturn(Optional.of(conf("Y", 2)));
        when(payFileDataRepository.findByBizKeyAndTranDtAndFileSeqNo(BIZ_KEY, TRAN_DT, FILE_SEQ_NO))
                .thenReturn(details(5)); // ceil(5/2) = 3 files

        BulkReceptionService spy = spy(service);
        // File 1 succeeds; file 2's 00301 fails (gateway BusinessException).
        doReturn(null)
                .doThrow(new BusinessException("WBE_EFS_GATEWAY_ERROR", "G001"))
                .when(spy).issueReception(any());

        spy.receive(TRAN_DT, BIZ_KEY, FILE_SEQ_NO);

        // Only files 1 and 2 were attempted — file 3 skipped after the fail-stop.
        verify(spy, times(2)).issueReception(any());
        // Both files' MST rows were saved (file 1 committed in its own tx BEFORE file 2 failed).
        verify(firmFileMstRepository, org.mockito.Mockito.atLeast(2)).save(any());
        // The shared pay-file ends FAIL (file 2's failBoth).
        ArgumentCaptor<TbPayFileMst> payCap = ArgumentCaptor.forClass(TbPayFileMst.class);
        verify(payFileMstRepository, org.mockito.Mockito.atLeastOnce()).save(payCap.capture());
        assertEquals(ProcStat.FAIL.code(),
                payCap.getAllValues().get(payCap.getAllValues().size() - 1).getProcStat());
    }

    /**
     * Item 2 + H-3: an UNEXPECTED (non-{@code BusinessException}) throw while dispatching a later
     * split file. Only that file's {@code REQUIRES_NEW} tx rolls back; the earlier file's work was
     * already committed in its own tx.
     *
     * <p><b>BEHAVIOUR CHANGED 2026-07-29 (H-3 / R-2 / N-3) — this test was updated, not weakened.</b>
     * It previously asserted the throw <b>propagates</b> out of {@code receive()}. That was the defect:
     * the only caller is {@code DispatchAutoReceiveRegistrar}'s {@code afterCommit()} hook, which
     * catches and logs, so "propagates" meant "disappears into a log line" — while the pay-file, whose
     * H-4 claim had already committed {@code '9'→'6'} in a separate transaction, was left at
     * {@code '6'} forever. No recovery or monitoring query selected {@code '6'}, so the batch became
     * invisible.
     *
     * <p>{@code receive()} now catches the {@code RuntimeException} and records a terminal FAIL through
     * the conditional {@code failClaimedPayFile} update. The two properties this test always existed to
     * protect are unchanged and still asserted below: the earlier file stays committed, and the loop
     * stops rather than continuing to dispatch after an unexpected failure.
     */
    @Test
    void unexpectedThrowInLaterSplit_recordsTerminalFail_earlierFileAlreadySaved() {
        when(payFileMstRepository.findByTranDtAndBizKeyAndFileSeqNo(TRAN_DT, BIZ_KEY, FILE_SEQ_NO))
                .thenReturn(Optional.of(payRow(ProcStat.WAIT.code())));
        when(firmConfRepository.findById(any())).thenReturn(Optional.of(conf("Y", 2)));
        when(payFileDataRepository.findByBizKeyAndTranDtAndFileSeqNo(BIZ_KEY, TRAN_DT, FILE_SEQ_NO))
                .thenReturn(details(5));
        when(payFileMstRepository.failClaimedPayFile(any(), any(), any(), any(), any())).thenReturn(1);

        BulkReceptionService spy = spy(service);
        doReturn(null)
                .doThrow(new RuntimeException("unexpected codec blowup"))
                .when(spy).issueReception(any());

        // No longer propagates — rethrowing would only be re-swallowed by the registrar, leaving the
        // committed claim stranded. The failure is recorded instead.
        assertDoesNotThrow(() -> spy.receive(TRAN_DT, BIZ_KEY, FILE_SEQ_NO));

        // UNCHANGED: file 1's MST was saved before file 2 blew up, and file 3 was never attempted —
        // per-file REQUIRES_NEW keeps file 1 committed while the loop stops at the failure.
        verify(spy, times(2)).issueReception(any());
        verify(firmFileMstRepository, org.mockito.Mockito.atLeast(2)).save(any());

        // NEW: the pay-file reaches a terminal state instead of being stranded at '6'.
        //
        // It gets there via failBoth inside the per-file transaction — the inner RuntimeException
        // guard around the EFS leg — NOT via the outer failClaimedPayFile conditional update. That is
        // the better of the two paths and the one to assert here: it records the reason on the
        // firm-file row as well as the pay-file, and it commits with the rest of that file's work. The
        // outer guard is the backstop for throws that escape the per-file transaction entirely (e.g.
        // the round lookup, or the firm-file insert itself) — covered in
        // BulkReceptionStrandedFailureTest.escapingThrow_isRecordedThroughTheConditionalUpdate.
        ArgumentCaptor<TbPayFileMst> payCap = ArgumentCaptor.forClass(TbPayFileMst.class);
        verify(payFileMstRepository, org.mockito.Mockito.atLeastOnce()).save(payCap.capture());
        assertEquals(ProcStat.FAIL.code(),
                payCap.getAllValues().get(payCap.getAllValues().size() - 1).getProcStat(),
                "the shared pay-file must end FAIL, never be left at REQ('6')");
        verify(payFileMstRepository, never())
                .failClaimedPayFile(anyString(), anyString(), anyString(), anyString(), anyString());

        // The partial-dispatch signal lives in the ops alert on this path: file 1 already reached the
        // bank, so the batch must not read as a clean retryable failure.
        ArgumentCaptor<String> alert = ArgumentCaptor.forClass(String.class);
        verify(opsAlertService, org.mockito.Mockito.atLeastOnce()).notify(anyString(), alert.capture());
        assertTrue(alert.getAllValues().stream().anyMatch(b -> b.contains("FILES_DISPATCHED")),
                "expected the dispatched-file count in the ops alert, got: " + alert.getAllValues());
    }
}
