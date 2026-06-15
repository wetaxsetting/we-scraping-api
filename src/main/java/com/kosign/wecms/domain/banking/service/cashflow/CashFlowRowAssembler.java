package com.kosign.wecms.domain.banking.service.cashflow;

import com.kosign.wecms.domain.banking.dto.CashFlowReportResponse.CashFlowRow;
import com.kosign.wecms.domain.setting.chartofaccounts.entity.ChartAcctInfm;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds the final ordered row list for the Cash Flow report.
 *
 * <p>Mirrors the legacy {@code report_union} CTE of
 * {@code get_cashflow_report()} byte-for-byte. Emits rows in this fixed
 * order (verified against DEV output):
 *
 * <ol>
 *   <li>Opening Balance row (single).</li>
 *   <li>For each ACC-01 / ACC-02 / ACC-03 root, in that order:
 *     <ol type="a">
 *       <li>Level 1 row (always — even when no L3 children exist).</li>
 *       <li>Level 2 rows (sorted by chart_acct_cd).</li>
 *       <li>Level 3 rows (sorted by chart_acct_cd).</li>
 *       <li>Level 1 Subtotal row (display_code 'S. TOTAL' / 'NON SALES PROFIT' for ACC-03).</li>
 *     </ol>
 *   </li>
 *   <li>Sales Profit row (revenue − expenses, no investment).</li>
 *   <li>End Balance row.</li>
 * </ol>
 *
 * <p>{@code display_name} formatting (per legacy report_union):
 * <ul>
 *   <li><b>Level 1</b>: from {@code fixed_level1_categories} —
 *       {@code "수입 REVENUE" / "지출 EXPENSES" / "투자 INVESTMENT"}.</li>
 *   <li><b>Level 2</b>: {@code coalesce(ko,'') + " " + coalesce(en,'')}.</li>
 *   <li><b>Level 3</b>: {@code coalesce(ko, '')} only (no English).</li>
 *   <li><b>L1 Subtotal</b>: {@code 'S. TOTAL (수입 부분합)'} / {@code '...(지출 부분합)'} / {@code 'NON SALES PROFIT'}.</li>
 *   <li><b>Sales Profit</b>: {@code 'SALES PROFIT (총수입-총지출)'}.</li>
 * </ul>
 *
 * <p>{@code group_key} vocabulary (used by frontend for stable grouping):
 * Opening Balance = {@code "AAAAA-OPENING-BALANCE"};
 * Level 1/2/3 + L1 Subtotal = the root's {@code chart_acct_cd}
 * ({@code "ACC-01"} etc.); Sales Profit = {@code "ZZZZZ-SALES-PROFIT"};
 * End Balance = {@code "ZZZZZ-END-BALANCE"}.
 *
 * <p>Reference: docs/refactor-wecms/features/cash-flow-report.md §3.2 + §3.3.
 */
@Component
public class CashFlowRowAssembler {

    // Frozen Level 1 display names — pulled from the legacy
    // fixed_level1_categories CTE. Refactor preserves verbatim.
    private static final Map<String, String> L1_DISPLAY_NAME = Map.of(
            "ACC-01", "수입 REVENUE",
            "ACC-02", "지출 EXPENSES",
            "ACC-03", "투자 INVESTMENT");

