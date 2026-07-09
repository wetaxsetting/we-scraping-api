package com.kosign.wecms.domain.banking.repository;

import java.math.BigDecimal;

/**
 * Native-query projection for one row of the HAENGSUNG daily-financial
 * ledger, returned by the legacy DB function
 * {@code fn_get_haengsung_daily_financial_report(p_biz_key, p_as_of_date,
 * p_account_filter)} (see {@link DailyFinancialHsReportRepository}).
 *
 * <p>Column → getter mapping is by the quoted camelCase aliases in the
 * repository query (same convention as {@link DailyCashAccountProjection}).
 * Amount columns surface as {@link BigDecimal} (nullable — the function
 * emits {@code NULL} for the amount cells on HEADER / TRANSACTION rows that
 * don't carry that figure); the service preserves null when projecting to
 * the wire so the frontend renders a blank cell rather than {@code "0"}.
 */
public interface DailyFinancialHsRow {

    /** HEADER | BEFORE_BALANCE | TRANSACTION | ACCOUNT_TOTAL | CURRENCY_TOTAL. */
    String getRowType();

    /** Composite bank/account label for HEADER; acct-no for BEFORE_BALANCE; "SUB TOTAL" / "". */
    String getBankAccount();

    String getCurrency();

    String getReptCd();

    String getPymnCd();

    String getDescriptionEnglish();

    String getDescriptionKorean();

    String getCustomerSupplier();

    BigDecimal getBeforeBalance();

    BigDecimal getReceipt();

    BigDecimal getPayment();

    BigDecimal getBalance();

    BigDecimal getBalanceCompanyActual();

    String getAccountNumber();

    Integer getAccountTypeOrder();

    String getAcctSeqNo();

    String getTransactionDate();

    String getTransactionTime();

    Integer getTransactionSeq();

    String getMemo();

    String getBankCdOut();

    String getBankNameSort();
}
