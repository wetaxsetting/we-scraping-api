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
 * Excel "Download with List" provider tests — 9-column legacy parity
 * ({@code SALES E-INVOICE_*.xls}). Covers the abstract
 * {@link EInvoiceExportProvider} via the concrete
 * {@link SalesEInvoiceExportProvider}:
 * <ul>
 *   <li>{@code allowedFieldIds} is the 9-column legacy-parity set
 *       (Date, e-Invoice No., Buyer's Name, Tax Code, Currency, Total,
 *       Status, Tax, Pre-tax) — buyer name + tax code split into two cells</li>
 *   <li>{@code fetch(...)} reuses {@link EInvoiceService#list} unpaged (IR-6)</li>
 *   <li>Filter / sort forwarding</li>
 *   <li>Subheader = inquiry-period (left) + per-CCY totals (right), the
 *       legacy {@code {left_title, right_title}} split</li>
 *   <li>Row map exposes the 9 FIELD_IDs; date is date-only {@code dd-MM-yyyy};
 *       amounts are raw wire decimals</li>
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
    // Static contract
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
        ExcelExportRequest req = requestWithFilters(Map.of());
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
    void allowedFieldIds_is9ColumnLegacyParitySet() {
        assertThat(provider.allowedFieldIds())
                .containsExactlyInAnyOrder(
                        "DATE", "INVOICE_NO", "BUYER_NAME", "TAX_CODE", "CURRENCY",
                        "TOTAL", "STATUS", "TAX", "PRETAX")
                .doesNotContain("CHECKBOX", "ETC", "DATE_TIME", "BUYER");
    }

    // ------------------------------------------------------------------
    // fetch() — argument forwarding
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
                eq("SALES"), eq("20240101"), eq("20240131"), eq("ADTECH"),
                pageableCaptor.capture(), isNull(), isNull());
        assertThat(pageableCaptor.getValue().isUnpaged()).isTrue();
    }

    @Test
    void fetch_typeFilterOverridesDefault() {
        ExcelExportRequest req = requestWithFilters(Map.of(
                "from", "20240101", "to", "20240131", "type", "BILL"));
        when(eInvoiceService.list(eq("BILL"), any(), any(), any(),
                any(Pageable.class), any(), any()))
                .thenReturn(emptyResponse());

        provider.fetch(req);

        verify(eInvoiceService).list(eq("BILL"), any(), any(), any(),
                any(Pageable.class), any(), any());
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
    // Subheader — legacy {left=period, right=totals} split
    // ------------------------------------------------------------------

    @Test
    void subheader_period_isLeftHalf() {
        ExcelExportRequest req = requestWithFilters(Map.of("from", "20240101", "to", "20240131"));
        when(eInvoiceService.list(anyString(), anyString(), anyString(), any(),
                any(Pageable.class), any(), any()))
                .thenReturn(emptyResponse());

        ExportFetch fetch = provider.fetch(req);

        assertThat(fetch.subHeader()).isEqualTo("Inquiry Period (01-01-2024 ~ 31-01-2024)");
        // No totals → right half empty.
        assertThat(fetch.subHeaderRight()).isEmpty();
    }

    @Test
    void subheader_totals_isRightHalf_singleCcy() {
        ExcelExportRequest req = requestWithFilters(Map.of("from", "20240101", "to", "20240131"));
        when(eInvoiceService.list(anyString(), anyString(), anyString(), any(),
                any(Pageable.class), any(), any()))
                .thenReturn(responseWith(
                        List.of(sampleRow("D1")),
                        List.of(new CurrencyTotal("VND", 1, "1000000", "100000", "900000"))));

        ExportFetch fetch = provider.fetch(req);

        // Dot-grouped, zero-decimal, no currency symbol (legacy .xls style).
        assertThat(fetch.subHeaderRight())
                .isEqualTo("Total (VND) 1 transaction(s) / 1.000.000");
    }

    @Test
    void subheader_totals_multiCcy_commaSeparated() {
        ExcelExportRequest req = requestWithFilters(Map.of("from", "20240101", "to", "20240131"));
        when(eInvoiceService.list(anyString(), anyString(), anyString(), any(),
                any(Pageable.class), any(), any()))
                .thenReturn(responseWith(
                        List.of(sampleRow("D1"), sampleRow("D2")),
                        List.of(
                                new CurrencyTotal("VND", 50, "100000000", "10000000", "90000000"),
                                new CurrencyTotal("USD",  3,      "5000",     "500",     "4500"))));

        ExportFetch fetch = provider.fetch(req);

        assertThat(fetch.subHeaderRight())
                .isEqualTo("Total (VND) 50 transaction(s) / 100.000.000, "
                        + "Total (USD) 3 transaction(s) / 5.000");
    }

    // ------------------------------------------------------------------
    // Row map — 9 FIELD_ID cells
    // ------------------------------------------------------------------

    @Test
    void rowMap_carriesAll9FieldIds() {
        ExcelExportRequest req = requestWithFilters(Map.of("from", "20240101", "to", "20240131"));
        when(eInvoiceService.list(anyString(), anyString(), anyString(), any(),
                any(Pageable.class), any(), any()))
                .thenReturn(responseWith(List.of(sampleRow("DOC123")), List.of()));

        Map<String, Object> cells = provider.fetch(req).rows().get(0);
        assertThat(cells).containsOnlyKeys(
                "DATE", "INVOICE_NO", "BUYER_NAME", "TAX_CODE", "CURRENCY",
                "TOTAL", "STATUS", "TAX", "PRETAX");
    }

    @Test
    void rowMap_dateCell_isDateOnly_ddMMyyyy() {
        ExcelExportRequest req = requestWithFilters(Map.of("from", "20240101", "to", "20240131"));
        when(eInvoiceService.list(anyString(), anyString(), anyString(), any(),
                any(Pageable.class), any(), any()))
                .thenReturn(responseWith(List.of(sampleRow("DOC123")), List.of()));

        Map<String, Object> cells = provider.fetch(req).rows().get(0);
        assertThat(cells.get("DATE")).isEqualTo("15-01-2024");
    }

    @Test
    void rowMap_buyerNameAndTaxCode_areSeparateCells() {
        ExcelExportRequest req = requestWithFilters(Map.of("from", "20240101", "to", "20240131"));
        when(eInvoiceService.list(anyString(), anyString(), anyString(), any(),
                any(Pageable.class), any(), any()))
                .thenReturn(responseWith(List.of(sampleRow("DOC123")), List.of()));

        Map<String, Object> cells = provider.fetch(req).rows().get(0);
        assertThat(cells.get("BUYER_NAME")).isEqualTo("CÔNG TY TNHH ADTECH");
        assertThat(cells.get("TAX_CODE")).isEqualTo("3702351124-003");
    }

    @Test
    void rowMap_invoiceNo_usesDispDocId() {
        ExcelExportRequest req = requestWithFilters(Map.of("from", "20240101", "to", "20240131"));
        when(eInvoiceService.list(anyString(), anyString(), anyString(), any(),
                any(Pageable.class), any(), any()))
                .thenReturn(responseWith(List.of(sampleRow("DOC123")), List.of()));

        Map<String, Object> cells = provider.fetch(req).rows().get(0);
        assertThat(cells.get("INVOICE_NO")).isEqualTo("DISP-DOC123");
    }

    @Test
    void rowMap_amountCells_areRawWireDecimals() {
        ExcelExportRequest req = requestWithFilters(Map.of("from", "20240101", "to", "20240131"));
        when(eInvoiceService.list(anyString(), anyString(), anyString(), any(),
                any(Pageable.class), any(), any()))
                .thenReturn(responseWith(List.of(sampleRow("DOC123")), List.of()));

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
