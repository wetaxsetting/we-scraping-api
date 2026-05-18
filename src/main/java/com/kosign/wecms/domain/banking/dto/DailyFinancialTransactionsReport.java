package com.kosign.wecms.domain.banking.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

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
 * <p>Wire is UPPER_SNAKE — Sprint 1 holdover; flips to lowercase when
 * the matching frontend type in {@code lib/types/api.ts} flips per the
 * CTO directive.
 */
public record DailyFinancialTransactionsReport(
        @JsonProperty("TOT_CNT")     long totCnt,
        @JsonProperty("TOT_IN_AMT")  String totInAmt,
        @JsonProperty("TOT_OUT_AMT") String totOutAmt,
        @JsonProperty("TOT_BAL")     String totBal,
        @JsonProperty("PAGE_NO")     int pageNo,
        @JsonProperty("PAGE_PER_CNT") int pagePerCnt,
        @JsonProperty("REC")         List<TransactionRow> rec
) {
    public record TransactionRow(
            @JsonProperty("TRSC_TM")  String trscTm,
            @JsonProperty("CCY_CD")   String ccyCd,
            @JsonProperty("TRSC_AMT") String trscAmt,
            @JsonProperty("BAL")      String bal,
            @JsonProperty("INOUT_DV") String inoutDv,
            @JsonProperty("RMRK")     String rmrk
    ) { }
}
