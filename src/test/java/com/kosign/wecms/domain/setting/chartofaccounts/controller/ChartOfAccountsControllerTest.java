package com.kosign.wecms.domain.setting.chartofaccounts.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.exception.GlobalExceptionHandler;
import com.kosign.wecms.common.i18n.MessageResolver;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.setting.chartofaccounts.dto.ChartOfAccountListResponse;
import com.kosign.wecms.domain.setting.chartofaccounts.dto.ChartOfAccountRow;
import com.kosign.wecms.domain.setting.chartofaccounts.service.ChartOfAccountsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Set;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone-MockMvc slice test for {@link ChartOfAccountsController}.
 * Verifies the snake_case wire contract (request + response), the
 * validation envelope, and the restricted-code rejection path.
 *
 * <p>Mirrors the {@code ContactsControllerTest} pattern: Jackson is
 * configured manually with {@code SNAKE_CASE} so the response bytes
 * match production exactly without spinning up the full Spring
 * context.
 */
class ChartOfAccountsControllerTest {

    private MockMvc mvc;
    private ChartOfAccountsService service;

    @BeforeEach
    void setUp() {
        service = mock(ChartOfAccountsService.class);

        ObjectMapper mapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .registerModule(new JavaTimeModule());
        MappingJackson2HttpMessageConverter jsonConverter =
                new MappingJackson2HttpMessageConverter(mapper);

        MessageResolver messages = mock(MessageResolver.class);
        when(messages.resolve(any(String.class))).thenReturn("Chart of accounts error.");
        when(messages.resolve(any(String.class), any(Object[].class)))
                .thenReturn("Chart of accounts error.");
        GlobalExceptionHandler advice = new GlobalExceptionHandler(messages);

        mvc = MockMvcBuilders
                .standaloneSetup(new ChartOfAccountsController(service))
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
     *  List
     * ================================================================= */

    @Test
    void list_happyPathSerialisesSnakeCase() throws Exception {
        ChartOfAccountRow row = new ChartOfAccountRow(
                "70", "ACC-01", "REVENUE", "REVENUE", "수입",
                null, null, null, "A",
                1, true, true,
                "VN_VNM0004_000001", "20250806065018",
                "VN_VNM0004_000001", "20250806065018");
        ChartOfAccountListResponse payload = new ChartOfAccountListResponse(
                List.of(row), 1, Set.of("ACC-01", "ACC-02", "ACC-03"));
        when(service.list(any(), any(), any())).thenReturn(payload);

        mvc.perform(get("/api/v1/settings/chart-of-accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error",          is(false)))
                .andExpect(jsonPath("$.status.code",           is("WBI0000")))
                .andExpect(jsonPath("$.data.total_count",      is(1)))
                .andExpect(jsonPath("$.data.items[0].chart_acct_seq_no", is("70")))
                .andExpect(jsonPath("$.data.items[0].chart_acct_cd",     is("ACC-01")))
                .andExpect(jsonPath("$.data.items[0].chart_acct_nm_en",  is("REVENUE")))
                .andExpect(jsonPath("$.data.items[0].chart_acct_level",  is(1)))
                .andExpect(jsonPath("$.data.items[0].has_children",      is(true)))
                .andExpect(jsonPath("$.data.items[0].restricted",        is(true)));
    }

    @Test
    void list_forwardsSearchAndSortParams() throws Exception {
        when(service.list(eq("Collection"), eq("CHART_ACCT_CD"), eq("DESC")))
                .thenReturn(new ChartOfAccountListResponse(List.of(), 0, Set.of()));

        mvc.perform(get("/api/v1/settings/chart-of-accounts")
                        .param("search", "Collection")
                        .param("sort_field", "CHART_ACCT_CD")
                        .param("sort_dir", "DESC"))
                .andExpect(status().isOk());

        verify(service).list("Collection", "CHART_ACCT_CD", "DESC");
    }

    /* =================================================================
     *  Create — validation envelope
     * ================================================================= */

    @Test
    void create_missingRequiredFieldReturnsValidationEnvelope() throws Exception {
        // Missing chart_acct_cd (required @NotBlank).
        String body = """
                {
                  "chart_acct_nm_en": "Collection 4",
                  "parent_chart_acct_cd": "ACC-01",
                  "stts": "A"
                }
                """;

        mvc.perform(post("/api/v1/settings/chart-of-accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error", is(true)))
                .andExpect(jsonPath("$.status.code",  is("WBE_VALIDATION")));

        verify(service, never()).create(any());
    }

    @Test
    void create_invalidSttsPatternReturnsValidationEnvelope() throws Exception {
        // stts must match [AI] — pass an invalid letter.
        String body = """
                {
                  "chart_acct_cd": "X-1",
                  "chart_acct_nm_en": "Bad status",
                  "stts": "Z"
                }
                """;

        mvc.perform(post("/api/v1/settings/chart-of-accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error", is(true)))
                .andExpect(jsonPath("$.status.code",  is("WBE_VALIDATION")));
    }

    @Test
    void create_happyPathDelegatesToService() throws Exception {
        ChartOfAccountRow result = new ChartOfAccountRow(
                "113", "1007009", "Collection 4", null, null,
                null, "ACC-01", null, "A",
                2, false, false,
                "tester", "20260513000000",
                "tester", "20260513000000");
        when(service.create(any())).thenReturn(result);

        String body = """
                {
                  "chart_acct_cd": "1007009",
                  "chart_acct_nm_en": "Collection 4",
                  "parent_chart_acct_cd": "ACC-01",
                  "stts": "A"
                }
                """;

        mvc.perform(post("/api/v1/settings/chart-of-accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error", is(false)))
                .andExpect(jsonPath("$.data.chart_acct_seq_no", is("113")))
                .andExpect(jsonPath("$.data.chart_acct_cd",     is("1007009")))
                .andExpect(jsonPath("$.data.restricted",        is(false)));
    }

    /* =================================================================
     *  Update / Delete — business error envelope
     * ================================================================= */

    @Test
    void update_restrictedRowSurfacesBusinessError() throws Exception {
        doThrow(new BusinessException("WBE_CHART_OF_ACCOUNTS_RESTRICTED_CODE"))
                .when(service).update(eq("70"), any());

        String body = """
                {
                  "chart_acct_cd": "ACC-01-RENAMED",
                  "chart_acct_nm_en": "Revenue Renamed",
                  "stts": "A"
                }
                """;

        mvc.perform(put("/api/v1/settings/chart-of-accounts/70")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error", is(true)))
                .andExpect(jsonPath("$.status.code",
                        is("WBE_CHART_OF_ACCOUNTS_RESTRICTED_CODE")));
    }

    @Test
    void delete_validPathDelegatesToService() throws Exception {
        mvc.perform(delete("/api/v1/settings/chart-of-accounts/99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error", is(false)));

        verify(service).delete("99");
    }

    // Path-variable @Pattern validation requires Spring's
    // MethodValidationPostProcessor (registered automatically under a
    // full @SpringBootTest context but NOT by MockMvcBuilders.standaloneSetup).
    // Verifying the "abc" → WBE_VALIDATION envelope therefore belongs in
    // an integration test, not this slice. The controller-level guard is
    // documented in code; the actual behaviour exercises in production /
    // future @SpringBootTest harness.
}
