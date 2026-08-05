package com.kosign.wecms.domain.payment.dispatch.callback.controller;

import com.kosign.wecms.domain.payment.dispatch.callback.MissingCallbackFieldException;
import com.kosign.wecms.domain.payment.dispatch.callback.dto.EfsCallbackEnvelope;
import com.kosign.wecms.domain.payment.dispatch.callback.service.BulkExecutionResultService;
import com.kosign.wecms.domain.payment.dispatch.callback.service.BulkReceptionResultService;
import com.kosign.wecms.domain.payment.dispatch.constant.FirmBankingResultCd;
import com.kosign.wecms.domain.payment.dispatch.van.client.VanApiKeyNotProvisionedException;
import com.kosign.wecms.domain.payment.dispatch.van.dto.EfsEnvelope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Routing + DTO-binding tests for the inbound EFS callback controllers —
 * {@code standaloneSetup} (no Spring context, no DB, no security), matching the
 * repo idiom. Proves: path mapping, that the vendor-cased envelope fields
 * ({@code ID}/{@code EV}/{@code VV}/…) bind via {@code @JsonProperty}, and that
 * the controller delegates to its service. No business assertions (services are
 * mocked skeletons).
 */
@ExtendWith(MockitoExtension.class)
class BulkCallbackRoutingTest {

    @Mock
    BulkReceptionResultService bulkReceptionResultService;

