package com.kosign.wecms.domain.setting.firmbankinghistory.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kosign.wecms.common.exception.GlobalExceptionHandler;
import com.kosign.wecms.common.i18n.MessageResolver;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.domain.setting.firmbankinghistory.dto.FirmBankingHistoryListResponse;
import com.kosign.wecms.domain.setting.firmbankinghistory.dto.FirmBankingHistoryRow;
import com.kosign.wecms.domain.setting.firmbankinghistory.service.FirmBankingHistoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone-MockMvc slice test for {@link FirmBankingHistoryController}.
 * Verifies the **snake_case wire contract**: every wire field on the
 * way in (request param) and on the way out (response JSON) is
 * lowercase snake_case, matching the global Jackson naming strategy
 * ({@code JacksonConfig}).
 *
 * <p>Standalone setup (no Spring context) keeps the test fast and
 * deterministic. The global SNAKE_CASE strategy is wired into the
 * {@link MockMvc} message converter manually so the response bytes
 * match production exactly.
 */
class FirmBankingHistoryControllerTest {

    private MockMvc mvc;
    private FirmBankingHistoryService service;

    @BeforeEach
    void setUp() {
        service = mock(FirmBankingHistoryService.class);

        // Mirror production: ObjectMapper with SNAKE_CASE naming
        // strategy. This is what JacksonConfig wires into the
        // auto-configured ObjectMapper. Without this the response
        // would serialize camelCase Java field names verbatim and
        // the snake_case wire assertions would fail.
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        MappingJackson2HttpMessageConverter converter =
                new MappingJackson2HttpMessageConverter(mapper);

        // GlobalExceptionHandler funnels validation failures into the
        // standard envelope; install it so the test exercises the same
        // path production does.
        MessageResolver messages = mock(MessageResolver.class);
        when(messages.resolve(any(String.class))).thenReturn("Required.");
        GlobalExceptionHandler advice = new GlobalExceptionHandler(messages);

        mvc = MockMvcBuilders
                .standaloneSetup(new FirmBankingHistoryController(service))
                .setMessageConverters(converter)
                .setControllerAdvice(advice)
                .build();

        TenantContext.set("tester", "VN_VNM0004_000001", LangCd.EN);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ------------------------------------------------------------------
    // Happy path — snake_case wire shape end-to-end
    // ------------------------------------------------------------------

    @Test
    void happyPath_returnsSnakeCaseWireFields() throws Exception {
        // Argument order MUST track the FirmBankingHistoryRow record's
        // component declaration: tarnDtm → svcType → svcTypeKey → svcNm
        // → svcNameKey → seqNo → depBankCd → rsltCd → rsltMsg → job →
        // reqData → vanOrgCd. svcTypeKey was added by Phase 3d to retire
        // the frontend's coerceFirmBankingServiceType switch — its wire
        // serialisation is asserted below.
        FirmBankingHistoryRow row = new FirmBankingHistoryRow(
                "20260115101010",
                "F",
                "settings.firm_banking_history.service_type.firm_banking_transaction",
                "201",
                "fb_history.service.201",
                "13768",
                "Shinhan VN - 970424",
                "00000000",
                "00000000",
                "201",
                null,
                "GV00000001");
        FirmBankingHistoryListResponse payload = new FirmBankingHistoryListResponse(
                List.of(row), 1L, 0, 15, 1);

        when(service.list(eq("20260101"), eq("20260131"), isNull(), isNull(),
                isNull(), isNull(), anyInt(), anyInt()))
                .thenReturn(payload);

        mvc.perform(get("/api/v1/settings/firm-banking-history")
                        .param("from_date", "20260101")
                        .param("to_date",   "20260131"))
                .andExpect(status().isOk())
                // Envelope — snake_case
                .andExpect(jsonPath("$.status.error",   is(false)))
                .andExpect(jsonPath("$.status.code",    is("WBI0000")))
                .andExpect(jsonPath("$.status.message", is("Success")))
                // List payload — snake_case
                .andExpect(jsonPath("$.data.total_count", is(1)))
                .andExpect(jsonPath("$.data.page_number", is(0)))
                .andExpect(jsonPath("$.data.page_size",   is(15)))
                .andExpect(jsonPath("$.data.total_pages", is(1)))
                // Row — every legacy WSVC field name on the wire, plus
                // the Phase-3d refactor-only svc_type_key + svc_name_key.
                // All field names lowercase snake_case per the global
                // Jackson naming strategy (no per-field @JsonProperty).
                .andExpect(jsonPath("$.data.list[0].tarn_dtm",      is("20260115101010")))
                .andExpect(jsonPath("$.data.list[0].svc_type",      is("F")))
                .andExpect(jsonPath("$.data.list[0].svc_type_key",  is("settings.firm_banking_history.service_type.firm_banking_transaction")))
                .andExpect(jsonPath("$.data.list[0].svc_nm",        is("201")))
                .andExpect(jsonPath("$.data.list[0].svc_name_key",  is("fb_history.service.201")))
                .andExpect(jsonPath("$.data.list[0].seq_no",        is("13768")))
                .andExpect(jsonPath("$.data.list[0].dep_bank_cd",   is("Shinhan VN - 970424")))
                .andExpect(jsonPath("$.data.list[0].rslt_cd",       is("00000000")))
                .andExpect(jsonPath("$.data.list[0].rslt_msg",      is("00000000")))
                .andExpect(jsonPath("$.data.list[0].job",           is("201")))
                .andExpect(jsonPath("$.data.list[0].van_org_cd",    is("GV00000001")));
    }

    @Test
    void requestParams_areReadFromSnakeCaseWireNames() throws Exception {
        when(service.list(eq("20260101"), eq("20260131"),
                eq("F"), eq("13768"),
                eq("TARN_DTM"), eq("DESC"),
                eq(1), eq(20)))
                .thenReturn(emptyResponse());

        mvc.perform(get("/api/v1/settings/firm-banking-history")
                        .param("from_date",      "20260101")
                        .param("to_date",        "20260131")
                        .param("svc_type",       "F")
                        .param("keyword",        "13768")
                        .param("sort_field",     "TARN_DTM")
                        .param("sort_direction", "DESC")
                        .param("page",           "1")
                        .param("size",           "20"))
                .andExpect(status().isOk());

        verify(service).list("20260101", "20260131",
                "F", "13768",
                "TARN_DTM", "DESC",
                1, 20);
    }

    // ------------------------------------------------------------------
    // Validation — required params funnel through GlobalExceptionHandler
    // ------------------------------------------------------------------

    @Test
    void missingFromDate_envelopesAsWbeValidation() throws Exception {
        // GlobalExceptionHandler reads TraceId.current() — when MDC is
        // empty (unit-test path), it generates a fresh id rather than
        // throwing, so no setup is needed.
        mvc.perform(get("/api/v1/settings/firm-banking-history")
                        .param("to_date", "20260131"))
                // Validation envelope is HTTP 200 per the project's
                // "BusinessException → 200 + status.error=true" contract.
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error", is(true)))
                .andExpect(jsonPath("$.status.code",  is("WBE_VALIDATION")));

        verify(service, never()).list(any(), any(), any(), any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void missingToDate_envelopesAsWbeValidation() throws Exception {
        mvc.perform(get("/api/v1/settings/firm-banking-history")
                        .param("from_date", "20260101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code", is("WBE_VALIDATION")));
    }

    private static FirmBankingHistoryListResponse emptyResponse() {
        return new FirmBankingHistoryListResponse(List.of(), 0L, 0, 15, 0);
    }
}
