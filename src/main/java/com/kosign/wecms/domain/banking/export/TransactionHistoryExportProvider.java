package com.kosign.wecms.domain.banking.export;

import com.kosign.wecms.common.export.ExcelExportRequest;
import com.kosign.wecms.common.export.ExportFetch;
import com.kosign.wecms.common.export.ExportRowProvider;
import com.kosign.wecms.domain.banking.dto.TransactionListResponse;
import com.kosign.wecms.domain.banking.dto.TransactionRow;
import com.kosign.wecms.domain.banking.service.AmountFormatter;
import com.kosign.wecms.domain.banking.service.TransactionService;
import com.kosign.wecms.domain.setting.erptransactionhistory.service.ErpTrnsHistoryWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Excel export for Banking &gt; Transaction History (VND + FCY).
 *
 * <p>Reuses {@link TransactionService#list} so export and list share one
 * query path. The wire {@code filters} carries the same shape as the
 * list endpoint plus {@code acctSeqNo} (mandatory — the export is
 * scoped to one account).
 *
 * <p>Allow-list mirrors the V128 seed minus the special {@code CHECKBOX}
 * row (filtered by {@link com.kosign.wecms.common.export.ExcelExportService}
 * via the View Setting special-field guard).
 *
 * <p>Abstract base: the VND and FCY scopes register distinct screenIds
 * ({@code banking-transaction-history-vnd} / {@code -fcy}, matching the
 * V156 seed and the frontend's {@code viewSettingScreenId}) and write
 * distinct ERP audit type codes ({@code "01"} VND / {@code "08"} FCY,
 * legacy {@code downloadProc.jsp} {@code fnsh_0002_001} /
 * {@code fnsh_0006_001} branches). The query / filter / sort / row-mapping
 * is identical for both, so it lives here and the subclasses bind only
 * the two scope-specific values.
 */
@RequiredArgsConstructor
public abstract class TransactionHistoryExportProvider implements ExportRowProvider {

    // Mirrors the V157 + V159 seed which aligns the screen with the
    // legacy on-screen table (12 fields, all FIX_YN='N'). The matching
    // panel fields (CONTACT_NM, CONFIRMED_BY) join via
    // ACCT_TRNS_RCV_PAY_INFM; TRSC_ID / FILE_NM / MTCH_DT / MTCH_BY /
    // PURPOSE are not yet on the refactor's `TransactionRow` DTO —
    // the row mapper below emits empty strings for them until the
    // DTO is extended in a follow-up.
    private static final Set<String> ALLOWED_FIELD_IDS = Set.of(
            "TRSC_ID",
            "TRSC_DTM",
            "DEPOSIT_AMT",
            "WITHDRAW_AMT",
            "BAL",
            "RMRK",
            "FILE_NM",
            "MTCH_DT",
            "MTCH_BY",
            "PURPOSE",
            "CONTACT_NM",
            "CONFIRMED_BY"
    );

    protected final TransactionService transactionService;

    /**
     * Shared ERP_TRNS_HSTR writer for the WeCMS&rarr;ERP 'W' export audit.
     * Field-injected so the VND / FCY subclasses keep their lean
     * {@code super(transactionService)} constructors — same precedent as
     * {@code PaymentResultExportProvider} / {@code AccountInquiryExportProvider}.
     */
    @Autowired
    private ErpTrnsHistoryWriter erpHistoryWriter;

    /** Subclass binds the concrete screenId
     *  ({@code banking-transaction-history-vnd} / {@code -fcy}). */
    @Override
    public abstract String screenId();

    /** The 2-char {@code ERP_TRNS_HSTR.TRNS_TYPE_CD} — {@code "01"}
     *  Transaction History (VND) / {@code "08"} Transaction History (FCY),
     *  legacy {@code downloadProc.jsp} {@code fnsh_0002_001} /
     *  {@code fnsh_0006_001} branches. */
    protected abstract String erpExportTrnsTypeCd();

    /** Legacy {@code downloadProc.jsp} writes a WeCMS&rarr;ERP 'W' audit row
     *  for every Transaction History Excel download. */
    @Override
    public boolean auditsErpExport() {
        return true;
    }

    /** Persist the WeCMS&rarr;ERP 'W' row by copying the generated workbook
     *  into the ERP-history tenant subtree. Non-blocking — the writer is
     *  {@code REQUIRES_NEW} + swallow-and-log, matching legacy. */
    @Override
    public void recordErpExport(Path workbookFile, String orgFileNm) {
        erpHistoryWriter.recordExportedFile(erpExportTrnsTypeCd(), workbookFile, orgFileNm);
    }

    @Override
    public Set<String> allowedFieldIds() {
        return ALLOWED_FIELD_IDS;
    }

    @Override
    public ExportFetch fetch(ExcelExportRequest request) {
        Map<String, Object> filters = request.filters() != null ? request.filters() : Map.of();
        String acctSeqNo = str(filters.get("acctSeqNo"));
        String fromDt    = str(filters.get("from"));
        String toDt      = str(filters.get("to"));
        String type      = str(filters.get("type"));
        String cnfm      = str(filters.get("cnfm_yn"));
        String keyword   = str(filters.get("keyword"));

        TransactionListResponse data = transactionService.list(
                acctSeqNo, fromDt, toDt, type, cnfm, keyword);

        List<TransactionRow> rows = data.rows() != null ? data.rows() : List.of();
        List<Map<String, Object>> mapped = new ArrayList<>(rows.size());
        for (TransactionRow r : rows) {
            mapped.add(toFieldMap(r));
        }

        String subHeader = String.format(Locale.ROOT,
                "%d transaction(s)  ·  Deposit total: %s  ·  Withdrawal total: %s",
                rows.size(),
                displayAmt(data.depositTotal()),
                displayAmt(data.withdrawTotal()));

        return ExportFetch.of(mapped, subHeader);
    }

    private static Map<String, Object> toFieldMap(TransactionRow r) {
        Map<String, Object> m = new HashMap<>();
        m.put("TRSC_ID",      nullToEmpty(r.bankTrnsShortId()));
        m.put("TRSC_DTM",     composeDtm(r.trscDt(), r.trscTm()));
        m.put("DEPOSIT_AMT",  r.depositAmt());
        m.put("WITHDRAW_AMT", r.withdrawAmt());
        m.put("BAL",          r.bal());
        m.put("RMRK",         nullToEmpty(r.rmrk()));
        m.put("FILE_NM",      nullToEmpty(r.payOrgFileNm()));
        m.put("MTCH_DT",      datePart(r.matchedAt()));
        m.put("MTCH_BY",      nullToEmpty(r.matchedBy()));
        m.put("PURPOSE",      nullToEmpty(r.useUsagCd()));
        m.put("CONTACT_NM",   nullToEmpty(r.rcvPayTrcoNm()));
        m.put("CONFIRMED_BY", nullToEmpty(r.confirmedBy()));
        return m;
    }

    /** {@code yyyyMMddHHmmss} (or {@code yyyyMMdd}) → {@code YYYY-MM-DD}; blank passes through. */
    private static String datePart(String dtm) {
        if (dtm == null || dtm.length() < 8) return nullToEmpty(dtm);
        return dtm.substring(0, 4) + "-" + dtm.substring(4, 6) + "-" + dtm.substring(6, 8);
    }

    /** {@code YYYYMMDD} + {@code HHmmss} → {@code YYYY-MM-DD HH:mm:ss}. */
    private static String composeDtm(String dt, String tm) {
        StringBuilder b = new StringBuilder();
        if (dt != null && dt.length() == 8) {
            b.append(dt, 0, 4).append('-').append(dt, 4, 6).append('-').append(dt, 6, 8);
        } else if (dt != null) {
            b.append(dt);
        }
        if (tm != null && tm.length() == 6) {
            b.append(' ').append(tm, 0, 2).append(':').append(tm, 2, 4).append(':').append(tm, 4, 6);
        } else if (tm != null && !tm.isEmpty()) {
            b.append(' ').append(tm);
        }
        return b.toString();
    }

    private static String displayAmt(String wire) {
        if (wire == null || wire.isBlank()) return "0";
        try {
            return AmountFormatter.forDisplay(new BigDecimal(wire), null);
        } catch (NumberFormatException ex) {
            return wire;
        }
    }

    private static String str(Object raw) {
        if (raw == null) return null;
        String s = raw.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
