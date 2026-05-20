package com.kosign.wecms.domain.banking.service.cashflow;

import com.kosign.wecms.domain.setting.chartofaccounts.entity.ChartOfAccount;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

/**
 * Computes the Opening Balance, End Balance, and Sales Profit accumulators
 * for the Cash Flow report.
 *
 * <p>Verified formulas (see docs/refactor-wecms/features/cash-flow-report.md
 * §3.6 + §3.7):
 *
 * <pre>
 *   jan_opening_balance = (prev-year ACC-01 totals)
 *                       - (prev-year ACC-02 totals)
 *                       + (prev-year INVESTMENT_BALANCE_CONTRIBUTING_CODES
 *                          rows under ACC-03, signed by cal_rule)
 *
 *   OpeningBalance[N] = jan_opening_balance
 *                     + sum months 1..N-1 of revenue
 *                     - sum months 1..N-1 of expense
 *                     + sum months 1..N-1 of investment_with_cal_rule
 *
 *   EndBalance[N] = OpeningBalance[N]
 *                 + revenue[N]
 *                 - expense[N]
 *                 + investment_with_cal_rule[N]
 *
 *   SalesProfit[N] = revenue[N] - expense[N]     (no investment)
 * </pre>
 *
 * <p>The hardcoded {@link #INVESTMENT_BALANCE_CONTRIBUTING_CODES} set
 * is preserved verbatim from the legacy function body (see §6 D6 of the
 * design doc). These four codes don't exist in tenant 000001's chart of
 * accounts — the opening balance always evaluates to zero for that
 * tenant. Production tenants with these codes activate the carve-out.
 *
 * <p>Reference: docs/refactor-wecms/features/cash-flow-report.md §3.0
 * + §3.6 + §3.7.
 */
@Component
public class BalanceCalculator {

    /**
     * Hardcoded INVESTMENT chart codes whose monthly amounts feed the
     * opening/end balance arithmetic. Preserved verbatim from the legacy
     * function body for production-tenant parity.
     */
    static final Set<String> INVESTMENT_BALANCE_CONTRIBUTING_CODES =
            Set.of("2001026", "2001027", "2002003", "2002002");

    /**
     * Compute {@code jan_opening_balance} from prior-year aggregates.
     *
     * @param hierarchy             previous year's hierarchy view (or
     *                              the current year's, if the L3 chart
     *                              set is stable across years — which
     *                              is the legacy assumption).
     * @param prevYearL3Totals      per-L3-code → previous-year monthly
     *                              accumulator, already FX-converted.
     * @return scalar opening-balance value for January of the current year.
     */
    public BigDecimal janOpeningBalance(ChartOfAccountsHierarchy hierarchy,
                                         Map<String, MonthlyAmountAccumulator> prevYearL3Totals) {
        BigDecimal revenue = sumOverRoot(hierarchy, prevYearL3Totals, "ACC-01", false);
        BigDecimal expense = sumOverRoot(hierarchy, prevYearL3Totals, "ACC-02", false);
        BigDecimal investment = sumOverInvestmentCarveOut(hierarchy, prevYearL3Totals);
        return revenue.subtract(expense).add(investment);
    }

    /**
     * Compute the Opening Balance accumulator (12 monthly buckets).
     * {@code monthly[N] = jan_opening_balance + cumsum(revenue[1..N-1])
     * - cumsum(expense[1..N-1]) + cumsum(investment_with_cal_rule[1..N-1])}.
     * Bucket [12] (December) carries Nov's End Balance.
     */
    public MonthlyAmountAccumulator openingBalance(ChartOfAccountsHierarchy hierarchy,
                                                    Map<String, MonthlyAmountAccumulator> currentYearL3Totals,
                                                    BigDecimal janOpeningBalance) {
        MonthlyAmountAccumulator opening = new MonthlyAmountAccumulator();
        BigDecimal cumulative = janOpeningBalance;
        // Jan's opening = jan_opening_balance directly.
        opening.add(1, cumulative);
        // For N = 2..12, opening[N] = opening[N-1] + revenue[N-1] - expense[N-1] + investment[N-1].
        for (int n = 2; n <= 12; n++) {
            BigDecimal rev = monthlyL1(hierarchy, currentYearL3Totals, "ACC-01", n - 1, false);
            BigDecimal exp = monthlyL1(hierarchy, currentYearL3Totals, "ACC-02", n - 1, false);
            BigDecimal inv = monthlyInvestmentCarveOut(hierarchy, currentYearL3Totals, n - 1);
            cumulative = cumulative.add(rev).subtract(exp).add(inv);
            opening.add(n, cumulative);
        }
        return opening;
    }

    /**
     * Compute the End Balance accumulator. {@code monthly[N] =
     * opening[N] + revenue[N] - expense[N] + investment_with_cal_rule[N]}.
     */
    public MonthlyAmountAccumulator endBalance(ChartOfAccountsHierarchy hierarchy,
                                                Map<String, MonthlyAmountAccumulator> currentYearL3Totals,
                                                MonthlyAmountAccumulator opening) {
        MonthlyAmountAccumulator end = new MonthlyAmountAccumulator();
        for (int n = 1; n <= 12; n++) {
            BigDecimal openN = opening.monthly(n);
            BigDecimal rev = monthlyL1(hierarchy, currentYearL3Totals, "ACC-01", n, false);
            BigDecimal exp = monthlyL1(hierarchy, currentYearL3Totals, "ACC-02", n, false);
            BigDecimal inv = monthlyInvestmentCarveOut(hierarchy, currentYearL3Totals, n);
            end.add(n, openN.add(rev).subtract(exp).add(inv));
        }
        return end;
    }

