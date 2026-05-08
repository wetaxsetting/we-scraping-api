package com.kosign.wecms.domain.einvoice.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.common.web.SortDirection;
import com.kosign.wecms.domain.einvoice.dto.EInvoiceListResponse;
import com.kosign.wecms.domain.einvoice.dto.EInvoiceListResponse.CurrencyTotal;
import com.kosign.wecms.domain.einvoice.dto.EInvoiceRow;
import com.kosign.wecms.domain.einvoice.entity.BusaDocMstr;
import com.kosign.wecms.domain.einvoice.repository.BusaDocMstrRepository;
import com.kosign.wecms.domain.einvoice.repository.EInvoiceCcyAggregate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Backend MR A + B coverage — IR-1 (DOC_TYPE), IR-3 (date range), IR-4
 * (raw EINV_INVOICESTATUS), IR-5 (5-column keyword search), IR-6
 * (full-filtered totals), IR-7 (no INV_TYPE), plus pagination
 * (clamping + Page response shape) and sort (whitelist parse +
 * default tiebreaker).
 */
@ExtendWith(MockitoExtension.class)
class EInvoiceServiceTest {

    @Mock BusaDocMstrRepository repo;

    private EInvoiceService service;

    private static final String BIZ  = "VN_VNM0004_000001";
    private static final String USER = "testuser";
    private static final String FROM = "20240101";
    private static final String TO   = "20240131";

    @BeforeEach
    void seed() {
        service = new EInvoiceService(repo);
        TenantContext.set(USER, BIZ, LangCd.EN);
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    // ------------------------------------------------------------------
    // IR-1 — DOC_TYPE mapping
    // ------------------------------------------------------------------

    @Test
    void resolveDocType_sales_mapsToI() {
        assertThat(service.resolveDocType("SALES")).isEqualTo("I");
        assertThat(service.resolveDocType("sales")).isEqualTo("I");
        assertThat(service.resolveDocType("I")).isEqualTo("I");
    }

    @Test
    void resolveDocType_expense_mapsToB() {
        assertThat(service.resolveDocType("EXPENSE")).isEqualTo("B");
        assertThat(service.resolveDocType("BILL")).isEqualTo("B");
        assertThat(service.resolveDocType("B")).isEqualTo("B");
    }

    @Test
    void resolveDocType_legacyEnumE_isRejected() {
        assertBusinessCode("WBE2002", () -> service.resolveDocType("E"));
    }

    @Test
    void resolveDocType_legacyEnumS_isRejected() {
        assertBusinessCode("WBE2002", () -> service.resolveDocType("S"));
        assertBusinessCode("WBE2002", () -> service.resolveDocType("PAYROLLBOOK"));
    }

    @Test
    void resolveDocType_blank_isRejected() {
        assertBusinessCode("WBE2002", () -> service.resolveDocType(""));
        assertBusinessCode("WBE2002", () -> service.resolveDocType("   "));
        assertBusinessCode("WBE2002", () -> service.resolveDocType(null));
    }

    @Test
    void resolveDocType_unknownValue_isRejected() {
        assertBusinessCode("WBE2002", () -> service.resolveDocType("FOO"));
    }

    // ------------------------------------------------------------------
    // IR-3 — date-range validation
    // ------------------------------------------------------------------

    @Test
    void validateDateRange_happyPath_passes() {
        service.validateDateRange("20240101", "20240131");
    }

    @Test
    void validateDateRange_missingFrom_throwsValidation() {
        assertBusinessCode("WBE_VALIDATION", () -> service.validateDateRange(null, "20240131"));
        assertBusinessCode("WBE_VALIDATION", () -> service.validateDateRange("", "20240131"));
        assertBusinessCode("WBE_VALIDATION", () -> service.validateDateRange("   ", "20240131"));
    }

    @Test
    void validateDateRange_missingTo_throwsValidation() {
        assertBusinessCode("WBE_VALIDATION", () -> service.validateDateRange("20240101", null));
        assertBusinessCode("WBE_VALIDATION", () -> service.validateDateRange("20240101", ""));
    }

    @Test
    void validateDateRange_malformedDate_throwsValidation() {
        assertBusinessCode("WBE_VALIDATION", () -> service.validateDateRange("20240230", "20240331"));
        assertBusinessCode("WBE_VALIDATION", () -> service.validateDateRange("garbage1", "20240131"));
    }

    @Test
    void validateDateRange_inverted_throwsValidation() {
        assertBusinessCode("WBE_VALIDATION", () -> service.validateDateRange("20240301", "20240101"));
    }

    @Test
    void validateDateRange_sameDay_passes() {
        service.validateDateRange("20240115", "20240115");
    }

    // ------------------------------------------------------------------
    // Pagination — clamp + Page passthrough
    // ------------------------------------------------------------------

    @Test
    void clampSize_defaultsTo10_whenZeroOrNegative() {
        assertThat(EInvoiceService.clampSize(0)).isEqualTo(10);
        assertThat(EInvoiceService.clampSize(-1)).isEqualTo(10);
        assertThat(EInvoiceService.clampSize(-100)).isEqualTo(10);
    }

    @Test
    void clampSize_capsAt100() {
        assertThat(EInvoiceService.clampSize(100)).isEqualTo(100);
        assertThat(EInvoiceService.clampSize(101)).isEqualTo(100);
        assertThat(EInvoiceService.clampSize(99999)).isEqualTo(100);
    }

    @Test
    void clampSize_passesThroughInRange() {
        assertThat(EInvoiceService.clampSize(1)).isEqualTo(1);
        assertThat(EInvoiceService.clampSize(20)).isEqualTo(20);
        assertThat(EInvoiceService.clampSize(50)).isEqualTo(50);
    }

    @Test
    void list_passesPageableToRepository_withClampedSize() {
        whenRepoReturnsEmpty();

        // size=200 should clamp to MAX_PAGE_SIZE=100, page=-3 to 0.
        service.list("SALES", FROM, TO, null, -3, 200, null, null);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repo).findFiltered(eq(BIZ), eq("I"), eq(FROM), eq(TO), isNull(), captor.capture());
        Pageable p = captor.getValue();
        assertThat(p.getPageNumber()).isEqualTo(0);
        assertThat(p.getPageSize()).isEqualTo(100);
    }