    @Mock
    BulkExecutionResultService bulkExecutionResultService;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new BulkReceptionResultController(bulkReceptionResultService),
                new BulkExecutionResultController(bulkExecutionResultService)
        ).build();
    }

    private static EfsEnvelope ack() {
        return new EfsEnvelope("GV00000002", null, "20260608120000", "123",
                "EV", "VV", "AES", "HmacSHA256", "0000", "OK");
    }

    @Test
    void receptionResult_route_binds_envelope_and_delegates() throws Exception {
        when(bulkReceptionResultService.handle(any())).thenReturn(ack());

        mockMvc.perform(post("/api/v1/payment/dispatch/callbacks/bulk-reception-result")
                        .contentType("application/json")
                        .content("""
                                {"ID":"GV00000002","EV":"ENCRYPTED","VV":"HMAC","EM":"1","VM":"1",
                                 "RQ_DTIME":"20260608120000","TNO":"123","RC":"0000","RM":"OK","CNT":"1"}
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<EfsCallbackEnvelope> captor = ArgumentCaptor.forClass(EfsCallbackEnvelope.class);
        verify(bulkReceptionResultService).handle(captor.capture());
        assertThat(captor.getValue().id()).isEqualTo("GV00000002");
        assertThat(captor.getValue().ev()).isEqualTo("ENCRYPTED");
        assertThat(captor.getValue().rqDtime()).isEqualTo("20260608120000");
    }

    @Test
    void executionResult_route_binds_envelope_and_delegates() throws Exception {
        when(bulkExecutionResultService.handle(any())).thenReturn(ack());

        mockMvc.perform(post("/api/v1/payment/dispatch/callbacks/bulk-execution-result")
                        .contentType("application/json")
                        .content("{\"ID\":\"GV00000002\",\"EV\":\"ENC\",\"VV\":\"HMAC\",\"TNO\":\"9\"}"))
                .andExpect(status().isOk());

        verify(bulkExecutionResultService).handle(org.mockito.ArgumentMatchers.any());
    }

    // ── error path: the gateway must get an EFS envelope, never an ApiResponse ────────────────

    /**
     * The gateway parses only {@code ID/RQ_DTIME/TNO/EV/VV/EM/VM/RC/RM}. Before the controller-level
     * catch, an escaped exception reached {@code GlobalExceptionHandler} and the gateway received
     * HTTP 500 with a {@code WBE9999} {@code ApiResponse} body — unparseable, so the callback outcome
     * was undefined from the bank's side. Legacy sets {@code RC}/{@code RM} on the echoed request
     * envelope instead ({@code FILE_DEPOSIT_TRANSFER_API1_act.jsp:425-441}).
     */
    @Test
    void receptionResult_escapedException_returns_efs_error_envelope_not_apiResponse() throws Exception {
        // A genuinely unclassified fault. NOTE: an unprovisioned API key used to be thrown here as a
        // bare IllegalStateException and asserted as G999; since 2026-08-03 it has its own type and
        // maps to G003 (see unprovisionedApiKey_maps_to_G003_onBothLegs below), so this test now uses
        // an unrelated failure to exercise the G999 default.
        when(bulkReceptionResultService.handle(any()))
                .thenThrow(new IllegalStateException("EFS payload could not be parsed"));

        mockMvc.perform(post("/api/v1/payment/dispatch/callbacks/bulk-reception-result")
                        .contentType("application/json")
                        .content("""
                                {"ID":"GV00000002","EV":"ENCRYPTED","VV":"HMAC","EM":"AES","VM":"HmacSHA256",
                                 "RQ_DTIME":"20260608120000","TNO":"123"}
                                """))
                // Not 500: the envelope IS the acknowledgement, and its RC carries the outcome.
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RC").value("G999"))          // CD_ETC_ERR
                .andExpect(jsonPath("$.ID").value("GV00000002"))    // request fields echoed
                .andExpect(jsonPath("$.TNO").value("123"))
                .andExpect(jsonPath("$.EV").value("ENCRYPTED"))
                .andExpect(jsonPath("$.VV").value("HMAC"))
                .andExpect(jsonPath("$.RM").exists())
                // The internal WeCMS envelope must not appear on this endpoint at all.
                .andExpect(jsonPath("$.status").doesNotExist())
                .andExpect(jsonPath("$.trace_id").doesNotExist());
    }

    /**
     * {@code WBE_EFS_RESULT_FILE} is the 01302 handler's <b>normal</b> outcome for a missing / empty /
     * malformed result file, and the throw is load-bearing (it rolls the parse back so the file stays
     * replayable at {@code PROC_STEP='2'}). It must still leave the gateway with a parseable envelope.
     */
    @Test
    void executionResult_resultFileFailure_returns_efs_error_envelope() throws Exception {
        when(bulkExecutionResultService.handle(any()))
                .thenThrow(new com.kosign.wecms.common.exception.BusinessException(
                        "WBE_EFS_RESULT_FILE", "R_GV00000001_WRB_001_20251223 is empty"));

        mockMvc.perform(post("/api/v1/payment/dispatch/callbacks/bulk-execution-result")
                        .contentType("application/json")
                        .content("{\"ID\":\"GV00000002\",\"EV\":\"ENC\",\"VV\":\"HMAC\",\"TNO\":\"9\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RC").value("G999"))
                .andExpect(jsonPath("$.TNO").value("9"))
                .andExpect(jsonPath("$.status").doesNotExist());
    }

    /**
     * A missing required field is a payload rejection, not a general error — the distinction is what
     * tells the gateway the payload was wrong rather than that WeCMS broke. <b>Which</b> rejection code
     * depends on the field, and legacy's split is not what the constant names suggest.
     *
     * <p>On 01301, {@code file_nm} is one of the six fields legacy answers {@code G001} for
     * ({@code FILE_DEPOSIT_TRANSFER_API1_act.jsp:131-132},
     * {@code WCConst.CD_BASE_NONE_INPUT_DATA}). This assertion was {@code G002} until 2026-08-03,
     * when we answered {@code G002} for all eight fields.
     */
    @Test
    void receptionResult_missingBaseField_maps_to_G001() throws Exception {
        when(bulkReceptionResultService.handle(any()))
                .thenThrow(new MissingCallbackFieldException(
                        FirmBankingResultCd.BASE_NONE_INPUT_DATA,
                        FirmBankingResultCd.MSG_BASE_NONE_INPUT_DATA,
                        "01301 callback missing required field: file_nm"));

        mockMvc.perform(post("/api/v1/payment/dispatch/callbacks/bulk-reception-result")
                        .contentType("application/json")
                        .content("{\"ID\":\"GV00000002\",\"EV\":\"ENC\",\"VV\":\"HMAC\",\"TNO\":\"7\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RC").value("G001"));
    }

    /**
     * The other half of the split: {@code bank_cd} / {@code prtn_id} are the only two fields 01301
     * answers {@code G002} for ({@code API1_act.jsp:125-129},
     * {@code WCConst.CD_DETAIL_NONE_INPUT_DATA}).
     */
    @Test
    void receptionResult_missingDetailField_maps_to_G002() throws Exception {
        when(bulkReceptionResultService.handle(any()))
                .thenThrow(new MissingCallbackFieldException(
                        FirmBankingResultCd.DETAIL_NONE_INPUT_DATA,
                        FirmBankingResultCd.MSG_DETAIL_NONE_INPUT_DATA,
                        "01301 callback missing required field: bank_cd"));

        mockMvc.perform(post("/api/v1/payment/dispatch/callbacks/bulk-reception-result")
                        .contentType("application/json")
                        .content("{\"ID\":\"GV00000002\",\"EV\":\"ENC\",\"VV\":\"HMAC\",\"TNO\":\"7\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RC").value("G002"));
    }

    /**
     * An unprovisioned VAN key acknowledges {@code G003} on <b>both</b> legs — we answered
     * {@code G999} until 2026-08-03.
     *
     * <p>Legacy never row-counts the key lookup; it only tests {@code DomainUtil.isError} (a DB-fault
     * test, proven by {@code API1_act.jsp} running a <i>separate</i> {@code getResultCount == 0} check
     * on the file lookup twenty lines later). A missing key therefore yields {@code CRYPT_KEY = null},
     * {@code VerifyMac} fails, and both actions answer {@code CD_ENC_VRFY_ERR}:
     * {@code API1_act.jsp:99-110} and {@code API2_act.jsp:105-119} (via
     * {@code CommUtil.getApiKey}, {@code CommUtil.java:66-70}).
     */
    @Test
    void unprovisionedApiKey_maps_to_G003_onBothLegs() throws Exception {
        when(bulkReceptionResultService.handle(any()))
                .thenThrow(new VanApiKeyNotProvisionedException("no key for GV00000002 (B2)"));
        when(bulkExecutionResultService.handle(any()))
                .thenThrow(new VanApiKeyNotProvisionedException("no key for GV00000002 (B2)"));

        mockMvc.perform(post("/api/v1/payment/dispatch/callbacks/bulk-reception-result")
                        .contentType("application/json")
                        .content("{\"ID\":\"GV00000002\",\"EV\":\"ENC\",\"VV\":\"HMAC\",\"TNO\":\"7\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RC").value("G003"));

        mockMvc.perform(post("/api/v1/payment/dispatch/callbacks/bulk-execution-result")
                        .contentType("application/json")
                        .content("{\"ID\":\"GV00000002\",\"EV\":\"ENC\",\"VV\":\"HMAC\",\"TNO\":\"7\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RC").value("G003"));
    }

    /** A persistence fault is legacy's {@code CD_DB_ERR}. */
    @Test
    void executionResult_dataAccessFailure_maps_to_G004() throws Exception {
        when(bulkExecutionResultService.handle(any()))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("dup key"));

        mockMvc.perform(post("/api/v1/payment/dispatch/callbacks/bulk-execution-result")
                        .contentType("application/json")
                        .content("{\"ID\":\"GV00000002\",\"EV\":\"ENC\",\"VV\":\"HMAC\",\"TNO\":\"8\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RC").value("G004"));
    }
}
