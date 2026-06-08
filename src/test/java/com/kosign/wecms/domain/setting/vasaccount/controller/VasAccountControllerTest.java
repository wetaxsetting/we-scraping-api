package com.kosign.wecms.domain.setting.vasaccount.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.exception.GlobalExceptionHandler;
import com.kosign.wecms.common.export.ExcelExportService;
import com.kosign.wecms.common.i18n.MessageResolver;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.setting.vasaccount.dto.VasAccountCreateRequest;
import com.kosign.wecms.domain.setting.vasaccount.dto.VasAccountListResponse;
import com.kosign.wecms.domain.setting.vasaccount.dto.VasAccountRow;
import com.kosign.wecms.domain.setting.vasaccount.dto.VasAccountUpdateRequest;
import com.kosign.wecms.domain.setting.vasaccount.service.VasAccountExcelService;
import com.kosign.wecms.domain.setting.vasaccount.service.VasAccountService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
 * Standalone-MockMvc slice test for {@link VasAccountController}.
 * Verifies:
 * <ul>
 *   <li>Wire format is lowercase snake_case end to end.</li>
 *   <li>Request params decode correctly ({@code search_wd},
 *       {@code stts} (repeatable), {@code sort_field},
 *       {@code sort_direction}, {@code page}, {@code size}).</li>
 *   <li>Response envelope shape — {@code status} +
 *       {@code data.{list, total_count, page_number, page_size,
 *       total_pages}}.</li>
 *   <li>Business exceptions flow through
 *       {@link GlobalExceptionHandler} to the standard error envelope.</li>
 *   <li>Validation rejects bad path / body inputs.</li>
 * </ul>
 */
class VasAccountControllerTest {

    private MockMvc mvc;
    private VasAccountService service;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        service = mock(VasAccountService.class);
        VasAccountExcelService excelService = mock(VasAccountExcelService.class);
        ExcelExportService excelExportService = mock(ExcelExportService.class);

        mapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .registerModule(new JavaTimeModule());
        MappingJackson2HttpMessageConverter jsonConverter =
                new MappingJackson2HttpMessageConverter(mapper);

        MessageResolver messages = mock(MessageResolver.class);
        when(messages.resolve(any(String.class))).thenReturn("VAS Account error.");
        when(messages.resolve(any(String.class), any(Object[].class))).thenReturn("VAS Account error.");
        GlobalExceptionHandler advice = new GlobalExceptionHandler(messages);

        mvc = MockMvcBuilders
                .standaloneSetup(new VasAccountController(service, excelService, excelExportService))
                .setMessageConverters(jsonConverter)
                .setControllerAdvice(advice)
                .build();

        TenantContext.set("tester", "tester", "VN_VNM0004_000001", LangCd.EN);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /* =================================================================
     *  list
     * ================================================================= */

