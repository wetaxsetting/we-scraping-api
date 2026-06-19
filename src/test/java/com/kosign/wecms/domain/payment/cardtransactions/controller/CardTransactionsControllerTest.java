package com.kosign.wecms.domain.payment.cardtransactions.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.exception.GlobalExceptionHandler;
import com.kosign.wecms.common.export.ExcelExportService;
import com.kosign.wecms.common.i18n.MessageResolver;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.payment.cardtransactions.dto.ActiveCard;
import com.kosign.wecms.domain.payment.cardtransactions.dto.CardListResponse;
import com.kosign.wecms.domain.payment.cardtransactions.dto.CardTransactionListResponse;
import com.kosign.wecms.domain.payment.cardtransactions.dto.CardTransactionRow;
import com.kosign.wecms.domain.payment.cardtransactions.dto.CardTransactionTotals;
import com.kosign.wecms.domain.payment.cardtransactions.service.CardTranListR001Service;
import com.kosign.wecms.domain.payment.cardtransactions.service.CardTransactionScrapeService;
import com.kosign.wecms.domain.payment.cardtransactions.service.CardTransactionSyncService;
import com.kosign.wecms.domain.payment.cardtransactions.service.CardTransactionsService;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone-MockMvc slice test for {@link CardTransactionsController}.
 * Verifies the snake_case wire contract (request + response), the
 * validation envelope, the discriminated PATCH body shape, and the
 * business-error envelope.
 *
 * <p>Mirrors the {@code ChartOfAccountsControllerTest} pattern: Jackson
 * configured manually with {@code SNAKE_CASE} so the response bytes
 * match production exactly without spinning up the full Spring context.
 */
class CardTransactionsControllerTest {

    private MockMvc mvc;
    private CardTransactionsService service;
    private CardTransactionSyncService syncService;
    private CardTransactionScrapeService scrapeService;
    private CardTranListR001Service cardTranListR001Service;
    private ExcelExportService excelExportService;

