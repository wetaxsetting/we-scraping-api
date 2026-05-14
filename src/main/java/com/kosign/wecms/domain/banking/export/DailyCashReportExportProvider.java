package com.kosign.wecms.domain.banking.export;

import com.kosign.wecms.common.auditlog.service.AuditLogContext;
import com.kosign.wecms.common.auditlog.service.AuditLogService;
import com.kosign.wecms.common.export.ExcelExportRequest;
import com.kosign.wecms.common.export.ExportFetch;
import com.kosign.wecms.common.export.ExportRowProvider;
import com.kosign.wecms.domain.banking.dto.DailyCashReportResponse;
import com.kosign.wecms.domain.banking.service.DailyCashCurrencyFilter;
import com.kosign.wecms.domain.banking.service.DailyCashReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Excel export for Daily Cash report. Reuses
 * {@link DailyCashReportService#search} so export and on-screen list
 * read the same query path. View Setting {@code WEB_SVC_ID} =
 * {@code daily-cash-report}; allow-list mirrors the V100 seed.
 */
@Component
@RequiredArgsConstructor
public class DailyCashReportExportProvider implements ExportRowProvider {

    private static final Set<String> ALLOWED_FIELD_IDS = Set.of(
            "BANK",
            "ACCT_NO",
            "BALANCE",
            "LOAN_NAME",
            "MATURITY_DATE",
            "SEND_COUNT",
            "SEND_AMOUNT",
            "RECEIVE_COUNT",
            "RECEIVE_AMOUNT"
    );

    private final DailyCashReportService service;
    private final AuditLogService auditLogService;

    @Override public String screenId() { return "daily-cash-report"; }
    @Override public Set<String> allowedFieldIds() { return ALLOWED_FIELD_IDS; }

    @Override
    public ExportFetch fetch(ExcelExportRequest request) {
        Map<String, Object> filters = request.filters() != null ? request.filters() : Map.of();
        String asOfDate = string(filters.get("asOfDate"));
        DailyCashCurrencyFilter ccyFilter = DailyCashCurrencyFilter.parse(string(filters.get("currency")));

        DailyCashReportResponse data = service.search(asOfDate, ccyFilter);

        List<DailyCashReportResponse.AccountRow> rows = data.accounts();
        List<Map<String, Object>> mapped = new ArrayList<>(rows.size());
        for (DailyCashReportResponse.AccountRow r : rows) {
            mapped.add(toFieldMap(r));
        }

        auditLogService.append(AuditLogContext.builder()
                .wsvcId("daily-cash-report")
                .refNo("DAILY_CASH_REPT_DAILY_SUMR_DETL")
                .refNo1(asOfDate)
                .refNo2(ccyFilter.name())
                .detailKey("audit.daily_cash_report.export.detail")
                .build());

        String subHeader = String.format(Locale.ROOT, "%d account(s) as of %s", rows.size(), nullSafe(asOfDate));
        return ExportFetch.of(mapped, subHeader);
    }

    private static Map<String, Object> toFieldMap(DailyCashReportResponse.AccountRow r) {
        Map<String, Object> m = new HashMap<>();
        m.put("BANK", composeBank(r));
        m.put("ACCT_NO", nullSafe(r.acctNo()));
        m.put("BALANCE", r.balance());
        m.put("LOAN_NAME", nullSafe(r.loanName()));
        m.put("MATURITY_DATE", nullSafe(r.maturityDate()));
        m.put("SEND_COUNT", r.sendCount());
        m.put("SEND_AMOUNT", r.sendAmount());
        m.put("RECEIVE_COUNT", r.receiveCount());
        m.put("RECEIVE_AMOUNT", r.receiveAmount());
        return m;
    }

    private static String composeBank(DailyCashReportResponse.AccountRow r) {
        String name = r.bankNm() != null && !r.bankNm().isBlank() ? r.bankNm() : nullSafe(r.bankCd());
        return name + " · " + nullSafe(r.currencyCd());
    }

    private static String string(Object raw) {
        if (raw == null) return null;
        String s = raw.toString().trim();
        return s.isEmpty() ? null : s;
    }
    private static String nullSafe(String s) { return s == null ? "" : s; }
}
