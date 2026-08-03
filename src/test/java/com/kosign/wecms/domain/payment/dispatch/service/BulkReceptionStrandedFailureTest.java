package com.kosign.wecms.domain.payment.dispatch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.domain.payment.dispatch.alert.OpsAlertService;
import com.kosign.wecms.domain.payment.dispatch.constant.FirmBankingResultCd;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * H-3 / R-2 / N-3 — a pay-file claimed for dispatch must never be left permanently at
 * {@code PROC_STAT='6'}.
 *
 * <h2>The defect</h2>
 * {@code buildAndDispatchFile} caught only {@code BusinessException} around the EFS {@code 00301} leg.
 * Several realistic failures on that leg are <b>not</b> {@code BusinessException}:
 * {@code VanApiKeyService.getApiKey} throws {@code IllegalStateException} when {@code TB_VAN_API_INFO}
 * has no key for the VAN (the B2 provisioning gap), and the crypto / serialisation steps throw
 * {@code IllegalStateException} / {@code IllegalArgumentException}. Each of those escaped, rolled back
 * the per-file {@code REQUIRES_NEW} transaction — discarding the {@code TB_FIRM_FILE_MST} row <i>and</i>
 * any {@code failBoth} write attempted inside it — then escaped {@code receive()} to be swallowed by
 * {@code DispatchAutoReceiveRegistrar}'s catch-and-log.
 *
 * <p>The result was a pay-file at {@code '6'}: neither {@code '9'} (WAIT) nor {@code '3'} (FAIL). Every
 * recovery and monitoring query selected only {@code '9'}, so the row was invisible forever, and the
 * class Javadoc's "on any SFTP/API failure the pay-file is set to {@code PROC_STAT='3'}" was simply not
 * true for this class of failure.
 *
 * <p><b>N-3 — H-4 made this worse.</b> The atomic claim now commits {@code '9'→'6'} in its own
 * transaction <i>before</i> any file work, so the {@code '6'} can no longer roll back with the per-file
 * transaction. Every failure between the claim and a terminal status strands the row.
 *
 * <h2>What is asserted</h2>
 * The invariant, not the mechanism: after {@code receive()} returns, the pay-file is never still at
 * {@code '6'} — either a split file recorded {@code failBoth} inside its own committed transaction, or
 * the outer guard recorded the failure through {@code failClaimedPayFile} (guarded on {@code '6'}, so it
 * cannot overwrite a status something else legitimately owns).
 *
 * @see BulkReceptionConcurrentClaimTest the H-4 claim itself
 * @see com.kosign.wecms.domain.payment.dispatch.monitor.StuckPayFileMonitorServiceTest the {@code '6'}
 *      detection sweep that backs this up
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BulkReceptionStrandedFailureTest {

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
    /**
     * A mock manager makes the per-file {@code REQUIRES_NEW} {@code TransactionTemplate} a synchronous
     * pass-through that still <b>propagates</b> exceptions — which is precisely the behaviour under
     * test: an escaping throw must be caught by the outer guard, not by the transaction template.
     */
    @Mock org.springframework.transaction.PlatformTransactionManager perFileTxManager;

    private final EfsCryptoService crypto = new EfsCryptoService();
    private final FixedLengthCodec codec = spy(new FixedLengthCodec());
    private final SftpProperties sftpProperties = new SftpProperties();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private BulkDispatchClaimTxService claimTxService;
    private BulkReceptionService service;

    @BeforeEach
    void setUp() {
        // A REAL claim service over the mocked repository: the claim and the terminal fail-write are
        // both part of the invariant under test, so stubbing them away would hide the defect.
        claimTxService = new BulkDispatchClaimTxService(payFileMstRepository, firmFileMstRepository);
        service = new BulkReceptionService(
                efsApiClient, crypto, vanApiKeyService, efsProperties, objectMapper,
                payFileMstRepository, payFileDataRepository, firmConfRepository,
                firmFileMstRepository, firmFileDataRepository, firmBatApiTranRepository,
                dispatchKeyGenerator, codec, sftpClient, sftpProperties, bulkTransferValidator,
                opsAlertService, operatingHoursService, claimTxService,
                org.mockito.Mockito.mock(com.kosign.wecms.domain.payment.common.PaymentBlockedCharGuard.class),
                org.mockito.Mockito.mock(com.kosign.wecms.domain.payment.common.OutBankCdNormalizer.class),
                perFileTxManager);

        when(bulkTransferValidator.validate(any(), any())).thenReturn(Optional.empty());
        when(operatingHoursService.evaluateBulk(any(), any())).thenReturn(OperatingHoursDecision.DISPATCH);
        when(payFileMstRepository.claimForReception(any(), any(), any(), any())).thenReturn(1);
        when(payFileMstRepository.failClaimedPayFile(any(), any(), any(), any(), any())).thenReturn(1);
        when(firmFileMstRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(payFileMstRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(firmFileDataRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(firmBatApiTranRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(dispatchKeyGenerator.nextTranSeq(anyString(), anyString())).thenReturn("1");
        when(firmFileMstRepository.findByTranDtAndOutBankCdAndBankPrtnId(anyString(), anyString(), anyString()))
                .thenReturn(List.of());
        // A writable staging dir, so writeLocalFile is not itself the failure under test.
        sftpProperties.setLocalUploadPath(System.getProperty("java.io.tmpdir"));
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
        p.setReqCnt(BigDecimal.ONE);
        p.setReqAmt(new BigDecimal("1000"));
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

    private TbPayFileData detail(int seq) {
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
        d.setFincMdaOwnerNm("NGUYEN VAN A");
        return d;
    }

    /** One-line arrange: a claimable pay-file with {@code n} detail rows and the given branch config. */
    private void given(int n, String branchDiv, int limit) {
        when(payFileMstRepository.findByTranDtAndBizKeyAndFileSeqNo(TRAN_DT, BIZ_KEY, FILE_SEQ_NO))
                .thenReturn(Optional.of(payRow()));
        when(firmConfRepository.findById(any())).thenReturn(Optional.of(conf(branchDiv, limit)));
        java.util.List<TbPayFileData> details = new java.util.ArrayList<>();
        for (int i = 1; i <= n; i++) {
            details.add(detail(i));
        }
        when(payFileDataRepository.findByBizKeyAndTranDtAndFileSeqNo(BIZ_KEY, TRAN_DT, FILE_SEQ_NO))
                .thenReturn(details);
        when(payFileDataRepository.aggregateValidationCounts(anyString(), anyString(), anyString()))
                .thenReturn(org.mockito.Mockito.mock(BulkValidationCounts.class));
    }

    /**
     * Every terminal {@code TB_PAY_FILE_MST} status this run wrote.
     *
     * <p>Two-phase dispatch moved every post-claim terminal write onto the {@code '6'}-guarded
     * UPDATEs ({@code failClaimedPayFile}) — phase B holds no transaction, so an entity {@code save}
     * is no longer available to it and the detached row from phase A must not be written back. Both
     * guarded updates set {@code PROC_STAT='3'}, so any invocation of either is a FAIL for the
     * purposes of these tests; what they assert is that a FAIL was recorded at all rather than the row
     * being abandoned at the claimed {@code '6'}.
     */
    private List<String> savedPayStatuses() {
        int viaGuardedUpdate = org.mockito.Mockito.mockingDetails(payFileMstRepository).getInvocations()
                .stream()
                .filter(i -> i.getMethod().getName().equals("failClaimedPayFile"))
                .toList().size();
        return viaGuardedUpdate > 0
                ? java.util.stream.IntStream.range(0, viaGuardedUpdate)
                        .mapToObj(i -> ProcStat.FAIL.code()).toList()
                : List.of();
    }

    /* ── 1. VanApiKeyService throws IllegalStateException (the B2 provisioning gap) ── */

    @Test
    @DisplayName("1: a missing VAN API key does not leave the pay-file stranded at '6'")
    void missingApiKey_doesNotStrandThePayFile() {
        given(1, "N", 0);
        // The exact real-world trigger: TB_VAN_API_INFO has no API_KEY for this VAN.
        when(vanApiKeyService.getApiKey(anyString()))
                .thenThrow(new IllegalStateException(
                        "No TB_VAN_API_INFO.API_KEY provisioned for VAN_ORG_CD=" + VAN));

        assertThatCode(() -> service.receive(TRAN_DT, BIZ_KEY, FILE_SEQ_NO))
                .as("receive() must not propagate — the registrar would only swallow it")
                .doesNotThrowAnyException();

        // The split file's own guard converted it to a committed FAIL.
        assertThat(savedPayStatuses())
                .as("the pay-file must reach FAIL, never be left at REQ('6')")
                .contains(ProcStat.FAIL.code())
                .doesNotContain(ProcStat.REQ.code());
        // Nothing was sent: the failure is inside the EFS leg, after SFTP.
        verify(sftpClient, times(1)).upload(anyString(), anyString());
    }

    @Test
    @DisplayName("1b: the failure is recorded on the audit row too, not only on the pay-file")
    void missingApiKey_recordsTheAuditOutcome() {
        given(1, "N", 0);
        when(vanApiKeyService.getApiKey(anyString())).thenThrow(new IllegalStateException("no key"));

        service.receive(TRAN_DT, BIZ_KEY, FILE_SEQ_NO);

        // The 00301 audit row is written BEFORE the call and updated after; a runtime failure must
        // still close it out, or the ledger shows a request that never resolved.
        ArgumentCaptor<com.kosign.wecms.domain.payment.dispatch.entity.TbFirmBatApiTran> audit =
                ArgumentCaptor.forClass(com.kosign.wecms.domain.payment.dispatch.entity.TbFirmBatApiTran.class);
        verify(firmBatApiTranRepository, atLeastOnce()).save(audit.capture());
        assertThat(audit.getAllValues()).anySatisfy(a ->
                assertThat(a.getRsCd()).isEqualTo(FirmBankingResultCd.ETC_ERR));
    }

    /* ── 2. Serialization / encryption throws a runtime exception ── */

    @Test
    @DisplayName("2: a crypto/serialisation runtime failure does not strand the pay-file")
    void cryptoFailure_doesNotStrandThePayFile() {
        given(1, "N", 0);
        // A key of an invalid AES length makes EfsCryptoService throw IllegalArgumentException from
        // inside issueReception — a non-BusinessException on the same leg.
        when(vanApiKeyService.getApiKey(anyString())).thenReturn("too-short");

        assertThatCode(() -> service.receive(TRAN_DT, BIZ_KEY, FILE_SEQ_NO))
                .doesNotThrowAnyException();

        assertThat(savedPayStatuses())
                .contains(ProcStat.FAIL.code())
                .doesNotContain(ProcStat.REQ.code());
    }

    /* ── 3. The split-file transaction rolls back before TB_FIRM_FILE_MST persists ── */

    @Test
    @DisplayName("3: a pre-egress build failure fails the pay-file even though no firm-file row survives")
    void preEgressBuildFailure_stillReachesATerminalState() {
        given(1, "N", 0);
        // The realistic pre-egress throw: the codec refuses an over-width field rather than silently
        // truncating it (which on a fixed-length bank file would be a wrong-amount bug).
        doThrow(new IllegalArgumentException("field value exceeds fixed width 150"))
                .when(codec).encode(any(), any(), any(), any());

        assertThatCode(() -> service.receive(TRAN_DT, BIZ_KEY, FILE_SEQ_NO))
                .doesNotThrowAnyException();

        assertThat(savedPayStatuses())
                .contains(ProcStat.FAIL.code())
                .doesNotContain(ProcStat.REQ.code());
        // Nothing left the building — that is what makes failing terminally correct here.
        verify(sftpClient, never()).upload(anyString(), anyString());
        verify(efsApiClient, never()).send(anyString(), any());
    }

    @Test
    @DisplayName("3b: a throw escaping the per-file transaction is caught by the outer guard")
    void escapingThrow_isRecordedThroughTheConditionalUpdate() {
        given(1, "N", 0);
        // nextFileRound runs in receive(), OUTSIDE buildAndDispatchFile — so its failure cannot be
        // handled by any per-file catch and must be caught by the post-claim guard.
        when(firmFileMstRepository.findByTranDtAndOutBankCdAndBankPrtnId(anyString(), anyString(), anyString()))
                .thenThrow(new org.springframework.dao.QueryTimeoutException("round lookup timed out"));

        assertThatCode(() -> service.receive(TRAN_DT, BIZ_KEY, FILE_SEQ_NO))
                .doesNotThrowAnyException();

        // The per-file transaction is gone, so the terminal write must come from the conditional
        // update against the pay-file key — guarded on '6' so it cannot clobber anything else.
        ArgumentCaptor<String> rsMsg = ArgumentCaptor.forClass(String.class);
        verify(payFileMstRepository).failClaimedPayFile(
                eq(BIZ_KEY), eq(TRAN_DT), eq(FILE_SEQ_NO), rsMsg.capture(), anyString());
        assertThat(rsMsg.getValue())
                .as("bounded, non-sensitive: exception TYPE and context only")
                .contains("QueryTimeoutException")
                .doesNotContain("timed out")          // the raw message stays in the log
                .hasSizeLessThanOrEqualTo(200);       // TbPayFileMst.rsMsg length
    }

    /* ── 4. Existing BusinessException behaviour is unchanged ── */

    @Test
    @DisplayName("4: a BusinessException from the EFS leg behaves exactly as before")
    void businessExceptionBehaviourIsUnchanged() {
        given(1, "N", 0);
        when(vanApiKeyService.getApiKey(anyString())).thenReturn("webcashvietnam0002TEST$#%$&*$&^!");
        when(efsApiClient.send(anyString(), any()))
                .thenThrow(new BusinessException("WBE_EFS_GATEWAY_ERROR", "G999"));

        service.receive(TRAN_DT, BIZ_KEY, FILE_SEQ_NO);

        // Same outcome as always: a committed FAIL, not rethrown (legacy parity — the FAIL state
        // commits rather than propagating).
        assertThat(savedPayStatuses()).contains(ProcStat.FAIL.code());

        // It took the PER-FILE failure path, not receive()'s outer catch-all. The tell is the
        // firm-file row: the per-file path stamps BOTH rows (failFirmFileAndPayFile), while the outer
        // guard only knows the pay-file key and can stamp nothing else.
        //
        // UPDATED for two-phase dispatch: this used to assert `failClaimedPayFile` was never called,
        // because the per-file path wrote through save(entity) and only the outer guard used the
        // guarded UPDATE. Phase B now holds no transaction, so BOTH paths write through guarded
        // UPDATEs and that signal no longer separates them — the firm-file stamp does.
        verify(firmFileMstRepository).failFirmFile(eq(BIZ_KEY), eq(TRAN_DT), eq(FILE_SEQ_NO),
                anyString(), anyString(), anyString(), anyString());
    }

    /* ── 8. A lost claim has no side effects at all ── */

    @Test
    @DisplayName("8: when the claim is lost, nothing is built, sent, or written")
    void lostClaim_hasNoSideEffects() {
        given(1, "N", 0);
        when(payFileMstRepository.claimForReception(any(), any(), any(), any())).thenReturn(0);

        service.receive(TRAN_DT, BIZ_KEY, FILE_SEQ_NO);

        verify(firmFileMstRepository, never()).save(any());
        verify(sftpClient, never()).upload(anyString(), anyString());
        verify(efsApiClient, never()).send(anyString(), any());
        // Critically: the loser must not mark the winner's file failed.
        verify(payFileMstRepository, never())
                .failClaimedPayFile(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    /* ── 9. Partial dispatch must be visible as partial, not as a clean retryable batch ── */

    @Test
    @DisplayName("9: files 1..N-1 accepted then a runtime failure reports PARTIAL dispatch")
    void partialDispatch_isReportedAsPartial() {
        // 3 details, branch limit 1 → 3 split files, each its own upload + 00301.
        given(3, "Y", 1);
        when(vanApiKeyService.getApiKey(anyString())).thenReturn("webcashvietnam0002TEST$#%$&*$&^!");
        // Files 1 and 2 succeed; file 3's upload fails at the transport layer.
        doThrow(new com.kosign.wecms.domain.payment.dispatch.sftp.JschSftpClient.SftpTransferException(
                        "link down", null))
                .doNothing()
                .when(sftpClient).upload(anyString(), anyString());

        service.receive(TRAN_DT, BIZ_KEY, FILE_SEQ_NO);

        // The operator MUST be able to see that some files already reached the bank: a batch reported
        // as a clean failure would invite a blind re-drive and double-send those files.
        ArgumentCaptor<String> alert = ArgumentCaptor.forClass(String.class);
        verify(opsAlertService, atLeastOnce()).notify(eq(VAN), alert.capture());
        assertThat(alert.getAllValues())
                .as("the alert body must carry the dispatched-file count")
                .anySatisfy(body -> assertThat(body).contains("FILES_DISPATCHED"));
    }

    @Test
    @DisplayName("9b: a throw AFTER a file was dispatched reports the true count, not zero")
    void partialDispatch_countSurvivesAnEscapingThrow() {
        // REWRITTEN 2026-07-29 after independent verification. The previous version asserted only
        // `contains("files dispatched before failure:")`, which passes with the value "0" — so it could
        // not detect that the count was being LOST on the throw path. It also mis-described its own
        // scenario: with the gateway mock returning null, file 1 failed at issueReception and the
        // escape came from failBoth's second firm-file save, so zero really was the right answer there
        // and the test proved nothing about a partial dispatch.
        //
        // This version constructs a genuine partial dispatch and asserts the exact count. It failed
        // against the pre-fix code (reported 0 while one file was already at the bank) and passes now
        // that the counter is an accumulator rather than a return value.
        //
        // TRIGGER MOVED for two-phase dispatch (the assertion is unchanged). It used to fail file 2's
        // TB_FIRM_FILE_DATA insert. That can no longer produce a partial dispatch at all: every
        // file's rows are now inserted in phase A, before ANY upload, so a detail-insert failure rolls
        // the whole group back with nothing sent — which is the improvement, not a gap. The escaping
        // throw is now raised in phase B, where a partial dispatch is genuinely reachable:
        // writeApiTran's firmBatApiTranRepository.save sits outside dispatchOneFile's try blocks, so
        // file 2's audit insert escapes to receive()'s outer guard AFTER file 1 is already at the bank
        // — the only path on which the count can still be lost.
        given(3, "Y", 1);                                     // 3 details, limit 1 → 3 split files
        when(vanApiKeyService.getApiKey(anyString())).thenReturn("webcashvietnam0002TEST$#%$&*$&^!");
        // File 1: audit insert, then the post-00301 success update. File 2: audit insert throws.
        when(firmBatApiTranRepository.save(any()))
                .thenAnswer(i -> i.getArgument(0))
                .thenAnswer(i -> i.getArgument(0))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("audit insert failed"));

        BulkReceptionService spy = spy(service);
        doReturn(null).when(spy).issueReception(any());        // file 1's 00301 is ACCEPTED

        spy.receive(TRAN_DT, BIZ_KEY, FILE_SEQ_NO);

        // File 1 genuinely reached the bank — uploaded and 00301-accepted.
        verify(sftpClient, atLeastOnce()).upload(anyString(), anyString());
        verify(spy, atLeastOnce()).issueReception(any());

        // The recorded reason must say ONE file went, not zero. Reporting zero would tell the operator
        // nothing left the building and invite a blind re-drive of a file the bank already has.
        ArgumentCaptor<String> rsMsg = ArgumentCaptor.forClass(String.class);
        verify(payFileMstRepository, atLeastOnce()).failClaimedPayFile(
                anyString(), anyString(), anyString(), rsMsg.capture(), anyString());
        assertThat(rsMsg.getValue()).contains("files dispatched before failure: 1");

        // …and so must the ops alert, which is what a human actually reads.
        ArgumentCaptor<String> alert = ArgumentCaptor.forClass(String.class);
        verify(opsAlertService, atLeastOnce()).notify(anyString(), alert.capture());
        assertThat(alert.getAllValues())
                .anySatisfy(body -> assertThat(body).contains("FILES_DISPATCHED : 1 / 3"));
    }

    /* ── Guard-rail: Error must NOT be converted into a business FAIL ── */

    @Test
    @DisplayName("Error is not swallowed — only RuntimeException is treated as a business failure")
    void errorIsNotCaught() {
        given(1, "N", 0);
        doThrow(new StackOverflowError("synthetic")).when(codec).encode(any(), any(), any(), any());

        // Catching Throwable here would mean an OutOfMemoryError silently became "00301 failed", from
        // a handler that may itself be unable to run. Errors must propagate untouched.
        assertThatCode(() -> service.receive(TRAN_DT, BIZ_KEY, FILE_SEQ_NO))
                .isInstanceOf(StackOverflowError.class);
    }

    /* ── The terminal write must be conditional, never a blind overwrite ── */

    @Test
    @DisplayName("the terminal write is skipped when the row is no longer stranded")
    void terminalWriteIsConditional() {
        given(1, "N", 0);
        when(firmFileMstRepository.findByTranDtAndOutBankCdAndBankPrtnId(anyString(), anyString(), anyString()))
                .thenThrow(new org.springframework.dao.QueryTimeoutException("timeout"));
        // Simulate the row having already left '6' — the conditional UPDATE matches 0 rows.
        when(payFileMstRepository.failClaimedPayFile(any(), any(), any(), any(), any())).thenReturn(0);

        assertThatCode(() -> service.receive(TRAN_DT, BIZ_KEY, FILE_SEQ_NO))
                .as("a 0-row result is a normal outcome, not an error")
                .doesNotThrowAnyException();

        verify(payFileMstRepository).failClaimedPayFile(
                anyString(), anyString(), anyString(), contains("QueryTimeoutException"), anyString());
    }
}
