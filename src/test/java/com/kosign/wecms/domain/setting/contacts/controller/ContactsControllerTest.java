package com.kosign.wecms.domain.setting.contacts.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.exception.GlobalExceptionHandler;
import com.kosign.wecms.common.i18n.MessageResolver;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.setting.contacts.dto.ContactAccountRow;
import com.kosign.wecms.domain.setting.contacts.dto.ContactAddressRow;
import com.kosign.wecms.domain.setting.contacts.dto.ContactListResponse;
import com.kosign.wecms.domain.setting.contacts.dto.ContactRow;
import com.kosign.wecms.domain.setting.contacts.service.ContactsFileService;
import com.kosign.wecms.domain.setting.contacts.service.ContactsImportService;
import com.kosign.wecms.domain.setting.contacts.service.ContactsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone-MockMvc slice test for {@link ContactsController}. Verifies
 * the snake_case wire contract (request body + response JSON), validation
 * envelope, and the {@code WBE1190} in-use guard path on
 * {@code DELETE /api/v1/settings/contacts/{trcoKey}}.
 *
 * <p>Mirrors the {@code ErpTrnsHistoryControllerTest} pattern &mdash; the
 * global SNAKE_CASE strategy is wired into the {@link MockMvc} Jackson
 * converter manually so the response bytes match production exactly
 * without spinning up the full Spring context.
 */
class ContactsControllerTest {

    private MockMvc mvc;
    private ContactsService service;
    private ContactsImportService importService;
    private ContactsFileService fileService;
    private com.kosign.wecms.common.export.ExcelExportService excelExportService;

    @BeforeEach
    void setUp() {
        service = mock(ContactsService.class);
        importService = mock(ContactsImportService.class);
        fileService = mock(ContactsFileService.class);
        excelExportService = mock(com.kosign.wecms.common.export.ExcelExportService.class);

        ObjectMapper mapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .registerModule(new JavaTimeModule());
        MappingJackson2HttpMessageConverter jsonConverter =
                new MappingJackson2HttpMessageConverter(mapper);

        MessageResolver messages = mock(MessageResolver.class);
        when(messages.resolve(any(String.class))).thenReturn("Contacts error.");
        when(messages.resolve(any(String.class), any(Object[].class))).thenReturn("Contacts error.");
        GlobalExceptionHandler advice = new GlobalExceptionHandler(messages);

        mvc = MockMvcBuilders
                .standaloneSetup(new ContactsController(service, importService, fileService, excelExportService))
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
        ContactRow row = new ContactRow(
                "1000001", "CC_TEST",
                "CÔNG TY TNHH WEBCASH", "CÔNG TY TNHH WEBCASH EN",
                "0123456789", "L", null,
                "20260513000000",
                List.of(new ContactAddressRow("1", "Some address", 1, "20260513", "20260514")),
                List.of(new ContactAccountRow(
                        "1", "1234567890", 1, "ABBank - ABBANK",
                        "970425", null, "VND", "Owner", null)),
                List.of());
        ContactListResponse payload = new ContactListResponse(List.of(row), 1L, 0, 15, 1);
        when(service.list(any(), any(), eq(0), eq(15), any(), any())).thenReturn(payload);

        mvc.perform(get("/api/v1/settings/contacts")
                        .param("page", "0").param("size", "15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error", is(false)))
                .andExpect(jsonPath("$.status.code",  is("WBI0000")))
                .andExpect(jsonPath("$.data.total_count",    is(1)))
                .andExpect(jsonPath("$.data.rows[0].trco_key",       is("1000001")))
                .andExpect(jsonPath("$.data.rows[0].hand_trco_key",  is("CC_TEST")))
                .andExpect(jsonPath("$.data.rows[0].trco_type",      is("L")))
                // OQ-8: disp_ord is the address discriminator
                .andExpect(jsonPath("$.data.rows[0].addresses[0].disp_ord", is(1)))
                // OQ-7: bank_nm projected on the wire
                .andExpect(jsonPath("$.data.rows[0].accounts[0].bank_nm", is("ABBank - ABBANK")))
                .andExpect(jsonPath("$.data.rows[0].accounts[0].napas_bank_cd", is("970425")));
    }

