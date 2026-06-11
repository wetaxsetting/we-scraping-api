package com.kosign.wecms.domain.banking.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.dto.BankFilter;
import com.kosign.wecms.domain.banking.dto.CashFlowReportResponse;
import com.kosign.wecms.domain.banking.dto.CashFlowReportResponse.CashFlowRow;
import com.kosign.wecms.domain.banking.service.cashflow.BalanceCalculator;
import com.kosign.wecms.domain.banking.service.cashflow.CashFlowRowAssembler;
import com.kosign.wecms.domain.banking.service.cashflow.ChartOfAccountsHierarchy;
import com.kosign.wecms.domain.banking.service.cashflow.FxConverter;
import com.kosign.wecms.domain.banking.service.cashflow.MonthlyAmountAccumulator;
import com.kosign.wecms.domain.banking.service.cashflow.PaymentDataAggregator;
import com.kosign.wecms.domain.banking.service.cashflow.PaymentDataRow;
import com.kosign.wecms.domain.setting.chartofaccounts.entity.ChartAcctInfm;
import com.kosign.wecms.domain.setting.chartofaccounts.repository.ChartOfAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cash Flow report service — refactor of legacy {@code cash_0001_001_r001}.
 *
 * <p>Phase 2 replaces the deterministic stub with a real aggregation
 * over the four legacy source tables ({@code ACCT_TRNS_HSTR},
 * {@code CARD_TRNS_HSTR}, {@code OVRS_PYMN_MSTR}, {@code SCDUL_PYMN_SUMR}
 * + {@code SCDUL_PYMN_SUMR_DETL}), grouped by {@code CHART_ACCT_INFM}
 * hierarchy. Mirrors the legacy {@code get_cashflow_report()} PL/pgSQL
 * function CTE-by-CTE — see docs/refactor-wecms/features/cash-flow-report.md
 * §3 for the verified algorithm.
 *
 * <p>Pipeline:
 * <ol>
 *   <li>Load the tenant's CHART_ACCT_INFM rows once. Build a
 *       {@link ChartOfAccountsHierarchy} view (replaces the legacy
 *       {@code wb_get_chart_acct_level_text} helper).</li>
 *   <li>Collect current-year + prior-year source rows via
 *       {@link PaymentDataAggregator}. Both years use the same approval-status
 *       resolver (replaces {@code wb_get_appr_stts}) — only Completed
 *       rows survive.</li>
 *   <li>Apply the bank filter + FX conversion (current year only).
 *       Prior year is FX-converted but never bank filtered
 *       (legacy parity — §3.6). Quarter is NOT a calculation input on
 *       either path: the full Jan–Dec year is always computed and the
 *       frontend hides out-of-quarter columns (legacy parity, gap D-1 /
 *       G5, PO decision 2026-06-04).</li>
 *   <li>Bucket per-L3-code monthly amounts via
 *       {@link MonthlyAmountAccumulator}.</li>
 *   <li>Compute {@code jan_opening_balance} from prior-year aggregates,
 *       then Opening Balance / End Balance / Sales Profit accumulators
 *       via {@link BalanceCalculator}.</li>
 *   <li>Assemble the final ordered row list via
 *       {@link CashFlowRowAssembler}.</li>
 * </ol>
 *
 * <p>Validation mirrors {@code cash_0001_001_r001_act.jsp:19-36}:
 * {@code year} / {@code baseCurrency} required; rates required ONLY
 * when the base currency differs (conditional check is documented as
 * deliberate divergence D2 in §6 of the design doc).
 *
 * <p>The legacy {@code login=N} flag is intentionally overridden — the
 * controller requires authentication (Sonny override 2026-05-13).
 */
@Service
@RequiredArgsConstructor
public class CashFlowReportService {

    private final ChartOfAccountRepository chartRepo;
    private final PaymentDataAggregator aggregator;
    private final FxConverter fx;
    private final BalanceCalculator balanceCalc;
    private final CashFlowRowAssembler assembler;

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

        // 1. Chart-of-accounts hierarchy for the tenant.
        List<ChartAcctInfm> tenantChart = chartRepo.findByBizKeyAndDelYn(bizKey, "N", Sort.unsorted());
        ChartOfAccountsHierarchy hierarchy = new ChartOfAccountsHierarchy(tenantChart);

        // 2a. Collect current-year source rows, then apply the bank filter + FX.
        // Quarter is deliberately NOT passed — the full Jan–Dec year is always
        // computed (legacy parity; quarter is a frontend column-visibility
        // concern only). PO decision 2026-06-04, gap D-1 / G5 — see
        // docs/features/cash-flow/parity-audit/gap-report.md.
        List<PaymentDataRow> currentRaw = aggregator.collect(bizKey, year);
        List<PaymentDataRow> currentFiltered = aggregator.applyFilters(
                currentRaw, bankFilters, baseCurrency, usdRate, krwRate, fx);
        Map<String, MonthlyAmountAccumulator> currentL3Totals =
                bucketByChartAcctCd(currentFiltered, hierarchy);

        // 2b. Collect prior-year rows for the opening-balance carry-forward.
        // No quarter / bank filter (legacy parity — §3.6). Same FX rates.
        List<PaymentDataRow> prevRaw = aggregator.collect(bizKey, year - 1);
        List<PaymentDataRow> prevConverted = aggregator.applyFxOnly(
                prevRaw, baseCurrency, usdRate, krwRate, fx);
        Map<String, MonthlyAmountAccumulator> prevL3Totals =
                bucketByChartAcctCd(prevConverted, hierarchy);

        // 3. Balance calculator: jan_opening_balance → Opening → End → Sales Profit.
        BigDecimal janOpen = balanceCalc.janOpeningBalance(hierarchy, prevL3Totals);
        MonthlyAmountAccumulator opening = balanceCalc.openingBalance(
                hierarchy, currentL3Totals, janOpen);
        MonthlyAmountAccumulator end = balanceCalc.endBalance(
                hierarchy, currentL3Totals, opening);
        MonthlyAmountAccumulator profit = balanceCalc.salesProfit(
                hierarchy, currentL3Totals);

        // 4. Assemble the ordered row list.
        List<CashFlowRow> rows = assembler.assemble(
                hierarchy, currentL3Totals, opening, end, profit, baseCurrency);

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
     * Bucket converted source rows by {@code chart_acct_cd} → 12-month
     * accumulator. Only rows whose {@code chart_acct_cd} maps to a
     * known L3 in the hierarchy contribute — rows whose code isn't in
     * the chart (or sits outside the ACC-* trees) are silently dropped,
     * matching the legacy {@code level3_with_hierarchy} CTE's INNER
     * JOIN behaviour.
     */
    private static Map<String, MonthlyAmountAccumulator> bucketByChartAcctCd(
            List<PaymentDataRow> rows, ChartOfAccountsHierarchy hierarchy) {
        // Pre-build the set of valid L3 codes so unrelated rows drop out fast.
        java.util.Set<String> validL3 = hierarchy.l3Rows().stream()
                .map(ChartAcctInfm::getChartAcctCd)
                .collect(java.util.stream.Collectors.toSet());
        Map<String, MonthlyAmountAccumulator> out = new HashMap<>();
        for (PaymentDataRow r : rows) {
            if (r.chartAcctCd() == null) continue;
            if (!validL3.contains(r.chartAcctCd())) continue;
            out.computeIfAbsent(r.chartAcctCd(), k -> new MonthlyAmountAccumulator())
                    .add(r.paymentMonth(), r.originalAmount());
        }
        return out;
    }
}
