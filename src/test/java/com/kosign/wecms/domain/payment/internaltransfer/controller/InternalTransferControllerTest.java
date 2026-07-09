package com.kosign.wecms.domain.payment.internaltransfer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.exception.GlobalExceptionHandler;
import com.kosign.wecms.common.i18n.MessageResolver;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.payment.internaltransfer.dto.ApprovalAttachmentRow;
import com.kosign.wecms.domain.payment.internaltransfer.dto.CreateScheduledTransferResponse;
import com.kosign.wecms.domain.payment.internaltransfer.dto.ScheduledTransferDetailResponse;
import com.kosign.wecms.domain.payment.internaltransfer.dto.ScheduledTransferListResponse;
import com.kosign.wecms.domain.payment.internaltransfer.dto.TransferLineDetail;
import com.kosign.wecms.domain.payment.internaltransfer.service.InternalTransferDispatchService;
import com.kosign.wecms.domain.payment.internaltransfer.service.InternalTransferService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone-MockMvc slice test for {@link InternalTransferController}.
 * Pins the snake_case wire contract end-to-end (request params on the
 * way in + response JSON on the way out) plus the global exception
 * envelope for validation failures and business errors.
 *
 * <p>Mirrors the {@link com.kosign.wecms.domain.payment.approvalbox.controller.ApprovalBoxController}
 * test precedent — global SNAKE_CASE strategy wired into the Jackson
 * converter manually so the response bytes match production exactly
 * without spinning up the full Spring context (no DB, no Flyway, no
 * security filter).
 */
class InternalTransferControllerTest {

    private MockMvc mvc;
    private InternalTransferService         service;
    private InternalTransferDispatchService dispatchService;
    private com.kosign.wecms.domain.payment.match.service.DomesticPaymentLineMatchService lineMatchService;
    private com.kosign.wecms.domain.payment.internaltransfer.service.InternalTransferDownloadService downloadService;
    private com.kosign.wecms.domain.setting.erptransactionhistory.service.ErpTrnsHistoryWriter erpHistoryWriter;

