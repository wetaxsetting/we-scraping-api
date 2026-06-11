package com.kosign.wecms.domain.banking.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.dto.BankFilter;
import com.kosign.wecms.domain.banking.dto.CashFlowReportResponse;
import com.kosign.wecms.domain.banking.dto.CashFlowReportResponse.CashFlowRow;
import com.kosign.wecms.domain.banking.service.cashflow.BalanceCalculator;
import com.kosign.wecms.domain.banking.service.cashflow.CashFlowRowAssembler;
import com.kosign.wecms.domain.banking.service.cashflow.FxConverter;
import com.kosign.wecms.domain.banking.service.cashflow.PaymentDataAggregator;
import com.kosign.wecms.domain.banking.service.cashflow.PaymentDataRow;
import com.kosign.wecms.domain.setting.chartofaccounts.entity.ChartAcctInfm;
import com.kosign.wecms.domain.setting.chartofaccounts.repository.ChartOfAccountRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Parity guard for gap <b>D-1 / G5</b> (PO decision 2026-06-04:
 * <i>KEEP LEGACY BEHAVIOR</i>). The legacy {@code cash_0001_001} always
 * computes the full Jan–Dec year and lets the frontend hide out-of-quarter
 * month columns client-side ({@code YEAR_QUAT=null} is always sent to
 * {@code get_cashflow_report}). The refactor must therefore produce
 * <b>byte-identical</b> Opening / End / monthly figures regardless of the
 * {@code quarter} argument — quarter is a column-visibility concern only.
 *
 * <p>This test reproduces the documented validation scenario verbatim:
 * <pre>Year = 2026 · Currency = VND · Bank = All Banks</pre>
 * comparing <i>This Year</i> (quarter = null) against Q1–Q4 and asserting
 * the row sets match exactly.
 *
 * <p>It also locks the lower-level invariant that
 * {@link PaymentDataAggregator#applyFilters} never drops a month — i.e.
 * the quarter filter is gone from the calculation path entirely, not just
 * from the service call site. If a future change re-introduces server-side
 * quarter filtering, one of these two tests fails (and re-adding a
 * {@code quarter} parameter to {@code applyFilters} would not even compile).
 *
 * <p>Evidence basis: docs/features/cash-flow/parity-audit/gap-report.md.
 */
@ExtendWith(MockitoExtension.class)
class CashFlowReportQuarterParityTest {

    @Mock ChartOfAccountRepository chartRepo;
    @Mock PaymentDataAggregator    aggregator;

    // Real collaborators — the actual balance / FX / assembly math runs.
    private final FxConverter         fx          = new FxConverter();
    private final BalanceCalculator   balanceCalc = new BalanceCalculator();
    private final CashFlowRowAssembler assembler  = new CashFlowRowAssembler();

    private CashFlowReportService service;

    private static final String BIZ  = "VN_VNM0004_000001";
    private static final String USER = "tester";

    private static final BigDecimal USD_RATE = new BigDecimal("25000");
    private static final BigDecimal KRW_RATE = new BigDecimal("18");

    @BeforeEach
    void setUp() {
        service = new CashFlowReportService(chartRepo, aggregator, fx, balanceCalc, assembler);
        TenantContext.set(USER, BIZ, LangCd.EN);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void quarter_doesNotChangeCalculation_year2026_vnd_allBanks() {
        // Chart: ACC-01/02/03 roots + one L2 + one L3 leaf under REVENUE and EXPENSES.
        List<ChartAcctInfm> chart = List.of(
                coa("ACC-01", null, null),
                coa("ACC-02", null, null),
                coa("ACC-03", null, null),
                coa("REV-L2", "ACC-01", null),
                coa("REV-1",  "REV-L2", null),
                coa("EXP-L2", "ACC-02", null),
                coa("EXP-1",  "EXP-L2", null));
        when(chartRepo.findByBizKeyAndDelYn(eq(BIZ), eq("N"), any(Sort.class))).thenReturn(chart);

        // Current year (2026): revenue 100 + expense 40 in EVERY month 1..12.
        when(aggregator.collect(eq(BIZ), eq(2026)))
                .thenReturn(monthlyRows("REV-1", "100", "EXP-1", "40"));
        // Prior year (2025): revenue 10 + expense 5 in every month → jan opening = (120 - 60) = 60.
        when(aggregator.collect(eq(BIZ), eq(2025)))
                .thenReturn(monthlyRows("REV-1", "10", "EXP-1", "5"));

        // Run the REAL filter/FX methods — they no longer take a quarter,
        // so they cannot trim by quarter. (VND base ⇒ FX is a no-op here.)
        when(aggregator.applyFilters(any(), any(), any(), any(), any(), any())).thenCallRealMethod();
        when(aggregator.applyFxOnly(any(), any(), any(), any(), any())).thenCallRealMethod();

        List<BankFilter> allBanks = List.of();   // empty = All Banks

        List<CashFlowRow> thisYear =
                service.search(2026, null, "VND", USD_RATE, KRW_RATE, allBanks).rows();

        // Sanity: the dataset spans the whole year, so End Balance must be
        // non-trivial and clearly reflect post-Q1 months — otherwise an
        // "identical" comparison below would be vacuous.
        CashFlowRow end = endBalanceRow(thisYear);
        assertThat(end.monthly().dec()).isEqualByComparingTo("780"); // 60 opening + 12×60
        assertThat(end.monthly().mar()).isEqualByComparingTo("240"); // 60 opening + 3×60

        // Every quarter selection must return the EXACT same rows.
        for (Integer quarter : List.of(1, 2, 3, 4)) {
            List<CashFlowRow> quarterRows =
                    service.search(2026, quarter, "VND", USD_RATE, KRW_RATE, allBanks).rows();
            assertThat(quarterRows)
                    .as("quarter=%s must match This Year (legacy parity, gap D-1/G5)", quarter)
                    .usingRecursiveComparison()
                    .isEqualTo(thisYear);
        }
    }

    @Test
    void applyFilters_keepsAllTwelveMonths_noQuarterTrim() {
        // applyFilters is a pure function now (no repo access) — build it
        // standalone and prove out-of-quarter months survive.
        PaymentDataAggregator pure =
                new PaymentDataAggregator(null, null, null, null, null);

        List<PaymentDataRow> rows = monthlyRows("REV-1", "100", "EXP-1", "40");
        List<PaymentDataRow> out = pure.applyFilters(
                rows, List.of(), "VND", USD_RATE, KRW_RATE, fx);

        assertThat(out).hasSameSizeAs(rows);
        assertThat(out).extracting(PaymentDataRow::paymentMonth)
                .containsExactlyInAnyOrder(
                        1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6,
                        7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12);
    }

    /* ---------------- canned-data helpers ---------------- */

    private static CashFlowRow endBalanceRow(List<CashFlowRow> rows) {
        return rows.stream()
                .filter(r -> "End Balance".equals(r.rowType()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no End Balance row in report"));
    }

    /** One revenue + one expense row in each month 1..12 (currency VND). */
    private static List<PaymentDataRow> monthlyRows(String revCd, String revAmt,
                                                    String expCd, String expAmt) {
        List<PaymentDataRow> rows = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            rows.add(new PaymentDataRow(revCd + "-" + m, revCd,
                    new BigDecimal(revAmt), "VND", "BANK01", "ACC0001", 0, m, "APR-" + m));
            rows.add(new PaymentDataRow(expCd + "-" + m, expCd,
                    new BigDecimal(expAmt), "VND", "BANK01", "ACC0001", 0, m, "APE-" + m));
        }
        return rows;
    }

    private static ChartAcctInfm coa(String code, String parentCode, String calRule) {
        ChartAcctInfm c = new ChartAcctInfm();
        c.setBizKey(BIZ);
        c.setChartAcctSeqNo(code);
        c.setChartAcctCd(code);
        c.setChartAcctNmEn(code);
        c.setChartAcctNmKo(code);
        c.setParentChartAcctCd(parentCode);
        c.setStts("A");
        c.setDelYn("N");
        c.setCalRule(calRule);
        c.setRegId(USER);
        c.setRegDtm("20260101000000");
        c.setModId(USER);
        c.setModDtm("20260101000000");
        return c;
    }
}
