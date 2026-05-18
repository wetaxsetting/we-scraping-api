package com.kosign.wecms.domain.banking.service.cashflow;

import com.kosign.wecms.domain.setting.chartofaccounts.entity.ChartOfAccount;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link BalanceCalculator}.
 *
 * <p>Pins the Opening/End/Sales-Profit formulas against the verified
 * 2025 sample from
 * docs/refactor-wecms/features/cash-flow-report.md §3.6:
 *
 * <pre>
 *   S.TOTAL REVENUE  Jan: 55_000  Feb: 55_000  Mar: 220_000  ...
 *                    Aug: 1_910_000  Sep: 130_000  rest: 0
 *   S.TOTAL EXPENSE  all months: 0
 *
 *   Opening Balance  Jan: 0   Feb: 55_000   Mar: 110_000   ...
 *                    Aug: 470_000   Sep: 2_380_000   Dec: 2_510_000
 *   End Balance      Jan: 55_000   Feb: 110_000   Mar: 330_000   ...
 *                    Aug: 2_380_000   Sep: 2_510_000   Dec: 2_510_000
 * </pre>
 *
 * <p>The sample uses tenant {@code VN_VNM0004_000001}'s real chart of
 * accounts (Code0011 SALES TEST, Code0012 SALES TEST 2, Code0033 OTHERS
 * SALE all under ACC-01) with no INVESTMENT carve-out rows present, so
 * {@code jan_opening_balance} evaluates to zero.
 */
class BalanceCalculatorTest {

    private final BalanceCalculator calc = new BalanceCalculator();

    /* ============================================================
     * Test fixture: tenant 000001's hierarchy + 2025 source amounts.
     * ============================================================ */

    /** Minimal three-tier hierarchy mirroring tenant 000001's relevant rows. */
    private ChartOfAccountsHierarchy hierarchy() {
        return new ChartOfAccountsHierarchy(List.of(
                root("ACC-01"),
                root("ACC-02"),
                root("ACC-03"),
                l2("Code001", "ACC-01"),                  // SALES (under REVENUE)
                l2("Code002", "ACC-01"),                  // OTHERS (under REVENUE)
                l3("Code0011", "Code001", null),          // SALES TEST
                l3("Code0012", "Code001", null),          // SALES TEST 2
                l3("Code0033", "Code002", null)));        // OTHERS SALE
    }

    private Map<String, MonthlyAmountAccumulator> currentYearL3Totals2025() {
        // Verified from DEV — see report_union row "Code0011 SALES TEST" etc.
        Map<String, MonthlyAmountAccumulator> out = new HashMap<>();
        out.put("Code0011", monthly(0, 0, 220_000, 0, 0, 0, 100_000, 0, 130_000, 0, 0, 0));
        out.put("Code0012", monthly(55_000, 55_000, 0, 0, 0, 0, 0, 1_000_000, 0, 0, 0, 0));
        out.put("Code0033", monthly(0, 0, 0, 0, 0, 0, 40_000, 910_000, 0, 0, 0, 0));
        return out;
    }

    /* ============================================================
     * Tests
     * ============================================================ */