    private static final List<String> ACC_ROOTS_IN_ORDER = List.of("ACC-01", "ACC-02", "ACC-03");

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    /**
     * Assemble the final ordered row list.
     *
     * @param hierarchy           tenant's CHART_ACCT_INFM view.
     * @param l3Totals            per-Level-3 chart code → monthly + total amounts (already FX-converted).
     *                            Codes with no source rows are omitted; the assembler emits zero rows for
     *                            the L3 chart entries that exist but have no transactions.
     * @param openingBalance      Opening Balance row (computed by {@link BalanceCalculator}).
     * @param endBalance          End Balance row (computed by {@link BalanceCalculator}).
     * @param salesProfit         Sales Profit row (computed by {@link BalanceCalculator}).
     * @param baseCurrency        wire currency code.
     */
    public List<CashFlowRow> assemble(ChartOfAccountsHierarchy hierarchy,
                                       Map<String, MonthlyAmountAccumulator> l3Totals,
                                       MonthlyAmountAccumulator openingBalance,
                                       MonthlyAmountAccumulator endBalance,
                                       MonthlyAmountAccumulator salesProfit,
                                       String baseCurrency) {
        List<CashFlowRow> rows = new ArrayList<>();
        int dispOrd = 0;

        // 1. Opening Balance — total_amount is hardcoded 0 in legacy.
        rows.add(new CashFlowRow(
                pad(dispOrd++),
                "Opening Balance", "Opening Balance",
                null, null,
                "Opening Balance",
                "AAAAA-OPENING-BALANCE",
                null,
                baseCurrency,
                openingBalance.snapshot(),
                ZERO));

        // 2. For each ACC-* root: L1 → L2s → L3s under each L2 → L1 Subtotal.
        for (String rootCd : ACC_ROOTS_IN_ORDER) {
            ChartAcctInfm l1 = hierarchy.byCode(rootCd);
            if (l1 == null) continue; // tenant missing this root — surface gracefully

            // Aggregate L1 amounts: sum of all L3 amounts under the root,
            // with cal_rule sign applied for ACC-03 only.
            MonthlyAmountAccumulator l1Acc = aggregateL1(hierarchy, l3Totals, rootCd);

            rows.add(new CashFlowRow(
                    pad(dispOrd++),
                    rootCd,
                    L1_DISPLAY_NAME.getOrDefault(rootCd, rootCd),
                    null, null,
                    "Level 1",
                    rootCd,
                    null,
                    baseCurrency,
                    l1Acc.snapshot(),
                    l1Acc.totalAmount()));

            // L2 rows under this root.
            for (ChartAcctInfm l2 : hierarchy.l2sUnderRoot(rootCd)) {
                MonthlyAmountAccumulator l2Acc = aggregateL2(hierarchy, l3Totals, l2.getChartAcctCd());

                rows.add(new CashFlowRow(
                        pad(dispOrd++),
                        l2.getChartAcctCd(),
                        joinKoEn(l2.getChartAcctNmKo(), l2.getChartAcctNmEn()),
                        null, null,
                        "Level 2",
                        rootCd,
                        null,
                        baseCurrency,
                        l2Acc.snapshot(),
                        l2Acc.totalAmount()));

                // L3 rows under this L2.
                for (ChartAcctInfm l3 : hierarchy.l3sUnderL2(l2.getChartAcctCd())) {
                    MonthlyAmountAccumulator l3Acc = l3Totals.getOrDefault(
                            l3.getChartAcctCd(), new MonthlyAmountAccumulator());

                    rows.add(new CashFlowRow(
                            pad(dispOrd++),
                            l3.getChartAcctCd(),
                            blankIfNull(l3.getChartAcctNmKo()),
                            l3.getChartAcctCd(),
                            blankIfNull(l3.getChartAcctNmEn()),
                            "Level 3",
                            rootCd,
                            l2.getChartAcctCd(),
                            baseCurrency,
                            l3Acc.snapshot(),
                            l3Acc.totalAmount()));
                }
            }

            // L1 Subtotal — same amounts as L1 (cal_rule applied for ACC-03).
            rows.add(new CashFlowRow(
                    pad(dispOrd++),
                    subtotalDisplayCode(rootCd),
                    subtotalDisplayName(rootCd),
                    null, null,
                    "Level 1 Subtotal",
                    rootCd,
                    null,
                    baseCurrency,
                    l1Acc.snapshot(),
                    l1Acc.totalAmount()));
        }

        // 3. Sales Profit (revenue − expenses, no investment).
        rows.add(new CashFlowRow(
                pad(dispOrd++),
                "SALES PROFIT", "SALES PROFIT (총수입-총지출)",
                null, null,
                "Sales Profit",
                "ZZZZZ-SALES-PROFIT",
                null,
                baseCurrency,
                salesProfit.snapshot(),
                salesProfit.totalAmount()));

        // 4. End Balance.
        rows.add(new CashFlowRow(
                pad(dispOrd),
                "End Balance", "End Balance",
                null, null,
                "End Balance",
                "ZZZZZ-END-BALANCE",
                null,
                baseCurrency,
                endBalance.snapshot(),
                endBalance.monthly(12))); // total_column = December's End Balance per §3.6

        return rows;
    }

