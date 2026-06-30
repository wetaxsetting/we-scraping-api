package com.kosign.wecms.domain.einvoice.export;

import com.kosign.wecms.common.export.ExcelExportRequest;
import com.kosign.wecms.common.export.ExcelSortSpec;
import com.kosign.wecms.common.export.ExportFetch;
import com.kosign.wecms.common.export.ExportRowProvider;
import com.kosign.wecms.domain.einvoice.dto.EInvoiceListResponse;
import com.kosign.wecms.domain.einvoice.dto.EInvoiceListResponse.CurrencyTotal;
import com.kosign.wecms.domain.einvoice.dto.EInvoiceRow;
import com.kosign.wecms.domain.einvoice.service.EInvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Common Excel export logic for Add-on Service &gt; Sales / Bill
 * e-Invoices "Download Excel with List". Reuses {@link EInvoiceService#list}
 * so the export and the on-screen list share one query path — same
 * baseline filters (IR-2), same date-range required check (IR-3), same
 * keyword search (IR-5), same sibling totals query (IR-6).
 *
 * <p><b>Legacy parity (2026-06-27)</b> — the column set now mirrors the
 * production {@code SALES E-INVOICE_*.xls} list export (9 columns):
 * <pre>
 *   Date | e-Invoice No. | Buyer's Name | Tax Code | Currency
 *        | Total Amount | Invoice Status | Tax Amount | Amount Before Tax
 * </pre>
 * Differences from the prior 8-column refactor shape, all isolated to this
 * provider + the frontend column registry (no shared {@code ExcelWorkbookWriter}
 * change):
 * <ul>
 *   <li>The composite {@code BUYER} cell is split into {@code BUYER_NAME}
 *       ({@code TRCO_CMNM}) + {@code TAX_CODE} ({@code TRCO_TAX_ID}) — two
 *       columns, matching the legacy file.</li>
 *   <li>{@code DATE_TIME} (composite) → {@code DATE} (date-only,
 *       pre-formatted {@code dd-MM-yyyy} text so the shared writer emits the
 *       production date format without a writer change).</li>
 *   <li>Amount columns are declared {@code format:"number"} on the wire so
 *       the writer's NUMBER style ({@code #,##0}, zero-decimal) is used,
 *       matching the production integer amount format (the AMOUNT style
 *       would render {@code #,##0.##}).</li>
 *   <li>The subheader uses the legacy two-half {@code {left, right}} shape —
 *       inquiry period on the left, per-CCY totals on the right — via
 *       {@link ExportFetch#of(List, String, String)} (the writer already
 *       supports the split; 9 cols ⇒ split at col 4 = D|E, matching the
 *       production {@code A3:D3 + E3:I3} layout).</li>
 * </ul>
 *
 * <p>Subclasses bind the concrete {@link #screenId()} + {@link #defaultType()}.
 */
@RequiredArgsConstructor
public abstract class EInvoiceExportProvider implements ExportRowProvider {

    /** FIELD_IDs allowed in the List Excel export — the 9-column legacy
     *  parity set. The screens are export-only (not seeded in
     *  {@code SYS_SCR_OPTION_WECMS}), so this allow-list ∩ the request
     *  columns is authoritative (no View Setting visibility intersection). */
    private static final Set<String> ALLOWED_FIELD_IDS = Set.of(
            "DATE",
            "INVOICE_NO",
            "BUYER_NAME",
            "TAX_CODE",
            "CURRENCY",
            "TOTAL",
            "STATUS",
            "TAX",
            "PRETAX"
    );

    private static final DateTimeFormatter SRC_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter OUT_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    protected final EInvoiceService eInvoiceService;

    /** Subclass binds the concrete screenId — must match a registered
     *  provider or the registry rejects the export with {@code WBE2010}. */
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
        String status  = stringOrNull(filters.get("status"));

        ExcelSortSpec sort = request.sort();
        String sortField     = sort != null ? sort.field()     : null;
        String sortDirection = sort != null ? sort.direction() : null;

        // Same query the list endpoint runs — IR-1 / IR-2 / IR-3 / IR-4
        // (status) / IR-5 / IR-6 all enforced inside the service, so the
        // export matches exactly what the on-screen filtered list shows.
        EInvoiceListResponse data = eInvoiceService.list(
                type, fromDt, toDt, keyword, status,
                Pageable.unpaged(),
                sortField, sortDirection);

        List<EInvoiceRow> rows = data.rows() != null ? data.rows() : List.of();
        List<Map<String, Object>> mapped = new ArrayList<>(rows.size());
        for (EInvoiceRow r : rows) {
            mapped.add(toFieldMap(r));
        }

        // Legacy {left_title, right_title} subheader: inquiry period (left)
        // + per-CCY totals (right). Mirrors SALES E-INVOICE_*.xls A3:D3 / E3:I3.
        // NOTE: the legacy .xls showed the preset label "(Last 30 Days)"; the
        // backend has only the resolved from/to dates, so we render the
        // explicit range "(dd-MM-yyyy ~ dd-MM-yyyy)" (same shape the Details
        // file and IDO R025 use). Documented divergence.
        String period = buildPeriod(fromDt, toDt);
        String totals = buildTotalsLine(data);
        return ExportFetch.of(mapped, period, totals);
    }

    /**
     * Translate an {@link EInvoiceRow} into the 9-column FIELD_ID map.
     * Buyer name + tax code are separate cells (legacy parity).
     * {@code INVOICE_NO} uses {@code DISP_DOC_ID} (the composed
     * {@code form+serial-invoiceNo} display id, e.g. {@code 1C26TWV-230})
     * to match the production "e-Invoice No." column value, falling back
     * to {@code einvInvoicesymbol} when the display id is blank. Numeric
     * columns ship raw wire decimals; the writer applies the NUMBER
     * ({@code #,##0}) cell format.
     */
    private static Map<String, Object> toFieldMap(EInvoiceRow r) {
        Map<String, Object> m = new HashMap<>();
        m.put("DATE",       formatDate(r.issuDt()));
        m.put("INVOICE_NO", firstNonBlank(r.dispDocId(), r.einvInvoicesymbol()));
        m.put("BUYER_NAME", nullToEmpty(r.trcoCmnm()));
        m.put("TAX_CODE",   nullToEmpty(r.trcoTaxId()));
        m.put("CURRENCY",   nullToEmpty(r.trcoTrscCcyCd()));
        m.put("TOTAL",      r.totlAmt());      // raw wire decimal — writer formats #,##0
        m.put("STATUS",     nullToEmpty(r.einvInvoicestatus()));
        m.put("TAX",        r.taxAmtSum());
        m.put("PRETAX",     r.splyAmtSum());
        return m;
    }

    /** {@code yyyyMMdd} (legacy {@code ISSU_DT}) → {@code dd-MM-yyyy} text.
     *  Passthrough on unparseable input. */
    private static String formatDate(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.length() < 8) return s;
        try {
            return LocalDate.parse(s.substring(0, 8), SRC_DATE).format(OUT_DATE);
        } catch (RuntimeException e) {
            return s;
        }
    }

    private static String buildPeriod(String fromDt, String toDt) {
        String f = formatDate(fromDt);
        String t = formatDate(toDt);
        if (f.isEmpty() && t.isEmpty()) return "";
        return "Inquiry Period (" + f + " ~ " + t + ")";
    }

    /**
     * Per-CCY totals line, mirroring the legacy right-half subheader
     * {@code "Total (VND) 33 transaction(s) / 91.979.830, Total (USD) …"}.
     * Amount is dot-grouped, zero-decimal, no currency symbol (matching the
     * production .xls list).
     */
    private static String buildTotalsLine(EInvoiceListResponse data) {
        List<CurrencyTotal> totals = data.totals();
        if (totals == null || totals.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < totals.size(); i++) {
            CurrencyTotal t = totals.get(i);
            if (i > 0) sb.append(", ");
            String ccy = (t.ccyCd() == null || t.ccyCd().isBlank()) ? "VND" : t.ccyCd().trim();
            sb.append("Total (").append(ccy).append(") ")
              .append(t.count()).append(" transaction(s) / ")
              .append(formatDotAmount(t.amount()));
        }
        return sb.toString();
    }

    /** Dot-thousands, zero-decimal integer formatting (legacy {@code #,##0}
     *  with the vi-VN/id-ID grouping the list amounts use). */
    private static String formatDotAmount(String wire) {
        BigDecimal v = parseDecimal(wire);
        if (v == null) return wire == null ? "0" : wire;
        DecimalFormatSymbols sym = new DecimalFormatSymbols(Locale.ROOT);
        sym.setGroupingSeparator('.');
        return new DecimalFormat("#,##0", sym).format(v);
    }

    private static BigDecimal parseDecimal(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return new BigDecimal(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
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

    private static String firstNonBlank(String... vals) {
        for (String v : vals) {
            if (v != null && !v.trim().isEmpty()) return v;
        }
        return "";
    }
}
