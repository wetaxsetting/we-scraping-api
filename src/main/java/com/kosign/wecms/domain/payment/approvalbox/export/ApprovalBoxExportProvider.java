package com.kosign.wecms.domain.payment.approvalbox.export;

import com.kosign.wecms.common.export.ExcelExportRequest;
import com.kosign.wecms.common.export.ExcelSortSpec;
import com.kosign.wecms.common.export.ExportFetch;
import com.kosign.wecms.common.export.ExportRowProvider;
import com.kosign.wecms.domain.payment.approvalbox.dto.ApprovalBoxListResponse;
import com.kosign.wecms.domain.payment.approvalbox.dto.ApprovalBoxRow;
import com.kosign.wecms.domain.payment.approvalbox.service.ApprovalBoxService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Payment &gt; Approval Box — Excel export provider.
 *
 * <p>Reuses {@link ApprovalBoxService#list} so the export and the
 * on-screen list draw from one query path — same filter, same sort,
 * same tenant scope (resolved in the service via {@code TenantContext}).
 * The export materialises every filtered row up to the shared
 * {@code MAX_ROWS} cap and ships {@code FIELD_ID}-keyed
 * {@code Map<String,Object>}s to the SXSSF writer.
 *
 * <p>Subheader is backend-computed per CLAUDE.md "Excel export" rule —
 * {@code <count> document(s)  ·  VND: <total>}. Amount goes through
 * {@link AmountFormatter#forDisplay} so the on-screen footer card,
 * the print popup, and the Excel subheader all read identically.
 *
 * <p>Per CLAUDE.md decision 2: the export pulls every filtered row, not
 * just the current page. We pass {@code page=0, size=MAX_ROWS} to the
 * underlying paginated service (the wire contract already clamps to
 * {@code MAX_PAGE_SIZE} server-side; a future MR may switch to an
 * unpaged variant if {@code MAX_ROWS} grows beyond the per-page cap).
 */
@Component
@RequiredArgsConstructor
public class ApprovalBoxExportProvider implements ExportRowProvider {

    /** FIELD_IDs allowed in the Excel export — matches the legacy
     *  {@code resl_0001_001_osmc.makeGrid_excel} column set (10 columns
     *  in legacy order). The Approver column is intentionally excluded
     *  from the workbook (legacy {@code FIELD_ID === "APPRER_NM_2"}
     *  branch returns early at line 1186-1188 in
     *  {@code resl_0001_001_osmc.ts}). The on-screen table still shows
     *  Approver; the Excel export deliberately drops it because the
     *  multi-line "name + count + chevron" cell does not flatten to a
     *  meaningful spreadsheet column. */
    private static final Set<String> ALLOWED_FIELD_IDS = Set.of(
            "REG_DTM",
            "DOC_NM",
            "DOC_NO",
            "TITL",
            "FILE_NM",
            "CURRENCY",
            "TOT_PAY_AMT",
            "PAY_END_YN",
            "PAY_DATE",
            "DRAFT_USER_NM"
    );

    /** Mirrors the frontend's `TAB_TO_APPR_TYPE_CD` map in
     *  `useApprovalBoxList.ts`. The export accepts tab keys
     *  (`all`/`pending`/`in_progress`/`approved`/`rejected`) because
     *  that's what the page state carries, but the service speaks
     *  `apprTypeCd` codes ({@code 00}/{@code 01}/{@code 02}/{@code 03}).
     *  Anything unrecognised → {@code ""} (no filter). */
    private static final Map<String, String> TAB_TO_APPR_TYPE_CD = Map.of(
            "all",         "",
            "pending",     "00",
            "approved",    "01",
            "rejected",    "02",
            "in_progress", "03"
    );

    /** Hard ceiling on the legacy {@code SortHelper}-driven service —
     *  matches {@code ApprovalBoxService.MAX_PAGE_SIZE} so a single
     *  page call covers the entire export. Bumping the global
     *  {@code MAX_ROWS} cap in {@code ExcelExportService} above this
     *  value requires switching to a paged loop here. */
    private static final int EXPORT_PAGE_SIZE = 1000;

    private final ApprovalBoxService approvalBoxService;

    @Override
    public String screenId() {
        // Must match the View Setting `WEB_SVC_ID` seeded in V109 / V207
        // and the frontend's `SCREEN_IDS.PAYMENT_APPROVAL_BOX` constant
        // (both are `"approval-box"` — no `payment-` prefix).
        return "approval-box";
    }

    @Override
    public Set<String> allowedFieldIds() {
        return ALLOWED_FIELD_IDS;
    }

    @Override
    public ExportFetch fetch(ExcelExportRequest request) {
        Map<String, Object> filters = request.filters() != null ? request.filters() : Map.of();

        String fromDate  = stringOrNull(filters.get("fromDate"));
        String toDate    = stringOrNull(filters.get("toDate"));
        String tab       = stringOrNull(filters.get("apprType"));
        String apprType  = TAB_TO_APPR_TYPE_CD.getOrDefault(tab == null ? "all" : tab, "");
        String keyword   = stringOrNull(filters.get("keyword"));

        ExcelSortSpec sort = request.sort();
        String sortField     = sort != null ? sort.field()     : null;
        String sortDirection = sort != null ? sort.direction() : null;

        // Reuse the same paginated list path the on-screen list uses
        // — single source of truth for the filter / sort chain. The
        // upper EXPORT_PAGE_SIZE matches the service's internal cap.
        ApprovalBoxListResponse data = approvalBoxService.list(
                fromDate, toDate, keyword, apprType,
                sortField, sortDirection,
                0, EXPORT_PAGE_SIZE);

        List<ApprovalBoxRow> rows = data.list() != null ? data.list() : List.of();
        List<Map<String, Object>> mapped = new ArrayList<>(rows.size());
        // Per-currency totals — legacy `resl_0001_001` subheader shows
        // "Total USD : … / Total VND : … / Total KRW : …" with separate
        // sums per currency code. Use a LinkedHashMap so the iteration
        // order is stable (USD, VND, KRW — same as the on-screen footer).
        java.util.LinkedHashMap<String, java.math.BigDecimal> totalsByCcy =
                new java.util.LinkedHashMap<>();
        totalsByCcy.put("USD", java.math.BigDecimal.ZERO);
        totalsByCcy.put("VND", java.math.BigDecimal.ZERO);
        totalsByCcy.put("KRW", java.math.BigDecimal.ZERO);
        for (ApprovalBoxRow r : rows) {
            mapped.add(toFieldMap(r));
            java.math.BigDecimal amt = parseAmount(r.totPayAmt());
            String ccy = r.currency() != null ? r.currency().toUpperCase(Locale.ROOT) : "";
            if (amt != null && totalsByCcy.containsKey(ccy)) {
                totalsByCcy.put(ccy, totalsByCcy.get(ccy).add(amt));
            }
        }

        String left  = composeSubHeaderLeft(fromDate, toDate);
        String right = composeSubHeaderRight(mapped.size(), totalsByCcy);
        return ExportFetch.of(mapped, left, right);
    }

    /** Mirrors the legacy {@code resl_0001_001_osmc.download_excel}
     *  {@code left_title} composition (lines 1170):
     *  {@code Date: 28-Nov-2025 ~ 27-May-2026}. Empty date components
     *  degrade to an empty string so a missing filter doesn't render
     *  literal {@code Date: null ~ null}. */
    private String composeSubHeaderLeft(String fromDate, String toDate) {
        String fromDisplay = formatDateLegacy(fromDate);
        String toDisplay   = formatDateLegacy(toDate);
        if (fromDisplay.isEmpty() && toDisplay.isEmpty()) return "";
        return "Date: " + fromDisplay + " ~ " + toDisplay;
    }

    /** Mirrors the legacy {@code resl_0001_001_osmc.download_excel}
     *  {@code right_title} composition (lines 1163-1173). Format:
     *  <pre>
     *  370 document(s) : Total USD : 202.845,00 / Total VND : 14.267.246.047
     *  </pre>
     *  Currency totals use vi-VN locale (dots for thousands, comma for
     *  decimals) so the output matches the legacy excel screenshot. */
    private String composeSubHeaderRight(
            int rowCount,
            java.util.LinkedHashMap<String, java.math.BigDecimal> totalsByCcy
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append(rowCount).append(" document(s)");
        for (Map.Entry<String, java.math.BigDecimal> e : totalsByCcy.entrySet()) {
            sb.append(" : Total ").append(e.getKey()).append(" : ")
              .append(formatTotalLegacy(e.getValue(), e.getKey()));
            // Legacy uses " / " between currency totals; we put it after
            // every entry except the last via a marker we trim below.
            sb.append(" /");
        }
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '/') {
            sb.setLength(sb.length() - 2); // trim " /"
        }
        return sb.toString();
    }

    /** Format a currency total with vi-VN locale (dots = thousands,
     *  comma = decimals). Matches the legacy excel "202.845,00" for USD
     *  and "14.267.246.047" for VND. */
    private static String formatTotalLegacy(java.math.BigDecimal value, String currency) {
        Locale viVn = Locale.forLanguageTag("vi-VN");
        java.text.NumberFormat nf = java.text.NumberFormat.getNumberInstance(viVn);
        if ("USD".equalsIgnoreCase(currency)) {
            nf.setMinimumFractionDigits(2);
            nf.setMaximumFractionDigits(2);
        } else {
            nf.setMinimumFractionDigits(0);
            nf.setMaximumFractionDigits(0);
        }
        return nf.format(value != null ? value : java.math.BigDecimal.ZERO);
    }

    /** Convert {@code yyyyMMdd} or {@code yyyyMMddHHmmss} → {@code DD-Mon-YYYY}
     *  ({@code 26-Dec-2025}). Matches the legacy excel date format
     *  ({@code resl_0001_001_osmc.formatExcel} pipes raw values through
     *  {@code common.app.formatDateLang} which produces this shape under
     *  the English locale). */
    private static String formatDateLegacy(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.length() < 8) return "";
        try {
            int year  = Integer.parseInt(s.substring(0, 4));
            int month = Integer.parseInt(s.substring(4, 6));
            int day   = Integer.parseInt(s.substring(6, 8));
            if (month < 1 || month > 12) return s;
            String[] months = {
                    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
                    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
            };
            return String.format("%02d-%s-%04d", day, months[month - 1], year);
        } catch (NumberFormatException e) {
            return s;
        }
    }

    /** Project one row onto a FIELD_ID-keyed map. Mirrors the legacy
     *  {@code resl_0001_001_osmc.formatExcel} cell formatting:
     *  <ul>
     *    <li>Dates → {@code DD-Mon-YYYY} (e.g. {@code 26-Dec-2025}).</li>
     *    <li>Type → human-readable label (matches the on-screen Type column).</li>
     *    <li>Approval Status → status label only ({@code Pending} /
     *        {@code Approved} / {@code Rejected} / {@code In Progress})
     *        — no {@code 1/3} count suffix in the workbook.</li>
     *    <li>Amount → raw decimal; POI writer applies {@code #,##0.##}
     *        from the column's {@code format = "amount"} declaration.</li>
     *  </ul>
     */
    private static Map<String, Object> toFieldMap(ApprovalBoxRow r) {
        Map<String, Object> m = new HashMap<>();
        java.math.BigDecimal amt = parseAmount(r.totPayAmt());
        m.put("REG_DTM",       formatDateLegacy(extractDatePart(r.regDtm())));
        m.put("DOC_NM",        nullToEmpty(r.typeLabel()));
        m.put("DOC_NO",        nullToEmpty(r.docNo()));
        m.put("TITL",          nullToEmpty(r.title()));
        // FILE_NM is a frontend-only column today — the sibling-table
        // join that populates the uploaded file name on the wire is
        // deferred (see design-backend.md §13 D1). Emit an empty cell
        // so the column renders blank in the workbook instead of
        // disappearing from the allow-list intersection.
        m.put("FILE_NM",       "");
        m.put("CURRENCY",      nullToEmpty(r.currency()));
        m.put("TOT_PAY_AMT",   amt != null ? amt : java.math.BigDecimal.ZERO);
        m.put("PAY_END_YN",    apprSttsLabel(r.apprStts()));
        m.put("PAY_DATE",      formatDateLegacy(r.payDate()));
        m.put("DRAFT_USER_NM", nullToEmpty(r.draftUserNm()));
        return m;
    }

    /** {@code yyyyMMddHHmmss} → {@code yyyyMMdd} so the legacy date
     *  formatter can render the registration timestamp as a plain
     *  date. Returns the input unchanged when it's already 8 chars or
     *  empty / null. */
    private static String extractDatePart(String raw) {
        if (raw == null) return null;
        return raw.length() >= 14 ? raw.substring(0, 8) : raw;
    }

    /** Map the derived {@code APPR_STTS} code to the same English label
     *  the legacy {@code resl_0001_001_osmc.formatExcel} emits (lines
     *  1207-1230). Code only — no {@code N/N} count suffix in Excel
     *  (the legacy excel cell drops the count too). */
    private static String apprSttsLabel(String code) {
        if (code == null) return "";
        return switch (code) {
            case "P" -> "Pending";
            case "C" -> "Approved";
            case "R" -> "Rejected";
            case "I" -> "In Progress";
            default  -> code;
        };
    }

    /** Parse the wire-string amount (now a {@code String} on
     *  {@link ApprovalBoxRow}) into a {@link java.math.BigDecimal} for
     *  summing and POI cell writing. Returns {@code null} on blank /
     *  unparseable input so the subheader total + cell value both
     *  degrade gracefully. */
    private static java.math.BigDecimal parseAmount(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;
        try {
            return new java.math.BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String stringOrNull(Object raw) {
        if (raw == null) return null;
        String s = raw.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
