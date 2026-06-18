package com.kosign.wecms.domain.banking.dto;

/**
 * Per-currency roll-up of a completed Bank Transaction Sync batch.
 *
 * <p>The Account Inquiry &gt; Update popup groups its result block by
 * currency ("Bank Accounts (USD)", "Bank Accounts (KRW)", …). Each group
 * needs its <em>own</em> account tally and Credit / Debit transaction
 * counts + totals — not the batch-wide aggregate repeated under every
 * currency. This record carries one entry per distinct {@code CCY_CD}
 * touched by the scrape so the frontend renders each group from its own
 * numbers.
 *
 * <p>Amounts are strings (matching the rest of the banking wire) to keep
 * VND precision intact across the JS {@code Number} cast. {@code successCount}
 * counts <em>accounts</em> of this currency that synced; {@code creditCount}
 * / {@code debitCount} count <em>transactions</em>. {@code failedCount} is
 * {@code 0} on a {@code COMPLETED} response — the sync orchestrator is
 * fail-fast (any account-level failure returns {@code ERROR} before the
 * batch finalizes), so a COMPLETED batch means every queued account of
 * every currency synced.
 *
 * <p>Wire shape (global Jackson SNAKE_CASE): {@code ccy_cd},
 * {@code success_count}, {@code failed_count}, {@code credit_count},
 * {@code credit_total}, {@code debit_count}, {@code debit_total}.
 */
public record CurrencySummary(
        String ccyCd,
        int successCount,
        int failedCount,
        int creditCount,
        String creditTotal,
        int debitCount,
        String debitTotal
) { }