    @Test
    void list_responseEnvelopesPaginationFields() {
        BusaDocMstr r = sampleRow("D1", "VND", new BigDecimal("100"));
        when(repo.findFiltered(eq(BIZ), eq("I"), eq(FROM), eq(TO), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(r), PageRequest.of(2, 10), 53L));
        when(repo.aggregateByCurrency(eq(BIZ), eq("I"), eq(FROM), eq(TO), isNull()))
                .thenReturn(List.of());

        EInvoiceListResponse res = service.list("SALES", FROM, TO, null, 2, 10, null, null);

        assertThat(res.totalCount()).isEqualTo(53L);
        assertThat(res.totalPages()).isEqualTo(6);    // ceil(53/10)
        assertThat(res.pageNumber()).isEqualTo(2);
        assertThat(res.pageSize()).isEqualTo(10);
    }

    // ------------------------------------------------------------------
    // Sort — whitelist parse + default fall-through + tiebreaker
    // ------------------------------------------------------------------

    @Test
    void sortField_unknown_fallsBackToDefault() {
        assertThat(EInvoiceSortField.parse("UNKNOWN")).isNull();
        assertThat(EInvoiceSortField.parse("")).isNull();
        assertThat(EInvoiceSortField.parse(null)).isNull();
    }

    @Test
    void sortField_whitelist_resolvesToEntityProperty() {
        assertThat(EInvoiceSortField.parse("DATE_TIME").entityProperty()).isEqualTo("issuDt");
        assertThat(EInvoiceSortField.parse("INVOICE_NO").entityProperty()).isEqualTo("einvInvoicesymbol");
        assertThat(EInvoiceSortField.parse("BUYER").entityProperty()).isEqualTo("trcoCmnm");
        assertThat(EInvoiceSortField.parse("CURRENCY").entityProperty()).isEqualTo("trcoTrscCcyCd");
        assertThat(EInvoiceSortField.parse("TOTAL").entityProperty()).isEqualTo("totlAmt");
        assertThat(EInvoiceSortField.parse("STATUS").entityProperty()).isEqualTo("einvInvoicestatus");
        assertThat(EInvoiceSortField.parse("TAX").entityProperty()).isEqualTo("taxAmtSum");
        assertThat(EInvoiceSortField.parse("PRETAX").entityProperty()).isEqualTo("splyAmtSum");
        assertThat(EInvoiceSortField.parse("date_time")).isEqualTo(EInvoiceSortField.DATE_TIME);
    }

