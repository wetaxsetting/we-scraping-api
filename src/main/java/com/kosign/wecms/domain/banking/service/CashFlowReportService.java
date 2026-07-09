package com.kosign.wecms.domain.banking.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.dto.BankFilter;
import com.kosign.wecms.domain.banking.dto.CashFlowReportResponse;
import com.kosign.wecms.domain.banking.dto.CashFlowReportResponse.CashFlowRow;
import com.kosign.wecms.domain.banking.dto.CashFlowReportResponse.MonthlyAmounts;
import com.kosign.wecms.domain.banking.repository.CashFlowReportRepository;
import com.kosign.wecms.domain.banking.repository.CashFlowReportRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cash Flow report service — refactor of legacy {@code cash_0001_001_r001}.
 *
 * <p><b>Architecture decision (2026-06-13, DB-function source of truth):</b>
 * Cash Flow calls the legacy PL/pgSQL function
 * {@code get_cashflow_report(...)} directly through
 * {@link CashFlowReportRepository} instead of recomputing in Java. The
 * earlier Java pipeline (PaymentDataAggregator / BalanceCalculator /
 * FxConverter / ChartOfAccountsHierarchy / CashFlowRowAssembler) is
 * <b>off the active path</b> — those classes are retained (not deleted)
 * but no longer wired here. This removes the parity-drift surface: the
 * approval gate ({@code wb_get_appr_stts}), the investment-code carve-out,
 * FX, hierarchy levels, and all balance math now live exclusively in the
 * DB function, exactly as legacy.
 *
 * <p>This service does NOT recompute anything. Its only jobs are:
 * <ol>
 *   <li>Validate the request (mirrors {@code cash_0001_001_r001_act.jsp}).</li>
 *   <li>Serialise the bank filter to the function's JSON contract.</li>
 *   <li>Call the function with {@code p_quarter = null} — production parity
 *       ({@code YEAR_QUAT=null} is always sent by the live UI; quarter is a
 *       frontend column-visibility concern only — gap D-1/G5). The requested
 *       {@code quarter} is echoed back in the response so the frontend hides
 *       the out-of-quarter columns.</li>
 *   <li>Re-assemble the function's row-type-major output into the
 *       frontend's root-major order (Opening → ACC-01 block → ACC-02 block →
 *       ACC-03 block → Sales Profit → End Balance) and map to
 *       {@link CashFlowReportResponse}. Ordering only — no figures change.</li>
 * </ol>
 *
 * <p>The wire/API response contract ({@link CashFlowReportResponse}) is
 * unchanged. The legacy {@code login=N} flag stays overridden — the
 * controller requires authentication (Sonny override 2026-05-13).
 */
@Service
@RequiredArgsConstructor
public class CashFlowReportService {

    private final CashFlowReportRepository reportRepo;
    private final ObjectMapper objectMapper;

    /** Fixed Level-1 roots in display order — mirrors the legacy
     *  {@code fixed_level1_categories} ordering (ACC-01, ACC-02, ACC-03). */
    private static final List<String> ROOT_ORDER = List.of("ACC-01", "ACC-02", "ACC-03");

    private static final String RT_OPENING = "Opening Balance";
    private static final String RT_LEVEL1 = "Level 1";
    private static final String RT_LEVEL2 = "Level 2";
    private static final String RT_LEVEL3 = "Level 3";
    private static final String RT_SUBTOTAL = "Level 1 Subtotal";
    private static final String RT_SALES_PROFIT = "Sales Profit";
    private static final String RT_END = "End Balance";

    @Transactional(readOnly = true)
    public CashFlowReportResponse search(
            int year,
            Integer quarter,
            String baseCurrency,
            BigDecimal usdRate,
            BigDecimal krwRate,
            List<BankFilter> bankFilters) {

        final String bizKey = TenantContext.currentBizKey();
        if (bizKey == null) throw new IllegalStateException("missing tenant context");

        validate(year, quarter, baseCurrency, usdRate, krwRate);

        final String bankFiltersJson = toBankFiltersJson(bankFilters);

        // p_quarter = null always (production parity — see class javadoc).
        List<CashFlowReportRow> raw = reportRepo.fetchReport(
                bizKey, year, null, baseCurrency, usdRate, krwRate, bankFiltersJson);

        List<CashFlowRow> rows = reassemble(raw);

        // Echo the REQUESTED quarter so the frontend hides out-of-quarter columns.
        return new CashFlowReportResponse(year, quarter, baseCurrency, usdRate, krwRate, rows);
    }

    private void validate(int year, Integer quarter, String baseCurrency,
                          BigDecimal usdRate, BigDecimal krwRate) {
        if (year < 1900 || year > 2999) {
            throw new BusinessException("WBE_VALIDATION", "year: out of range");
        }
        if (quarter != null && (quarter < 1 || quarter > 4)) {
            throw new BusinessException("WBE_VALIDATION", "quarter: must be 1..4");
        }
        if (baseCurrency == null || baseCurrency.isBlank()) {
            throw new BusinessException("WBE_VALIDATION", "baseCurrency: required");
        }
        switch (baseCurrency) {
            case "VND", "USD", "KRW" -> { /* ok */ }
            default -> throw new BusinessException("WBE_VALIDATION", "baseCurrency: must be VND|USD|KRW");
        }
        if (!"USD".equals(baseCurrency) && (usdRate == null || usdRate.signum() <= 0)) {
            throw new BusinessException("WBE_CASH_FLOW_RATE_REQUIRED", "USD");
        }
        if (!"KRW".equals(baseCurrency) && (krwRate == null || krwRate.signum() <= 0)) {
            throw new BusinessException("WBE_CASH_FLOW_RATE_REQUIRED", "KRW");
        }
    }

