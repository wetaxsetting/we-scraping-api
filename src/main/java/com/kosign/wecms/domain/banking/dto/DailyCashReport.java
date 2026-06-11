package com.kosign.wecms.domain.banking.dto;

import java.util.List;

/**
 * Daily Cash report payload — mirrors {@code rept_0009_001_r001} and the
 * static-UI types in {@code components/banking/reports/daily-cash/types.ts}.
 *
 * Sections:
 *   - {@code summary}  — one row per currency bucket (cash + current + savings + …).
 *   - {@code holdings} — physical cash holdings by type.
 *   - {@code accounts} — per-bank-account ledger.
 *
 * <p>Wire is lowercase snake_case via the global Jackson SNAKE_CASE strategy.
 * Wire field names follow the camelCase record components
 * (e.g. {@code openingBalance} → {@code opening_balance}, {@code balance}
 * → {@code balance}).
 */
public record DailyCashReport(
        String asOfDt,
        String currencyScope,
        List<SummaryRow> summary,
        List<HoldingRow> holdings,
        List<AccountLedgerRow> accounts
) {
    public record SummaryRow(
            String ccyCd,
            String cash,
            String current,
            String savings,
            String timeDeposit,
            String available
    ) { }

    public record HoldingRow(
            String id,
            String type,
            String ccyCd,
            String openingBalance,
            int numDebits,
            String debit,
            int numCredits,
            String credit,
            String balance
    ) { }

    public record AccountLedgerRow(
            String id,
            String bank,
            String acctNo,
            String ccyCd,
            int numDebits,
            String debit,
            int numCredits,
            String credit,
            String balance
    ) { }
}