    @Test
    void buildSort_default_isIssuDtRegDtmDesc() {
        Sort s = EInvoiceService.buildSort(null, null);
        List<Sort.Order> orders = s.toList();
        assertThat(orders).hasSize(2);
        assertThat(orders.get(0).getProperty()).isEqualTo("issuDt");
        assertThat(orders.get(0).getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(orders.get(1).getProperty()).isEqualTo("regDtm");
        assertThat(orders.get(1).getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void buildSort_userPrimary_appendsRegDtmTiebreaker() {
        // BUYER ASC + tiebreakers (issuDt DESC, regDtm DESC).
        Sort s = EInvoiceService.buildSort(EInvoiceSortField.BUYER, SortDirection.ASC);
        List<Sort.Order> orders = s.toList();
        assertThat(orders).extracting(Sort.Order::getProperty)
                .containsExactly("trcoCmnm", "issuDt", "regDtm");
        assertThat(orders.get(0).getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void buildSort_dateTimePrimary_avoidsDuplicateIssuDt() {
        // DATE_TIME asc + only regDtm DESC (no duplicate issuDt).
        Sort s = EInvoiceService.buildSort(EInvoiceSortField.DATE_TIME, SortDirection.ASC);
        List<Sort.Order> orders = s.toList();
        assertThat(orders).extracting(Sort.Order::getProperty)
                .containsExactly("issuDt", "regDtm");
        assertThat(orders.get(0).getDirection()).isEqualTo(Sort.Direction.ASC);
        assertThat(orders.get(1).getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void list_unknownSortField_passesDefaultPageable() {
        whenRepoReturnsEmpty();

        service.list("SALES", FROM, TO, null, 0, 10, "BOGUS", "ASC");

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repo).findFiltered(eq(BIZ), eq("I"), eq(FROM), eq(TO), isNull(), captor.capture());
        List<Sort.Order> orders = captor.getValue().getSort().toList();
        assertThat(orders).extracting(Sort.Order::getProperty)
                .containsExactly("issuDt", "regDtm");
    }

    // ------------------------------------------------------------------
    // IR-5 — keyword pattern building + repo call
    // ------------------------------------------------------------------

    @Test
    void buildKeywordPattern_blank_returnsNull() {
        assertThat(EInvoiceService.buildKeywordPattern(null)).isNull();
        assertThat(EInvoiceService.buildKeywordPattern("")).isNull();
        assertThat(EInvoiceService.buildKeywordPattern("   ")).isNull();
    }

    @Test
    void buildKeywordPattern_lowercasesAndWraps() {
        assertThat(EInvoiceService.buildKeywordPattern("CÔNG TY"))
                .isEqualTo("%công ty%");
    }

    @Test
    void buildKeywordPattern_escapesLikeMetaChars() {
        // Underscore, percent, and the escape char itself ("!") must
        // be prefixed with "!" so the LIKE treats them as literals.
        assertThat(EInvoiceService.buildKeywordPattern("100%_test"))
                .isEqualTo("%100!%!_test%");
        assertThat(EInvoiceService.buildKeywordPattern("a!b"))
                .isEqualTo("%a!!b%");
    }

    @Test
    void list_blankKeyword_passesNullPatternToRepo() {
        whenRepoReturnsEmpty();
        service.list("SALES", FROM, TO, "   ", 0, 10, null, null);
        verify(repo).findFiltered(eq(BIZ), eq("I"), eq(FROM), eq(TO), isNull(), any(Pageable.class));
        verify(repo).aggregateByCurrency(eq(BIZ), eq("I"), eq(FROM), eq(TO), isNull());
    }

    @Test
    void list_nonBlankKeyword_passesEscapedPatternToRepo() {
        whenRepoReturnsEmpty();
        service.list("SALES", FROM, TO, "ADTECH", 0, 10, null, null);
        verify(repo).findFiltered(eq(BIZ), eq("I"), eq(FROM), eq(TO), eq("%adtech%"), any(Pageable.class));
        verify(repo).aggregateByCurrency(eq(BIZ), eq("I"), eq(FROM), eq(TO), eq("%adtech%"));
    }

    // ------------------------------------------------------------------
    // IR-6 — totals from full filtered dataset
    // ------------------------------------------------------------------

    @Test
    void list_totals_comeFromAggregateQuery_notPageSlice() {
        // Page slice has 1 row (page 2 of 6), but the aggregate query
        // returns the full filtered dataset's totals — VND with 53
        // count and 100 million total. Phase 2 trusts the aggregator.
        BusaDocMstr r = sampleRow("D1", "VND", new BigDecimal("100"));
        when(repo.findFiltered(eq(BIZ), eq("I"), eq(FROM), eq(TO), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(r), PageRequest.of(2, 10), 53L));

        when(repo.aggregateByCurrency(eq(BIZ), eq("I"), eq(FROM), eq(TO), isNull()))
                .thenReturn(List.of(
                        new EInvoiceCcyAggregate("VND", 50L,
                                new BigDecimal("100000000"), new BigDecimal("10000000"), new BigDecimal("90000000")),
                        new EInvoiceCcyAggregate("USD",  3L,
                                new BigDecimal("5000"), new BigDecimal("500"), new BigDecimal("4500"))));

        EInvoiceListResponse res = service.list("SALES", FROM, TO, null, 2, 10, null, null);

        assertThat(res.totals()).hasSize(2);
        assertThat(res.totals()).extracting(CurrencyTotal::ccyCd).containsExactly("VND", "USD");

        CurrencyTotal vnd = res.totals().get(0);
        assertThat(vnd.count()).isEqualTo(50);
        assertThat(vnd.amount()).isEqualTo("100000000");
        assertThat(vnd.taxAmount()).isEqualTo("10000000");
        assertThat(vnd.pretaxAmount()).isEqualTo("90000000");

        CurrencyTotal usd = res.totals().get(1);
        assertThat(usd.count()).isEqualTo(3);
        assertThat(usd.amount()).isEqualTo("5000");
        assertThat(usd.taxAmount()).isEqualTo("500");
        assertThat(usd.pretaxAmount()).isEqualTo("4500");
    }

    // ------------------------------------------------------------------
    // Smoke — list() integration with mocked repo
    // ------------------------------------------------------------------

    @Test
    void list_sales_callsRepoWithI_andCurrentBizKey() {
        BusaDocMstr r = sampleRow("D1", "VND", new BigDecimal("100"));
        when(repo.findFiltered(eq(BIZ), eq("I"), eq(FROM), eq(TO), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(r), PageRequest.of(0, 10), 1L));
        when(repo.aggregateByCurrency(eq(BIZ), eq("I"), eq(FROM), eq(TO), isNull()))
                .thenReturn(List.of(new EInvoiceCcyAggregate("VND", 1L,
                        new BigDecimal("100"), BigDecimal.ZERO, new BigDecimal("100"))));

        EInvoiceListResponse res = service.list("SALES", FROM, TO, null, 0, 10, null, null);

        assertThat(res.totalCount()).isEqualTo(1L);
        assertThat(res.rows()).hasSize(1);
        assertThat(res.rows().get(0).docId()).isEqualTo("D1");
        assertThat(res.totals()).hasSize(1);
        verify(repo).findFiltered(eq(BIZ), eq("I"), eq(FROM), eq(TO), isNull(), any(Pageable.class));
    }

    @Test
    void list_expense_callsRepoWithB() {
        whenRepoReturnsEmpty();
        service.list("EXPENSE", FROM, TO, null, 0, 10, null, null);
        verify(repo).findFiltered(eq(BIZ), eq("B"), eq(FROM), eq(TO), isNull(), any(Pageable.class));
    }

    @Test
    void list_invalidType_skipsRepo() {
        assertBusinessCode("WBE2002", () -> service.list("E", FROM, TO, null, 0, 10, null, null));
        verify(repo, never()).findFiltered(any(), any(), any(), any(), any(), any(Pageable.class));
        verify(repo, never()).aggregateByCurrency(any(), any(), any(), any(), any());
    }

    @Test
    void list_invalidDateRange_skipsRepo() {
        assertBusinessCode("WBE_VALIDATION", () -> service.list("SALES", null, TO, null, 0, 10, null, null));
        verify(repo, never()).findFiltered(any(), any(), any(), any(), any(), any(Pageable.class));
    }

    // ------------------------------------------------------------------
    // IR-7 — INV_TYPE is not on the wire
    // ------------------------------------------------------------------

    @Test
    void wireRow_doesNotCarryInvType() {
        // EInvoiceRow components: 16 fields under IR-7 + MR B
        // additions (DOC_ID, DISP_DOC_ID, ISSU_DT, ISSU_TM,
        // EINV_INVOICESYMBOL, TRCO_CMNM, TRCO_TAX_ID,
        // TRCO_TRSC_CCY_CD, TOTL_AMT, SPLY_AMT_SUM, TAX_AMT_SUM,
        // EINV_INVOICESTATUS, EINV_INVOICESTATUS_BEF, PROV_STTS,
        // DUE_DT, OVER_DUE_DAYS) — and nothing else.
        assertThat(EInvoiceRow.class.getRecordComponents()).hasSize(16);
        assertThat(EInvoiceRow.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .doesNotContain("invType")
                .doesNotContain("INV_TYPE");
    }

    // ------------------------------------------------------------------
    // IR-4 — EINV_INVOICESTATUS passthrough on the wire
    // ------------------------------------------------------------------

    @Test
    void wireRow_carriesRawEinvInvoicestatus_notLocalised() {
        BusaDocMstr r = sampleRow("D1", "VND", new BigDecimal("100"));
        r.setEinvInvoicestatus("Hóa đơn thay thế"); // legacy stored Vietnamese form
        r.setEinvInvoicestatusBef("Hóa đơn mới");
        when(repo.findFiltered(eq(BIZ), eq("I"), eq(FROM), eq(TO), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(r), PageRequest.of(0, 10), 1L));
        when(repo.aggregateByCurrency(eq(BIZ), eq("I"), eq(FROM), eq(TO), isNull()))
                .thenReturn(List.of());

        EInvoiceListResponse res = service.list("SALES", FROM, TO, null, 0, 10, null, null);

        EInvoiceRow row = res.rows().get(0);
        assertThat(row.einvInvoicestatus()).isEqualTo("Hóa đơn thay thế");
        assertThat(row.einvInvoicestatusBef()).isEqualTo("Hóa đơn mới");
        // Phase 2 returns the raw stored value — frontend handles
        // regex-based display per IR-4.
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private void whenRepoReturnsEmpty() {
        when(repo.findFiltered(eq(BIZ), any(), eq(FROM), eq(TO), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0L));
        when(repo.aggregateByCurrency(eq(BIZ), any(), eq(FROM), eq(TO), any()))
                .thenReturn(List.of());
    }

    private static BusaDocMstr sampleRow(String docId, String ccy, BigDecimal totl) {
        BusaDocMstr m = new BusaDocMstr();
        m.setBizKey(BIZ);
        m.setDocId(docId);
        m.setDocType("I");
        m.setIssuDt("20240115");
        m.setRegDtm("20240115113950");
        m.setTrcoCmnm("Test Counterparty");
        m.setTrcoTaxId("1234567890");
        m.setTrcoTrscCcyCd(ccy);
        m.setTotlAmt(totl);
        m.setSplyAmtSum(totl);
        m.setTaxAmtSum(BigDecimal.ZERO);
        m.setEinvInvoicestatus("Hóa đơn mới");
        m.setEinvInvoicesymbol("1C24TLM-0001");
        m.setProvStts("OP");
        m.setHandRegYn("N");
        return m;
    }

    private void assertBusinessCode(String expectedCode, Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(expectedCode);
    }
}
