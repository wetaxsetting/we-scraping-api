package com.kosign.wecms.domain.setting.firmbankingsettings.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.exception.GlobalExceptionHandler;
import com.kosign.wecms.common.i18n.MessageResolver;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.setting.firmbankingsettings.dto.FirmBankingAuditEntry;
import com.kosign.wecms.domain.setting.firmbankingsettings.dto.FirmBankingAuditListResponse;
import com.kosign.wecms.domain.setting.firmbankingsettings.dto.FirmBankingSettingCreateRequest;
import com.kosign.wecms.domain.setting.firmbankingsettings.dto.FirmBankingSettingDetail;
import com.kosign.wecms.domain.setting.firmbankingsettings.dto.FirmBankingSettingListResponse;
import com.kosign.wecms.domain.setting.firmbankingsettings.dto.FirmBankingSettingRow;
import com.kosign.wecms.domain.setting.firmbankingsettings.dto.FirmBankingSettingUpdateRequest;
import com.kosign.wecms.domain.setting.firmbankingsettings.service.FirmBankingSettingsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
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
 * Standalone-MockMvc slice for {@link FirmBankingSettingsController}.
 * Verifies the snake_case wire contract end-to-end (request URI →
 * service stub → response JSON) plus the
 * {@link com.kosign.wecms.common.exception.BusinessException} →
 * {@link com.kosign.wecms.common.response.ApiResponse} envelope
 * funnel.
 *
 * <p>Production wires the global SNAKE_CASE strategy via
 * {@code JacksonConfig}; we install the same converter here so the
 * response bytes match production exactly.
 *
 * <p>{@link GlobalExceptionHandler} is registered as the controller
 * advice so {@link BusinessException} and the standard validation
 * exceptions funnel into the {@code ApiResponse} envelope on the
 * same path production exercises.
 *
 * <p>Path-variable {@code @Pattern} validation is intentionally not
 * exercised here — see the comment block before
 * {@link #detail_rowMissing_envelopesAsWBE2020()} for why.
 */
class FirmBankingSettingsControllerTest {

    private MockMvc mvc;
    private FirmBankingSettingsService service;

    private static final String BIZ      = "VN_VNM0004_000001";
    private static final String OUT_BANK = "970424";
    private static final String WDRL_ACT = "700005285280";

    @BeforeEach
    void setUp() {
        service = mock(FirmBankingSettingsService.class);

        // Mirror production: ObjectMapper with SNAKE_CASE strategy.
        ObjectMapper mapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        MappingJackson2HttpMessageConverter converter =
                new MappingJackson2HttpMessageConverter(mapper);

        // GlobalExceptionHandler funnels every error path into the
        // ApiResponse envelope. Wire a stub MessageResolver that
        // returns a deterministic placeholder so the assertions below
        // stay locale-agnostic.
        MessageResolver messages = mock(MessageResolver.class);
        when(messages.resolve(org.mockito.ArgumentMatchers.any(String.class)))
                .thenReturn("Localized message");
        when(messages.resolve(org.mockito.ArgumentMatchers.any(String.class),
                org.mockito.ArgumentMatchers.any(Object[].class)))
                .thenReturn("Localized message");

        GlobalExceptionHandler advice = new GlobalExceptionHandler(messages);

        mvc = MockMvcBuilders
                .standaloneSetup(new FirmBankingSettingsController(service))
                .setMessageConverters(converter)
                .setControllerAdvice(advice)
                .build();

        TenantContext.set("tester", BIZ, LangCd.EN);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ------------------------------------------------------------------
    // List
    // ------------------------------------------------------------------

    @Test
    void list_returnsSnakeCaseRows_andCleartextWdrlActNo() throws Exception {
        // The service ships cleartext wdrl_act_no on the list wire (the
        // frontend mapper is what produces the masked display string).
        // This test pins the wire shape; the masking move from server
        // to frontend is documented in
        // FirmBankingSettingsService.toListRow Javadoc.
        FirmBankingSettingRow row = new FirmBankingSettingRow(
                BIZ,
                OUT_BANK,
                "Shinhan Bank Vietnam",
                "VND",
                "WEBC1",
                "GV00000001",
                "1",
                WDRL_ACT,                       // cleartext PK on the wire
                "SHB",
                "Y",
                BigDecimal.valueOf(2000),
                "20260101090000",
                "20260101090000",
                "20260101090000",
                "20260415120000",
                "demo_user",
                new BigDecimal("71745969.000000"),
                null);
        when(service.list()).thenReturn(new FirmBankingSettingListResponse(List.of(row)));

        mvc.perform(get("/api/v1/settings/firm-banking-settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error",   is(false)))
                .andExpect(jsonPath("$.status.code",    is("WBI0000")))
                // Every wire field is lowercase snake_case
                .andExpect(jsonPath("$.data.list[0].biz_key",                 is(BIZ)))
                .andExpect(jsonPath("$.data.list[0].out_bank_cd",             is(OUT_BANK)))
                .andExpect(jsonPath("$.data.list[0].bank_nm",                 is("Shinhan Bank Vietnam")))
                .andExpect(jsonPath("$.data.list[0].ccy_cd",                  is("VND")))
                .andExpect(jsonPath("$.data.list[0].bank_prtn_id",            is("WEBC1")))
                .andExpect(jsonPath("$.data.list[0].van_org_cd",              is("GV00000001")))
                .andExpect(jsonPath("$.data.list[0].org_stat",                is("1")))
                .andExpect(jsonPath("$.data.list[0].wdrl_act_no",             is(WDRL_ACT)))
                .andExpect(jsonPath("$.data.list[0].file_type",               is("SHB")))
                .andExpect(jsonPath("$.data.list[0].bat_tran_branch_div",     is("Y")))
                .andExpect(jsonPath("$.data.list[0].bat_tran_branch_limit",   is(2000)))
                .andExpect(jsonPath("$.data.list[0].reg_dttm",                is("20260101090000")))
                .andExpect(jsonPath("$.data.list[0].mod_dttm",                is("20260415120000")))
                .andExpect(jsonPath("$.data.list[0].last_edtr",               is("demo_user")))
                .andExpect(jsonPath("$.data.list[0].expi_dt",                 is(nullValue())));
    }

    // ------------------------------------------------------------------
    // Detail
    // ------------------------------------------------------------------

    @Test
    void detail_returnsCleartextWdrlActNo_andSnakeCaseFields() throws Exception {
        FirmBankingSettingDetail detail = new FirmBankingSettingDetail(
                BIZ, OUT_BANK, "WEBC1", "GV00000001", "1",
                WDRL_ACT, "SHB", "Y", BigDecimal.valueOf(2000),
                "20260101090000", "20260101090000", "20260101090000",
                "20260415120000", "demo_user");
        when(service.detail(eq(OUT_BANK), eq(WDRL_ACT))).thenReturn(detail);

        mvc.perform(get("/api/v1/settings/firm-banking-settings/{out}/{wdr}", OUT_BANK, WDRL_ACT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error",   is(false)))
                .andExpect(jsonPath("$.data.out_bank_cd", is(OUT_BANK)))
                // Detail returns cleartext for the edit-dialog round-trip
                .andExpect(jsonPath("$.data.wdrl_act_no", is(WDRL_ACT)))
                .andExpect(jsonPath("$.data.bank_prtn_id", is("WEBC1")))
                .andExpect(jsonPath("$.data.org_stat",     is("1")));

        verify(service).detail(OUT_BANK, WDRL_ACT);
    }

    // Path-variable {@code @Pattern} validation (e.g. rejecting
    // {@code out_bank_cd="abc"}) requires the
    // {@code MethodValidationPostProcessor} which is auto-configured
    // by Spring Boot but is NOT wired by
    // {@link org.springframework.test.web.servlet.setup.MockMvcBuilders#standaloneSetup}.
    // The @Pattern guards are still in production code and fire under
    // a full Spring context — they are exercised by the manual /
    // integration test path (live curl + SpringBootTest), which is
    // out of scope for this read-only Phase-2 slice. Add a
    // {@code @WebMvcTest} or @{@code SpringBootTest} test in the
    // follow-up Update / Delete MR if this matters earlier.

    @Test
    void detail_rowMissing_envelopesAsWBE2020() throws Exception {
        when(service.detail(eq(OUT_BANK), eq("9".repeat(12))))
                .thenThrow(new BusinessException("WBE2020"));

        mvc.perform(get("/api/v1/settings/firm-banking-settings/{out}/{wdr}",
                        OUT_BANK, "9".repeat(12)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error", is(true)))
                .andExpect(jsonPath("$.status.code",  is("WBE2020")));
    }

    // ------------------------------------------------------------------
    // Audit-read
    // ------------------------------------------------------------------

    @Test
    void audit_returnsLatestFirstRowsWithSnakeCaseFields() throws Exception {
        FirmBankingAuditEntry e = new FirmBankingAuditEntry(
                "Firm Banking Details",
                "Update firmbanking details",
                "lamine@gmail.com",
                "lamine",
                "20260402115348");
        when(service.auditLog(eq(OUT_BANK), eq(WDRL_ACT)))
                .thenReturn(new FirmBankingAuditListResponse(List.of(e)));

        mvc.perform(get("/api/v1/settings/firm-banking-settings/{out}/{wdr}/audit",
                        OUT_BANK, WDRL_ACT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error", is(false)))
                .andExpect(jsonPath("$.data.list[0].audit_logs_actv",     is("Firm Banking Details")))
                .andExpect(jsonPath("$.data.list[0].audit_logs_actv_dtl", is("Update firmbanking details")))
                .andExpect(jsonPath("$.data.list[0].reg_id",              is("lamine@gmail.com")))
                .andExpect(jsonPath("$.data.list[0].reg_nm",              is("lamine")))
                .andExpect(jsonPath("$.data.list[0].reg_dtm",             is("20260402115348")));
    }

    @Test
    void audit_emptyResultList_rendersAsEmptyArray() throws Exception {
        when(service.auditLog(eq(OUT_BANK), eq(WDRL_ACT)))
                .thenReturn(new FirmBankingAuditListResponse(List.of()));

        mvc.perform(get("/api/v1/settings/firm-banking-settings/{out}/{wdr}/audit",
                        OUT_BANK, WDRL_ACT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error", is(false)))
                .andExpect(jsonPath("$.data.list").isEmpty());
    }

    // ------------------------------------------------------------------
    // Create (OQ-8a Option (c))
    // ------------------------------------------------------------------

    @Test
    void create_success_returnsSnakeCaseDetailEnvelope_withServerDerivedFileType() throws Exception {
        FirmBankingSettingDetail created = new FirmBankingSettingDetail(
                BIZ, OUT_BANK, "WEBC1", "GV00000001", "1",
                WDRL_ACT, "SHB", "Y", BigDecimal.valueOf(2000),
                "20260509120000", "20260509120000", null,
                "20260509120000", "tester");
        when(service.create(org.mockito.ArgumentMatchers.any(FirmBankingSettingCreateRequest.class)))
                .thenReturn(created);

        mvc.perform(post("/api/v1/settings/firm-banking-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "out_bank_cd": "970424",
                                  "wdrl_act_no": "700005285280",
                                  "bank_prtn_id": "WEBC1",
                                  "van_org_cd": "GV00000001",
                                  "org_stat": "1"
                                }"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error",   is(false)))
                .andExpect(jsonPath("$.status.code",    is("WBI0000")))
                .andExpect(jsonPath("$.data.out_bank_cd",  is(OUT_BANK)))
                .andExpect(jsonPath("$.data.wdrl_act_no",  is(WDRL_ACT)))
                .andExpect(jsonPath("$.data.file_type",    is("SHB")))
                .andExpect(jsonPath("$.data.bat_tran_branch_div",   is("Y")))
                .andExpect(jsonPath("$.data.bat_tran_branch_limit", is(2000)))
                .andExpect(jsonPath("$.data.org_stat",     is("1")));
    }

    @Test
    void create_unsupportedBank_envelopesAsWBE0001() throws Exception {
        // Service throws WBE0001 for any out_bank_cd outside the
        // bounded-bank set. The DTO @Pattern only enforces format
        // ([0-9]{1,15}); the allow-list lives in the service.
        when(service.create(org.mockito.ArgumentMatchers.any(FirmBankingSettingCreateRequest.class)))
                .thenThrow(new BusinessException("WBE0001", "OUT_BANK_CD"));

        mvc.perform(post("/api/v1/settings/firm-banking-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "out_bank_cd": "999999",
                                  "wdrl_act_no": "700005285280",
                                  "bank_prtn_id": "WEBC1",
                                  "van_org_cd": "GV00000001",
                                  "org_stat": "1"
                                }"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error", is(true)))
                .andExpect(jsonPath("$.status.code",  is("WBE0001")));
    }

    @Test
    void create_duplicatePk_envelopesAsWBE2021() throws Exception {
        when(service.create(org.mockito.ArgumentMatchers.any(FirmBankingSettingCreateRequest.class)))
                .thenThrow(new BusinessException("WBE2021", OUT_BANK, WDRL_ACT));

        mvc.perform(post("/api/v1/settings/firm-banking-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "out_bank_cd": "970424",
                                  "wdrl_act_no": "700005285280",
                                  "bank_prtn_id": "WEBC1",
                                  "van_org_cd": "GV00000001",
                                  "org_stat": "1"
                                }"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error", is(true)))
                .andExpect(jsonPath("$.status.code",  is("WBE2021")));
    }

    @Test
    void create_missingRequiredField_envelopesAsValidation() throws Exception {
        // wdrl_act_no missing → @NotBlank fails →
        // MethodArgumentNotValidException → WBE_VALIDATION envelope.
        mvc.perform(post("/api/v1/settings/firm-banking-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "out_bank_cd": "970424",
                                  "bank_prtn_id": "WEBC1",
                                  "van_org_cd": "GV00000001",
                                  "org_stat": "1"
                                }"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error", is(true)))
                .andExpect(jsonPath("$.status.code",  is("WBE_VALIDATION")));
    }

    @Test
    void create_clientSendingFileType_isIgnored_neverEchoedBack() throws Exception {
        // Defence-in-depth: Jackson FAIL_ON_UNKNOWN_PROPERTIES is off
        // by default, so a stray `file_type` in the request body is
        // dropped silently — the DTO doesn't carry the field. The
        // server still derives file_type from out_bank_cd and the
        // response carries the derived value (SHB for Shinhan).
        FirmBankingSettingDetail created = new FirmBankingSettingDetail(
                BIZ, OUT_BANK, "WEBC1", "GV00000001", "1",
                WDRL_ACT, "SHB", "Y", BigDecimal.valueOf(2000),
                "20260509120000", "20260509120000", null,
                "20260509120000", "tester");
        when(service.create(org.mockito.ArgumentMatchers.any(FirmBankingSettingCreateRequest.class)))
                .thenReturn(created);

        mvc.perform(post("/api/v1/settings/firm-banking-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "out_bank_cd": "970424",
                                  "wdrl_act_no": "700005285280",
                                  "bank_prtn_id": "WEBC1",
                                  "van_org_cd": "GV00000001",
                                  "org_stat": "1",
                                  "file_type": "WRB"
                                }"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.file_type", is("SHB"))); // server-derived, not WRB
    }

    // ------------------------------------------------------------------
    // Update
    // ------------------------------------------------------------------

    @Test
    void update_success_returnsSnakeCaseDetailEnvelope() throws Exception {
        FirmBankingSettingDetail updated = new FirmBankingSettingDetail(
                BIZ, OUT_BANK, "WEBC2-NEW", "GV99999999", "2",
                WDRL_ACT, "SHB", "Y", BigDecimal.valueOf(2000),
                "20260101090000", "20260101090000", "20260101090000",
                "20260509120000", "tester");
        when(service.update(eq(OUT_BANK), eq(WDRL_ACT),
                org.mockito.ArgumentMatchers.any(FirmBankingSettingUpdateRequest.class)))
                .thenReturn(updated);

        mvc.perform(put("/api/v1/settings/firm-banking-settings/{out}/{wdr}",
                        OUT_BANK, WDRL_ACT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bank_prtn_id": "WEBC2-NEW",
                                  "van_org_cd": "GV99999999",
                                  "org_stat": "2"
                                }"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error",   is(false)))
                .andExpect(jsonPath("$.status.code",    is("WBI0000")))
                .andExpect(jsonPath("$.data.out_bank_cd",  is(OUT_BANK)))
                .andExpect(jsonPath("$.data.wdrl_act_no",  is(WDRL_ACT)))
                .andExpect(jsonPath("$.data.bank_prtn_id", is("WEBC2-NEW")))
                .andExpect(jsonPath("$.data.van_org_cd",   is("GV99999999")))
                .andExpect(jsonPath("$.data.org_stat",     is("2")))
                .andExpect(jsonPath("$.data.last_edtr",    is("tester")))
                .andExpect(jsonPath("$.data.mod_dttm",     is("20260509120000")));

        verify(service).update(eq(OUT_BANK), eq(WDRL_ACT),
                org.mockito.ArgumentMatchers.any(FirmBankingSettingUpdateRequest.class));
    }

    @Test
    void update_rowMissing_envelopesAsWBE2020() throws Exception {
        when(service.update(eq(OUT_BANK), eq("9".repeat(12)),
                org.mockito.ArgumentMatchers.any(FirmBankingSettingUpdateRequest.class)))
                .thenThrow(new BusinessException("WBE2020"));

        mvc.perform(put("/api/v1/settings/firm-banking-settings/{out}/{wdr}",
                        OUT_BANK, "9".repeat(12))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bank_prtn_id": "WEBC2",
                                  "van_org_cd": "GV0",
                                  "org_stat": "1"
                                }"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error", is(true)))
                .andExpect(jsonPath("$.status.code",  is("WBE2020")));
    }

    @Test
    void update_missingRequiredField_envelopesAsValidation() throws Exception {
        // bank_prtn_id missing → @NotBlank fails →
        // MethodArgumentNotValidException → WBE_VALIDATION envelope.
        mvc.perform(put("/api/v1/settings/firm-banking-settings/{out}/{wdr}",
                        OUT_BANK, WDRL_ACT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "van_org_cd": "GV0",
                                  "org_stat": "1"
                                }"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error", is(true)))
                .andExpect(jsonPath("$.status.code",  is("WBE_VALIDATION")));
    }

    @Test
    void update_invalidOrgStat_envelopesAsValidation() throws Exception {
        // org_stat="9" fails the @Pattern("^[0-3]$") guard.
        mvc.perform(put("/api/v1/settings/firm-banking-settings/{out}/{wdr}",
                        OUT_BANK, WDRL_ACT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bank_prtn_id": "WEBC2",
                                  "van_org_cd": "GV0",
                                  "org_stat": "9"
                                }"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error", is(true)))
                .andExpect(jsonPath("$.status.code",  is("WBE_VALIDATION")));
    }

    // ------------------------------------------------------------------
    // Delete
    // ------------------------------------------------------------------

    @Test
    void delete_success_returnsOkEnvelopeWithNullData() throws Exception {
        // Service returns void; the controller wraps as ApiResponse.ok(null)
        // which Jackson + @JsonInclude(NON_NULL) serializes WITHOUT a
        // "data" key. The wire stays a clean { status } envelope.
        mvc.perform(delete("/api/v1/settings/firm-banking-settings/{out}/{wdr}",
                        OUT_BANK, WDRL_ACT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error",   is(false)))
                .andExpect(jsonPath("$.status.code",    is("WBI0000")))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(service).delete(OUT_BANK, WDRL_ACT);
    }

    @Test
    void delete_rowMissing_envelopesAsWBE2020() throws Exception {
        org.mockito.Mockito.doThrow(new BusinessException("WBE2020"))
                .when(service).delete(eq(OUT_BANK), eq("9".repeat(12)));

        mvc.perform(delete("/api/v1/settings/firm-banking-settings/{out}/{wdr}",
                        OUT_BANK, "9".repeat(12)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error", is(true)))
                .andExpect(jsonPath("$.status.code",  is("WBE2020")));
    }
}
