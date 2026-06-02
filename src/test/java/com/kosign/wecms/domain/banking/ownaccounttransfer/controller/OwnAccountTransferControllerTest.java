package com.kosign.wecms.domain.banking.ownaccounttransfer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.exception.GlobalExceptionHandler;
import com.kosign.wecms.common.i18n.MessageResolver;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.ownaccounttransfer.dto.response.BalanceRefreshResponse;
import com.kosign.wecms.domain.banking.ownaccounttransfer.dto.response.NapasBankInfoRow;
import com.kosign.wecms.domain.banking.ownaccounttransfer.dto.response.SourceAccountListResponse;
import com.kosign.wecms.domain.banking.ownaccounttransfer.dto.response.SourceAccountRow;
import com.kosign.wecms.domain.banking.ownaccounttransfer.service.OatNxisasService;
import com.kosign.wecms.domain.banking.ownaccounttransfer.service.OwnAccountTransferService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone-MockMvc slice test for {@link OwnAccountTransferController}.
 * Verifies the snake_case wire contract (request + response), the
 * validation envelope on each endpoint, and the error-code mapping
 * for the BusinessException paths the service raises.
 *
 * <p>Mirrors the {@code ChartOfAccountsControllerTest} pattern:
 * Jackson is configured manually with {@code SNAKE_CASE} so the
 * response bytes match production exactly without spinning up the
 * full Spring context.
 */
class OwnAccountTransferControllerTest {

    private MockMvc mvc;
    private OwnAccountTransferService service;

