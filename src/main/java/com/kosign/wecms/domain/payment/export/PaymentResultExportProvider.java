package com.kosign.wecms.domain.payment.export;

import com.kosign.wecms.common.export.ExcelExportRequest;
import com.kosign.wecms.common.export.ExcelSortSpec;
import com.kosign.wecms.common.export.ExportFetch;
import com.kosign.wecms.common.export.ExportRowProvider;
import com.kosign.wecms.domain.payment.internaltransfer.dto.CurrencyTotal;
import com.kosign.wecms.domain.payment.internaltransfer.dto.TransferResultListResponse;
import com.kosign.wecms.domain.payment.internaltransfer.dto.TransferResultRow;
import com.kosign.wecms.domain.setting.erptransactionhistory.service.ErpTrnsHistoryWriter;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Common Excel export logic for the four Payment Result list pages
 * (Internal Transfer / Vendor / Expense / Payroll). All four pages
 * share the same wire shape ({@link TransferResultListResponse}) and
 * the same 8 columns, so the row-to-FIELD_ID map + subheader
 * composition is parameterised once here.
 *
 * <p>Subclasses bind:
 * <ul>
 *   <li>{@link #screenId()} — must match the View Setting seed entry
 *       ({@code payment-internal-transfer-result}, …) or
 *       {@code ExportableScreenRegistry} rejects with {@code WBE2010}.</li>
 *   <li>{@link #titleLabel()} — human-readable label used in the
 *       subheader's "{n} {title}(s)" count, e.g. "transfer batch".</li>
 *   <li>{@link #loadList(...)} — typically delegates to the module's
 *       own {@code *ResultService.list(...)}. The base passes through
 *       the dispatch-table count query so the spreadsheet's TRNF_STATUS
 *       column reads "Completed"/"In Progress" the same way the on-screen
 *       list does.</li>
 * </ul>
 *
 * <p>Export is unpaginated by design — the spreadsheet contains every
 * matching row regardless of the user's current viewport page.
 *
 * <p>Subheader is computed server-side per CLAUDE.md decision 5. The
 * frontend never ships a count + totals string for these screens.
 */
public abstract class PaymentResultExportProvider implements ExportRowProvider {

    /** FIELD_IDs from the V113 / V141 / V143 / V144 seeds. The shared
     *  shell + view-setting seed mirror these 8 codes; the writer drops
     *  CHECKBOX / ETC automatically via
     *  {@code ScreenOptionService.isSpecialFieldId}. */
    private static final Set<String> ALLOWED_FIELD_IDS = Set.of(
            "REG_DTM",
            "TITL",
            "TOT_TRNS_CNT",
            "TOTAL_TRAN_AMT",
            "TRNF_YN",
            "APPR_SEQ_NO",
            "DOC_NO"
    );

    /**
     * Shared ERP_TRNS_HSTR writer for the export 'W' audit. Field-injected
     * (not constructor) so the four subclasses keep their lean
     * {@code @RequiredArgsConstructor} (own ResultService only) without each
     * threading a {@code super(...)} call. Spring injects it into the concrete
     * provider bean after construction.
     */
    @Autowired
    private ErpTrnsHistoryWriter erpHistoryWriter;

    @Override
    public abstract String screenId();

    /** Singular noun for the subheader count, e.g. "transfer batch". */
    protected abstract String titleLabel();

    /**
     * The 2-char {@code ERP_TRNS_HSTR.TRNS_TYPE_CD} for this module's Payment
     * Result export ({@code "04"} Vendor / {@code "05"} Expense / {@code "06"}
     * Payroll / {@code "17"} Internal Transfer — legacy
     * {@code ErpTypeCd.*_PAYMENT_RESULT} / {@code INTERNAL_TRANSFER_RESULT}).
     */
    protected abstract String erpResultTrnsTypeCd();

    /** F2 — these four Result exports write a WeCMS→ERP 'W' audit row (legacy
     *  {@code pymn_0002_002_r003} / {@code pymn_0006_002_r003} /
     *  {@code pymn_0009_003_r002}). */
    @Override
    public boolean auditsErpExport() {
        return true;
    }

    /** Persist the WeCMS→ERP 'W' row by copying the generated workbook into the
     *  ERP-history tenant subtree. Non-blocking — the writer is
     *  {@code REQUIRES_NEW} + swallow-and-log, matching legacy. */
    @Override
    public void recordErpExport(java.nio.file.Path workbookFile, String orgFileNm) {
        erpHistoryWriter.recordExportedFile(erpResultTrnsTypeCd(), workbookFile, orgFileNm);
    }

    /** Subclass hook — call the module's matching ResultService.list. */
    protected abstract TransferResultListResponse loadList(
            String fromDate, String toDate, String srchWd, String trnfStts,
            String sortField, String sortDirection,
            int page, int size);

    @Override
    public Set<String> allowedFieldIds() {
        return ALLOWED_FIELD_IDS;
    }

    @Override
    public ExportFetch fetch(ExcelExportRequest request) {
        Map<String, Object> filters = request.filters() != null ? request.filters() : Map.of();
        String fromDate = stringOrDefault(filters.get("fromDate"), legacyDefaultFromDate());
        String toDate   = stringOrDefault(filters.get("toDate"),   legacyDefaultToDate());
        String srchWd   = stringOrNull(filters.get("srchWd"));
        String trnfStts = stringOrNull(filters.get("trnfStts"));

        ExcelSortSpec sort = request.sort();
        String sortField = sort != null ? sort.field() : null;
        String sortDirection = sort != null ? sort.direction() : null;

        // Export with a wide page-size so every matching row lands in the
        // spreadsheet. The service-side cap on `size` (50_000) matches
        // the writer's {@link ExcelExportService#MAX_ROWS} limit — any
        // larger result fails fast there with WBE2011.
        TransferResultListResponse data = loadList(
                fromDate, toDate, srchWd, trnfStts,
                sortField, sortDirection,
                0, 50_000);

        List<TransferResultRow> rows = data.rows() != null ? data.rows() : List.of();
        List<Map<String, Object>> mapped = new ArrayList<>(rows.size());
        for (TransferResultRow r : rows) {
            mapped.add(toFieldMap(r));
        }

        String subHeader = composeSubHeader(rows.size(), data.currencyTotals());
        return ExportFetch.of(mapped, subHeader);
    }

    /**
     * Compose the subheader: "{n} {titleLabel}(es)  ·  Total (CCY): n.nnn.nnn".
     * Multi-currency batches emit one segment per CCY. Locale-independent
     * thousand separators (vi-VN convention — '.' as thousands) so the
     * Excel header reads identically to the on-screen footer.
     */
    protected String composeSubHeader(int rowCount, List<CurrencyTotal> totals) {
        StringBuilder b = new StringBuilder();
        b.append(rowCount).append(' ').append(titleLabel())
                .append(rowCount == 1 ? "" : "(es)");
        if (totals != null) {
            for (CurrencyTotal t : totals) {
                String amount = t.totalAmount() != null
                        ? formatViVnAmount(t.totalAmount())
                        : "0";
                b.append("  ·  Total (").append(t.ccyCd()).append("): ").append(amount);
            }
        }
        return b.toString();
    }

    /** Default {@code from_date} when the request omits it — 30 days
     *  ago, matching the frontend's default period. */
    protected String legacyDefaultFromDate() {
        // The frontend always sends the actual window; this fallback
        // exists for direct API tests / curl. yyyyMMdd format.
        java.time.LocalDate start = java.time.LocalDate.now().minusDays(30);
        return start.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
    }

    protected String legacyDefaultToDate() {
        return java.time.LocalDate.now()
                .format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
    }

    /**
     * Translate one {@link TransferResultRow} into a {@code FIELD_ID}-keyed
     * map. Amount values ship raw — POI's {@code #,##0.##} cell format
     * is applied by {@code ExcelWorkbookWriter} when the column carries
     * {@code format: "amount"}.
     *
     * <p>{@code TRNF_YN} ships as a localised label ("Completed" /
     * "In Progress") rather than the raw {@code Y}/{@code N} code so
     * the spreadsheet reads naturally without further mapping.
     */
    private static Map<String, Object> toFieldMap(TransferResultRow r) {
        Map<String, Object> m = new HashMap<>();
        m.put("REG_DTM",         formatRegDtm(r.regDtm()));
        m.put("TITL",            nullToEmpty(r.titl()));
        m.put("TOT_TRNS_CNT",    r.totTrnsCnt() != null ? r.totTrnsCnt() : BigDecimal.ZERO);
        m.put("TOTAL_TRAN_AMT",  r.totalTranAmt() != null ? r.totalTranAmt() : BigDecimal.ZERO);
        m.put("TRNF_YN",         deriveStatus(r));
        m.put("APPR_SEQ_NO",     nullToEmpty(r.apprSeqNo()));
        m.put("DOC_NO",          nullToEmpty(r.docNo()));
        return m;
    }

    /**
     * Mirror the legacy two-status rule from
     * {@code pymn_0010_001.ts:371-378}: in_progress while the batch
     * hasn't fully dispatched, completed otherwise. Same math the
     * frontend uses so the spreadsheet matches what the user sees.
     */
    private static String deriveStatus(TransferResultRow r) {
        long succ = r.totTrnsSuccCnt() != null ? r.totTrnsSuccCnt().longValue() : 0L;
        long err  = r.totTrnsErrorCnt() != null ? r.totTrnsErrorCnt().longValue() : 0L;
        long total = r.totTrnsCnt() != null ? r.totTrnsCnt().longValue() : 0L;
        boolean inProgress = !"Y".equals(r.trnfStts()) || (succ + err) < total;
        return inProgress ? "In Progress" : "Completed";
    }

    /** {@code yyyyMMddHHmmss} → {@code dd-MM-yyyy HH:mm:ss}. */
    private static String formatRegDtm(String raw) {
        if (raw == null || raw.length() != 14) return nullToEmpty(raw);
        return raw.substring(6, 8) + "-" + raw.substring(4, 6) + "-" + raw.substring(0, 4)
                + " "
                + raw.substring(8, 10) + ":" + raw.substring(10, 12) + ":" + raw.substring(12, 14);
    }

    /** vi-VN convention: '.' as the thousands separator (same as the
     *  legacy footer + on-screen totals card). No decimals — the wire
     *  decimal is already whole VND. */
    private static String formatViVnAmount(BigDecimal amount) {
        // Strip scale so 100000.00 prints as 100.000 (not 100.000,00).
        long whole = amount.setScale(0, java.math.RoundingMode.HALF_UP).longValue();
        java.text.NumberFormat fmt =
                java.text.NumberFormat.getInstance(java.util.Locale.forLanguageTag("vi-VN"));
        return fmt.format(whole);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
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

    // Suppress unused warning for the Locale import (kept for future i18n).
    @SuppressWarnings("unused")
    private static final Locale LOCALE_HINT = Locale.ROOT;
}
