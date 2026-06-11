package com.kosign.wecms.domain.banking.export;

import com.kosign.wecms.common.export.ExcelExportRequest;
import com.kosign.wecms.common.export.ExportFetch;
import com.kosign.wecms.common.export.ExportRowProvider;
import com.kosign.wecms.domain.banking.dto.TransactionListResponse;
import com.kosign.wecms.domain.banking.dto.TransactionRow;
import com.kosign.wecms.domain.banking.service.AmountFormatter;
import com.kosign.wecms.domain.banking.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
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
 */
@Component
@RequiredArgsConstructor
public class TransactionHistoryExportProvider implements ExportRowProvider {

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

    private final TransactionService transactionService;

    @Override
    public String screenId() {
        return "banking-transaction-history";
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
