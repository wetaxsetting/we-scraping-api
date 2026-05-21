package com.kosign.wecms.domain.banking.transferhistory.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.exception.GlobalExceptionHandler;
import com.kosign.wecms.common.i18n.MessageResolver;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.transferhistory.dto.TransferHistoryListRequest;
import com.kosign.wecms.domain.banking.transferhistory.dto.TransferHistoryListResponse;
import com.kosign.wecms.domain.banking.transferhistory.dto.TransferHistoryRow;
import com.kosign.wecms.domain.banking.transferhistory.service.TransferHistoryQueryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone-MockMvc slice test for {@link TransferHistoryController}.
 *
 * <p>Mirrors the {@code ChartOfAccountsControllerTest} pattern: Jackson
 * is configured manually with {@code SNAKE_CASE} so the response bytes
 * match production exactly, and the controller is wrapped in a
 * {@code MethodValidationPostProcessor} so the {@code @Validated} +
 * {@code @Pattern} guards on the {@code @RequestParam}s fire as
 * {@code ConstraintViolationException} → {@code WBE_VALIDATION}.
 */
class TransferHistoryControllerTest {

    private MockMvc mvc;
    private TransferHistoryQueryService service;

    @BeforeEach
    void setUp() {
        service = mock(TransferHistoryQueryService.class);

        ObjectMapper mapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .registerModule(new JavaTimeModule());
        MappingJackson2HttpMessageConverter jsonConverter =
                new MappingJackson2HttpMessageConverter(mapper);

        MessageResolver messages = mock(MessageResolver.class);
        when(messages.resolve(any(String.class))).thenReturn("Transfer history error.");
        when(messages.resolve(any(String.class), any(Object[].class)))
                .thenReturn("Transfer history error.");
        GlobalExceptionHandler advice = new GlobalExceptionHandler(messages);

        TransferHistoryController controller = new TransferHistoryController(service);

        // Apply MethodValidationPostProcessor so @Validated + @Pattern
        // / @NotBlank on @RequestParam fire as ConstraintViolationException
        // — without it MockMvc would silently let invalid params through.
        MethodValidationPostProcessor processor = new MethodValidationPostProcessor();
        processor.afterPropertiesSet();
        Object proxied = processor.postProcessAfterInitialization(controller, "transferHistoryController");

        mvc = MockMvcBuilders
                .standaloneSetup(proxied)
                .setMessageConverters(jsonConverter)
                .setControllerAdvice(advice)
                .build();

        TenantContext.set("tester", "VN_VNM0004_000001", LangCd.EN);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /* =================================================================
     *  Happy path
     * ================================================================= */

    @Test
    void list_happyPathSerialisesSnakeCase() throws Exception {
        TransferHistoryRow row = new TransferHistoryRow(
                1L,
                "20260515", "1030",
                "11704088", "Shinhan Bank", "0001-00000569-14",
                "970418",   "BIDV",         "4001-00234569-14",
                "Bob",
                "VND",
                new BigDecimal("50000.000000"),
                BigDecimal.ZERO,
                "2", "salary",
                "00000000", null,
                "Alice Operator");
        TransferHistoryListResponse payload = new TransferHistoryListResponse(
                1L,
                new BigDecimal("50000.000000"),
                BigDecimal.ZERO,
                1L, 0L,
                List.of(row));
        when(service.list(any(TransferHistoryListRequest.class))).thenReturn(payload);

        mvc.perform(get("/api/v1/banking/internal-transfer/transfer-history")
                        .param("from_dt", "20260501")
                        .param("to_dt",   "20260520"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error",       is(false)))
                .andExpect(jsonPath("$.status.code",        is("WBI0000")))
                .andExpect(jsonPath("$.data.total_count",   is(1)))
                .andExpect(jsonPath("$.data.total_vnd_amount", is(50000.0)))
                .andExpect(jsonPath("$.data.total_vnd_count",  is(1)))
                .andExpect(jsonPath("$.data.total_usd_count",  is(0)))
                .andExpect(jsonPath("$.data.rows[0].seq_no",       is(1)))
                .andExpect(jsonPath("$.data.rows[0].trns_dt",      is("20260515")))
                .andExpect(jsonPath("$.data.rows[0].trns_tm",      is("1030")))
                .andExpect(jsonPath("$.data.rows[0].out_bank_code", is("11704088")))
                .andExpect(jsonPath("$.data.rows[0].out_bank_nm",   is("Shinhan Bank")))
                .andExpect(jsonPath("$.data.rows[0].in_bank_nm",    is("BIDV")))
                .andExpect(jsonPath("$.data.rows[0].ccy_cd",       is("VND")))
                .andExpect(jsonPath("$.data.rows[0].trns_stts",    is("00000000")))
                .andExpect(jsonPath("$.data.rows[0].user_nm",      is("Alice Operator")));
    }

    /* =================================================================
     *  Validation envelope
     * ================================================================= */

    @Test
    void list_missingFromDtReturnsValidationEnvelope() throws Exception {
        mvc.perform(get("/api/v1/banking/internal-transfer/transfer-history")
                        .param("to_dt", "20260520"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error", is(true)))
                .andExpect(jsonPath("$.status.code",  is("WBE_VALIDATION")));

        verify(service, never()).list(any());
    }

    @Test
    void list_malformedFromDtReturnsValidationEnvelope() throws Exception {
        mvc.perform(get("/api/v1/banking/internal-transfer/transfer-history")
                        .param("from_dt", "2026-05-01")
                        .param("to_dt",   "20260520"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error", is(true)))
                .andExpect(jsonPath("$.status.code",  is("WBE_VALIDATION")));

        verify(service, never()).list(any());
    }

    @Test
    void list_invalidStatusSentinelReturnsValidationEnvelope() throws Exception {
        mvc.perform(get("/api/v1/banking/internal-transfer/transfer-history")
                        .param("from_dt",   "20260501")
                        .param("to_dt",     "20260520")
                        .param("trns_stts", "ABCDEFGH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error", is(true)))
                .andExpect(jsonPath("$.status.code",  is("WBE_VALIDATION")));

        verify(service, never()).list(any());
    }

    @Test
    void list_pagePerCntOutOfWhitelistReturnsValidationEnvelope() throws Exception {
        mvc.perform(get("/api/v1/banking/internal-transfer/transfer-history")
                        .param("from_dt",      "20260501")
                        .param("to_dt",        "20260520")
                        .param("page_per_cnt", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error", is(true)))
                .andExpect(jsonPath("$.status.code",  is("WBE_VALIDATION")));

        verify(service, never()).list(any());
    }

    @Test
    void list_pageNoZeroReturnsValidationEnvelope() throws Exception {
        mvc.perform(get("/api/v1/banking/internal-transfer/transfer-history")
                        .param("from_dt",  "20260501")
                        .param("to_dt",    "20260520")
                        .param("page_no",  "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error", is(true)))
                .andExpect(jsonPath("$.status.code",  is("WBE_VALIDATION")));

        verify(service, never()).list(any());
    }

    /* =================================================================
     *  Business error envelope
     * ================================================================= */

    @Test
    void list_invertedPeriodSurfacesBusinessError() throws Exception {
        when(service.list(any()))
                .thenThrow(new BusinessException("WBE_TRANSFER_HISTORY_INVALID_PERIOD"));

        mvc.perform(get("/api/v1/banking/internal-transfer/transfer-history")
                        .param("from_dt", "20260520")
                        .param("to_dt",   "20260501"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error", is(true)))
                .andExpect(jsonPath("$.status.code",  is("WBE_TRANSFER_HISTORY_INVALID_PERIOD")));
    }
}