    @BeforeEach
    void setUp() {
        service         = mock(InternalTransferService.class);
        dispatchService = mock(InternalTransferDispatchService.class);
        lineMatchService = mock(com.kosign.wecms.domain.payment.match.service.DomesticPaymentLineMatchService.class);
        downloadService = mock(com.kosign.wecms.domain.payment.internaltransfer.service.InternalTransferDownloadService.class);
        erpHistoryWriter = mock(com.kosign.wecms.domain.setting.erptransactionhistory.service.ErpTrnsHistoryWriter.class);

        ObjectMapper mapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .registerModule(new JavaTimeModule());
        MappingJackson2HttpMessageConverter jsonConverter =
                new MappingJackson2HttpMessageConverter(mapper);

        MessageResolver messages = mock(MessageResolver.class);
        when(messages.resolve(anyString())).thenReturn("Internal transfer error.");
        when(messages.resolve(anyString(), any(Object[].class))).thenReturn("Internal transfer error.");
        GlobalExceptionHandler advice = new GlobalExceptionHandler(messages);

        mvc = MockMvcBuilders
                .standaloneSetup(new InternalTransferController(service, dispatchService, lineMatchService, downloadService, erpHistoryWriter))
                .setMessageConverters(jsonConverter)
                .setControllerAdvice(advice)
                .build();

        TenantContext.set("tester", "tester", "VN_VNM0004_000001",
                LangCd.EN, TenantContext.AUTH_GR_USER, "F");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /* =================================================================
     *  GET /api/v1/payment/internal-transfer
     * ================================================================= */

    @Test
    void should_list_when_dates_provided() throws Exception {
        ScheduledTransferListResponse stub = new ScheduledTransferListResponse(
                0L, 0, 10, List.of(), List.of());
        when(service.list(eq("20260101"), eq("20260131"),
                any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(stub);

        mvc.perform(get("/api/v1/payment/internal-transfer")
                        .param("from_date", "20260101")
                        .param("to_date",   "20260131"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error").value(false))
                .andExpect(jsonPath("$.status.code").value("WBI0000"))
                .andExpect(jsonPath("$.data.total_count").value(0))
                .andExpect(jsonPath("$.data.rows").isArray());
    }

    @Test
    void should_return_validation_envelope_when_from_date_missing() throws Exception {
        mvc.perform(get("/api/v1/payment/internal-transfer")
                        .param("to_date", "20260131"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error").value(true))
                .andExpect(jsonPath("$.status.code").value("WBE_VALIDATION"));
    }

    /* =================================================================
     *  GET /api/v1/payment/internal-transfer/{id}
     * ================================================================= */

    @Test
    void should_return_detail_when_id_is_valid() throws Exception {
        ScheduledTransferDetailResponse stub = new ScheduledTransferDetailResponse(
                "20260513000001", "03", "11704000", null, "1234567890",
                "Demo", (BigDecimal) null, (BigDecimal) null, null, "tester",
                null, null, null, null, null, "N", null, "VND", "VND",
                "demo.xlsx", (BigDecimal) null, List.<TransferLineDetail>of(),
                List.<ApprovalAttachmentRow>of());
        when(service.detail(eq("20260513000001"))).thenReturn(stub);

        mvc.perform(get("/api/v1/payment/internal-transfer/20260513000001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error").value(false))
                .andExpect(jsonPath("$.data.scdul_pymn_no").value("20260513000001"))
                .andExpect(jsonPath("$.data.scdul_pymn_type_cd").value("03"))
                .andExpect(jsonPath("$.data.trnf_yn").value("N"));
    }

    // NOTE: path-variable @Pattern validation requires Spring's
    // MethodValidationPostProcessor which is NOT wired under
    // {@code MockMvcBuilders.standaloneSetup}. The @Pattern guards are
    // production code (and fire under a full Spring context) — they
    // are exercised by integration tests, out of scope here. Same
    // precedent as FirmBankingSettingsControllerTest's documented
    // limitation. Hence no "id contains letters" path-validation
    // test in this slice.

    @Test
    void should_propagate_business_exception_when_batch_missing() throws Exception {
        when(service.detail(eq("20260513999999"))).thenThrow(new BusinessException("WBE2060"));

        mvc.perform(get("/api/v1/payment/internal-transfer/20260513999999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error").value(true))
                .andExpect(jsonPath("$.status.code").value("WBE2060"));
    }

    /* =================================================================
     *  POST /api/v1/payment/internal-transfer (multipart create)
     * ================================================================= */

    @Test
    void should_create_when_request_part_is_valid() throws Exception {
        when(service.createDraft(any(), any()))
                .thenReturn(new CreateScheduledTransferResponse("20260513000001"));

        String body = """
                {
                  "titl": "Demo batch",
                  "bank_cd": "11704000",
                  "acct_no": "1234567890",
                  "ccy_cd": "VND",
                  "trnf_ccy_cd": "VND",
                  "lines": [
                    {
                      "tran_amt": 1000000,
                      "finc_mda_no": "9876543210"
                    }
                  ]
                }
                """;
        org.springframework.mock.web.MockMultipartFile requestPart =
                new org.springframework.mock.web.MockMultipartFile(
                        "request", "request", MediaType.APPLICATION_JSON_VALUE,
                        body.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/api/v1/payment/internal-transfer")
                        .file(requestPart))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error").value(false))
                .andExpect(jsonPath("$.data.scdul_pymn_no").value("20260513000001"));
    }

    @Test
    void should_reject_create_when_lines_empty() throws Exception {
        String body = """
                {
                  "titl": "Demo batch",
                  "bank_cd": "11704000",
                  "acct_no": "1234567890",
                  "ccy_cd": "VND",
                  "trnf_ccy_cd": "VND",
                  "lines": []
                }
                """;
        org.springframework.mock.web.MockMultipartFile requestPart =
                new org.springframework.mock.web.MockMultipartFile(
                        "request", "request", MediaType.APPLICATION_JSON_VALUE,
                        body.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/api/v1/payment/internal-transfer")
                        .file(requestPart))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error").value(true))
                .andExpect(jsonPath("$.status.code").value("WBE_VALIDATION"));
    }

    /* =================================================================
     *  PATCH /api/v1/payment/internal-transfer/{id}/lines/{lineId}/reporting-account
     * ================================================================= */

    @Test
    void should_update_reporting_account_when_request_valid() throws Exception {
        mvc.perform(patch(
                        "/api/v1/payment/internal-transfer/20260513000001/lines/000001/reporting-account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chart_acct_seq_no\":\"12\",\"chart_acct_cd\":\"COA-100\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error").value(false));
        verify(service).updateReportingAccount(eq("20260513000001"), eq("000001"), any());
    }

    @Test
    void should_reject_update_when_chart_acct_seq_no_blank() throws Exception {
        mvc.perform(patch(
                        "/api/v1/payment/internal-transfer/20260513000001/lines/000001/reporting-account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chart_acct_seq_no\":\"\",\"chart_acct_cd\":\"COA-100\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error").value(true))
                .andExpect(jsonPath("$.status.code").value("WBE_VALIDATION"));
    }

    /* =================================================================
     *  POST /api/v1/payment/internal-transfer/{id}/dispatch
     * ================================================================= */

    @Test
    void should_dispatch_when_request_valid() throws Exception {
        mvc.perform(post("/api/v1/payment/internal-transfer/20260513000001/dispatch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trnf_ccy_cd\":\"VND\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error").value(false));
        verify(dispatchService).dispatch(eq("20260513000001"), any());
    }

    @Test
    void should_reject_dispatch_when_currency_invalid() throws Exception {
        mvc.perform(post("/api/v1/payment/internal-transfer/20260513000001/dispatch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trnf_ccy_cd\":\"abc\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error").value(true))
                .andExpect(jsonPath("$.status.code").value("WBE_VALIDATION"));
    }

    /* =================================================================
     *  DELETE /api/v1/payment/internal-transfer/{id}
     * ================================================================= */

    @Test
    void should_delete_when_id_valid() throws Exception {
        mvc.perform(delete("/api/v1/payment/internal-transfer/20260513000001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error").value(false));
        verify(service).delete(eq("20260513000001"));
    }

    @Test
    void should_reject_delete_when_batch_under_approval() throws Exception {
        doThrow(new BusinessException("WBE2062")).when(service).delete(eq("20260513000001"));

        mvc.perform(delete("/api/v1/payment/internal-transfer/20260513000001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error").value(true))
                .andExpect(jsonPath("$.status.code").value("WBE2062"));
    }
}