    @Test
    void list_happyPath_serializesSnakeCase() throws Exception {
        VasAccountRow row = new VasAccountRow(
                "132", "323", "Jinpai", null, "0", "settings.vas_account.type.dual",
                "A", "settings.vas_account.status.active", null,
                "lamine@gmail.com", "20251205092155");
        when(service.list(isNull(), isNull(), isNull(), isNull(), eq(0), eq(0)))
                .thenReturn(new VasAccountListResponse(List.of(row), 1L, 0, 20, 1));

        mvc.perform(get("/api/v1/settings/vas-account"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error",         is(false)))
                .andExpect(jsonPath("$.status.code",          is("WBI0000")))
                .andExpect(jsonPath("$.data.list",            org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.data.list[0].vas_acct_seq_no",   is("132")))
                .andExpect(jsonPath("$.data.list[0].vas_acct_cd",       is("323")))
                .andExpect(jsonPath("$.data.list[0].vas_acct_type",     is("0")))
                .andExpect(jsonPath("$.data.list[0].vas_acct_type_key", is("settings.vas_account.type.dual")))
                .andExpect(jsonPath("$.data.list[0].stts",              is("A")))
                .andExpect(jsonPath("$.data.list[0].stts_key",          is("settings.vas_account.status.active")))
                .andExpect(jsonPath("$.data.list[0].mod_id",            is("lamine@gmail.com")))
                .andExpect(jsonPath("$.data.list[0].mod_dtm",           is("20251205092155")))
                .andExpect(jsonPath("$.data.total_count",     is(1)))
                .andExpect(jsonPath("$.data.page_number",     is(0)))
                .andExpect(jsonPath("$.data.page_size",       is(20)))
                .andExpect(jsonPath("$.data.total_pages",     is(1)));
    }

    @Test
    void list_passesAllQueryParams() throws Exception {
        when(service.list(eq("Tien"), eq(List.of("A", "I")), eq("VAS_ACCT_CD"), eq("DESC"), eq(1), eq(50)))
                .thenReturn(new VasAccountListResponse(List.of(), 0L, 1, 50, 0));

        mvc.perform(get("/api/v1/settings/vas-account")
                        .param("search_wd",      "Tien")
                        .param("stts",           "A")
                        .param("stts",           "I")
                        .param("sort_field",     "VAS_ACCT_CD")
                        .param("sort_direction", "DESC")
                        .param("page",           "1")
                        .param("size",           "50"))
                .andExpect(status().isOk());

        verify(service).list("Tien", List.of("A", "I"), "VAS_ACCT_CD", "DESC", 1, 50);
    }

    @Test
    void list_allTrue_routesToListAll() throws Exception {
        when(service.listAll(isNull(), isNull(), isNull(), isNull()))
                .thenReturn(new VasAccountListResponse(List.of(), 0L, 0, 0, 0));

        mvc.perform(get("/api/v1/settings/vas-account").param("all", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error", is(false)));

        verify(service).listAll(null, null, null, null);
        // The paginated path must NOT be taken when all=true.
        verify(service, never()).list(any(), any(), any(), any(), anyInt(), anyInt());
    }

    /* =================================================================
     *  detail
     * ================================================================= */

    @Test
    void detail_returnsRow() throws Exception {
        VasAccountRow row = new VasAccountRow(
                "132", "323", "Jinpai", "Jinpai Hill", "0",
                "settings.vas_account.type.dual", "A",
                "settings.vas_account.status.active", "notes",
                "u@x.com", "20260101000000");
        when(service.detail(132L)).thenReturn(row);

        mvc.perform(get("/api/v1/settings/vas-account/{seq}", 132))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.vas_acct_seq_no", is("132")))
                .andExpect(jsonPath("$.data.cont",            is("notes")));
    }

    @Test
    void detail_notFound_returnsBusinessEnvelope() throws Exception {
        when(service.detail(999L)).thenThrow(new BusinessException("WBE2040"));

        mvc.perform(get("/api/v1/settings/vas-account/{seq}", 999))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error", is(true)))
                .andExpect(jsonPath("$.status.code",  is("WBE2040")))
                .andExpect(jsonPath("$.trace_id").exists())
                .andExpect(jsonPath("$.path",         is("/api/v1/settings/vas-account/999")));
    }

    @Test
    void detail_nonNumericPath_validation() throws Exception {
        mvc.perform(get("/api/v1/settings/vas-account/{seq}", "not-a-number"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error", is(true)))
                .andExpect(jsonPath("$.status.code",  is("WBE_VALIDATION")));
    }

    /* =================================================================
     *  create
     * ================================================================= */

    @Test
    void create_happyPath_returnsCreatedRow() throws Exception {
        VasAccountRow row = new VasAccountRow(
                "200", "999", "New", null, "0",
                "settings.vas_account.type.dual", "A",
                "settings.vas_account.status.active", null,
                "tester", "20260511000000");
        when(service.create(any(VasAccountCreateRequest.class))).thenReturn(row);

        String body = """
                {"vas_acct_cd":"999","vas_acct_nm":"New","vas_acct_type":"0","stts":"A"}
                """;
        mvc.perform(post("/api/v1/settings/vas-account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error",         is(false)))
                .andExpect(jsonPath("$.data.vas_acct_seq_no", is("200")))
                .andExpect(jsonPath("$.data.vas_acct_cd",     is("999")));
    }

    @Test
    void create_missingRequiredField_returnsValidationEnvelope() throws Exception {
        String body = """
                {"vas_acct_nm":"NoCode","vas_acct_type":"0","stts":"A"}
                """;
        mvc.perform(post("/api/v1/settings/vas-account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error", is(true)))
                .andExpect(jsonPath("$.status.code",  is("WBE_VALIDATION")));
    }

    @Test
    void create_badCodeFormat_returnsValidationEnvelope() throws Exception {
        // vas_acct_cd contains a banned character (space) — @Pattern rejects.
        String body = """
                {"vas_acct_cd":"a b","vas_acct_nm":"x","vas_acct_type":"0","stts":"A"}
                """;
        mvc.perform(post("/api/v1/settings/vas-account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error", is(true)))
                .andExpect(jsonPath("$.status.code",  is("WBE_VALIDATION")));
    }

    @Test
    void create_duplicate_surfacesWBE0003() throws Exception {
        when(service.create(any(VasAccountCreateRequest.class)))
                .thenThrow(new BusinessException("WBE0003", "VAS_ACCT_CD"));

        String body = """
                {"vas_acct_cd":"999","vas_acct_nm":"New","vas_acct_type":"0","stts":"A"}
                """;
        mvc.perform(post("/api/v1/settings/vas-account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error", is(true)))
                .andExpect(jsonPath("$.status.code",  is("WBE0003")));
    }

    /* =================================================================
     *  update
     * ================================================================= */

    @Test
    void update_happyPath_returnsUpdatedRow() throws Exception {
        VasAccountRow row = new VasAccountRow(
                "132", "999", "Renamed", null, "1",
                "settings.vas_account.type.credit", "I",
                "settings.vas_account.status.inactive", null,
                "tester", "20260511010000");
        when(service.update(eq(132L), any(VasAccountUpdateRequest.class))).thenReturn(row);

        String body = """
                {"vas_acct_cd":"999","vas_acct_nm":"Renamed","vas_acct_type":"1","stts":"I"}
                """;
        mvc.perform(put("/api/v1/settings/vas-account/{seq}", 132)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.vas_acct_cd", is("999")))
                .andExpect(jsonPath("$.data.stts",        is("I")));
    }

    /* =================================================================
     *  delete
     * ================================================================= */

    @Test
    void delete_happyPath_returnsSuccessEnvelope() throws Exception {
        mvc.perform(delete("/api/v1/settings/vas-account/{seq}", 132))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error", is(false)))
                .andExpect(jsonPath("$.status.code",  is("WBI0000")));
        verify(service).softDelete(132L);
    }
}
