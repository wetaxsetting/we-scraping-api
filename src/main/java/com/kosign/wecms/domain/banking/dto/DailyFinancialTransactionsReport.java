package com.kosign.wecms.domain.banking.dto;

import java.util.List;

/**
 * Daily Financial "Transaction History Details" popup payload — mirrors
 * legacy {@code rept_0015_002_r001} (the popup that opens when the user
 * clicks the Bank / Account No. / Account Nickname cells in the Current
 * Accounts or Saving Accounts ledger on the parent {@code rept_0015_001}
 * page).
 *
 * <p>Server returns paginated transaction rows for one account on the
 * selected as-of date, plus totals across all pages (total count, total
 * credit, total debit, closing balance).
 *
 * <p>Wire is lowercase snake_case via the global Jackson SNAKE_CASE strategy.
 */
public record DailyFinancialTransactionsReport(
        long totCnt,
        String totInAmt,
        String totOutAmt,
        String totBal,
        int pageNo,
        int pagePerCnt,
        List<TransactionRow> rec
) {
    public record TransactionRow(
            String trscTm,
            String ccyCd,
            String trscAmt,
            String bal,
            String inoutDv,
            String rmrk
    ) { }
}
