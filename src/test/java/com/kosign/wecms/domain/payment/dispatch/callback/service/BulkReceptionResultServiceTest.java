package com.kosign.wecms.domain.payment.dispatch.callback.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.domain.payment.dispatch.callback.dto.BulkReceptionResultRequest;
import com.kosign.wecms.domain.payment.dispatch.callback.dto.EfsCallbackEnvelope;
import com.kosign.wecms.domain.payment.dispatch.constant.FirmBankingResultCd;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmBatApiTran;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileMst;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmFileMst;
import com.kosign.wecms.domain.payment.dispatch.enums.ApiTranTp;
import com.kosign.wecms.domain.payment.dispatch.enums.ProcStat;
import com.kosign.wecms.domain.payment.dispatch.enums.ProcStep;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmBatApiTranRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmFileMstRepository;
import com.kosign.wecms.domain.payment.dispatch.service.DispatchKeyGenerator;
import com.kosign.wecms.domain.payment.dispatch.van.client.EfsApiClient;
import com.kosign.wecms.domain.payment.dispatch.van.client.EfsProperties;
import com.kosign.wecms.domain.payment.dispatch.van.client.VanApiKeyService;
import com.kosign.wecms.domain.payment.dispatch.van.crypto.EfsCryptoService;
import com.kosign.wecms.domain.payment.dispatch.van.dto.Efs302Response;
import com.kosign.wecms.domain.payment.dispatch.van.dto.EfsEnvelope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BulkReceptionResultService}.
 *
 * <ul>
 *   <li>The 00302 <b>API request leg</b> ({@code issueExecution}).</li>
 *   <li><b>K9 inline trigger</b> — {@code handle} (01301 → 00302) with the real
 *       {@link EfsCryptoService} + the published TEST key; the gateway, key
 *       service, repositories, and key generator are stubbed → no live network
 *       call, no real key, no DB.</li>
 * </ul>
 *
 * <p>Lenient strictness: the {@code handle} branches (success / mismatch / failed
 * / ignored) each exercise a different subset of the collaborators.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BulkReceptionResultServiceTest {

    private static final String TEST_KEY = "webcashvietnam0002TEST$#%$&*$&^!";
    private static final String VAN_ORG = "ORG000001";
    private static final String BANK_CD = "970424";
    private static final String PRTN_ID = "ORG000001";
    private static final String BIZ_KEY = "VN_VNM0004_000001";
    private static final String FILE_SEQ = "0000001";
    private static final String FILE_NM = "FB_20240101_0001.txt";

    private final EfsCryptoService crypto = new EfsCryptoService();
    private final ObjectMapper mapper = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    private final EfsProperties props = new EfsProperties(); // D-2: envelope ID is always TB_FIRM_CONF.VAN_ORG_CD (legacy-verified)

    @Mock
    EfsApiClient efsApiClient;
    @Mock
    VanApiKeyService vanApiKeyService;
    @Mock
    TbFirmFileMstRepository firmFileMstRepository;
    @Mock
    TbFirmBatApiTranRepository firmBatApiTranRepository;
    @Mock
    DispatchKeyGenerator dispatchKeyGenerator;
    @Mock
    com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileMstRepository payFileMstRepository;
    /** Legacy BATCH2 :266-280 ops alert on a permanent 00302 failure (restored 2026-07-29). */
    @Mock
    com.kosign.wecms.domain.payment.dispatch.alert.OpsAlertService opsAlertService;
    /** REQUIRES_NEW audit writer — the unmatched-callback path routes its row through it. */
    @Mock
    DispatchApiTranAuditWriter apiTranAuditWriter;

    /**
     * The 00302 fan-out now runs per sibling in {@link BulkExecutionInlineTxService}, not inline in the
     * handler. It is <b>not</b> mocked here: these tests assert the 01301 → 00302 chain end to end, so a
     * real instance is wired over the same mock repositories, with the handler's own
     * {@code this::issueExecution} as the sender (exactly the production wiring). Its transaction
     * annotations are inert without a Spring proxy, which is what
     * {@code BulkReceptionResultTransactionBoundaryTest} covers instead.
     */
    private BulkExecutionInlineTxService inlineTxService() {
        return new BulkExecutionInlineTxService(firmFileMstRepository, firmBatApiTranRepository,
                payFileMstRepository, apiTranAuditWriter, dispatchKeyGenerator, opsAlertService);
    }

    private BulkReceptionResultService service() {
        return new BulkReceptionResultService(efsApiClient, crypto, vanApiKeyService, props, mapper,
                firmFileMstRepository, firmBatApiTranRepository, dispatchKeyGenerator, opsAlertService,
                payFileMstRepository, inlineTxService(), apiTranAuditWriter);
    }

    // ── issueExecution (00302 API request leg) ────────────────────────────────

    private static BulkExecutionApiCommand cmd() {
        return new BulkExecutionApiCommand(VAN_ORG, BANK_CD, PRTN_ID, FILE_NM, "3", "1500000", "0", "0000042");
    }

    private EfsEnvelope success302(String rc) throws Exception {
        Efs302Response body = new Efs302Response(BANK_CD, PRTN_ID, FILE_NM, "3", "1500000", "0");
        String respJson = mapper.writeValueAsString(body);
        String ev = crypto.encryptEv(respJson, TEST_KEY, "20240101000000");
        String vv = crypto.buildVv(respJson, TEST_KEY);
        return new EfsEnvelope(VAN_ORG, null, "20240101000000", "TNO-RESP",
                ev, vv, "AES", "HmacSHA256", rc, "OK");
    }

    private EfsEnvelope bareEnvelope(String rc) {
        return new EfsEnvelope(VAN_ORG, null, "20240101000000", "TNO-RESP",
                null, null, "AES", "HmacSHA256", rc, "ERR");
    }

    @Test
    void success_maps_302_totals_and_assembles_envelope() throws Exception {
        when(vanApiKeyService.getApiKey(VAN_ORG)).thenReturn(TEST_KEY);
        when(efsApiClient.send(eq(EfsApiClient.EFS_302), any())).thenReturn(success302("0000"));

        BulkExecutionApiResult result = service().issueExecution(cmd());

        assertThat(result.fileNm()).isEqualTo(FILE_NM);
        assertThat(result.dataCnt()).isEqualTo("3");
        assertThat(result.dataAmt()).isEqualTo("1500000");

        ArgumentCaptor<EfsEnvelope> captor = ArgumentCaptor.forClass(EfsEnvelope.class);
        verify(efsApiClient).send(eq(EfsApiClient.EFS_302), captor.capture());
        String requestJson = crypto.decryptEv(captor.getValue().ev(), TEST_KEY);
        assertThat(requestJson)
                .contains("\"file_nm\":\"" + FILE_NM + "\"")
                .contains("\"data_cnt\":\"3\"")
                .contains("\"data_amt\":\"1500000\"");
    }

    @Test
    void non_0000_rc_throws_business_exception_and_does_not_decrypt() {
        when(vanApiKeyService.getApiKey(VAN_ORG)).thenReturn(TEST_KEY);
        when(efsApiClient.send(eq(EfsApiClient.EFS_302), any())).thenReturn(bareEnvelope("G001"));

        assertThatThrownBy(() -> service().issueExecution(cmd()))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("WBE_EFS_GATEWAY_ERROR");
    }

    @Test
    void missing_api_key_propagates_b2_failure_without_calling_gateway() {
        when(vanApiKeyService.getApiKey(VAN_ORG))
                .thenThrow(new IllegalStateException("No TB_VAN_API_INFO.API_KEY provisioned ... B2"));

        assertThatThrownBy(() -> service().issueExecution(cmd()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("B2");

        verify(efsApiClient, never()).send(any(), any());
    }

    @Test
    void invalid_input_rejected_before_key_lookup_or_gateway_call() {
        BulkExecutionApiCommand bad = new BulkExecutionApiCommand(
                VAN_ORG, BANK_CD, PRTN_ID, "  ", "3", "1500000", "0", "0000042"); // blank fileNm

        assertThatThrownBy(() -> service().issueExecution(bad))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(efsApiClient, vanApiKeyService);
    }

    // ── K9 inline 01301 → 00302 trigger ───────────────────────────────────────

    private static BulkReceptionResultRequest receptionReq(String resultCd, String dataCnt, String dataAmt) {
        return new BulkReceptionResultRequest(
                BANK_CD, PRTN_ID, FILE_NM, dataCnt, dataAmt, "0", resultCd, "OK");
    }

    private EfsCallbackEnvelope callbackOf(BulkReceptionResultRequest req) throws Exception {
        String json = mapper.writeValueAsString(req);
        String ev = crypto.encryptEv(json, TEST_KEY, "20240101000000");
        String vv = crypto.buildVv(json, TEST_KEY);
        return new EfsCallbackEnvelope(VAN_ORG, ev, vv, "AES", "HmacSHA256",
                "20240101000000", "TNO-CB", null, null, null);
    }

    /** A reception-requested file (PROC_STEP='0' / PROC_STAT='6'), 3 records / 1,500,000 VND. */
    private TbFirmFileMst receptionFile() {
        TbFirmFileMst f = new TbFirmFileMst();
        f.setTranDt("20240101");
        f.setBizKey(BIZ_KEY);
        f.setFileSeqNo(FILE_SEQ);
        f.setFirmFileNm(FILE_NM);
        f.setOutBankCd(BANK_CD);
        f.setBankPrtnId(PRTN_ID);
        f.setVanOrgCd(VAN_ORG);
        f.setProcStep(ProcStep.REQ.code());   // 0
        f.setProcStat(ProcStat.REQ.code());   // 6
        f.setReqCnt(new BigDecimal("3"));
        f.setReqAmt(new BigDecimal("1500000.00"));
        return f;
    }

    private void stubReceptionLookup(TbFirmFileMst file) {
        when(vanApiKeyService.getApiKey(VAN_ORG)).thenReturn(TEST_KEY);
        when(dispatchKeyGenerator.nextTranSeq(anyString(), anyString())).thenReturn("1000001");
        when(firmBatApiTranRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        // The 00302 marker is written through the REQUIRES_NEW writer, not the repository (FIX 2).
        when(apiTranAuditWriter.saveInNewTransaction(any())).thenAnswer(inv -> inv.getArgument(0));
        when(firmFileMstRepository.findByTranDtAndFirmFileNmAndProcStepAndProcStat(
                anyString(), eq(FILE_NM), eq(ProcStep.REQ.code()), eq(ProcStat.REQ.code())))
                .thenReturn(Optional.of(file));
        when(firmFileMstRepository.findByTranDtAndBizKeyAndFileSeqNo(anyString(), eq(BIZ_KEY), eq(FILE_SEQ)))
                .thenReturn(List.of(file));
        // The fan-out re-reads each sibling by PK from committed data instead of reusing the caller's
        // entity, so the PK lookup must resolve to the same instance the assertions inspect.
        when(firmFileMstRepository.findById(any())).thenReturn(Optional.of(file));
    }

    @Test
    void successful_01301_triggers_00302_inline_and_advances_step_to_2() throws Exception {
        TbFirmFileMst file = receptionFile();
        stubReceptionLookup(file);
        when(firmBatApiTranRepository.existsByBizKeyAndFirmFileNmAndApiTranTp(
                eq(BIZ_KEY), eq(FILE_NM), eq(ApiTranTp.BULK_EXECUTION.code())))
                .thenReturn(false);
        when(efsApiClient.send(eq(EfsApiClient.EFS_302), any())).thenReturn(success302("0000"));

        EfsEnvelope ackEnv = service().handle(callbackOf(receptionReq("BT00", "3", "1500000")));

        // A2 (audit C1): 01301 success ack — RC=0000, TNO echoed, EV REBUILT over the six echo
        // fields (decryptable; not the request EV, not carrying result_cd), VM/EM set.
        assertThat(ackEnv.rc()).isEqualTo("0000");
        assertThat(ackEnv.tno()).isEqualTo("TNO-CB");
        assertThat(ackEnv.em()).isEqualTo("AES");
        assertThat(ackEnv.vm()).isEqualTo("HmacSHA256");
        String ackJson = crypto.decryptEv(ackEnv.ev(), TEST_KEY);
        assertThat(ackJson)
                .contains("\"bank_cd\":\"" + BANK_CD + "\"")
                .contains("\"file_nm\":\"" + FILE_NM + "\"")
                .contains("\"data_cnt\":\"3\"")
                .doesNotContain("result_cd"); // only the six echo fields
        assertThat(crypto.verifyVv(ackEnv.ev(), ackEnv.vv(), TEST_KEY)).isTrue();

        // 00302 issued exactly once, carrying the requested totals.
        ArgumentCaptor<EfsEnvelope> captor = ArgumentCaptor.forClass(EfsEnvelope.class);
        verify(efsApiClient).send(eq(EfsApiClient.EFS_302), captor.capture());
        String req302 = crypto.decryptEv(captor.getValue().ev(), TEST_KEY);
        assertThat(req302).contains("\"data_cnt\":\"3\"").contains("\"data_amt\":\"1500000\"");

        // File advanced 0 → 1 (reception) → 2 (execution requested).
        assertThat(file.getProcStep()).isEqualTo(ProcStep.DEPO_REQ.code());

        // Both the 01301 and the 00302 audit legs were written.
        ArgumentCaptor<TbFirmBatApiTran> audits = ArgumentCaptor.forClass(TbFirmBatApiTran.class);
        verify(firmBatApiTranRepository, Mockito.atLeast(2)).save(audits.capture());
        assertThat(audits.getAllValues()).extracting(TbFirmBatApiTran::getApiTranTp)
                .contains(ApiTranTp.BULK_RECEPTION_RESULT.code(), ApiTranTp.BULK_EXECUTION.code());
    }

    @Test
    void duplicate_callback_does_not_double_execute_00302() throws Exception {
        TbFirmFileMst file = receptionFile();
        stubReceptionLookup(file);
        // A 00302 row already exists for this file → idempotent skip.
        when(firmBatApiTranRepository.existsByBizKeyAndFirmFileNmAndApiTranTp(
                eq(BIZ_KEY), eq(FILE_NM), eq(ApiTranTp.BULK_EXECUTION.code())))
                .thenReturn(true);

        service().handle(callbackOf(receptionReq("BT00", "3", "1500000")));

        verify(efsApiClient, never()).send(eq(EfsApiClient.EFS_302), any());
        // Not advanced to execution-requested — stays at reception-success.
        assertThat(file.getProcStep()).isEqualTo(ProcStep.REQ_RECV.code());
    }

    // ── C1 sibling fail-stop (G998) + C2 pay-file failure propagation (audit H-3 / M-1) ──

    @Test
    void siblingFailStop_whenPayFileAlreadyFailed_marksG998_noReceptionNoExecution() throws Exception {
        TbFirmFileMst file = receptionFile();
        stubReceptionLookup(file);
        // The shared pay-file is already FAIL (a prior split sibling failed) → C1 fail-stop.
        TbPayFileMst payFile = new TbPayFileMst();
        payFile.setProcStat(ProcStat.FAIL.code());
        when(payFileMstRepository.findByTranDtAndBizKeyAndFileSeqNo(anyString(), eq(BIZ_KEY), eq(FILE_SEQ)))
                .thenReturn(Optional.of(payFile));

        service().handle(callbackOf(receptionReq("BT00", "3", "1500000")));

        // No 00302 — partial money movement prevented.
        verify(efsApiClient, never()).send(eq(EfsApiClient.EFS_302), any());
        // This firm file is marked PROC_STEP='1'/PROC_STAT='3' with G998.
        assertThat(file.getProcStep()).isEqualTo(ProcStep.REQ_RECV.code());
        assertThat(file.getProcStat()).isEqualTo(ProcStat.FAIL.code());
        assertThat(file.getRegRsltCd()).isEqualTo(FirmBankingResultCd.TARGET_STAT_ERR); // G998
        // The G998 01301 audit row is keyed by the callback TNO (A1).
        ArgumentCaptor<TbFirmBatApiTran> audit = ArgumentCaptor.forClass(TbFirmBatApiTran.class);
        verify(firmBatApiTranRepository).save(audit.capture());
        assertThat(audit.getValue().getRsCd()).isEqualTo(FirmBankingResultCd.TARGET_STAT_ERR);
        assertThat(audit.getValue().getTranSeq()).isEqualTo("TNO-CB");
    }

    @Test
    void receptionFailure_propagatesFailToPayFile() throws Exception {
        TbFirmFileMst file = receptionFile();
        stubReceptionLookup(file);
        TbPayFileMst payFile = new TbPayFileMst();
        payFile.setProcStat(ProcStat.REQ.code()); // in-progress before the failure
        when(payFileMstRepository.findByTranDtAndBizKeyAndFileSeqNo(anyString(), eq(BIZ_KEY), eq(FILE_SEQ)))
                .thenReturn(Optional.of(payFile));
        when(payFileMstRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // result_cd != BT00 → reception failure.
        service().handle(callbackOf(receptionReq("BT99", "3", "1500000")));

        verify(efsApiClient, never()).send(eq(EfsApiClient.EFS_302), any());
        assertThat(file.getProcStat()).isEqualTo(ProcStat.FAIL.code()); // firm file FAIL
        // C2: the shared pay-file summary is also marked FAIL (so the UI reflects it + C1 trips next).
        ArgumentCaptor<TbPayFileMst> pay = ArgumentCaptor.forClass(TbPayFileMst.class);
        verify(payFileMstRepository).save(pay.capture());
        assertThat(pay.getValue().getProcStat()).isEqualTo(ProcStat.FAIL.code());
    }

    @Test
    void already_progressed_file_is_safely_ignored() throws Exception {
        when(vanApiKeyService.getApiKey(VAN_ORG)).thenReturn(TEST_KEY);
        // No file at PROC_STEP='0'/PROC_STAT='6' (already advanced or unknown).
        when(firmFileMstRepository.findByTranDtAndFirmFileNmAndProcStepAndProcStat(
                anyString(), eq(FILE_NM), eq(ProcStep.REQ.code()), eq(ProcStat.REQ.code())))
                .thenReturn(Optional.empty());

        service().handle(callbackOf(receptionReq("BT00", "3", "1500000"))); // no throw

        verify(efsApiClient, never()).send(any(), any());
        verify(firmFileMstRepository, never()).save(any());
        verifyNoInteractions(firmBatApiTranRepository);
    }

    @Test
    void mismatched_totals_fail_the_file_and_do_not_execute_00302() throws Exception {
        TbFirmFileMst file = receptionFile();
        stubReceptionLookup(file);

        // result_cd success but received count (2) != requested (3).
        service().handle(callbackOf(receptionReq("BT00", "2", "1500000")));

        verify(efsApiClient, never()).send(eq(EfsApiClient.EFS_302), any());
        assertThat(file.getProcStep()).isEqualTo(ProcStep.REQ_RECV.code());
        assertThat(file.getProcStat()).isEqualTo(ProcStat.FAIL.code());
        assertThat(file.getRegRsltCd()).isEqualTo("9999"); // FirmBankingResultCd.AMT_ERR
    }

    @Test
    void failed_reception_result_fails_the_file_and_does_not_execute_00302() throws Exception {
        TbFirmFileMst file = receptionFile();
        stubReceptionLookup(file);

        // result_cd != BT00 → reception itself failed.
        service().handle(callbackOf(receptionReq("BT99", "3", "1500000")));

        verify(efsApiClient, never()).send(eq(EfsApiClient.EFS_302), any());
        assertThat(file.getProcStep()).isEqualTo(ProcStep.REQ_RECV.code());
        assertThat(file.getProcStat()).isEqualTo(ProcStat.FAIL.code());
        assertThat(file.getRegRsltCd()).isEqualTo("BT99");
    }

    @Test
    void no_response_on_00302_leaves_file_at_step_1_for_manual_recovery() throws Exception {
        TbFirmFileMst file = receptionFile();
        stubReceptionLookup(file);
        when(firmBatApiTranRepository.existsByBizKeyAndFirmFileNmAndApiTranTp(
                eq(BIZ_KEY), eq(FILE_NM), eq(ApiTranTp.BULK_EXECUTION.code())))
                .thenReturn(false);
        // Gateway no-response (G999) → issueExecution throws; handle must NOT rethrow.
        when(efsApiClient.send(eq(EfsApiClient.EFS_302), any())).thenReturn(bareEnvelope("G999"));

        service().handle(callbackOf(receptionReq("BT00", "3", "1500000"))); // no throw

        // The 00302 was attempted (a marker row exists), but the file stays at reception-success
        // (PROC_STEP='1') — recovery is manual/monitoring, no automatic re-issue.
        verify(efsApiClient).send(eq(EfsApiClient.EFS_302), any());
        assertThat(file.getProcStep()).isEqualTo(ProcStep.REQ_RECV.code());
    }

    // ── FIX 1(b) / audit H-2 — a PERMANENT 00302 failure is a terminal FAIL, not "still processing" ──

    /**
     * Legacy {@code FILE_DEPOSIT_TRANSFER_BATCH2:264-304}: any code outside
     * {@code BT01}/{@code G999}/{@code C999} (and an empty body) falls into the {@code else} branch,
     * which marks the firm file {@code PROC_STEP='2'} + {@code PROC_STAT='3'} and the pay file
     * {@code PROC_STAT='3'}, and alerts. The refactor left both at {@code '6'}, so the UI showed a bulk
     * payment the bank had permanently rejected as still processing, indefinitely.
     *
     * <p>Note {@code PROC_STEP} advancing to {@code '2'} on a <i>failure</i> is legacy-correct, not a
     * typo: the record means "execution was instructed and the instruction failed". Verified against
     * {@code IDO.TB_FIRM_FILE_MST_U001.xml}, where {@code PROC_STEP} / {@code PROC_STAT} are
     * {@code SET} values (via {@code COALESCE}), not {@code WHERE} predicates.
     */
    @Test
    void permanent_00302_failure_marks_firm_file_and_pay_file_failed() throws Exception {
        TbFirmFileMst file = receptionFile();
        stubReceptionLookup(file);
        TbPayFileMst payFile = new TbPayFileMst();
        payFile.setProcStat(ProcStat.REQ.code());
        when(payFileMstRepository.findByTranDtAndBizKeyAndFileSeqNo(anyString(), eq(BIZ_KEY), eq(FILE_SEQ)))
                .thenReturn(Optional.of(payFile));
        when(payFileMstRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(firmBatApiTranRepository.existsByBizKeyAndFirmFileNmAndApiTranTp(
                eq(BIZ_KEY), eq(FILE_NM), eq(ApiTranTp.BULK_EXECUTION.code())))
                .thenReturn(false);
        // G001 is permanent — not in {BT01, G999, C999}.
        when(efsApiClient.send(eq(EfsApiClient.EFS_302), any())).thenReturn(bareEnvelope("G001"));

        service().handle(callbackOf(receptionReq("BT00", "3", "1500000"))); // no throw

        assertThat(file.getProcStep()).isEqualTo(ProcStep.DEPO_REQ.code());  // '2' — legacy parity
        assertThat(file.getProcStat()).isEqualTo(ProcStat.FAIL.code());      // '3'
        ArgumentCaptor<TbPayFileMst> pay = ArgumentCaptor.forClass(TbPayFileMst.class);
        verify(payFileMstRepository).save(pay.capture());
        assertThat(pay.getValue().getProcStat()).isEqualTo(ProcStat.FAIL.code());
        assertThat(pay.getValue().getRsMsg()).contains("G001");
        // Legacy alerts on this branch only.
        verify(opsAlertService).notify(eq(VAN_ORG), Mockito.contains("00302 permanent failure"));
    }

    /**
     * The transient half of the same split. Legacy {@code :232} {@code continue}s on
     * {@code BT01}/{@code G999}/{@code C999} without writing any status, because its next 60-second poll
     * would retry. The refactor has no retry, so the row stays {@code '1'/'6'} for the monitor to report
     * as {@code BULK_EXECUTION_UNCONFIRMED} — the same state, a different recovery mechanism. It must
     * NOT be marked FAIL: the bank may still execute it.
     */
    @Test
    void transient_00302_failure_does_not_mark_failed_and_does_not_alert() throws Exception {
        TbFirmFileMst file = receptionFile();
        stubReceptionLookup(file);
        when(firmBatApiTranRepository.existsByBizKeyAndFirmFileNmAndApiTranTp(
                eq(BIZ_KEY), eq(FILE_NM), eq(ApiTranTp.BULK_EXECUTION.code())))
                .thenReturn(false);
        when(efsApiClient.send(eq(EfsApiClient.EFS_302), any())).thenReturn(bareEnvelope("BT01"));

        service().handle(callbackOf(receptionReq("BT00", "3", "1500000")));

        assertThat(file.getProcStep()).isEqualTo(ProcStep.REQ_RECV.code()); // still '1'
        assertThat(file.getProcStat()).isEqualTo(ProcStat.REQ.code());      // still '6'
        verify(payFileMstRepository, never()).save(any());
        verifyNoInteractions(opsAlertService);
    }

    // ── FIX 1(a) — a RuntimeException from the 00302 leg is recorded, not propagated ──

    /**
     * The 00302 leg can fail with something that is not a {@code BusinessException}: an unprovisioned VAN
     * key ({@code IllegalStateException} from {@code VanApiKeyService} — the B2 gap), a crypto failure, a
     * serialize failure, or a transport error from {@code RestClientEfsApiClient}. The old catch covered
     * only {@code BusinessException}, so all of those escaped the sibling loop and rolled back the whole
     * {@code @Transactional handle()} — discarding committed reception state for siblings whose 00302 had
     * already reached the bank. The 00301 leg has caught both shapes since the H-3 fix; this is the same
     * boundary on 00302.
     *
     * <p><b>The outcome is TRANSIENT, deliberately</b> — the opposite of what the 00301 leg does with the
     * same exception shape. A {@code RuntimeException} means there was no gateway result code at all
     * (legacy's nearest analogue is its empty-body {@code continue} at {@code :202-206}), and egress is
     * ambiguous: the request may or may not have reached the bank. Marking a terminal FAIL on a bulk
     * payment the bank may have executed is the worse error, so the file is left at {@code '1'/'6'} where
     * the monitor reports it {@code BULK_EXECUTION_UNCONFIRMED} — "reconcile with the VAN Center before
     * acting". What the fix buys is that the failure is now <i>recorded and contained</i> instead of
     * taking the whole callback down with it.
     */
    @Test
    void runtimeException_from_00302_is_recorded_and_contained_not_propagated() throws Exception {
        TbFirmFileMst file = receptionFile();
        stubReceptionLookup(file);
        when(firmBatApiTranRepository.existsByBizKeyAndFirmFileNmAndApiTranTp(
                eq(BIZ_KEY), eq(FILE_NM), eq(ApiTranTp.BULK_EXECUTION.code())))
                .thenReturn(false);
        when(efsApiClient.send(eq(EfsApiClient.EFS_302), any()))
                .thenThrow(new IllegalStateException("EFS envelope unparseable"));

        // Must not throw out of the callback — this is the defect being fixed.
        EfsEnvelope ack = service().handle(callbackOf(receptionReq("BT00", "3", "1500000")));
        assertThat(ack.rc()).isEqualTo("0000"); // the RECEPTION succeeded; the ack reflects that

        // Recorded on the audit row as G999...
        ArgumentCaptor<TbFirmBatApiTran> audits = ArgumentCaptor.forClass(TbFirmBatApiTran.class);
        verify(firmBatApiTranRepository, Mockito.atLeastOnce()).save(audits.capture());
        assertThat(audits.getAllValues())
                .filteredOn(a -> ApiTranTp.BULK_EXECUTION.code().equals(a.getApiTranTp()))
                .extracting(TbFirmBatApiTran::getRsCd)
                .contains(FirmBankingResultCd.ETC_ERR);
        // ...and left transient: NOT a terminal FAIL, because the bank may have executed it.
        assertThat(file.getProcStep()).isEqualTo(ProcStep.REQ_RECV.code()); // '1'
        assertThat(file.getProcStat()).isEqualTo(ProcStat.REQ.code());      // '6'
        verify(payFileMstRepository, never()).save(any());
        verifyNoInteractions(opsAlertService);
    }

    /**
     * One sibling failing must not stop the others — legacy's per-row {@code continue}. Before the
     * boundary fix a throw on sibling 1 aborted the loop <i>and</i> rolled back sibling 2's committed
     * reception state; now each is issued independently.
     */
    @Test
    void oneSiblingFailing_doesNotBlockTheOthers() throws Exception {
        TbFirmFileMst a = receptionFile();
        a.setFirmFileNm("S_FB_A");
        a.setProcStep(ProcStep.REQ_RECV.code());
        TbFirmFileMst b = receptionFile();
        b.setFirmFileNm("S_FB_B");
        b.setProcStep(ProcStep.REQ_RECV.code());

        when(vanApiKeyService.getApiKey(VAN_ORG)).thenReturn(TEST_KEY);
        when(dispatchKeyGenerator.nextTranSeq(anyString(), anyString())).thenReturn("1000001");
        when(firmBatApiTranRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(apiTranAuditWriter.saveInNewTransaction(any())).thenAnswer(i -> i.getArgument(0));
        when(payFileMstRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        // The callback is for a third file that becomes reception-success during handle().
        TbFirmFileMst c = receptionFile();
        when(firmFileMstRepository.findByTranDtAndFirmFileNmAndProcStepAndProcStat(
                anyString(), eq(FILE_NM), eq(ProcStep.REQ.code()), eq(ProcStat.REQ.code())))
                .thenReturn(Optional.of(c));
        when(firmFileMstRepository.findByTranDtAndBizKeyAndFileSeqNo(anyString(), eq(BIZ_KEY), eq(FILE_SEQ)))
                .thenReturn(List.of(a, b));
        when(firmFileMstRepository.findById(any())).thenAnswer(inv -> {
            String nm = ((com.kosign.wecms.domain.payment.dispatch.entity.TbFirmFileMstId)
                    inv.getArgument(0)).getFirmFileNm();
            return Optional.of("S_FB_A".equals(nm) ? a : b);
        });
        when(firmBatApiTranRepository.existsByBizKeyAndFirmFileNmAndApiTranTp(
                anyString(), anyString(), eq(ApiTranTp.BULK_EXECUTION.code())))
                .thenReturn(false);
        // First sibling blows up unexpectedly; second must still be sent.
        when(efsApiClient.send(eq(EfsApiClient.EFS_302), any()))
                .thenThrow(new IllegalStateException("boom"))
                .thenReturn(success302("0000"));

        service().handle(callbackOf(receptionReq("BT00", "3", "1500000")));

        verify(efsApiClient, Mockito.times(2)).send(eq(EfsApiClient.EFS_302), any());
        assertThat(b.getProcStep())
                .as("the second sibling's 00302 succeeded and advanced it, unaffected by the first's failure")
                .isEqualTo(ProcStep.DEPO_REQ.code());
    }

    // ── FIX 2 — the idempotency marker commits BEFORE the gateway call ──

    /**
     * The guard against double-executing 00302 is a probe for an existing {@code TB_FIRM_BAT_API_TRAN}
     * row, which only works if that row outlives a failure of the callback transaction. Written with the
     * plain repository it rolled back with the caller, the guard then found nothing, and a gateway retry
     * re-sent 00302 <b>to the bank</b>. Routing it through the {@code REQUIRES_NEW} writer is what makes
     * "we have attempted this" durable before the attempt happens.
     */
    @Test
    void the_00302_marker_is_written_through_the_requiresNew_writer_before_the_call() throws Exception {
        TbFirmFileMst file = receptionFile();
        stubReceptionLookup(file);
        when(firmBatApiTranRepository.existsByBizKeyAndFirmFileNmAndApiTranTp(
                eq(BIZ_KEY), eq(FILE_NM), eq(ApiTranTp.BULK_EXECUTION.code())))
                .thenReturn(false);
        when(efsApiClient.send(eq(EfsApiClient.EFS_302), any())).thenReturn(success302("0000"));

        service().handle(callbackOf(receptionReq("BT00", "3", "1500000")));

        ArgumentCaptor<TbFirmBatApiTran> marker = ArgumentCaptor.forClass(TbFirmBatApiTran.class);
        verify(apiTranAuditWriter).saveInNewTransaction(marker.capture());
        assertThat(marker.getValue().getApiTranTp()).isEqualTo(ApiTranTp.BULK_EXECUTION.code());
        // No assertion that the captured row's RS_CD is still null: Mockito captures the live reference,
        // and the success path mutates that same instance afterwards (RS_CD='0000'). What actually
        // matters is the ORDERING below, which is the property the idempotency guard depends on.
        org.mockito.InOrder order = Mockito.inOrder(apiTranAuditWriter, efsApiClient);
        order.verify(apiTranAuditWriter).saveInNewTransaction(any());
        order.verify(efsApiClient).send(eq(EfsApiClient.EFS_302), any());
    }

    // ── FIX 4 — the empty-lookup branch is three outcomes, not one ──

    /** Case 1: a row exists at a later PROC_STEP → genuine replay → keep the success ack. */
    @Test
    void unmatched_butFileAlreadyProgressed_isAReplay_acksSuccess() throws Exception {
        when(vanApiKeyService.getApiKey(VAN_ORG)).thenReturn(TEST_KEY);
        when(apiTranAuditWriter.saveInNewTransaction(any())).thenAnswer(i -> i.getArgument(0));
        when(dispatchKeyGenerator.nextTranSeq(anyString(), anyString())).thenReturn("1000001");
        when(firmFileMstRepository.findByTranDtAndFirmFileNmAndProcStepAndProcStat(
                anyString(), eq(FILE_NM), eq(ProcStep.REQ.code()), eq(ProcStat.REQ.code())))
                .thenReturn(Optional.empty());
        TbFirmFileMst progressed = receptionFile();
        progressed.setProcStep(ProcStep.DEPO_REQ.code()); // '2' — already executed
        when(firmFileMstRepository.findByTranDtAndFirmFileNm(anyString(), eq(FILE_NM)))
                .thenReturn(List.of(progressed));

        EfsEnvelope ack = service().handle(callbackOf(receptionReq("BT00", "3", "1500000")));

        assertThat(ack.rc()).isEqualTo(FirmBankingResultCd.SUCC);
        // Audited even though nothing was processed, and through the REQUIRES_NEW writer.
        verify(apiTranAuditWriter).saveInNewTransaction(any());
        verify(efsApiClient, never()).send(any(), any());
        verifyNoInteractions(opsAlertService);
    }

    /**
     * Case 2: no row with this FIRM_FILE_NM in any state. Acking {@code 0000} told the gateway to stop
     * and lost the reception result with nothing recorded. Now: a retryable RC so it resends, plus an ops
     * alert — no audit row is possible because {@code TB_FIRM_BAT_API_TRAN.BIZ_KEY} is a NOT NULL PK
     * column and an unmatched callback resolves no tenant.
     */
    @Test
    void unmatched_unknownFile_answersRetryable_andAlerts_withNoAuditRow() throws Exception {
        when(vanApiKeyService.getApiKey(VAN_ORG)).thenReturn(TEST_KEY);
        when(firmFileMstRepository.findByTranDtAndFirmFileNmAndProcStepAndProcStat(
                anyString(), eq(FILE_NM), eq(ProcStep.REQ.code()), eq(ProcStat.REQ.code())))
                .thenReturn(Optional.empty());
        when(firmFileMstRepository.findByTranDtAndFirmFileNm(anyString(), eq(FILE_NM)))
                .thenReturn(List.of());

        EfsEnvelope ack = service().handle(callbackOf(receptionReq("BT00", "3", "1500000")));

        assertThat(ack.rc())
                .as("must NOT be 0000 — that is the 'stop resending' signal and the result would be lost")
                .isEqualTo(FirmBankingResultCd.ETC_ERR);
        verify(opsAlertService).notify(eq(VAN_ORG), Mockito.contains("unmatched callback"));
        verifyNoInteractions(apiTranAuditWriter);
        verify(firmFileMstRepository, never()).save(any());
    }

    /** Case 3: the row exists but in a state the callback cannot act on → retryable, and audited. */
    @Test
    void unmatched_unexpectedState_answersRetryable_andAudits() throws Exception {
        when(vanApiKeyService.getApiKey(VAN_ORG)).thenReturn(TEST_KEY);
        when(apiTranAuditWriter.saveInNewTransaction(any())).thenAnswer(i -> i.getArgument(0));
        when(dispatchKeyGenerator.nextTranSeq(anyString(), anyString())).thenReturn("1000001");
        when(firmFileMstRepository.findByTranDtAndFirmFileNmAndProcStepAndProcStat(
                anyString(), eq(FILE_NM), eq(ProcStep.REQ.code()), eq(ProcStat.REQ.code())))
                .thenReturn(Optional.empty());
        TbFirmFileMst odd = receptionFile();
        odd.setProcStep(ProcStep.REQ.code());          // still '0' ...
        odd.setProcStat(ProcStat.FAIL.code());          // ... but FAIL, so R004 missed it
        when(firmFileMstRepository.findByTranDtAndFirmFileNm(anyString(), eq(FILE_NM)))
                .thenReturn(List.of(odd));

        EfsEnvelope ack = service().handle(callbackOf(receptionReq("BT00", "3", "1500000")));

        assertThat(ack.rc()).isEqualTo(FirmBankingResultCd.ETC_ERR);
        ArgumentCaptor<TbFirmBatApiTran> audit = ArgumentCaptor.forClass(TbFirmBatApiTran.class);
        verify(apiTranAuditWriter).saveInNewTransaction(audit.capture());
        assertThat(audit.getValue().getApiTranTp()).isEqualTo(ApiTranTp.BULK_RECEPTION_RESULT.code());
        assertThat(audit.getValue().getRsCd()).isEqualTo(FirmBankingResultCd.ETC_ERR);
        assertThat(audit.getValue().getTranSeq()).isEqualTo("TNO-CB"); // keyed by the callback TNO (A1)
    }

    // ── FIX J — case 4: the name is AMBIGUOUS (more than one row carries it) ──
    //
    // C-2 split the empty-lookup branch into three outcomes reasoning about ONE row. Under a
    // FIRM_FILE_NM collision the branch sees several, and the two mechanisms it uses to classify —
    // `byName.get(0)` (arbitrary; the query has no ORDER BY) and `anyMatch` (ANY row's progress) —
    // then answer the wrong question. The dangerous combination is one row advanced + one row not:
    // the callback is acked RC=0000 on the strength of the OTHER row, and RC=0000 means "stop
    // resending", so the reception result for the row that did NOT advance is lost with no retry.
    //
    // Reachability is established, not hypothetical: the round is allocated per
    // (TRAN_DT, OUT_BANK_CD, BANK_PRTN_ID) while the name encodes only (VAN_ORG_CD, FILE_TYPE), and
    // JEX DEV holds two TB_FIRM_CONF rows differing only in BANK_PRTN_ID under GV00000001/SHB.

    /** The dangerous shape: X advanced, Y did not. Must NOT ack success off X's progress. */
    @Test
    void ambiguousName_oneRowAdvancedOneNot_answersRetryable_alerts_andDoesNotAudit() throws Exception {
        when(vanApiKeyService.getApiKey(VAN_ORG)).thenReturn(TEST_KEY);
        when(firmFileMstRepository.findByTranDtAndFirmFileNmAndProcStepAndProcStat(
                anyString(), eq(FILE_NM), eq(ProcStep.REQ.code()), eq(ProcStat.REQ.code())))
                .thenReturn(Optional.empty());

        TbFirmFileMst advanced = receptionFile();
        advanced.setFileSeqNo("101");
        advanced.setBankPrtnId("WEBC1");
        advanced.setProcStep(ProcStep.DEPO_REQ.code());   // '2' — this one is done
        TbFirmFileMst notAdvanced = receptionFile();
        notAdvanced.setFileSeqNo("102");
        notAdvanced.setBankPrtnId("000001106");           // the OTHER partner id — the collision axis
        notAdvanced.setProcStep(ProcStep.REQ.code());     // still '0' ...
        notAdvanced.setProcStat(ProcStat.FAIL.code());    // ... in a state R004 does not match
        when(firmFileMstRepository.findByTranDtAndFirmFileNm(anyString(), eq(FILE_NM)))
                .thenReturn(List.of(advanced, notAdvanced));

        EfsEnvelope ack = service().handle(callbackOf(receptionReq("BT00", "3", "1500000")));

        assertThat(ack.rc())
                .as("RC=0000 here would consume a callback that may belong to the row still at '0' "
                        + "and lose its reception result with no retry")
                .isEqualTo(FirmBankingResultCd.ETC_ERR);
        // No audit row: BIZ_KEY is a NOT NULL PK column and the tenant is ambiguous, so writing one
        // would mean guessing a tenant for a payment-ledger entry — the same objection the no-row case
        // already records against inventing one.
        verifyNoInteractions(apiTranAuditWriter);
        verify(firmFileMstRepository, never()).save(any());
        // Escalated, with both rows identified by FILE_SEQ_NO + BANK_PRTN_ID so a human can act.
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(opsAlertService).notify(eq(VAN_ORG), body.capture());
        assertThat(body.getValue())
                .contains("ambiguous FIRM_FILE_NM")
                .contains("MATCHING_ROWS : 2")
                .contains("fileSeqNo=101")
                .contains("fileSeqNo=102")
                .contains("bankPrtnId=WEBC1")
                .contains("bankPrtnId=000001106");
    }

    /** Both advanced: still ambiguous. Reaching the right answer by luck is not a pass. */
    @Test
    void ambiguousName_bothRowsAdvanced_stillAnswersRetryable() throws Exception {
        when(vanApiKeyService.getApiKey(VAN_ORG)).thenReturn(TEST_KEY);
        when(firmFileMstRepository.findByTranDtAndFirmFileNmAndProcStepAndProcStat(
                anyString(), eq(FILE_NM), eq(ProcStep.REQ.code()), eq(ProcStat.REQ.code())))
                .thenReturn(Optional.empty());
        TbFirmFileMst a = receptionFile();
        a.setFileSeqNo("101");
        a.setProcStep(ProcStep.DEPO_REQ.code());
        TbFirmFileMst b = receptionFile();
        b.setFileSeqNo("102");
        b.setProcStep(ProcStep.DEPO_RES_RECV.code());
        when(firmFileMstRepository.findByTranDtAndFirmFileNm(anyString(), eq(FILE_NM)))
                .thenReturn(List.of(a, b));

        EfsEnvelope ack = service().handle(callbackOf(receptionReq("BT00", "3", "1500000")));

        // A "replay" ack would be defensible for either row individually, but we cannot know the
        // callback was not about a third state we have mis-read — and the gateway retrying costs
        // nothing here, whereas a wrong 0000 is unrecoverable.
        assertThat(ack.rc()).isEqualTo(FirmBankingResultCd.ETC_ERR);
        verify(opsAlertService).notify(eq(VAN_ORG), Mockito.contains("ambiguous FIRM_FILE_NM"));
        verifyNoInteractions(apiTranAuditWriter);
    }

    /** The single-row cases must be untouched — this fix narrows, it does not re-route. */
    @Test
    void singleRow_stillClassifiesAsBefore_replayAcksSuccess() throws Exception {
        when(vanApiKeyService.getApiKey(VAN_ORG)).thenReturn(TEST_KEY);
        when(apiTranAuditWriter.saveInNewTransaction(any())).thenAnswer(i -> i.getArgument(0));
        when(dispatchKeyGenerator.nextTranSeq(anyString(), anyString())).thenReturn("1000001");
        when(firmFileMstRepository.findByTranDtAndFirmFileNmAndProcStepAndProcStat(
                anyString(), eq(FILE_NM), eq(ProcStep.REQ.code()), eq(ProcStat.REQ.code())))
                .thenReturn(Optional.empty());
        TbFirmFileMst progressed = receptionFile();
        progressed.setProcStep(ProcStep.DEPO_REQ.code());
        when(firmFileMstRepository.findByTranDtAndFirmFileNm(anyString(), eq(FILE_NM)))
                .thenReturn(List.of(progressed));

        EfsEnvelope ack = service().handle(callbackOf(receptionReq("BT00", "3", "1500000")));

        assertThat(ack.rc()).isEqualTo(FirmBankingResultCd.SUCC);
        verify(apiTranAuditWriter).saveInNewTransaction(any());
        verifyNoInteractions(opsAlertService);
    }
}
