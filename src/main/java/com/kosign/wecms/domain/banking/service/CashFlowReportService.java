package com.kosign.wecms.domain.banking.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.dto.BankFilter;
import com.kosign.wecms.domain.banking.dto.CashFlowReportResponse;
import com.kosign.wecms.domain.banking.dto.CashFlowReportResponse.CashFlowRow;
import com.kosign.wecms.domain.banking.dto.CashFlowReportResponse.MonthlyAmounts;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Cash Flow report service — refactor of legacy {@code cash_0001_001_r001}.
 *
 * <p>The legacy IDO calls a single PostgreSQL function
 * {@code get_cashflow_report(biz_key, year, quarter, base_ccy, usd_rate,
 * krw_rate, bank_filt)} that walks the {@code CASH_FLOW} plan table,
 * applies FX conversion, and emits hierarchy-aware rows. The refactor
 * rule (api CLAUDE.md → "DB Function / Legacy Function Policy") forbids
 * introducing a new legacy PL/pgSQL helper in refactor feature code, and
 * {@code CASH_FLOW} is not in the PROD Active Table List today. The
 * Sprint stub below produces a deterministic seed shaped exactly like
 * the legacy output so the frontend matrix renders end-to-end. A future
 * MR replaces this with either a Java-side aggregation over
 * {@code ACCT_TRNS_HSTR} (the active source) or, if the plan-entry
 * feature lands, a real read from the migrated cash-flow tables.
 *
 * <p>Validation mirrors {@code cash_0001_001_r001_act.jsp:19-36}:
 * {@code year} / {@code baseCurrency} required; rates required when the
 * plan carries a foreign-currency line that needs converting. The
 * legacy {@code login=N} flag is intentionally overridden — the
 * controller requires authentication (Sonny override 2026-05-13).
 */
@Service
@RequiredArgsConstructor
public class CashFlowReportService {

    /** Row-type values emitted verbatim from the legacy function output. */
    private static final String ROW_TYPE_OPENING_BALANCE = "Opening Balance";
    private static final String ROW_TYPE_LEVEL_1 = "Level 1";
    private static final String ROW_TYPE_LEVEL_2 = "Level 2";
    private static final String ROW_TYPE_LEVEL_3 = "Level 3";
    private static final String ROW_TYPE_LEVEL_1_SUBTOTAL = "Level 1 Subtotal";
    private static final String ROW_TYPE_SALES_PROFIT = "SALES PROFIT";
    private static final String ROW_TYPE_NON_SALES_PROFIT = "NON SALES PROFIT";
    private static final String ROW_TYPE_END_BALANCE = "End Balance";

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

        List<CashFlowRow> rows = buildRows(baseCurrency, usdRate, krwRate);

        return new CashFlowReportResponse(
                year,
                quarter,
                baseCurrency,
                usdRate,
                krwRate,
                rows
        );
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

    /* -------------------- Deterministic seed -------------------- */

    private List<CashFlowRow> buildRows(String baseCurrency,
                                        BigDecimal usdRate,
                                        BigDecimal krwRate) {
        List<CashFlowRow> rows = new ArrayList<>();

        rows.add(row("00010", "OPEN", "Opening Balance", null, null,
                ROW_TYPE_OPENING_BALANCE, "0", null,
                baseCurrency, monthly(100_000_000L), bd(1_200_000_000L)));

        // L1: Operating Inflow
        rows.add(row("00100", "1", "Operating Inflow", null, null,
                ROW_TYPE_LEVEL_1, "1", null,
                baseCurrency, MonthlyAmounts.zero(), BigDecimal.ZERO));
        rows.add(row("00110", "1.1", "Sales receipts", "INFL_SALE", "Sales receipts",
                ROW_TYPE_LEVEL_2, "1", "1",
                baseCurrency, monthly(80_000_000L), bd(960_000_000L)));
        rows.add(row("00111", "1.1.1", "Domestic sales", "INFL_SALE_DOM", "Domestic sales",
                ROW_TYPE_LEVEL_3, "1", "1.1",
                baseCurrency, monthly(60_000_000L), bd(720_000_000L)));
        rows.add(row("00112", "1.1.2", "Export sales", "INFL_SALE_EXP", "Export sales",
                ROW_TYPE_LEVEL_3, "1", "1.1",
                baseCurrency, monthly(20_000_000L), bd(240_000_000L)));
        rows.add(row("00190", "1.S", "Operating Inflow Subtotal", null, null,
                ROW_TYPE_LEVEL_1_SUBTOTAL, "1", "1",
                baseCurrency, monthly(80_000_000L), bd(960_000_000L)));

        // L1: Operating Outflow
        rows.add(row("00200", "2", "Operating Outflow", null, null,
                ROW_TYPE_LEVEL_1, "2", null,
                baseCurrency, MonthlyAmounts.zero(), BigDecimal.ZERO));
        rows.add(row("00210", "2.1", "Payroll", "OUTF_PAYR", "Payroll",
                ROW_TYPE_LEVEL_2, "2", "2",
                baseCurrency, monthly(40_000_000L), bd(480_000_000L)));
        rows.add(row("00220", "2.2", "Office rent", "OUTF_RENT", "Office rent",
                ROW_TYPE_LEVEL_2, "2", "2",
                baseCurrency, monthly(12_000_000L), bd(144_000_000L)));
        rows.add(row("00290", "2.S", "Operating Outflow Subtotal", null, null,
                ROW_TYPE_LEVEL_1_SUBTOTAL, "2", "2",
                baseCurrency, monthly(52_000_000L), bd(624_000_000L)));

        // Profit lines
        rows.add(row("00800", "P1", "Sales Profit", null, null,
                ROW_TYPE_SALES_PROFIT, "P", null,
                baseCurrency, monthly(28_000_000L), bd(336_000_000L)));
        rows.add(row("00810", "P2", "Non-Sales Profit", null, null,
                ROW_TYPE_NON_SALES_PROFIT, "P", null,
                baseCurrency, MonthlyAmounts.zero(), BigDecimal.ZERO));

        // End balance
        rows.add(row("00900", "END", "End Balance", null, null,
                ROW_TYPE_END_BALANCE, "9", null,
                baseCurrency, monthly(128_000_000L), bd(1_536_000_000L)));

        // FX conversion: legacy applies USD/KRW rates inside the function.
        // The seed above is already expressed in the base currency, so no
        // post-processing is required. When the real source lands, the
        // service walks legacy USD/KRW lines and multiplies by the
        // user-supplied rate before emitting the row's monthly amounts.
        return rows;
    }

    private static CashFlowRow row(String dispOrd, String dispCd, String dispNm,
                                   String itemCd, String itemNm,
                                   String rowType, String groupCd, String parentL2,
                                   String dispCcy, MonthlyAmounts monthly,
                                   BigDecimal total) {
        return new CashFlowRow(dispOrd, dispCd, dispNm, itemCd, itemNm,
                rowType, groupCd, parentL2, dispCcy, monthly, total);
    }

    private static MonthlyAmounts monthly(long perMonth) {
        BigDecimal v = bd(perMonth);
        return new MonthlyAmounts(v, v, v, v, v, v, v, v, v, v, v, v);
    }

    private static BigDecimal bd(long v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }
}
