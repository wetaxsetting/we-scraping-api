package com.kosign.wecms.domain.einvoice.export;

import com.kosign.wecms.common.export.ExcelColumnSpec;
import com.kosign.wecms.common.export.ExcelExportRequest;
import com.kosign.wecms.common.export.ExcelSortSpec;
import com.kosign.wecms.common.export.ExportFetch;
import com.kosign.wecms.domain.einvoice.dto.EInvoiceListResponse;
import com.kosign.wecms.domain.einvoice.dto.EInvoiceListResponse.CurrencyTotal;
import com.kosign.wecms.domain.einvoice.dto.EInvoiceRow;
import com.kosign.wecms.domain.einvoice.service.EInvoiceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Backend MR C — Excel export provider tests. Covers the abstract
 * {@link EInvoiceExportProvider} contract via the concrete
 * {@link SalesEInvoiceExportProvider} subclass:
 * <ul>
 *   <li>{@code allowedFieldIds} is the 8-column subset (no special CHECKBOX/ETC)</li>
 *   <li>{@code fetch(...)} reuses {@link EInvoiceService#list} with
 *       {@link Pageable#unpaged()} so the export runs against the
 *       full filtered dataset (rule IR-6)</li>
 *   <li>Filter extraction defaults {@code type} when the request omits it</li>
 *   <li>Sort flows through from {@link ExcelSortSpec}</li>
 *   <li>Subheader composition — single CCY / multi CCY / empty</li>
 *   <li>Row map exposes the 8 wire FIELD_IDs plus the BUYER + DATE_TIME
 *       composite cells</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class EInvoiceExportProviderTest {

    @Mock EInvoiceService eInvoiceService;

    private SalesEInvoiceExportProvider provider;

    @BeforeEach
    void setup() {
        provider = new SalesEInvoiceExportProvider(eInvoiceService);
    }

    // ------------------------------------------------------------------
    // Static contract — screenId, allowedFieldIds
    // ------------------------------------------------------------------

    @Test
    void salesProvider_screenId_isAddonSalesEinvoices() {
        assertThat(provider.screenId()).isEqualTo("addon-sales-einvoices");
    }

    @Test
    void billProvider_screenId_isAddonExpensesEinvoices() {
        BillEInvoiceExportProvider bill = new BillEInvoiceExportProvider(eInvoiceService);
        assertThat(bill.screenId()).isEqualTo("addon-expenses-einvoices");
    }

    @Test
    void salesProvider_defaultType_isSALES() {
        // Indirectly verified via the fetch defaults test below; this
        // pins the protected method's contract for the subclass binding.
        ExcelExportRequest req = requestWithFilters(Map.of()); // no type override
        when(eInvoiceService.list(eq("SALES"), any(), any(), any(),
                any(Pageable.class), any(), any()))
                .thenReturn(emptyResponse());

        provider.fetch(req);

        verify(eInvoiceService).list(eq("SALES"), any(), any(), any(),
                any(Pageable.class), any(), any());
    }

    @Test
    void billProvider_defaultType_isEXPENSE() {
        BillEInvoiceExportProvider bill = new BillEInvoiceExportProvider(eInvoiceService);
        ExcelExportRequest req = requestWithFilters(Map.of());
        when(eInvoiceService.list(eq("EXPENSE"), any(), any(), any(),
                any(Pageable.class), any(), any()))
                .thenReturn(emptyResponse());

        bill.fetch(req);

        verify(eInvoiceService).list(eq("EXPENSE"), any(), any(), any(),
                any(Pageable.class), any(), any());
    }

    @Test
    void allowedFieldIds_is8ColumnSubset() {
        assertThat(provider.allowedFieldIds())
                .containsExactlyInAnyOrder(
                        "DATE_TIME", "INVOICE_NO", "BUYER", "CURRENCY",
                        "TOTAL", "STATUS", "TAX", "PRETAX")
                .doesNotContain("CHECKBOX", "ETC");
    }

    // ------------------------------------------------------------------
    // fetch() — argument forwarding to EInvoiceService
    // ------------------------------------------------------------------

    @Test
    void fetch_unpagedPageable_isPassedToList() {
        ExcelExportRequest req = requestWithFilters(Map.of(
                "from", "20240101",
                "to",   "20240131",
                "keyword", "ADTECH"));
        when(eInvoiceService.list(anyString(), anyString(), anyString(), anyString(),
                any(Pageable.class), any(), any()))
                .thenReturn(emptyResponse());

        provider.fetch(req);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(eInvoiceService).list(
                eq("SALES"),
                eq("20240101"),
                eq("20240131"),
                eq("ADTECH"),
                pageableCaptor.capture(),
                isNull(),
                isNull());
        // Export must be unpaged so the spreadsheet contains every
        // matching row regardless of which page the user is viewing.
        assertThat(pageableCaptor.getValue().isUnpaged()).isTrue();
    }

    @Test
    void fetch_typeFilterOverridesDefault() {
        // Sales provider with explicit `type=BILL` in filters — the
        // override wins. Defends against a screenId/type mismatch from
        // a misbehaving frontend (we don't auto-correct; the wire
        // value flows through to the service which will resolve it).
        ExcelExportRequest req = requestWithFilters(Map.of(
                "from", "20240101",
                "to",   "20240131",
                "type", "BILL"));
        when(eInvoiceService.list(eq("BILL"), any(), any(), any(),
                any(Pageable.class), any(), any()))
                .thenReturn(emptyResponse());

        provider.fetch(req);

        verify(eInvoiceService).list(eq("BILL"), any(), any(), any(),
                any(Pageable.class), any(), any());
    }

    @Test
    void fetch_blankFilters_passNullToService() {
        ExcelExportRequest req = requestWithFilters(Map.of(
                "from", "  ",        // blank → null
                "to",   ""));        // empty → null
        when(eInvoiceService.list(anyString(), isNull(), isNull(), isNull(),
                any(Pageable.class), any(), any()))
                .thenReturn(emptyResponse());

        provider.fetch(req);

        verify(eInvoiceService).list(eq("SALES"), isNull(), isNull(), isNull(),
                any(Pageable.class), any(), any());
        // Service then enforces IR-3 (validateDateRange) and rejects
        // with WBE_VALIDATION — the export thereby inherits the same
        // required-date-range contract as the list endpoint.
    }

    @Test
    void fetch_sortSpec_flowsToService() {
        ExcelExportRequest req = requestWithFiltersAndSort(
                Map.of("from", "20240101", "to", "20240131"),
                new ExcelSortSpec("BUYER", "ASC"));
        when(eInvoiceService.list(anyString(), anyString(), anyString(), any(),
                any(Pageable.class), eq("BUYER"), eq("ASC")))
                .thenReturn(emptyResponse());

        provider.fetch(req);

        verify(eInvoiceService).list(eq("SALES"), eq("20240101"), eq("20240131"),
                isNull(), any(Pageable.class), eq("BUYER"), eq("ASC"));
    }

    // ------------------------------------------------------------------
    // Subheader composition — IR-6 totals from full filtered dataset
    // ------------------------------------------------------------------

    @Test
    void subheader_emptyResult_isCountOnly() {
        ExcelExportRequest req = requestWithFilters(Map.of("from", "20240101", "to", "20240131"));
        when(eInvoiceService.list(anyString(), anyString(), anyString(), any(),
                any(Pageable.class), any(), any()))
                .thenReturn(emptyResponse());

        ExportFetch fetch = provider.fetch(req);

        assertThat(fetch.rows()).isEmpty();
        assertThat(fetch.subHeader()).isEqualTo("0 invoice(s)");
    }

    @Test
    void subheader_singleCcy_isSingleLine() {
        ExcelExportRequest req = requestWithFilters(Map.of("from", "20240101", "to", "20240131"));
        when(eInvoiceService.list(anyString(), anyString(), anyString(), any(),
                any(Pageable.class), any(), any()))
                .thenReturn(responseWith(
                        List.of(sampleRow("D1")),
                        List.of(new CurrencyTotal("VND", 1, "1000000", "100000", "900000"))));

        ExportFetch fetch = provider.fetch(req);

        assertThat(fetch.rows()).hasSize(1);
        // VND uses vi-VN locale + ₫ suffix per AmountFormatter rules.
        assertThat(fetch.subHeader())
                .doesNotContain("\n")
                .contains("1 invoice(s)")
                .contains("Total: 1.000.000₫")
                .contains("Tax: 100.000₫")
                .contains("Pre-tax: 900.000₫");
    }

    @Test
    void subheader_multiCcy_oneLinePerGroup() {
        ExcelExportRequest req = requestWithFilters(Map.of("from", "20240101", "to", "20240131"));
        when(eInvoiceService.list(anyString(), anyString(), anyString(), any(),
                any(Pageable.class), any(), any()))
                .thenReturn(responseWith(
                        List.of(sampleRow("D1"), sampleRow("D2")),
                        List.of(
                                new CurrencyTotal("VND", 50, "100000000", "10000000", "90000000"),
                                new CurrencyTotal("USD",  3,      "5000",     "500",     "4500"))));

        ExportFetch fetch = provider.fetch(req);

        String[] lines = fetch.subHeader().split("\n");
        assertThat(lines).hasSize(2);
        // Line 1 — VND (vi-VN, ₫ suffix, 0 fraction digits)
        assertThat(lines[0])
                .contains("50 invoice(s)")
                .contains("Total: 100.000.000₫")
                .contains("Tax: 10.000.000₫")
                .contains("Pre-tax: 90.000.000₫");
        // Line 2 — USD (en-US, $ prefix, 2 fraction digits)
        assertThat(lines[1])
                .contains("3 invoice(s)")
                .contains("Total: $5,000.00")
                .contains("Tax: $500.00")
                .contains("Pre-tax: $4,500.00");
    }

    // ------------------------------------------------------------------
    // Row map — FIELD_ID-keyed cells
    // ------------------------------------------------------------------

    @Test
    void rowMap_carriesAll8FieldIds() {
        ExcelExportRequest req = requestWithFilters(Map.of("from", "20240101", "to", "20240131"));
        EInvoiceRow row = sampleRow("DOC123");
        when(eInvoiceService.list(anyString(), anyString(), anyString(), any(),
                any(Pageable.class), any(), any()))
                .thenReturn(responseWith(List.of(row), List.of()));

        ExportFetch fetch = provider.fetch(req);

        assertThat(fetch.rows()).hasSize(1);
        Map<String, Object> cells = fetch.rows().get(0);
        assertThat(cells).containsOnlyKeys(
                "DATE_TIME", "INVOICE_NO", "BUYER", "CURRENCY",
                "TOTAL", "STATUS", "TAX", "PRETAX");
    }

    @Test
    void rowMap_dateTimeCell_isSpaceJoinedDateAndTime() {
        ExcelExportRequest req = requestWithFilters(Map.of("from", "20240101", "to", "20240131"));
        EInvoiceRow row = sampleRow("DOC123");
        when(eInvoiceService.list(anyString(), anyString(), anyString(), any(),
                any(Pageable.class), any(), any()))
                .thenReturn(responseWith(List.of(row), List.of()));

        Map<String, Object> cells = provider.fetch(req).rows().get(0);
        assertThat(cells.get("DATE_TIME")).isEqualTo("20240115 113950");
    }

    @Test
    void rowMap_buyerCell_isNameWithMstSuffix() {
        ExcelExportRequest req = requestWithFilters(Map.of("from", "20240101", "to", "20240131"));
        EInvoiceRow row = sampleRow("DOC123");
        when(eInvoiceService.list(anyString(), anyString(), anyString(), any(),
                any(Pageable.class), any(), any()))
                .thenReturn(responseWith(List.of(row), List.of()));

        Map<String, Object> cells = provider.fetch(req).rows().get(0);
        assertThat(cells.get("BUYER"))
                .isEqualTo("CÔNG TY TNHH ADTECH (MST: 3702351124-003)");
    }

    @Test
    void rowMap_amountCells_areRawWireDecimals() {
        // Writer applies POI's #,##0.## cell-level format on AMOUNT
        // columns; provider ships raw wire strings so the user can
        // re-sum the column in Excel.
        ExcelExportRequest req = requestWithFilters(Map.of("from", "20240101", "to", "20240131"));
        EInvoiceRow row = sampleRow("DOC123");
        when(eInvoiceService.list(anyString(), anyString(), anyString(), any(),
                any(Pageable.class), any(), any()))
                .thenReturn(responseWith(List.of(row), List.of()));

        Map<String, Object> cells = provider.fetch(req).rows().get(0);
        assertThat(cells.get("TOTAL")).isEqualTo("1000000");
        assertThat(cells.get("TAX")).isEqualTo("100000");
        assertThat(cells.get("PRETAX")).isEqualTo("900000");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static EInvoiceListResponse emptyResponse() {
        return responseWith(List.of(), List.of());
    }

    private static EInvoiceListResponse responseWith(
            List<EInvoiceRow> rows, List<CurrencyTotal> totals) {
        return new EInvoiceListResponse(rows, rows.size(), 1, 0, rows.size(), totals);
    }

    private static EInvoiceRow sampleRow(String docId) {
        return new EInvoiceRow(
                docId,
                "DISP-" + docId,
                "20240115",
                "113950",
                "1C24TLM-0405",
                "CÔNG TY TNHH ADTECH",
                "3702351124-003",
                "VND",
                "1000000",
                "900000",
                "100000",
                "Hóa đơn mới",
                null,
                "OP",
                null,
                null);
    }

    private static ExcelExportRequest requestWithFilters(Map<String, Object> filters) {
        return requestWithFiltersAndSort(filters, null);
    }

    private static ExcelExportRequest requestWithFiltersAndSort(
            Map<String, Object> filters, ExcelSortSpec sort) {
        return new ExcelExportRequest(
                "addon-sales-einvoices",
                "default",
                "Sales e-Invoices",
                "Sales e-Invoices",
                filters,
                sort,
                List.<ExcelColumnSpec>of(),
                null);
    }
}
