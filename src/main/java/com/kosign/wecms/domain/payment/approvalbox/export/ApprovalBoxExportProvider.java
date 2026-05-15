package com.kosign.wecms.domain.payment.approvalbox.export;

import com.kosign.wecms.common.export.ExcelExportRequest;
import com.kosign.wecms.common.export.ExcelSortSpec;
import com.kosign.wecms.common.export.ExportFetch;
import com.kosign.wecms.common.export.ExportRowProvider;
import com.kosign.wecms.domain.banking.service.AmountFormatter;
import com.kosign.wecms.domain.payment.approvalbox.dto.ApprovalBoxApprover;
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

    /** FIELD_IDs allowed in the Excel export — matches the V84
     *  {@code payment-approval-box} View Setting seed minus the special
     *  {@code CHECKBOX} / {@code ETC} rows (filtered server-side by
     *  {@code ScreenOptionService.SPECIAL_FIELD_IDS} regardless). */
    private static final Set<String> ALLOWED_FIELD_IDS = Set.of(
            "REG_DTM",
            "DOC_NM",
            "DOC_NO",
            "TITL",
            "CURRENCY",
            "TOT_PAY_AMT",
            "PAY_END_YN",
            "PAY_DATE",
            "DRAFT_USER_NM",
            "APPRER_NM"
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
        return "payment-approval-box";
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
        java.math.BigDecimal vndTotal = java.math.BigDecimal.ZERO;
        for (ApprovalBoxRow r : rows) {
            mapped.add(toFieldMap(r));
            java.math.BigDecimal amt = parseAmount(r.totPayAmt());
            if (amt != null && "VND".equalsIgnoreCase(r.currency())) {
                vndTotal = vndTotal.add(amt);
            }
        }

        String subHeader = composeSubHeader(mapped.size(), vndTotal);
        return ExportFetch.of(mapped, subHeader);
    }

    /** {@code <count> document(s)  ·  VND: <total>}. Locale rendering
     *  via {@link AmountFormatter#forDisplay} mirrors the on-screen
     *  footer + the print summary line. */
    private String composeSubHeader(int rowCount, java.math.BigDecimal vndTotal) {
        return String.format(
                Locale.ROOT,
                "%d document(s)  ·  VND: %s",
                rowCount,
                AmountFormatter.forDisplay(vndTotal, "VND"));
    }

    /** Project one row onto a FIELD_ID-keyed map. Amount cells stay
     *  raw decimal — the POI writer applies {@code #,##0.##} from the
     *  column's {@code format = "amount"} declaration. Status &
     *  scheduled date follow legacy formatting (Y/N for `PAY_END_YN`,
     *  yyyyMMdd → DD-MM-YYYY for `PAY_DATE`). */
    private static Map<String, Object> toFieldMap(ApprovalBoxRow r) {
        Map<String, Object> m = new HashMap<>();
        java.math.BigDecimal amt = parseAmount(r.totPayAmt());
        m.put("REG_DTM",       formatDateTime(r.regDtm()));
        m.put("DOC_NM",        nullToEmpty(r.typeLabel()));
        m.put("DOC_NO",        nullToEmpty(r.docNo()));
        m.put("TITL",          nullToEmpty(r.title()));
        m.put("CURRENCY",      nullToEmpty(r.currency()));
        m.put("TOT_PAY_AMT",   amt != null ? amt : java.math.BigDecimal.ZERO);
        m.put("PAY_END_YN",    "Y".equalsIgnoreCase(r.payEndYn()) ? "Y" : "N");
        m.put("PAY_DATE",      formatDate(r.payDate()));
        m.put("DRAFT_USER_NM", nullToEmpty(r.draftUserNm()));
        m.put("APPRER_NM",     composeApproverCell(r.approvers(), r.apprvrTotCnt()));
        return m;
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

    /** Single-line approver cell: {@code <first approver> (+N)} when
     *  there are additional approvers, just the first name when there's
     *  only one. Empty when the chain is missing. */
    private static String composeApproverCell(List<ApprovalBoxApprover> approvers, long totalCount) {
        if (approvers == null || approvers.isEmpty()) return "";
        ApprovalBoxApprover first = approvers.get(0);
        String firstName = first != null && first.apprvrNm() != null ? first.apprvrNm() : "";
        long remainder = totalCount - 1;
        if (remainder <= 0) return firstName;
        return firstName + "  (+" + remainder + ")";
    }

    /** {@code yyyyMMddHHmmss} → {@code DD-MM-YYYY HH:mm:ss}. Returns
     *  the raw string when the input doesn't match the legacy 14-char
     *  shape (defensive — the export must never throw on malformed
     *  legacy seed rows). */
    private static String formatDateTime(String raw) {
        if (raw == null || raw.length() != 14) return nullToEmpty(raw);
        return raw.substring(6, 8) + "-" + raw.substring(4, 6) + "-" + raw.substring(0, 4)
             + " " + raw.substring(8, 10) + ":" + raw.substring(10, 12) + ":" + raw.substring(12, 14);
    }

    /** {@code yyyyMMdd} → {@code DD-MM-YYYY}. Same defensive fallback
     *  as {@link #formatDateTime}. */
    private static String formatDate(String raw) {
        if (raw == null || raw.length() != 8) return nullToEmpty(raw);
        return raw.substring(6, 8) + "-" + raw.substring(4, 6) + "-" + raw.substring(0, 4);
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