    /**
     * Serialise the bank filter to the legacy function's JSON contract —
     * a JSON array of {@code {"BANK_CD":..,"ACCT_NO":..}} objects (verbatim
     * keys from {@code filtered_payment_data}). Empty / null → {@code null}
     * (the function treats null / '' / 'null' as "no filter").
     */
    private String toBankFiltersJson(List<BankFilter> bankFilters) {
        if (bankFilters == null || bankFilters.isEmpty()) return null;
        List<Map<String, String>> payload = new ArrayList<>(bankFilters.size());
        for (BankFilter f : bankFilters) {
            Map<String, String> entry = new LinkedHashMap<>();
            entry.put("BANK_CD", f.bankCd());
            entry.put("ACCT_NO", f.accountNo());
            payload.add(entry);
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new BusinessException("WBE_VALIDATION", "bankFilters: serialization failed");
        }
    }

    /**
     * Re-order the function's row-type-major output into the frontend's
     * root-major order and map each projection to a {@link CashFlowRow}.
     *
     * <p>Frontend {@code CashFlowPage.groupReport} walks the rows linearly
     * and expects, for each root: the Level-1 row, then each Level-2 row
     * immediately followed by its Level-3 children, then the Level-1
     * Subtotal. The function instead groups by row type (all Level-1, then
     * all Level-2, …), so we bucket by {@code group_key} (root) +
     * {@code parent_l2_code} and re-emit. No amounts are touched.
     */
    private List<CashFlowRow> reassemble(List<CashFlowReportRow> raw) {
        CashFlowReportRow opening = null;
        CashFlowReportRow salesProfit = null;
        CashFlowReportRow endBalance = null;

        // Per-root buckets, preserving discovery order for any unexpected root.
        Map<String, CashFlowReportRow> l1ByRoot = new LinkedHashMap<>();
        Map<String, CashFlowReportRow> subtotalByRoot = new LinkedHashMap<>();
        Map<String, List<CashFlowReportRow>> l2ByRoot = new LinkedHashMap<>();
        Map<String, List<CashFlowReportRow>> l3ByL2 = new LinkedHashMap<>();
        List<CashFlowReportRow> leftovers = new ArrayList<>();

        for (CashFlowReportRow r : raw) {
            String type = r.getRowType() == null ? "" : r.getRowType();
            switch (type) {
                case RT_OPENING -> opening = r;
                case RT_SALES_PROFIT -> salesProfit = r;
                case RT_END -> endBalance = r;
                case RT_LEVEL1 -> l1ByRoot.put(nz(r.getGroupKey()), r);
                case RT_SUBTOTAL -> subtotalByRoot.put(nz(r.getGroupKey()), r);
                case RT_LEVEL2 -> l2ByRoot
                        .computeIfAbsent(nz(r.getGroupKey()), k -> new ArrayList<>())
                        .add(r);
                case RT_LEVEL3 -> l3ByL2
                        .computeIfAbsent(nz(r.getParentL2Code()), k -> new ArrayList<>())
                        .add(r);
                default -> leftovers.add(r);
            }
        }

        // Root order: the three fixed roots first, then any other root seen
        // (defensive — legacy only emits ACC-01/02/03).
        List<String> roots = new ArrayList<>(ROOT_ORDER);
        for (String root : l1ByRoot.keySet()) {
            if (!roots.contains(root)) roots.add(root);
        }

        Comparator<CashFlowReportRow> byDisplayCode =
                Comparator.comparing(row -> nz(row.getDisplayCode()));

        List<CashFlowRow> out = new ArrayList<>();
        int[] seq = {0};

        if (opening != null) out.add(map(opening, seq));

        for (String root : roots) {
            CashFlowReportRow l1 = l1ByRoot.get(root);
            if (l1 == null) continue; // root not present in this tenant
            out.add(map(l1, seq));

            List<CashFlowReportRow> l2s = l2ByRoot.getOrDefault(root, List.of());
            l2s.stream().sorted(byDisplayCode).forEach(l2 -> {
                out.add(map(l2, seq));
                List<CashFlowReportRow> l3s = l3ByL2.getOrDefault(nz(l2.getDisplayCode()), List.of());
                l3s.stream().sorted(byDisplayCode).forEach(l3 -> out.add(map(l3, seq)));
            });

            CashFlowReportRow subtotal = subtotalByRoot.get(root);
            if (subtotal != null) out.add(map(subtotal, seq));
        }

        if (salesProfit != null) out.add(map(salesProfit, seq));
        if (endBalance != null) out.add(map(endBalance, seq));

        // Defensive: never silently drop a row the function emitted.
        for (CashFlowReportRow r : leftovers) out.add(map(r, seq));

        return out;
    }

    private CashFlowRow map(CashFlowReportRow r, int[] seq) {
        return new CashFlowRow(
                pad(seq[0]++),
                r.getDisplayCode(),
                r.getDisplayName(),
                r.getItemCode(),
                r.getItemName(),
                r.getRowType(),
                r.getGroupKey(),
                r.getParentL2Code(),
                r.getDisplayCurrency(),
                new MonthlyAmounts(
                        r.getJanAmount(), r.getFebAmount(), r.getMarAmount(), r.getAprAmount(),
                        r.getMayAmount(), r.getJunAmount(), r.getJulAmount(), r.getAugAmount(),
                        r.getSepAmount(), r.getOctAmount(), r.getNovAmount(), r.getDecAmount()),
                r.getTotalAmount());
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String pad(int n) {
        return String.format("%05d", n);
    }
}
