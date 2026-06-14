package com.kosign.wecms.domain.banking.dto;

import java.util.List;

/**
 * Daily Financial HAENGSUNG variant — real DB-backed payload (DF-2).
 *
 * <p>Mirrors {@code rept_0017_001}: the rows are returned verbatim (row type
 * + order preserved) from the legacy DB function
 * {@code fn_get_haengsung_daily_financial_report(biz_key, as_of_date,
 * account_filter)} via {@link com.kosign.wecms.domain.banking.repository.DailyFinancialHsReportRepository}.
 * The earlier Sprint-1 hardcoded 6-row stub was removed (DF-2, 2026-06-13).
 *
 * <p>Wire is lowercase snake_case via the global Jackson SNAKE_CASE strategy.
 * Amount components are nullable wire strings (raw numeric, no separators —
 * {@code lib/currency.ts} re-formats for display); {@code null} is preserved
 * for the cells the function leaves empty (e.g. HEADER amounts, the
 * receipt/payment cells of a BEFORE_BALANCE row) so the frontend renders a
 * blank cell instead of {@code "0"}.
 */
public record DailyFinancialHsReport(
        String asOfDt,
        String accountFilter,
        List<LedgerRow> rows
) {
    /**
     * One ledger row, 1:1 with a {@code fn_get_haengsung_daily_financial_report}
     * output row. {@code rowType} ∈ {@code HEADER | BEFORE_BALANCE |
     * TRANSACTION | ACCOUNT_TOTAL | CURRENCY_TOTAL}; the frontend projects
     * these into its bank-grouped render.
     */
    public record LedgerRow(
            String rowType,
            String bankAccount,
            String currency,
            String reptCd,
            String pymnCd,
            String descriptionEn,
            String descriptionKo,
            String customerSupplier,
            String beforeBalance,
            String receipt,
            String payment,
            String balance,
            String balanceCompanyActual,
            String accountNumber,
            Integer accountTypeOrder,
            String acctSeqNo,
            String transactionDate,
            String transactionTime,
            Integer transactionSeq,
            String memo,
            String bankCd,
            String bankNameSort
    ) { }
}