    @BeforeEach
    void setUp() {
        service = mock(OwnAccountTransferService.class);

        ObjectMapper mapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .registerModule(new JavaTimeModule());
        MappingJackson2HttpMessageConverter jsonConverter =
                new MappingJackson2HttpMessageConverter(mapper);

        MessageResolver messages = mock(MessageResolver.class);
        when(messages.resolve(any(String.class))).thenReturn("Own Account Transfer error.");
        when(messages.resolve(any(String.class), any(Object[].class)))
                .thenReturn("Own Account Transfer error.");
        GlobalExceptionHandler advice = new GlobalExceptionHandler(messages);

        mvc = MockMvcBuilders
                .standaloneSetup(new OwnAccountTransferController(service, nxisasService))
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
     *  GET /source-accounts
     * ================================================================= */

    @Test
    void sourceAccounts_happyPathSerialisesSnakeCase() throws Exception {
        SourceAccountRow row = sampleSourceRow();
        SourceAccountListResponse payload = new SourceAccountListResponse(
                List.of(row), 1, new BigDecimal("100.00"), new BigDecimal("90.00"),
                List.of("11704088", "11704003", "11704004", "11704001", "11704020"));
        when(service.listSourceAccounts(any(), any(), any())).thenReturn(payload);

        mvc.perform(get("/api/v1/banking/own-account-transfer/source-accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error",                 is(false)))
                .andExpect(jsonPath("$.status.code",                  is("WBI0000")))
                .andExpect(jsonPath("$.data.total_count",             is(1)))
                .andExpect(jsonPath("$.data.list[0].bank_cd",         is("11704088")))
                .andExpect(jsonPath("$.data.list[0].bank_nm",         is("Shinhan VN")))
                .andExpect(jsonPath("$.data.list[0].acct_no",         is("0001")))
                .andExpect(jsonPath("$.data.list[0].acct_nick_nm",    is("Main")))
                .andExpect(jsonPath("$.data.list[0].acct_type_cd",    is("03")))
                .andExpect(jsonPath("$.data.list[0].ccy_cd",          is("VND")))
                .andExpect(jsonPath("$.data.list[0].acct_bal_inq_stts", is("00000000")))
                .andExpect(jsonPath("$.data.list[0].bank_cert_seq",   is("12345")))
                .andExpect(jsonPath("$.data.list[0].can_transfer",    is(true)))
                .andExpect(jsonPath("$.data.allowed_bank_cds[0]",     is("11704088")));
    }

    @Test
    void sourceAccounts_forwardsBankCdAndSortParams() throws Exception {
        when(service.listSourceAccounts(eq("11704001"), eq("BAL"), eq("DESC")))
                .thenReturn(new SourceAccountListResponse(
                        List.of(), 0, BigDecimal.ZERO, BigDecimal.ZERO, List.of()));

        mvc.perform(get("/api/v1/banking/own-account-transfer/source-accounts")
                        .param("bank_cd",        "11704001")
                        .param("sortField",     "BAL")
                        .param("sortDirection", "DESC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error", is(false)));
    }

    /* =================================================================
     *  POST /balance-refresh
     * ================================================================= */

    @Test
    void balanceRefresh_happyPathReturnsPatchedRow() throws Exception {
        when(service.applyBalanceRefresh(any())).thenReturn(
                new BalanceRefreshResponse(
                        "11704088", "0001", new BigDecimal("150"),
                        "VND", "20260518100000"));

        String body = """
            {
              "bank_cd":             "11704088",
              "acct_no":             "0001",
              "bal":                 150,
              "real_amt":            140,
              "acct_bal_inq_stts":   "00000000",
              "acct_bal_inq_msg":    "Refreshed",
              "ccy_cd":              "VND"
            }
            """;

        mvc.perform(post("/api/v1/banking/own-account-transfer/balance-refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error",        is(false)))
                .andExpect(jsonPath("$.data.bank_cd",        is("11704088")))
                .andExpect(jsonPath("$.data.acct_no",        is("0001")))
                .andExpect(jsonPath("$.data.bal",            is(150)))
                .andExpect(jsonPath("$.data.ccy_cd",         is("VND")));
    }

    @Test
    void balanceRefresh_missingRequiredField_returnsValidationEnvelope() throws Exception {
        // bank_cd is required by @NotBlank
        String body = """
            {
              "acct_no":           "0001",
              "acct_bal_inq_stts": "00000000"
            }
            """;

        mvc.perform(post("/api/v1/banking/own-account-transfer/balance-refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error", is(true)))
                .andExpect(jsonPath("$.status.code",  is("WBE_VALIDATION")));

        verify(service, never()).applyBalanceRefresh(any());
    }

    @Test
    void balanceRefresh_accountNotFound_mapsToBusinessCode() throws Exception {
        doThrow(new BusinessException("WBE_OWN_ACCOUNT_TRANSFER_ACCOUNT_NOT_FOUND"))
                .when(service).applyBalanceRefresh(any());

        String body = """
            {
              "bank_cd":           "11704088",
              "acct_no":           "0001",
              "acct_bal_inq_stts": "00000000"
            }
            """;

        mvc.perform(post("/api/v1/banking/own-account-transfer/balance-refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error", is(true)))
                .andExpect(jsonPath("$.status.code",
                        is("WBE_OWN_ACCOUNT_TRANSFER_ACCOUNT_NOT_FOUND")));
    }

    @Test
    void balanceRefresh_userWithoutAuth_mapsToForbidden() throws Exception {
        doThrow(new BusinessException("WBE_OWN_ACCOUNT_TRANSFER_FORBIDDEN"))
                .when(service).applyBalanceRefresh(any());

        String body = """
            {
              "bank_cd":           "11704088",
              "acct_no":           "0001",
              "acct_bal_inq_stts": "00000000"
            }
            """;

        mvc.perform(post("/api/v1/banking/own-account-transfer/balance-refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code",
                        is("WBE_OWN_ACCOUNT_TRANSFER_FORBIDDEN")));
    }

    /* =================================================================
     *  POST /engine-connection-check
     * ================================================================= */

    @Test
    void engineCheck_happyPathReturns204() throws Exception {
        String body = """
            {
              "result":         "OK",
              "engine_vendor":  "COCON",
              "engine_version": "NXSAS 2025.03.11.0",
              "user_agent":     "Mozilla/5.0",
              "os_platform":    "Win32"
            }
            """;

        mvc.perform(post("/api/v1/banking/own-account-transfer/engine-connection-check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNoContent());

        verify(service).recordEngineConnectionCheck(any());
    }

    @Test
    void engineCheck_invalidResultEnum_validationEnvelope() throws Exception {
        String body = """
            {
              "result":         "BOGUS",
              "engine_vendor":  "COCON",
              "engine_version": "NXSAS 2025.03.11.0"
            }
            """;

        mvc.perform(post("/api/v1/banking/own-account-transfer/engine-connection-check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error", is(true)))
                .andExpect(jsonPath("$.status.code",  is("WBE_VALIDATION")));

        verify(service, never()).recordEngineConnectionCheck(any());
    }

    /* =================================================================
     *  GET /napas-bank-info/{napas_bank_cd}
     * ================================================================= */

    @Test
    void napasLookup_happyPath() throws Exception {
        when(service.lookupNapasBankInfo("970424"))
                .thenReturn(new NapasBankInfoRow(
                        "970424", null, null, null, "11704088"));

        mvc.perform(get("/api/v1/banking/own-account-transfer/napas-bank-info/{n}", "970424"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error",   is(false)))
                .andExpect(jsonPath("$.data.napas_bank_cd", is("970424")))
                .andExpect(jsonPath("$.data.bank_cd",       is("11704088")));
    }

    @Test
    void napasLookup_miss_mapsToNapasNotFound() throws Exception {
        doThrow(new BusinessException("WBE_OWN_ACCOUNT_TRANSFER_NAPAS_NOT_FOUND"))
                .when(service).lookupNapasBankInfo("000000");

        mvc.perform(get("/api/v1/banking/own-account-transfer/napas-bank-info/{n}", "000000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error", is(true)))
                .andExpect(jsonPath("$.status.code",
                        is("WBE_OWN_ACCOUNT_TRANSFER_NAPAS_NOT_FOUND")));
    }

    /* =================================================================
     *  Test fixtures
     * ================================================================= */

    /**
     * Canonical {@link SourceAccountRow} fixture for the happy-path
     * serialisation assertions. Centralised as a factory so a future
     * DTO field addition (this record gained five Phase-B cert fields
     * in the composer/cert MR) only touches this one method instead of
     * every inline {@code new SourceAccountRow(...)} call site.
     *
     * <p>Field order mirrors the record: bank/acct identity → balances →
     * ccy → last-inquiry telemetry → {@code bankCertSeq} → the four
     * Phase-B cert fields → {@code canTransfer}. The cert fields carry
     * representative non-null values; the happy-path test asserts only
     * the non-cert columns + {@code bank_cert_seq} + {@code can_transfer}.
     */
    private static SourceAccountRow sampleSourceRow() {
        return new SourceAccountRow(
                "11704088", "Shinhan VN", "0001", "Main",
                "03",
                new BigDecimal("100.00"), new BigDecimal("90.00"),
                "VND", "00000000", null,
                "20260518100000", "20260518100000",
                "12345",
                // Phase-B cert fields: certInfoCon / certInfoDetlCon
                // (decrypted) / certInfoDetlCon2 / nxisasGbSeqNo.
                "cert-login-id", "cert-login-pw", null, "9001",
                true);
    }
}
