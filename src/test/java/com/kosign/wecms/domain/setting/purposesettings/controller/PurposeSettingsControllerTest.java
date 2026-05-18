package com.kosign.wecms.domain.setting.purposesettings.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.exception.GlobalExceptionHandler;
import com.kosign.wecms.common.i18n.MessageResolver;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.setting.purposesettings.dto.PurposeSettingDetail;
import com.kosign.wecms.domain.setting.purposesettings.dto.PurposeSettingListResponse;
import com.kosign.wecms.domain.setting.purposesettings.dto.PurposeSettingRow;
import com.kosign.wecms.domain.setting.purposesettings.service.PurposeSettingsService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone-MockMvc slice for {@link PurposeSettingsController}.
 * Verifies the snake_case wire contract end-to-end and the
 * {@link BusinessException} → {@code ApiResponse} envelope funnel.
 *
 * <p>Production wires the global SNAKE_CASE strategy via
 * {@code JacksonConfig}; the same converter is installed here so the
 * response bytes match production exactly.
 */
class PurposeSettingsControllerTest {

    private MockMvc mvc;
    private PurposeSettingsService service;

    private static final String BIZ      = "VN_VNM0004_000001";
    private static final String CODE_A   = "0000000007";
    private static final String HAND_A   = "P01";

    @BeforeEach
    void setUp() {
        service = mock(PurposeSettingsService.class);
        com.kosign.wecms.domain.setting.purposesettings.service.PurposeExcelService excelService =
                mock(com.kosign.wecms.domain.setting.purposesettings.service.PurposeExcelService.class);

        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        MappingJackson2HttpMessageConverter converter =
                new MappingJackson2HttpMessageConverter(mapper);

        MessageResolver messages = mock(MessageResolver.class);
        when(messages.resolve(org.mockito.ArgumentMatchers.any(String.class)))
                .thenReturn("Localized message");
        when(messages.resolve(org.mockito.ArgumentMatchers.any(String.class),
                org.mockito.ArgumentMatchers.any(Object[].class)))
                .thenReturn("Localized message");
        GlobalExceptionHandler advice = new GlobalExceptionHandler(messages);

        mvc = MockMvcBuilders
                .standaloneSetup(new PurposeSettingsController(service, excelService))
                .setMessageConverters(converter)
                .setControllerAdvice(advice)
                .build();

        TenantContext.set("tester", "tester", BIZ, LangCd.EN);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ------------------------------------------------------------------
    // List
    // ------------------------------------------------------------------

    @Test
    void list_returnsSnakeCaseNestedRows() throws Exception {
        PurposeSettingRow child = new PurposeSettingRow(
                "0000000008", "P1.1", "Sale", "Sale business",
                CODE_A, "Y", 193, false, "demo", "20260101090000", null);
        PurposeSettingRow parent = new PurposeSettingRow(
                CODE_A, HAND_A, "Deposit", "Deposit purpose",
                null, "Y", 192, true, "demo", "20260101090000", List.of(child));
        when(service.list(eq((String) null)))
                .thenReturn(new PurposeSettingListResponse(List.of(parent)));

        mvc.perform(get("/api/v1/settings/purpose-settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error", is(false)))
                .andExpect(jsonPath("$.status.code",  is("WBI0000")))
                .andExpect(jsonPath("$.data.list[0].use_usag_cd",          is(CODE_A)))
                .andExpect(jsonPath("$.data.list[0].hand_use_usag_cd",     is(HAND_A)))
                .andExpect(jsonPath("$.data.list[0].use_usag_nm",          is("Deposit")))
                .andExpect(jsonPath("$.data.list[0].parent_use_usag_cd",   org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.list[0].disp_yn",              is("Y")))
                .andExpect(jsonPath("$.data.list[0].disp_ord",             is(192)))
                .andExpect(jsonPath("$.data.list[0].used_in_transactions", is(true)))
                .andExpect(jsonPath("$.data.list[0].mod_id",               is("demo")))
                .andExpect(jsonPath("$.data.list[0].mod_dtm",              is("20260101090000")))
                .andExpect(jsonPath("$.data.list[0].children[0].use_usag_cd",
                        is("0000000008")))
                .andExpect(jsonPath("$.data.list[0].children[0].parent_use_usag_cd",
                        is(CODE_A)))
                .andExpect(jsonPath("$.data.list[0].children[0].used_in_transactions",
                        is(false)));
    }

    @Test
    void list_forwardsKeywordQueryParam() throws Exception {
        when(service.list("Sale")).thenReturn(new PurposeSettingListResponse(List.of()));

        mvc.perform(get("/api/v1/settings/purpose-settings").param("keyword", "Sale"))
                .andExpect(status().isOk());

        verify(service).list("Sale");
    }

    // ------------------------------------------------------------------
    // Detail + error funnel
    // ------------------------------------------------------------------

    @Test
    void detail_returnsSnakeCaseFields() throws Exception {
        PurposeSettingDetail detail = new PurposeSettingDetail(
                CODE_A, HAND_A, "Deposit", "cont", null, "Y", 192, false,
                "demo", "20260101090000");
        when(service.detail(eq(CODE_A))).thenReturn(detail);

        mvc.perform(get("/api/v1/settings/purpose-settings/{c}", CODE_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.use_usag_cd",      is(CODE_A)))
                .andExpect(jsonPath("$.data.hand_use_usag_cd", is(HAND_A)));
        verify(service).detail(CODE_A);
    }

    @Test
    void detail_businessException_envelopesAsCode() throws Exception {
        when(service.detail("missing"))
                .thenThrow(new BusinessException("WBE_VALIDATION", "use_usag_cd: not found in this tenant"));

        mvc.perform(get("/api/v1/settings/purpose-settings/{c}", "missing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error", is(true)))
                .andExpect(jsonPath("$.status.code",  is("WBE_VALIDATION")));
    }

    // ------------------------------------------------------------------
    // Create + Update + Delete wire contract
    // ------------------------------------------------------------------

    @Test
    void create_acceptsSnakeCaseBody_andReturnsSnakeCaseDetail() throws Exception {
        PurposeSettingDetail detail = new PurposeSettingDetail(
                "0000000010", "BD02", "Operating Expenses", "cont", null,
                "Y", 200, false, "tester", "20260512000000");
        when(service.create(any())).thenReturn(detail);

        String body = "{"
                + "\"hand_use_usag_cd\":\"BD02\","
                + "\"use_usag_nm\":\"Operating Expenses\","
                + "\"use_usag_cont\":\"cont\","
                + "\"disp_yn\":\"Y\""
                + "}";

        mvc.perform(post("/api/v1/settings/purpose-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.use_usag_cd",      is("0000000010")))
                .andExpect(jsonPath("$.data.hand_use_usag_cd", is("BD02")))
                .andExpect(jsonPath("$.data.use_usag_nm",      is("Operating Expenses")));
    }

    @Test
    void create_missingRequiredField_envelopesAsValidation() throws Exception {
        // hand_use_usag_cd intentionally missing
        String body = "{\"use_usag_nm\":\"Operating Expenses\"}";

        mvc.perform(post("/api/v1/settings/purpose-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error", is(true)))
                .andExpect(jsonPath("$.status.code",  is("WBE_VALIDATION")));
    }

    @Test
    void create_duplicateHandCd_envelopesAsWBW1152() throws Exception {
        when(service.create(any())).thenThrow(new BusinessException("WBW1152"));

        String body = "{"
                + "\"hand_use_usag_cd\":\"BD02\","
                + "\"use_usag_nm\":\"Operating Expenses\""
                + "}";

        mvc.perform(post("/api/v1/settings/purpose-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error", is(true)))
                .andExpect(jsonPath("$.status.code",  is("WBW1152")));
    }

    @Test
    void update_passesPathPkToService() throws Exception {
        PurposeSettingDetail detail = new PurposeSettingDetail(
                CODE_A, HAND_A, "Renamed", null, null, "Y", 192, false,
                "tester", "20260512000000");
        when(service.update(eq(CODE_A), any())).thenReturn(detail);

        String body = "{"
                + "\"hand_use_usag_cd\":\"P01\","
                + "\"use_usag_nm\":\"Renamed\""
                + "}";

        mvc.perform(put("/api/v1/settings/purpose-settings/{c}", CODE_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.use_usag_nm", is("Renamed")));
        verify(service).update(eq(CODE_A), any());
    }

    @Test
    void delete_blockedByChildren_envelopesAsWBE1199() throws Exception {
        org.mockito.Mockito.doThrow(new BusinessException("WBE1199")).when(service).delete(CODE_A);

        mvc.perform(delete("/api/v1/settings/purpose-settings/{c}", CODE_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error", is(true)))
                .andExpect(jsonPath("$.status.code",  is("WBE1199")));
    }

    @Test
    void delete_success_returnsEmptyEnvelope() throws Exception {
        mvc.perform(delete("/api/v1/settings/purpose-settings/{c}", CODE_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error", is(false)))
                .andExpect(jsonPath("$.status.code",  is("WBI0000")));
        verify(service).delete(CODE_A);
    }

    // ------------------------------------------------------------------
    // Reorder wire contract — Excel preview/import endpoints have
    // their own slice tests under PurposeExcelServiceTest (service-
    // level) and require multipart form-data which standalone MockMvc
    // setup wires through a different path. The controller is a thin
    // forward to PurposeExcelService.preview / .import_ — service
    // coverage exercises the behaviour.
    // ------------------------------------------------------------------

    @Test
    void reorder_acceptsItemsArray() throws Exception {
        when(service.reorder(any())).thenReturn(new PurposeSettingListResponse(List.of()));

        String body = "{"
                + "\"items\":["
                + "{\"use_usag_cd\":\"0000000001\",\"disp_ord\":1},"
                + "{\"use_usag_cd\":\"0000000005\",\"disp_ord\":2}"
                + "]}";

        mvc.perform(put("/api/v1/settings/purpose-settings/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error", is(false)));
        verify(service).reorder(any());
    }
}