    /**
     * Compute the Sales Profit accumulator. {@code monthly[N] =
     * revenue[N] - expense[N]} (no investment — distinct from End Balance).
     */
    public MonthlyAmountAccumulator salesProfit(ChartOfAccountsHierarchy hierarchy,
                                                 Map<String, MonthlyAmountAccumulator> currentYearL3Totals) {
        MonthlyAmountAccumulator profit = new MonthlyAmountAccumulator();
        for (int n = 1; n <= 12; n++) {
            BigDecimal rev = monthlyL1(hierarchy, currentYearL3Totals, "ACC-01", n, false);
            BigDecimal exp = monthlyL1(hierarchy, currentYearL3Totals, "ACC-02", n, false);
            profit.add(n, rev.subtract(exp));
        }
        return profit;
    }

    /* ============== helpers ============== */

    /**
     * Sum of monthly amounts across all L3 rows under a root, for a
     * specific month. When {@code applyCalRule} is true (called on the
     * ACC-03 row from L1 / L1 Subtotal context), each L3's cal_rule
     * signs its contribution.
     */
    private static BigDecimal monthlyL1(ChartOfAccountsHierarchy hierarchy,
                                         Map<String, MonthlyAmountAccumulator> l3Totals,
                                         String rootCd,
                                         int month,
                                         boolean applyCalRule) {
        BigDecimal sum = BigDecimal.ZERO;
        for (ChartOfAccount l2 : hierarchy.l2sUnderRoot(rootCd)) {
            for (ChartOfAccount l3 : hierarchy.l3sUnderL2(l2.getChartAcctCd())) {
                MonthlyAmountAccumulator acc = l3Totals.get(l3.getChartAcctCd());
                if (acc == null) continue;
                BigDecimal v = acc.monthly(month);
                if (applyCalRule && "M".equals(l3.getCalRule())) v = v.negate();
                sum = sum.add(v);
            }
        }
        return sum;
    }

    /**
     * Sum the total_amount column across all L3 rows under a root.
     */
    private static BigDecimal sumOverRoot(ChartOfAccountsHierarchy hierarchy,
                                           Map<String, MonthlyAmountAccumulator> l3Totals,
                                           String rootCd,
                                           boolean applyCalRule) {
        BigDecimal sum = BigDecimal.ZERO;
        for (ChartOfAccount l2 : hierarchy.l2sUnderRoot(rootCd)) {
            for (ChartOfAccount l3 : hierarchy.l3sUnderL2(l2.getChartAcctCd())) {
                MonthlyAmountAccumulator acc = l3Totals.get(l3.getChartAcctCd());
                if (acc == null) continue;
                BigDecimal v = acc.totalAmount();
                if (applyCalRule && "M".equals(l3.getCalRule())) v = v.negate();
                sum = sum.add(v);
            }
        }
        return sum;
    }

    /**
     * Sum the investment carve-out contribution for one month
     * (over ALL months when called via sumOverInvestmentCarveOut).
     * Includes only L3 rows whose {@code chart_acct_cd} is in
     * {@link #INVESTMENT_BALANCE_CONTRIBUTING_CODES} AND that sit under
     * ACC-03.
     */
    private static BigDecimal monthlyInvestmentCarveOut(ChartOfAccountsHierarchy hierarchy,
                                                         Map<String, MonthlyAmountAccumulator> l3Totals,
                                                         int month) {
        BigDecimal sum = BigDecimal.ZERO;
        for (ChartOfAccount l2 : hierarchy.l2sUnderRoot("ACC-03")) {
            for (ChartOfAccount l3 : hierarchy.l3sUnderL2(l2.getChartAcctCd())) {
                if (!INVESTMENT_BALANCE_CONTRIBUTING_CODES.contains(l3.getChartAcctCd())) continue;
                MonthlyAmountAccumulator acc = l3Totals.get(l3.getChartAcctCd());
                if (acc == null) continue;
                BigDecimal v = acc.monthly(month);
                String rule = l3.getCalRule();
                if ("P".equals(rule)) sum = sum.add(v);
                else if ("M".equals(rule)) sum = sum.subtract(v);
                // else: contributes zero (legacy ELSE branch)
            }
        }
        return sum;
    }

    /** Investment carve-out summed across all 12 months (for jan_opening_balance). */
    private static BigDecimal sumOverInvestmentCarveOut(ChartOfAccountsHierarchy hierarchy,
                                                         Map<String, MonthlyAmountAccumulator> l3Totals) {
        BigDecimal sum = BigDecimal.ZERO;
        for (int n = 1; n <= 12; n++) {
            sum = sum.add(monthlyInvestmentCarveOut(hierarchy, l3Totals, n));
        }
        return sum;
    }
}