    /**
     * Build the L1 aggregate by summing all L3 amounts under the root.
     * For ACC-03, the cal_rule on each contributing L3 row applies a
     * sign to its monthly amount; for ACC-01 / ACC-02, raw amounts.
     */
    private MonthlyAmountAccumulator aggregateL1(ChartOfAccountsHierarchy hierarchy,
                                                  Map<String, MonthlyAmountAccumulator> l3Totals,
                                                  String rootCd) {
        boolean isAcc03 = "ACC-03".equals(rootCd);
        MonthlyAmountAccumulator acc = new MonthlyAmountAccumulator();
        for (ChartAcctInfm l2 : hierarchy.l2sUnderRoot(rootCd)) {
            for (ChartAcctInfm l3 : hierarchy.l3sUnderL2(l2.getChartAcctCd())) {
                MonthlyAmountAccumulator l3Acc = l3Totals.get(l3.getChartAcctCd());
                if (l3Acc == null) continue;
                if (isAcc03) {
                    acc.addSigned(l3Acc, calRuleSign(l3.getCalRule()));
                } else {
                    acc.addAll(l3Acc);
                }
            }
        }
        return acc;
    }

    /**
     * L2 aggregate — raw sum of L3 children. Cal_rule does NOT apply
     * here even when the root is ACC-03 (matches legacy report_union
     * which applies cal_rule only on L1 and L1 Subtotal rows).
     */
    private MonthlyAmountAccumulator aggregateL2(ChartOfAccountsHierarchy hierarchy,
                                                  Map<String, MonthlyAmountAccumulator> l3Totals,
                                                  String l2Cd) {
        MonthlyAmountAccumulator acc = new MonthlyAmountAccumulator();
        for (ChartAcctInfm l3 : hierarchy.l3sUnderL2(l2Cd)) {
            MonthlyAmountAccumulator l3Acc = l3Totals.get(l3.getChartAcctCd());
            if (l3Acc != null) acc.addAll(l3Acc);
        }
        return acc;
    }

    /**
     * Cal_rule sign: 'P' → +1, 'M' → -1, else → +1 (passthrough). The
     * "else +1" matches the legacy {@code WHEN ... ELSE fh.jan_amount}
     * branch — unset / unknown cal_rule treats the amount as positive.
     */
    private static int calRuleSign(String calRule) {
        if (calRule == null) return 1;
        return switch (calRule) {
            case "M" -> -1;
            default  ->  1;  // 'P' or other → positive contribution
        };
    }

    /** {@code "korean english"} with empty-string fallback for nulls. */
    private static String joinKoEn(String ko, String en) {
        String k = ko == null ? "" : ko;
        String e = en == null ? "" : en;
        return (k + " " + e).trim();
    }

    private static String blankIfNull(String s) {
        return s == null ? "" : s;
    }

    private static String subtotalDisplayCode(String rootCd) {
        return "ACC-03".equals(rootCd) ? "NON SALES PROFIT" : "S. TOTAL";
    }

    private static String subtotalDisplayName(String rootCd) {
        return switch (rootCd) {
            case "ACC-01" -> "S. TOTAL (수입 부분합)";
            case "ACC-02" -> "S. TOTAL (지출 부분합)";
            case "ACC-03" -> "NON SALES PROFIT";
            default       -> "S. TOTAL";
        };
    }

    /** {@code dispOrd} formatted as a 5-char zero-padded string. */
    private static String pad(int n) {
        return String.format("%05d", n);
    }
}
