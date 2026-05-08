package com.kosign.wecms.domain.einvoice.export;

import com.kosign.wecms.common.export.ExcelExportRequest;
import com.kosign.wecms.common.export.ExcelSortSpec;
import com.kosign.wecms.common.export.ExportFetch;
import com.kosign.wecms.common.export.ExportRowProvider;
import com.kosign.wecms.domain.banking.service.AmountFormatter;
import com.kosign.wecms.domain.einvoice.dto.EInvoiceListResponse;
import com.kosign.wecms.domain.einvoice.dto.EInvoiceListResponse.CurrencyTotal;
import com.kosign.wecms.domain.einvoice.dto.EInvoiceRow;
import com.kosign.wecms.domain.einvoice.service.EInvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Common Excel export logic for Add-on Service &gt; Sales / Bill
 * e-Invoices. Reuses {@link EInvoiceService#list} so the export and the
 * on-screen list share one query path — same baseline filters (IR-2),
 * same date-range required check (IR-3), same keyword search (IR-5),
 * same sibling totals query (IR-6). No second source of truth.
 *
 * <p>Subclasses bind the concrete {@link #screenId()} +
 * {@link #defaultType()}; the rest is shared.
 *
 * <p>Filter handling — frontend ships {@code filters.from},
 * {@code filters.to}, {@code filters.keyword}, optional
 * {@code filters.type}. {@code from}/{@code to} are <b>required</b>
 * (rule IR-3) — empty / missing pair surfaces as {@code WBE_VALIDATION}
 * via {@link EInvoiceService#validateDateRange}. The {@code type}
 * default falls back to the subclass's {@link #defaultType()} so each
 * screen stays scoped even if the frontend forgets it.
 *
 * <p>Export is unpaged — the entire filtered result is materialised via
 * {@link Pageable#unpaged()} so the spreadsheet contains every matching
 * row regardless of which page the user is currently viewing (rule IR-6).
 *
 * <p>Subheader is computed server-side per CLAUDE.md decision 5 — one
 * line per CCY group, sourced from {@link EInvoiceListResponse#totals()}.
 * For mixed-currency results (typical Sales / Bill listing — VND + USD)
 * the subheader spans multiple lines.
 */
@RequiredArgsConstructor
public abstract class EInvoiceExportProvider implements ExportRowProvider {

    /** FIELD_IDs allowed in the Excel export — matches the Add-on
     *  Service e-Invoices View Setting seed (V45) minus the special
     *  {@code CHECKBOX} row (which the writer drops via
     *  {@code ScreenOptionService.SPECIAL_FIELD_IDS} regardless). */
    private static final Set<String> ALLOWED_FIELD_IDS = Set.of(
            "DATE_TIME",
            "INVOICE_NO",
            "BUYER",
            "CURRENCY",
            "TOTAL",
            "STATUS",
            "TAX",
            "PRETAX"
    );

    protected final EInvoiceService eInvoiceService;

    /** Subclass binds the concrete screenId — must match a row set in
     *  {@code SYS_SCR_OPTION_WECMS} or the registry rejects the export
     *  with {@code WBE2010}. */
    @Override
    public abstract String screenId();

    /** Default {@code filters.type} when the request omits the key.
     *  Sales subclass returns {@code "SALES"}; Bill subclass returns
     *  {@code "EXPENSE"}. {@link EInvoiceService#resolveDocType} maps
     *  these to legacy DB-level codes ({@code 'I'} / {@code 'B'}). */
    protected abstract String defaultType();

    @Override
    public Set<String> allowedFieldIds() {
        return ALLOWED_FIELD_IDS;
    }

    @Override
    public ExportFetch fetch(ExcelExportRequest request) {
        Map<String, Object> filters = request.filters() != null ? request.filters() : Map.of();
        String type    = stringOrDefault(filters.get("type"), defaultType());
        String fromDt  = stringOrNull(filters.get("from"));
        String toDt    = stringOrNull(filters.get("to"));
        String keyword = stringOrNull(filters.get("keyword"));

        ExcelSortSpec sort = request.sort();
        String sortField     = sort != null ? sort.field()     : null;
        String sortDirection = sort != null ? sort.direction() : null;

        // Run the same query the list endpoint runs — IR-1 / IR-2 / IR-3
        // / IR-5 / IR-6 are all enforced inside the service.
        EInvoiceListResponse data = eInvoiceService.list(
                type, fromDt, toDt, keyword,
                Pageable.unpaged(),
                sortField, sortDirection);

        List<EInvoiceRow> rows = data.rows() != null ? data.rows() : List.of();
        List<Map<String, Object>> mapped = new ArrayList<>(rows.size());
        for (EInvoiceRow r : rows) {
            mapped.add(toFieldMap(r));
        }

        String subHeader = composeSubHeader(mapped.size(), data);

        return ExportFetch.of(mapped, subHeader);
    }

    /**
     * Compose the subheader. One line per CCY group, sourced from
     * {@link EInvoiceListResponse#totals()} which is computed by the
     * sibling unpaged aggregation query (rule IR-6). Each line carries
     * count + total + tax + pretax, formatted via
     * {@link AmountFormatter#forDisplay} so the Excel header reads
     * identically to the on-screen footer rows and the print popup.
     *
     * <p>Subclasses may override; the default fits both Sales and Bill.
     *
     * <p>e.g. multi-currency output (Sales with VND + USD):
     * <pre>
     * 50 invoice(s)  ·  Total: 100.000.000₫  ·  Tax: 10.000.000₫  ·  Pre-tax: 90.000.000₫
     *  3 invoice(s)  ·  Total: $5,000.00     ·  Tax: $500.00      ·  Pre-tax: $4,500.00
     * </pre>
     *
     * <p>Empty result (zero rows in the filtered set) degrades to a
     * count-only line so the subheader is never blank.
     */
    protected String composeSubHeader(int rowCount, EInvoiceListResponse data) {
        List<CurrencyTotal> totals = data.totals();
        if (totals == null || totals.isEmpty()) {
            return String.format(Locale.ROOT, "%d invoice(s)", rowCount);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < totals.size(); i++) {
            if (i > 0) sb.append('\n');
            CurrencyTotal t = totals.get(i);
            String ccy = t.ccyCd() == null ? "" : t.ccyCd().trim();
            sb.append(String.format(Locale.ROOT,
                    "%d invoice(s)  ·  Total: %s  ·  Tax: %s  ·  Pre-tax: %s",
                    t.count(),
                    AmountFormatter.forDisplay(t.amount(),       ccy),
                    AmountFormatter.forDisplay(t.taxAmount(),    ccy),
                    AmountFormatter.forDisplay(t.pretaxAmount(), ccy)));
        }
        return sb.toString();
    }

    /**
     * Translate an {@link EInvoiceRow} into a {@code FIELD_ID}-keyed
     * map. {@code DATE_TIME} renders as a single composite cell
     * (date + space + time) so the spreadsheet user sees one line
     * per invoice rather than two. {@code BUYER} composes
     * {@code "TRCO_CMNM (MST: TRCO_TAX_ID)"} — the on-screen sub-line
     * format flattened to one cell. Numeric columns ship raw decimal
     * strings; the writer applies POI's {@code #,##0.##} cell-level
     * format via {@link com.kosign.wecms.common.export.ExcelColumnFormat#AMOUNT}.
     */
    private static Map<String, Object> toFieldMap(EInvoiceRow r) {
        Map<String, Object> m = new HashMap<>();
        m.put("DATE_TIME",  composeDateTimeCell(r));
        m.put("INVOICE_NO", nullToEmpty(r.einvInvoicesymbol()));
        m.put("BUYER",      composeBuyerCell(r));
        m.put("CURRENCY",   nullToEmpty(r.trcoTrscCcyCd()));
        m.put("TOTAL",      r.totlAmt());      // raw wire decimal — writer formats
        m.put("STATUS",     nullToEmpty(r.einvInvoicestatus()));
        m.put("TAX",        r.taxAmtSum());
        m.put("PRETAX",     r.splyAmtSum());
        return m;
    }

    private static String composeDateTimeCell(EInvoiceRow r) {
        String d = nullToEmpty(r.issuDt()).trim();
        String t = nullToEmpty(r.issuTm()).trim();
        if (t.isEmpty()) return d;
        if (d.isEmpty()) return t;
        return d + " " + t;
    }

    private static String composeBuyerCell(EInvoiceRow r) {
        String name = nullToEmpty(r.trcoCmnm()).trim();
        String mst  = nullToEmpty(r.trcoTaxId()).trim();
        if (mst.isEmpty()) return name;
        if (name.isEmpty()) return "MST: " + mst;
        return name + " (MST: " + mst + ")";
    }

    private static String stringOrNull(Object raw) {
        if (raw == null) return null;
        String s = raw.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static String stringOrDefault(Object raw, String defaultValue) {
        String s = stringOrNull(raw);
        return s != null ? s : defaultValue;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
