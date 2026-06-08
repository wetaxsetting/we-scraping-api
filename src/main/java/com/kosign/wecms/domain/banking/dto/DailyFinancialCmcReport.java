package com.kosign.wecms.domain.banking.dto;

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
 * <p>Wire is lowercase snake_case via the global Jackson SNAKE_CASE strategy.
 * Wire field names are derived from the camelCase record components
 * (e.g. {@code acctNickname} → {@code acct_nickname}, {@code balance} →
 * {@code balance}).
 */
public record DailyFinancialCmcReport(
        String asOfDt,
        String currencyScope,
        boolean activeAccountsOnly,
        boolean includeInternalTransfers,
        List<FinancialStatusRow> financialStatus,
        List<CurrentAccountRow> currentAccounts
) {
    public record FinancialStatusRow(
            String ccyCd,
            String total,
            String currentAccounts,
            String savingAccounts
    ) { }

    public record CurrentAccountRow(
            String id,
            String bank,
            String bankCd,
            String acctNo,
            String acctNickname,
            String ccyCd,
            String acctTypeCd,
            String actvYn,
            int numDebits,
            String debit,
            int numInternalDebits,
            String internalDebit,
            int numCredits,
            String credit,
            int numInternalCredits,
            String internalCredit,
            String balance,
            List<TransactionRow> transactions
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
            String acctSeqNo,
            String transactionTime,
            String amount,
            String balanceAfter,
            String inOutDiv,
            String remark,
            String rcvPayUsageCd,
            String rcvPayUsageNm
    ) { }
}
