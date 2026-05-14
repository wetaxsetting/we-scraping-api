package com.kosign.wecms.domain.banking.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Daily Cash report payload (refactor wire shape — lowercase snake_case
 * via global Jackson naming strategy). Mirrors legacy WSVC
 * {@code rept_0009_001_r001} REC / REC1 / REC2.
 *
 * <ul>
 *   <li>{@code currencySummary} — one row per currency present in the
 *       result, with totals by account-type bucket (REC).</li>
 *   <li>{@code accounts} — per-account detail rows (REC1).</li>
 *   <li>{@code currencyFlow} — currency rollup with opening / closing /
 *       debit / credit counts and amounts (REC2).</li>
 * </ul>
 */
public record DailyCashReportResponse(
        String asOfDate,
        String currency,
        List<CurrencySummary> currencySummary,
        List<AccountRow> accounts,
        List<CurrencyFlow> currencyFlow
) {
    public record CurrencySummary(
            String     currencyCd,
            BigDecimal totalCash,
            BigDecimal totalDemandDeposit,
            BigDecimal totalTermDeposit,
            BigDecimal totalLoan,
            BigDecimal totalSavings
    ) { }

    public record AccountRow(
            String     bizKey,
            String     lastCollectedDate,
            String     refDate,
            String     currencyCd,
            String     acctTypeCd,
            String     bankCd,
            String     bankNm,
            String     acctNo,
            BigDecimal balance,
            String     loanName,
            String     maturityDate,
            BigDecimal agreementAmount,
            BigDecimal sendCount,
            BigDecimal sendAmount,
            BigDecimal receiveCount,
            BigDecimal receiveAmount
    ) { }

    public record CurrencyFlow(
            String     currencyCd,
            BigDecimal beforeDateBalance,
            BigDecimal balance,
            BigDecimal sendCount,
            BigDecimal sendAmount,
            BigDecimal receiveCount,
            BigDecimal receiveAmount
    ) { }
}
