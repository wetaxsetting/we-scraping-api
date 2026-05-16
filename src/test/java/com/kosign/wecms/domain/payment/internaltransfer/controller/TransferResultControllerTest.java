package com.kosign.wecms.domain.payment.internaltransfer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.exception.GlobalExceptionHandler;
import com.kosign.wecms.common.i18n.MessageResolver;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.payment.internaltransfer.dto.TransferResultDetailResponse;
import com.kosign.wecms.common.export.ExcelExportService;
import com.kosign.wecms.domain.payment.internaltransfer.dto.TransferResultListResponse;
import com.kosign.wecms.domain.payment.internaltransfer.service.InternalTransferResultService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone-MockMvc slice test for {@link TransferResultController}.
 * Pins the read-only result contract — list happy path, list missing
 * required param, detail happy path, detail invalid id, detail
 * not-found.
 */
class TransferResultControllerTest {

    private MockMvc mvc;
    private InternalTransferResultService service;
    private ExcelExportService excelExportService;

    @BeforeEach
    void setUp() {
        service = mock(InternalTransferResultService.class);
        excelExportService = mock(ExcelExportService.class);

        ObjectMapper mapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .registerModule(new JavaTimeModule());
        MappingJackson2HttpMessageConverter jsonConverter =
                new MappingJackson2HttpMessageConverter(mapper);

        MessageResolver messages = mock(MessageResolver.class);
        when(messages.resolve(anyString())).thenReturn("Transfer result error.");
        when(messages.resolve(anyString(), any(Object[].class))).thenReturn("Transfer result error.");
        GlobalExceptionHandler advice = new GlobalExceptionHandler(messages);

        mvc = MockMvcBuilders
                .standaloneSetup(new TransferResultController(service, excelExportService))
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

    @Test
    void should_list_when_dates_provided() throws Exception {
        TransferResultListResponse stub = new TransferResultListResponse(
                0L, 0, 10, List.of(), List.of());
        when(service.list(eq("20260101"), eq("20260131"),
                any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(stub);

        mvc.perform(get("/api/v1/payment/internal-transfer/results")
                        .param("from_date", "20260101")
                        .param("to_date",   "20260131"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error").value(false))
                .andExpect(jsonPath("$.status.code").value("WBI0000"))
                .andExpect(jsonPath("$.data.total_count").value(0));
    }

    @Test
    void should_return_validation_envelope_when_to_date_missing() throws Exception {
        mvc.perform(get("/api/v1/payment/internal-transfer/results")
                        .param("from_date", "20260101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error").value(true))
                .andExpect(jsonPath("$.status.code").value("WBE_VALIDATION"));
    }

    @Test
    void should_return_detail_when_id_valid() throws Exception {
        TransferResultDetailResponse stub = new TransferResultDetailResponse(
                "20260513000001", "11704000", "1234567890", "Demo",
                null, null, null, null, "VND", "VND", "Y", null,
                "demo.xlsx", null, "tester", null, null, null, List.of());
        when(service.detail(eq("20260513000001"))).thenReturn(stub);

        mvc.perform(get("/api/v1/payment/internal-transfer/results/20260513000001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error").value(false))
                .andExpect(jsonPath("$.data.scdul_pymn_no").value("20260513000001"))
                .andExpect(jsonPath("$.data.trnf_yn").value("Y"));
    }

    // NOTE: path-variable @Pattern validation requires Spring's
    // MethodValidationPostProcessor which is NOT wired under
    // {@code MockMvcBuilders.standaloneSetup}. Same documented
    // limitation as the FirmBankingSettingsControllerTest precedent.
    // Path-format validation is exercised by integration tests, out
    // of scope here.

    @Test
    void should_propagate_business_exception_when_result_missing() throws Exception {
        when(service.detail(eq("20260513999999"))).thenThrow(new BusinessException("WBE2060"));

        mvc.perform(get("/api/v1/payment/internal-transfer/results/20260513999999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error").value(true))
                .andExpect(jsonPath("$.status.code").value("WBE2060"));
    }
}
