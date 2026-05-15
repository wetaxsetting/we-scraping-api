package com.kosign.wecms.domain.payment.approvalbox.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.exception.GlobalExceptionHandler;
import com.kosign.wecms.common.i18n.MessageResolver;
import com.kosign.wecms.common.export.ExcelExportService;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.payment.approvalbox.dto.ApprovalBoxActionResponse;
import com.kosign.wecms.domain.payment.approvalbox.dto.ApprovalBoxBulkApproveResponse;
import com.kosign.wecms.domain.payment.approvalbox.dto.ApprovalBoxListResponse;
import com.kosign.wecms.domain.payment.approvalbox.dto.ApprovalBoxTabCounts;
import com.kosign.wecms.domain.payment.approvalbox.service.ApprovalBoxService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone-MockMvc slice test for {@link ApprovalBoxController}.
 * Pins the snake_case wire contract end-to-end (request params on the
 * way in + response JSON on the way out), the global exception envelope
 * for missing required params, and routing for the approve / reject /
 * bulk-approve write paths.
 *
 * <p>Mirrors the {@code ErpTrnsHistoryControllerTest} setup — the
 * global SNAKE_CASE strategy is wired into the {@link MockMvc} Jackson
 * converter manually so the response bytes match production exactly
 * without spinning up the full Spring context.
 */
class ApprovalBoxControllerTest {

    private MockMvc mvc;
    private ApprovalBoxService service;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        service = mock(ApprovalBoxService.class);
        ExcelExportService excelExportService = mock(ExcelExportService.class);

        mapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .registerModule(new JavaTimeModule());
        MappingJackson2HttpMessageConverter jsonConverter =
                new MappingJackson2HttpMessageConverter(mapper);

        MessageResolver messages = mock(MessageResolver.class);
        when(messages.resolve(any(String.class))).thenReturn("Approval box error.");
        when(messages.resolve(any(String.class), any(Object[].class))).thenReturn("Approval box error.");
        GlobalExceptionHandler advice = new GlobalExceptionHandler(messages);

        mvc = MockMvcBuilders
                .standaloneSetup(new ApprovalBoxController(service, excelExportService))
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
     *  GET /api/v1/payment/approval-box
     * ================================================================= */

    @Test
    void list_happyPath_returnsSnakeCaseEnvelope() throws Exception {
        ApprovalBoxListResponse stub = new ApprovalBoxListResponse(
                List.of(),
                0L,
                new ApprovalBoxTabCounts(2L, 1L, 3L, 4L),
                0, 15, 0);
        when(service.list(eq("20260101"), eq("20260131"),
                any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(stub);

        mvc.perform(get("/api/v1/payment/approval-box")
                        .param("from_date", "20260101")
                        .param("to_date",   "20260131"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error").value(false))
                .andExpect(jsonPath("$.status.code").value("WBI0000"))
                .andExpect(jsonPath("$.data.total_count").value(0))
                .andExpect(jsonPath("$.data.tab_counts.pending").value(2))
                .andExpect(jsonPath("$.data.tab_counts.in_progress").value(1))
                .andExpect(jsonPath("$.data.tab_counts.approved").value(3))
                .andExpect(jsonPath("$.data.tab_counts.rejected").value(4));
    }

    @Test
    void list_missingFromDate_returnsValidationEnvelope() throws Exception {
        mvc.perform(get("/api/v1/payment/approval-box")
                        .param("to_date", "20260131"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error").value(true))
                .andExpect(jsonPath("$.status.code").value("WBE_VALIDATION"));
    }

    /* =================================================================
     *  POST /api/v1/payment/approval-box/{apprSeqNo}/approve
     * ================================================================= */

    @Test
    void approve_returnsActionResponse() throws Exception {
        when(service.approve("1540")).thenReturn(
                new ApprovalBoxActionResponse("1540", "I", 1L, 3L));

        mvc.perform(post("/api/v1/payment/approval-box/1540/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error").value(false))
                .andExpect(jsonPath("$.data.appr_seq_no").value("1540"))
                .andExpect(jsonPath("$.data.appr_stts").value("I"))
                .andExpect(jsonPath("$.data.apprvr_appr_tot_cnt").value(1))
                .andExpect(jsonPath("$.data.apprvr_tot_cnt").value(3));

        verify(service).approve("1540");
    }

    @Test
    void approve_surfacesBusinessCodeFromService() throws Exception {
        when(service.approve("1540")).thenThrow(new BusinessException("WBE2050"));

        mvc.perform(post("/api/v1/payment/approval-box/1540/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error").value(true))
                .andExpect(jsonPath("$.status.code").value("WBE2050"));
    }

    /* =================================================================
     *  POST /api/v1/payment/approval-box/{apprSeqNo}/reject
     * ================================================================= */

    @Test
    void reject_returnsActionResponse() throws Exception {
        when(service.reject("1540")).thenReturn(
                new ApprovalBoxActionResponse("1540", "R", 0L, 3L));

        mvc.perform(post("/api/v1/payment/approval-box/1540/reject"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error").value(false))
                .andExpect(jsonPath("$.data.appr_stts").value("R"));

        verify(service).reject("1540");
    }

    /* =================================================================
     *  POST /api/v1/payment/approval-box/bulk-approve
     * ================================================================= */

    @Test
    void bulkApprove_returnsPartialResult() throws Exception {
        ApprovalBoxBulkApproveResponse stub = new ApprovalBoxBulkApproveResponse(
                List.of(new ApprovalBoxActionResponse("1", "C", 1L, 1L)),
                List.of(new ApprovalBoxBulkApproveResponse.Failure("2", "WBE2050", "Approval not found.")));
        when(service.bulkApprove(any())).thenReturn(stub);

        String body = "{\"appr_seq_nos\":[\"1\",\"2\"]}";
        mvc.perform(post("/api/v1/payment/approval-box/bulk-approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error").value(false))
                .andExpect(jsonPath("$.data.successes[0].appr_seq_no").value("1"))
                .andExpect(jsonPath("$.data.successes[0].appr_stts").value("C"))
                .andExpect(jsonPath("$.data.failures[0].appr_seq_no").value("2"))
                .andExpect(jsonPath("$.data.failures[0].code").value("WBE2050"));
    }

    @Test
    void bulkApprove_emptyBody_rejectedByBeanValidation() throws Exception {
        String body = "{\"appr_seq_nos\":[]}";
        mvc.perform(post("/api/v1/payment/approval-box/bulk-approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error").value(true))
                .andExpect(jsonPath("$.status.code").value("WBE_VALIDATION"));
    }
}