    @Test
    void janOpeningBalance_isZero_whenNoPriorYearData() {
        // Tenant 000001 has no 2024 transactions → prevYearL3Totals empty.
        Map<String, MonthlyAmountAccumulator> prev = Map.of();
        assertThat(calc.janOpeningBalance(hierarchy(), prev))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void openingBalance_2025_matchesVerifiedGolden() {
        MonthlyAmountAccumulator opening = calc.openingBalance(
                hierarchy(), currentYearL3Totals2025(), BigDecimal.ZERO);

        // From §3.6 verified sample:
        assertThat(opening.monthly(1)).isEqualByComparingTo("0");           // Jan
        assertThat(opening.monthly(2)).isEqualByComparingTo("55000");       // Feb = Jan End
        assertThat(opening.monthly(3)).isEqualByComparingTo("110000");      // Mar = Feb End
        assertThat(opening.monthly(4)).isEqualByComparingTo("330000");      // Apr = Mar End
        // Apr-Jun: no revenue inflow → Opening flat at 330k
        assertThat(opening.monthly(5)).isEqualByComparingTo("330000");
        assertThat(opening.monthly(6)).isEqualByComparingTo("330000");
        assertThat(opening.monthly(7)).isEqualByComparingTo("330000");
        assertThat(opening.monthly(8)).isEqualByComparingTo("470000");      // Aug = Jul End (330k + 140k)
        assertThat(opening.monthly(9)).isEqualByComparingTo("2380000");     // Sep = Aug End
        assertThat(opening.monthly(10)).isEqualByComparingTo("2510000");
        assertThat(opening.monthly(11)).isEqualByComparingTo("2510000");
        assertThat(opening.monthly(12)).isEqualByComparingTo("2510000");
    }

    @Test
    void endBalance_2025_matchesVerifiedGolden() {
        MonthlyAmountAccumulator opening = calc.openingBalance(
                hierarchy(), currentYearL3Totals2025(), BigDecimal.ZERO);
        MonthlyAmountAccumulator end = calc.endBalance(
                hierarchy(), currentYearL3Totals2025(), opening);

        assertThat(end.monthly(1)).isEqualByComparingTo("55000");           // Jan
        assertThat(end.monthly(2)).isEqualByComparingTo("110000");          // Feb
        assertThat(end.monthly(3)).isEqualByComparingTo("330000");          // Mar
        assertThat(end.monthly(8)).isEqualByComparingTo("2380000");         // Aug
        assertThat(end.monthly(9)).isEqualByComparingTo("2510000");         // Sep
        assertThat(end.monthly(12)).isEqualByComparingTo("2510000");        // Dec (year-end)
    }

    @Test
    void salesProfit_2025_equalsRevenueMinusExpense() {
        // Tenant 000001 has no expense rows → Sales Profit == Revenue.
        MonthlyAmountAccumulator profit = calc.salesProfit(
                hierarchy(), currentYearL3Totals2025());
        assertThat(profit.monthly(1)).isEqualByComparingTo("55000");
        assertThat(profit.monthly(8)).isEqualByComparingTo("1910000");      // 1_000_000 (Code0012) + 910_000 (Code0033)
        assertThat(profit.totalAmount()).isEqualByComparingTo("2510000");
    }

    @Test
    void salesProfit_doesNotIncludeInvestment() {
        // Synthetic: revenue 100, expense 30, investment 999 (cal_rule 'P').
        // Sales Profit must = 70, NOT 70 + 999.
        ChartOfAccountsHierarchy h = new ChartOfAccountsHierarchy(List.of(
                root("ACC-01"),
                root("ACC-02"),
                root("ACC-03"),
                l2("L2-REV", "ACC-01"),
                l2("L2-EXP", "ACC-02"),
                l2("L2-INV", "ACC-03"),
                l3("CD-REV", "L2-REV", null),
                l3("CD-EXP", "L2-EXP", null),
                l3("2001026", "L2-INV", "P")));    // hardcoded INVESTMENT code with cal_rule
        Map<String, MonthlyAmountAccumulator> totals = new HashMap<>();
        totals.put("CD-REV", monthlyJan("100"));
        totals.put("CD-EXP", monthlyJan("30"));
        totals.put("2001026", monthlyJan("999"));

        MonthlyAmountAccumulator profit = calc.salesProfit(h, totals);
        assertThat(profit.monthly(1)).isEqualByComparingTo("70");
    }

    @Test
    void endBalance_includesInvestmentCarveOut_calRuleP() {
        // Synthetic: revenue 100, expense 30, investment 999 (cal_rule 'P').
        // End Balance must = 100 - 30 + 999 = 1069.
        ChartOfAccountsHierarchy h = new ChartOfAccountsHierarchy(List.of(
                root("ACC-01"),
                root("ACC-02"),
                root("ACC-03"),
                l2("L2-REV", "ACC-01"),
                l2("L2-EXP", "ACC-02"),
                l2("L2-INV", "ACC-03"),
                l3("CD-REV", "L2-REV", null),
                l3("CD-EXP", "L2-EXP", null),
                l3("2001026", "L2-INV", "P")));
        Map<String, MonthlyAmountAccumulator> totals = new HashMap<>();
        totals.put("CD-REV", monthlyJan("100"));
        totals.put("CD-EXP", monthlyJan("30"));
        totals.put("2001026", monthlyJan("999"));

        MonthlyAmountAccumulator opening = calc.openingBalance(h, totals, BigDecimal.ZERO);
        MonthlyAmountAccumulator end = calc.endBalance(h, totals, opening);
        assertThat(end.monthly(1)).isEqualByComparingTo("1069");
    }

    @Test
    void endBalance_includesInvestmentCarveOut_calRuleM_subtracts() {
        // Synthetic: revenue 100, investment 50 (cal_rule 'M').
        // End Balance must = 100 - 50 = 50.
        ChartOfAccountsHierarchy h = new ChartOfAccountsHierarchy(List.of(
                root("ACC-01"),
                root("ACC-02"),
                root("ACC-03"),
                l2("L2-REV", "ACC-01"),
                l2("L2-INV", "ACC-03"),
                l3("CD-REV", "L2-REV", null),
                l3("2002002", "L2-INV", "M")));
        Map<String, MonthlyAmountAccumulator> totals = new HashMap<>();
        totals.put("CD-REV", monthlyJan("100"));
        totals.put("2002002", monthlyJan("50"));

        MonthlyAmountAccumulator opening = calc.openingBalance(h, totals, BigDecimal.ZERO);
        MonthlyAmountAccumulator end = calc.endBalance(h, totals, opening);
        assertThat(end.monthly(1)).isEqualByComparingTo("50");
    }

    @Test
    void endBalance_ignoresInvestment_whenCodeNotInCarveOutSet() {
        // Tenant 000001's CDX-001 has cal_rule='P' but it's NOT in
        // INVESTMENT_BALANCE_CONTRIBUTING_CODES. Must NOT contribute.
        ChartOfAccountsHierarchy h = new ChartOfAccountsHierarchy(List.of(
                root("ACC-01"),
                root("ACC-02"),
                root("ACC-03"),
                l2("L2-REV", "ACC-01"),
                l2("Code009", "ACC-03"),
                l3("CD-REV", "L2-REV", null),
                l3("CDX-001", "Code009", "P")));   // NOT in carve-out set
        Map<String, MonthlyAmountAccumulator> totals = new HashMap<>();
        totals.put("CD-REV", monthlyJan("100"));
        totals.put("CDX-001", monthlyJan("777"));

        MonthlyAmountAccumulator opening = calc.openingBalance(h, totals, BigDecimal.ZERO);
        MonthlyAmountAccumulator end = calc.endBalance(h, totals, opening);
        assertThat(end.monthly(1)).isEqualByComparingTo("100");
    }

    @Test
    void janOpeningBalance_carriesForwardPriorYearRevenue() {
        // Prior year had 500 revenue, no expenses, no investment.
        // jan_opening_balance must = 500.
        ChartOfAccountsHierarchy h = new ChartOfAccountsHierarchy(List.of(
                root("ACC-01"),
                root("ACC-02"),
                root("ACC-03"),
                l2("L2-REV", "ACC-01"),
                l3("CD-REV", "L2-REV", null)));
        Map<String, MonthlyAmountAccumulator> prev = Map.of(
                "CD-REV", monthlyJan("500"));
        assertThat(calc.janOpeningBalance(h, prev))
                .isEqualByComparingTo("500");
    }

    @Test
    void investmentCarveOutSet_isExactlyTheLegacyFourCodes() {
        // Pinning the hardcoded set against accidental edits.
        assertThat(BalanceCalculator.INVESTMENT_BALANCE_CONTRIBUTING_CODES)
                .containsExactlyInAnyOrder("2001026", "2001027", "2002003", "2002002");
    }

    /* ============================================================
     * Fixture builders
     * ============================================================ */

    private static ChartOfAccount row(String cd, String parent, String calRule) {
        ChartOfAccount c = new ChartOfAccount();
        c.setBizKey("TEST");
        c.setChartAcctSeqNo("seq-" + cd);
        c.setChartAcctCd(cd);
        c.setParentChartAcctCd(parent);
        c.setStts("A");
        c.setDelYn("N");
        c.setRegId("test");
        c.setRegDtm("20250101000000");
        c.setModId("test");
        c.setModDtm("20250101000000");
        c.setCalRule(calRule);
        c.setChartAcctNmEn(cd);
        return c;
    }

    private static ChartOfAccount root(String cd)    { return row(cd, null, null); }
    private static ChartOfAccount l2(String cd, String parent) { return row(cd, parent, null); }
    private static ChartOfAccount l3(String cd, String parent, String calRule) {
        return row(cd, parent, calRule);
    }

    private static MonthlyAmountAccumulator monthly(long... v) {
        if (v.length != 12) throw new IllegalArgumentException("monthly() needs 12 args");
        MonthlyAmountAccumulator a = new MonthlyAmountAccumulator();
        for (int i = 0; i < 12; i++) a.add(i + 1, BigDecimal.valueOf(v[i]));
        return a;
    }

    private static MonthlyAmountAccumulator monthlyJan(String janVal) {
        MonthlyAmountAccumulator a = new MonthlyAmountAccumulator();
        a.add(1, new BigDecimal(janVal));
        return a;
    }
}
