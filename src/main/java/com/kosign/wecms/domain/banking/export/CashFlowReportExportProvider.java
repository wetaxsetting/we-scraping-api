package com.kosign.wecms.domain.banking.export;

import com.kosign.wecms.common.export.ExcelExportRequest;
import com.kosign.wecms.common.export.ExportFetch;
import com.kosign.wecms.common.export.ExportRowProvider;
import com.kosign.wecms.domain.banking.dto.BankFilter;
import com.kosign.wecms.domain.banking.dto.CashFlowReportResponse;
import com.kosign.wecms.domain.banking.dto.CashFlowReportResponse.CashFlowRow;
import com.kosign.wecms.domain.banking.dto.CashFlowReportResponse.MonthlyAmounts;
import com.kosign.wecms.domain.banking.service.CashFlowReportService;
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
 * Excel export for Cash Flow Report. Reuses {@link CashFlowReportService}
 * so export and on-screen list share one query path. View Setting
 * {@code WEB_SVC_ID} = {@code cash-flow-report}.
 *
 * <p>Allow-list mirrors the report matrix: hierarchy columns
 * (DISP_CD / DISP_NM / ITEM_CD / ITEM_NM / ROW_TYPE) + twelve monthly
 * amount columns + annual total + display currency.
 */
@Component
@RequiredArgsConstructor
public class CashFlowReportExportProvider implements ExportRowProvider {

    private static final Set<String> ALLOWED_FIELD_IDS = Set.of(
            "DISP_CD",
            "DISP_NM",
            "ITEM_CD",
            "ITEM_NM",
            "ROW_TYPE",
            "JAN_AMT", "FEB_AMT", "MAR_AMT", "APR_AMT", "MAY_AMT", "JUN_AMT",
            "JUL_AMT", "AUG_AMT", "SEP_AMT", "OCT_AMT", "NOV_AMT", "DEC_AMT",
            "TOTAL_AMT",
            "DISP_CCY_CD"
    );

    private final CashFlowReportService service;

    @Override public String screenId() { return "cash-flow-report"; }
    @Override public Set<String> allowedFieldIds() { return ALLOWED_FIELD_IDS; }

    @Override
    public ExportFetch fetch(ExcelExportRequest request) {
        Map<String, Object> filters = request.filters() != null ? request.filters() : Map.of();

        int year = parseInt(filters.get("year"), 2026);
        Integer quarter = parseQuarter(filters.get("quarter"));
        String baseCurrency = string(filters.get("baseCurrency"), "VND");
        BigDecimal usdRate = parseDecimal(filters.get("usdRate"));
        BigDecimal krwRate = parseDecimal(filters.get("krwRate"));

        CashFlowReportResponse data = service.search(
                year, quarter, baseCurrency, usdRate, krwRate, List.<BankFilter>of());

        List<Map<String, Object>> mapped = new ArrayList<>(data.rows().size());
        for (CashFlowRow r : data.rows()) {
            mapped.add(toFieldMap(r));
        }

        String subHeader = String.format(Locale.ROOT,
                "%d row(s) · Year: %d · Base: %s",
                data.rows().size(), year, baseCurrency);
        return ExportFetch.of(mapped, subHeader);
    }

    private static Map<String, Object> toFieldMap(CashFlowRow r) {
        Map<String, Object> m = new HashMap<>();
        m.put("DISP_CD", nullSafe(r.displayCd()));
        m.put("DISP_NM", nullSafe(r.displayNm()));
        m.put("ITEM_CD", nullSafe(r.itemCd()));
        m.put("ITEM_NM", nullSafe(r.itemNm()));
        m.put("ROW_TYPE", nullSafe(r.rowType()));
        MonthlyAmounts a = r.monthly() != null ? r.monthly() : MonthlyAmounts.zero();
        m.put("JAN_AMT", a.jan());
        m.put("FEB_AMT", a.feb());
        m.put("MAR_AMT", a.mar());
        m.put("APR_AMT", a.apr());
        m.put("MAY_AMT", a.may());
        m.put("JUN_AMT", a.jun());
        m.put("JUL_AMT", a.jul());
        m.put("AUG_AMT", a.aug());
        m.put("SEP_AMT", a.sep());
        m.put("OCT_AMT", a.oct());
        m.put("NOV_AMT", a.nov());
        m.put("DEC_AMT", a.dec());
        m.put("TOTAL_AMT", r.annualTotal());
        m.put("DISP_CCY_CD", nullSafe(r.displayCurrency()));
        return m;
    }

    private static String string(Object raw, String fallback) {
        if (raw == null) return fallback;
        String s = raw.toString().trim();
        return s.isEmpty() ? fallback : s;
    }

    private static int parseInt(Object raw, int fallback) {
        if (raw == null) return fallback;
        try { return Integer.parseInt(raw.toString().trim()); }
        catch (NumberFormatException e) { return fallback; }
    }

    private static Integer parseQuarter(Object raw) {
        if (raw == null) return null;
        try {
            int q = Integer.parseInt(raw.toString().trim());
            return q >= 1 && q <= 4 ? q : null;
        } catch (NumberFormatException e) { return null; }
    }

    private static BigDecimal parseDecimal(Object raw) {
        if (raw == null) return null;
        try { return new BigDecimal(raw.toString().trim()); }
        catch (NumberFormatException e) { return null; }
    }

    private static String nullSafe(String s) { return s == null ? "" : s; }
}
