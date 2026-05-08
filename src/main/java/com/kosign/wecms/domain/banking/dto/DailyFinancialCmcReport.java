package com.kosign.wecms.domain.banking.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Daily Financial CMC report payload — mirrors {@code rept_0015_001} and
 * the static-UI types in
 * {@code components/banking/reports/daily-financial/types.ts}.
 */
public record DailyFinancialCmcReport(
        @JsonProperty("AS_OF_DT")           String asOfDt,
        @JsonProperty("CURRENCY_SCOPE")     String currencyScope,
        @JsonProperty("FINANCIAL_STATUS")   List<FinancialStatusRow> financialStatus,
        @JsonProperty("CURRENT_ACCOUNTS")   List<CurrentAccountRow> currentAccounts
) {
    public record FinancialStatusRow(
            @JsonProperty("CCY_CD")           String ccyCd,
            @JsonProperty("TOTAL")            String total,
            @JsonProperty("CURRENT_ACCOUNTS") String currentAccounts,
            @JsonProperty("SAVING_ACCOUNTS")  String savingAccounts
    ) { }

    public record CurrentAccountRow(
            @JsonProperty("ID")                  String id,
            @JsonProperty("BANK")                String bank,
            @JsonProperty("ACCT_NO")             String acctNo,
            @JsonProperty("ACCT_NICK_NM")        String acctNickname,
            @JsonProperty("CCY_CD")              String ccyCd,
            @JsonProperty("NUM_DEBITS")          int numDebits,
            @JsonProperty("DEBIT")               String debit,
            @JsonProperty("NUM_INTERNAL_DEBITS") int numInternalDebits,
            @JsonProperty("INTERNAL_DEBIT")      String internalDebit,
            @JsonProperty("NUM_CREDITS")         int numCredits,
            @JsonProperty("CREDIT")              String credit,
            @JsonProperty("NUM_INTERNAL_CREDITS")int numInternalCredits,
            @JsonProperty("INTERNAL_CREDIT")     String internalCredit,
            @JsonProperty("BAL")                 String balance
    ) { }
}