    @BeforeEach
    void setUp() {
        service = mock(CardTransactionsService.class);
        syncService = mock(CardTransactionSyncService.class);
        scrapeService = mock(CardTransactionScrapeService.class);
        cardTranListR001Service = mock(CardTranListR001Service.class);
        excelExportService = mock(ExcelExportService.class);

        ObjectMapper mapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .registerModule(new JavaTimeModule());
        MappingJackson2HttpMessageConverter jsonConverter =
                new MappingJackson2HttpMessageConverter(mapper);

        MessageResolver messages = mock(MessageResolver.class);
        when(messages.resolve(any(String.class))).thenReturn("Card transactions error.");
        when(messages.resolve(any(String.class), any(Object[].class)))
                .thenReturn("Card transactions error.");
        GlobalExceptionHandler advice = new GlobalExceptionHandler(messages);

        mvc = MockMvcBuilders
                .standaloneSetup(new CardTransactionsController(service, syncService, scrapeService, cardTranListR001Service, excelExportService))
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
        CardTransactionRow row = new CardTransactionRow(
                "156", "11704020", "VietinBank",
                "407465******5559", null, "VTB(5559)", "LE MINH",
                "GOLDEN LOTUS - TVL",
                "VND", new BigDecimal("799200.000000"),
                "20251019", "114140",
                "1007010", "100", "100",
                "Lunch", "1390",
                null,   // apprDocNo
                "1", "payment.card_transactions.status.1",
                List.of(), List.of(),
                "lamine@gmail.com", "20251023073754",
                "lamine@gmail.com", "20251216043342",
                // GAP-CARD-FIELDS — usingType / authCode / city / note
                "ONLINE", "AUTH123", "HANOI", "client dinner");

        CardTransactionListResponse payload = new CardTransactionListResponse(
                0, 15, 1L,
                new CardTransactionTotals(1L,
                        new BigDecimal("799200.000000"),
                        1L, new BigDecimal("799200.000000"),
                        0L, BigDecimal.ZERO),
                List.of(row));

        when(service.list(eq("20250101"), eq("20251231"), any(), any(), any(), any(),
                any(), any(), anyInt(), anyInt())).thenReturn(payload);

        mvc.perform(get("/api/v1/payment/card-transactions")
                        .param("from_date", "20250101")
                        .param("to_date",   "20251231"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error",                  is(false)))
                .andExpect(jsonPath("$.status.code",                   is("WBI0000")))
                .andExpect(jsonPath("$.data.total_count",              is(1)))
                .andExpect(jsonPath("$.data.page",                     is(0)))
                .andExpect(jsonPath("$.data.page_size",                is(15)))
                .andExpect(jsonPath("$.data.totals.total_count",       is(1)))
                .andExpect(jsonPath("$.data.totals.totl_appr_req_cnt", is(1)))
                .andExpect(jsonPath("$.data.rows[0].card_seq_no",      is("156")))
                .andExpect(jsonPath("$.data.rows[0].bank_cd",          is("11704020")))
                .andExpect(jsonPath("$.data.rows[0].bank_nm",          is("VietinBank")))
                .andExpect(jsonPath("$.data.rows[0].card_nick_nm",     is("VTB(5559)")))
                .andExpect(jsonPath("$.data.rows[0].stts_key",         is("payment.card_transactions.status.1")))
                .andExpect(jsonPath("$.data.rows[0].linked_docs",      is(List.of())))
                .andExpect(jsonPath("$.data.rows[0].approvers",        is(List.of())));
    }

    @Test
    void list_missingFromDtReturnsValidationEnvelope() throws Exception {
        mvc.perform(get("/api/v1/payment/card-transactions")
                        .param("to_date", "20251231"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error", is(true)))
                .andExpect(jsonPath("$.status.code",  is("WBE_VALIDATION")));
    }

    // `@Pattern` on the `@RequestParam` from_date / to_date is a method-
    // parameter constraint that requires Spring's
    // MethodValidationPostProcessor (registered automatically under
    // @SpringBootTest but NOT by MockMvcBuilders.standaloneSetup). Like
    // ChartOfAccountsControllerTest, the malformed-value envelope
    // verification belongs in an integration test, not this slice. The
    // service layer's validateDateRange() guard provides the same
    // coverage at the service-level unit test.

    @Test
    void list_forwardsAllQueryParams() throws Exception {
        CardTransactionListResponse empty = new CardTransactionListResponse(
                0, 15, 0L,
                new CardTransactionTotals(0L, BigDecimal.ZERO, 0L, BigDecimal.ZERO,
                        0L, BigDecimal.ZERO),
                List.of());
        when(service.list(any(), any(), any(), any(), any(), any(),
                any(), any(), anyInt(), anyInt())).thenReturn(empty);

        mvc.perform(get("/api/v1/payment/card-transactions")
                        .param("from_date",      "20250101")
                        .param("to_date",        "20251231")
                        .param("srch_wd",        "Coffee")
                        .param("bank_cd",        "11704020")
                        .param("bank_cd",        "11704088")
                        .param("card_no",        "407465******5559")
                        .param("link_yn",        "Y")
                        .param("sort_field",     "TRSC_AMT")
                        .param("sort_direction", "DESC")
                        .param("page",           "2")
                        .param("size",           "25"))
                .andExpect(status().isOk());

        verify(service).list(
                eq("20250101"), eq("20251231"), eq("Coffee"),
                eq(List.of("11704020", "11704088")),
                eq(List.of("407465******5559")),
                eq("Y"),
                eq("TRSC_AMT"), eq("DESC"),
                eq(2), eq(25));
    }

    /* =================================================================
     *  Cards dropdown
     * ================================================================= */

    @Test
    void cards_happyPathSerialisesSnakeCase() throws Exception {
        CardListResponse payload = new CardListResponse(List.of(
                new ActiveCard("11704088", "Shinhan Bank VN",
                        "4696********4372", "", "Shinhan VN(4372)", "LEE JAEHWAN",
                        "20260619120000")));
        when(service.cards(eq(null))).thenReturn(payload);

        mvc.perform(get("/api/v1/payment/card-transactions/cards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cards[0].bank_cd",     is("11704088")))
                .andExpect(jsonPath("$.data.cards[0].bank_nm",     is("Shinhan Bank VN")))
                .andExpect(jsonPath("$.data.cards[0].card_no",     is("4696********4372")))
                .andExpect(jsonPath("$.data.cards[0].card_hldr_nm",is("LEE JAEHWAN")))
                .andExpect(jsonPath("$.data.cards[0].trn_lst_suc_dtm", is("20260619120000")));
    }

    /* =================================================================
     *  PATCH — discriminated body
     * ================================================================= */

    @Test
    void patch_descriptionHappyPathDelegatesToService() throws Exception {
        String body = """
                { "field": "description", "value": "Lunch with client" }
                """;

        mvc.perform(patch("/api/v1/payment/card-transactions/156")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error", is(false)));

        verify(service).patch(eq("156"), any());
    }

    @Test
    void patch_purposeHappyPathDelegatesToService() throws Exception {
        String body = """
                { "field": "purpose", "value": "1007010" }
                """;

        mvc.perform(patch("/api/v1/payment/card-transactions/156")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error", is(false)));

        verify(service).patch(eq("156"), any());
    }

    @Test
    void patch_unknownFieldDiscriminatorReturnsBadRequestEnvelope() throws Exception {
        // Jackson's @JsonSubTypes resolver rejects an unknown discriminator
        // with InvalidTypeIdException, which Spring's MessageConverter wraps
        // as HttpMessageNotReadableException → handled by
        // GlobalExceptionHandler with HTTP 400 + WBE_BAD_REQUEST. The
        // envelope shape matches every other malformed-body path in the API.
        String body = """
                { "field": "unknown_action", "value": "x" }
                """;

        mvc.perform(patch("/api/v1/payment/card-transactions/156")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status.error", is(true)))
                .andExpect(jsonPath("$.status.code",  is("WBE_BAD_REQUEST")));

        verify(service, never()).patch(any(), any());
    }

    @Test
    void patch_notFoundSurfacesBusinessError() throws Exception {
        doThrow(new BusinessException("WBE_CARD_TRANSACTIONS_NOT_FOUND"))
                .when(service).patch(eq("missing"), any());

        String body = """
                { "field": "description", "value": "x" }
                """;

        mvc.perform(patch("/api/v1/payment/card-transactions/missing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error", is(true)))
                .andExpect(jsonPath("$.status.code",  is("WBE_CARD_TRANSACTIONS_NOT_FOUND")));
    }

    /* =================================================================
     *  PUT — reporting account
     * ================================================================= */

    @Test
    void updateReportingAccount_happyPathDelegatesToService() throws Exception {
        String body = """
                { "chart_acct_seq_no": "100", "chart_acct_cd": "100" }
                """;

        mvc.perform(put("/api/v1/payment/card-transactions/156/reporting-account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error", is(false)));

        verify(service).updateReportingAccount(eq("156"), any());
    }

    @Test
    void updateReportingAccount_missingChartAcctSeqNoReturnsValidationEnvelope() throws Exception {
        String body = """
                { "chart_acct_cd": "100" }
                """;

        mvc.perform(put("/api/v1/payment/card-transactions/156/reporting-account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error", is(true)))
                .andExpect(jsonPath("$.status.code",  is("WBE_VALIDATION")));

        verify(service, never()).updateReportingAccount(any(), any());
    }

    @Test
    void updateReportingAccount_chartAcctInvalidSurfacesBusinessError() throws Exception {
        doThrow(new BusinessException("WBE_CARD_TRANSACTIONS_CHART_ACCT_INVALID"))
                .when(service).updateReportingAccount(eq("156"), any());

        String body = """
                { "chart_acct_seq_no": "999", "chart_acct_cd": "999" }
                """;

        mvc.perform(put("/api/v1/payment/card-transactions/156/reporting-account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error", is(true)))
                .andExpect(jsonPath("$.status.code",
                        is("WBE_CARD_TRANSACTIONS_CHART_ACCT_INVALID")));
    }

    // Path-variable @Pattern validation requires Spring's
    // MethodValidationPostProcessor (registered automatically under a
    // full @SpringBootTest context but NOT by MockMvcBuilders.standaloneSetup).
    // Verifying the "abc" → WBE_VALIDATION envelope on path inputs
    // therefore belongs in an integration test, not this slice. Same
    // precedent as ChartOfAccountsControllerTest.
}
