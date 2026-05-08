package com.kosign.wecms.domain.banking.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Daily Cash report payload — mirrors {@code rept_0009_001_r001} and the
 * static-UI types in {@code components/banking/reports/daily-cash/types.ts}.
 *
 * Sections:
 *   - {@code SUMMARY}  — one row per currency bucket (cash + current + savings + …).
 *   - {@code HOLDINGS} — physical cash holdings by type.
 *   - {@code ACCOUNTS} — per-bank-account ledger.
 */
public record DailyCashReport(
        @JsonProperty("AS_OF_DT")    String asOfDt,
        @JsonProperty("CURRENCY_SCOPE") String currencyScope,
        @JsonProperty("SUMMARY")     List<SummaryRow> summary,
        @JsonProperty("HOLDINGS")    List<HoldingRow> holdings,
        @JsonProperty("ACCOUNTS")    List<AccountLedgerRow> accounts
) {
    public record SummaryRow(
            @JsonProperty("CCY_CD")        String ccyCd,
            @JsonProperty("CASH")          String cash,
            @JsonProperty("CURRENT")       String current,
            @JsonProperty("SAVINGS")       String savings,
            @JsonProperty("TIME_DEPOSIT")  String timeDeposit,
            @JsonProperty("AVAILABLE")     String available
    ) { }

    public record HoldingRow(
            @JsonProperty("ID")             String id,
            @JsonProperty("TYPE")           String type,
            @JsonProperty("CCY_CD")         String ccyCd,
            @JsonProperty("OPENING_BAL")    String openingBalance,
            @JsonProperty("NUM_DEBITS")     int numDebits,
            @JsonProperty("DEBIT")          String debit,
            @JsonProperty("NUM_CREDITS")    int numCredits,
            @JsonProperty("CREDIT")         String credit,
            @JsonProperty("BAL")            String balance
    ) { }

    public record AccountLedgerRow(
            @JsonProperty("ID")             String id,
            @JsonProperty("BANK")           String bank,
            @JsonProperty("ACCT_NO")        String acctNo,
            @JsonProperty("CCY_CD")         String ccyCd,
            @JsonProperty("NUM_DEBITS")     int numDebits,
            @JsonProperty("DEBIT")          String debit,
            @JsonProperty("NUM_CREDITS")    int numCredits,
            @JsonProperty("CREDIT")         String credit,
            @JsonProperty("BAL")            String balance
    ) { }
}