    /* =================================================================
     *  Create — happy path + validation
     * ================================================================= */

    @Test
    void create_happyPathRoundTripsSnakeCase() throws Exception {
        ContactRow created = new ContactRow(
                "1000002", "NEW_CONTACT",
                "Co", "Co EN",
                null, "L", null,
                "20260513000000",
                List.of(), List.of(), List.of());
        when(service.create(any())).thenReturn(created);

        String body = """
                {
                  "hand_trco_key": "NEW_CONTACT",
                  "trco_cmnm": "Co",
                  "trco_cmenm": "Co EN",
                  "trco_type": "L",
                  "addresses": [],
                  "accounts": [
                    {
                      "op": "upsert",
                      "trco_acct_key": "1",
                      "bank_nm": "ABBank - ABBANK",
                      "acct_no": "1234567890",
                      "napas_bank_cd": "970425",
                      "acct_ccy_cd": "VND"
                    }
                  ],
                  "overseas_beneficiaries": []
                }
                """;

        mvc.perform(post("/api/v1/settings/contacts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error", is(false)))
                .andExpect(jsonPath("$.data.trco_key", is("1000002")));
    }

    @Test
    void create_missingRequiredFieldYieldsValidationEnvelope() throws Exception {
        // hand_trco_key omitted; should fail @NotBlank on the record.
        String body = """
                {
                  "trco_cmnm": "Co",
                  "trco_cmenm": "Co EN",
                  "trco_type": "L"
                }
                """;

        mvc.perform(post("/api/v1/settings/contacts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error", is(true)))
                .andExpect(jsonPath("$.status.code",  containsString("WBE_VALIDATION")));
    }

    @Test
    void create_invalidTrcoTypeYieldsValidationEnvelope() throws Exception {
        // trco_type 'X' fails the @Pattern(^[LO]$).
        String body = """
                {
                  "hand_trco_key": "TEST",
                  "trco_cmnm": "Co",
                  "trco_cmenm": "Co EN",
                  "trco_type": "X"
                }
                """;

        mvc.perform(post("/api/v1/settings/contacts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error", is(true)))
                .andExpect(jsonPath("$.status.code",  containsString("WBE_VALIDATION")));
    }

    /* =================================================================
     *  Soft-delete — happy path + WBE1190 (OQ-2 T1)
     * ================================================================= */

    @Test
    void delete_happyPath() throws Exception {
        mvc.perform(delete("/api/v1/settings/contacts/{trcoKey}", "1000001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error", is(false)))
                .andExpect(jsonPath("$.status.code",  is("WBI0000")));
        verify(service).softDelete("1000001");
    }

    @Test
    void delete_inUseContactThrowsWbe1190() throws Exception {
        doThrow(new BusinessException("WBE1190")).when(service).softDelete("1000001");

        mvc.perform(delete("/api/v1/settings/contacts/{trcoKey}", "1000001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error", is(true)))
                .andExpect(jsonPath("$.status.code",  is("WBE1190")));
    }

    /* =================================================================
     *  Bulk soft-delete — happy path + empty list rejected
     * ================================================================= */

    @Test
    void bulkDelete_happyPath() throws Exception {
        String body = "{ \"trco_keys\": [\"1000001\", \"1000002\"] }";

        mvc.perform(post("/api/v1/settings/contacts/bulk-delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code", is("WBI0000")));
        verify(service).bulkSoftDelete(List.of("1000001", "1000002"));
    }

    @Test
    void bulkDelete_emptyListRejected() throws Exception {
        String body = "{ \"trco_keys\": [] }";

        mvc.perform(post("/api/v1/settings/contacts/bulk-delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error", is(true)))
                .andExpect(jsonPath("$.status.code",  containsString("WBE_VALIDATION")));
        verify(service, never()).bulkSoftDelete(any());
    }
}
