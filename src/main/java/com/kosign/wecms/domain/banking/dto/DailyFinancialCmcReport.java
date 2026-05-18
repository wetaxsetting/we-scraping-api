package com.kosign.wecms.domain.banking.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Daily Financial CMC report payload — mirrors {@code rept_0015_001} and
 * the static-UI types in
 * {@code components/banking/reports/daily-financial/types.ts}.
 *
 * <p>Per the {@code daily-financial-report} feature spec the report now
 * carries per-account transaction drill-down ({@link CurrentAccountRow#transactions})
 * on top of the legacy Financial Status header + Current Accounts ledger.
 * Recompute of the underlying summary tables
 * ({@code DAILY_FINCL_REPT_DAILY_SUMR_DETL} /
 * {@code DAILY_FINCL_REPT_DAILY_CASH_SUMR}) remains JEX-owned for now;
 * the refactor consumes the materialised rows.
 *
 * <p>The legacy WSVC contract carried a {@code CURRENCY_FLOW} rollup
 * (REC3) but the live JEX panel doesn't render it. The refactor drops the
 * field on both wire and frontend to match the production UI; if a future
 * consumer needs the rollup, the field can be re-added without a schema
 * change since it's a pure projection over {@code ACCT_MSTR}.
 *
 * <p>Wire is UPPER_SNAKE — Sprint 1 holdover until the matching frontend
 * types in {@code lib/types/api.ts} flip to lowercase per the CTO directive.
 */
public record DailyFinancialCmcReport(
        @JsonProperty("AS_OF_DT")               String asOfDt,
        @JsonProperty("CURRENCY_SCOPE")         String currencyScope,
        @JsonProperty("ACTIVE_ACCOUNTS_ONLY")   boolean activeAccountsOnly,
        @JsonProperty("INCLUDE_INTERNAL_TRANSFERS") boolean includeInternalTransfers,
        @JsonProperty("FINANCIAL_STATUS")       List<FinancialStatusRow> financialStatus,
        @JsonProperty("CURRENT_ACCOUNTS")       List<CurrentAccountRow> currentAccounts
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
            @JsonProperty("BANK_CD")             String bankCd,
            @JsonProperty("ACCT_NO")             String acctNo,
            @JsonProperty("ACCT_NICK_NM")        String acctNickname,
            @JsonProperty("CCY_CD")              String ccyCd,
            @JsonProperty("ACCT_TYPE_CD")        String acctTypeCd,
            @JsonProperty("ACTV_YN")             String actvYn,
            @JsonProperty("NUM_DEBITS")          int numDebits,
            @JsonProperty("DEBIT")               String debit,
            @JsonProperty("NUM_INTERNAL_DEBITS") int numInternalDebits,
            @JsonProperty("INTERNAL_DEBIT")      String internalDebit,
            @JsonProperty("NUM_CREDITS")         int numCredits,
            @JsonProperty("CREDIT")              String credit,
            @JsonProperty("NUM_INTERNAL_CREDITS")int numInternalCredits,
            @JsonProperty("INTERNAL_CREDIT")     String internalCredit,
            @JsonProperty("BAL")                 String balance,
            @JsonProperty("TRANSACTIONS")        List<TransactionRow> transactions
    ) { }

    /**
     * One per-account transaction row that powers the Daily Financial
     * drill-down. Mirrors {@code REC2_1} from
     * {@code WSVC.rept_0015_001_r001.xml}.
     *
     * <p>Skeleton: backend currently emits an empty list — Sprint 2 ports
     * the real {@code ACCT_TRNS_HSTR} read.
     */
    public record TransactionRow(
            @JsonProperty("ACCT_SEQ_NO")         String acctSeqNo,
            @JsonProperty("TRSC_TM")             String transactionTime,
            @JsonProperty("TRSC_AMT")            String amount,
            @JsonProperty("BAL")                 String balanceAfter,
            @JsonProperty("INOUT_DV")            String inOutDiv,
            @JsonProperty("RMRK")                String remark,
            @JsonProperty("RCV_PAY_USAG_CD")     String rcvPayUsageCd,
            @JsonProperty("RCV_PAY_USAG_NM")     String rcvPayUsageNm
    ) { }
}
