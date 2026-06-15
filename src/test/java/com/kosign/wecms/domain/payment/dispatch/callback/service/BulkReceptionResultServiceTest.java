package com.kosign.wecms.domain.payment.dispatch.callback.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.domain.payment.dispatch.callback.dto.BulkReceptionResultRequest;
import com.kosign.wecms.domain.payment.dispatch.callback.dto.EfsCallbackEnvelope;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmBatApiTran;
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
    private final EfsProperties props = new EfsProperties(); // partnerId blank → ID = vanOrgCd

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

    private BulkReceptionResultService service() {
        return new BulkReceptionResultService(efsApiClient, crypto, vanApiKeyService, props, mapper,
                firmFileMstRepository, firmBatApiTranRepository, dispatchKeyGenerator);
    }

    // ── issueExecution (00302 API request leg) ────────────────────────────────

    private static BulkExecutionApiCommand cmd() {
        return new BulkExecutionApiCommand(VAN_ORG, BANK_CD, PRTN_ID, FILE_NM, "3", "1500000", "0");
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
                VAN_ORG, BANK_CD, PRTN_ID, "  ", "3", "1500000", "0"); // blank fileNm

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
        when(firmFileMstRepository.findByTranDtAndFirmFileNmAndProcStepAndProcStat(
                anyString(), eq(FILE_NM), eq(ProcStep.REQ.code()), eq(ProcStat.REQ.code())))
                .thenReturn(Optional.of(file));
        when(firmFileMstRepository.findByTranDtAndBizKeyAndFileSeqNo(anyString(), eq(BIZ_KEY), eq(FILE_SEQ)))
                .thenReturn(List.of(file));
    }

    @Test
    void successful_01301_triggers_00302_inline_and_advances_step_to_2() throws Exception {
        TbFirmFileMst file = receptionFile();
        stubReceptionLookup(file);
        when(firmBatApiTranRepository.existsByTranDtAndBizKeyAndFirmFileNmAndApiTranTp(
                anyString(), eq(BIZ_KEY), eq(FILE_NM), eq(ApiTranTp.BULK_EXECUTION.code())))
                .thenReturn(false);
        when(efsApiClient.send(eq(EfsApiClient.EFS_302), any())).thenReturn(success302("0000"));

        service().handle(callbackOf(receptionReq("BT00", "3", "1500000")));

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
        when(firmBatApiTranRepository.existsByTranDtAndBizKeyAndFirmFileNmAndApiTranTp(
                anyString(), eq(BIZ_KEY), eq(FILE_NM), eq(ApiTranTp.BULK_EXECUTION.code())))
                .thenReturn(true);

        service().handle(callbackOf(receptionReq("BT00", "3", "1500000")));

        verify(efsApiClient, never()).send(eq(EfsApiClient.EFS_302), any());
        // Not advanced to execution-requested — stays at reception-success.
        assertThat(file.getProcStep()).isEqualTo(ProcStep.REQ_RECV.code());
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
        when(firmBatApiTranRepository.existsByTranDtAndBizKeyAndFirmFileNmAndApiTranTp(
                anyString(), eq(BIZ_KEY), eq(FILE_NM), eq(ApiTranTp.BULK_EXECUTION.code())))
                .thenReturn(false);
        // Gateway no-response (G999) → issueExecution throws; handle must NOT rethrow.
        when(efsApiClient.send(eq(EfsApiClient.EFS_302), any())).thenReturn(bareEnvelope("G999"));

        service().handle(callbackOf(receptionReq("BT00", "3", "1500000"))); // no throw

        // The 00302 was attempted (a marker row exists), but the file stays at reception-success
        // (PROC_STEP='1') — recovery is manual/monitoring, no automatic re-issue.
        verify(efsApiClient).send(eq(EfsApiClient.EFS_302), any());
        assertThat(file.getProcStep()).isEqualTo(ProcStep.REQ_RECV.code());
    }
}
