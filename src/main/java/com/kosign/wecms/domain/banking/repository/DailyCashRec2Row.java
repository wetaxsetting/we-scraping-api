package com.kosign.wecms.domain.banking.repository;

import java.math.BigDecimal;

/**
 * Native-query projection for one per-currency summary row returned by the
 * legacy DB function {@code rept_0900_001_rec2(p_biz_key, p_req_date,
 * p_req_date2)} (legacy WSVC {@code rept_0009_001_r001} REC2). {@code rec2}
 * aggregates {@code rec1} by currency into the four bank-account buckets — it
 * does NOT carry cash (legacy cash totals come from {@code rec4}).
 *
 * <p>DB-confirmed bucket mapping (from the {@code rec2} body):
 * <ul>
 *   <li>{@code totl_norm_amt} — account types {@code 03},{@code 04} (demand / current)</li>
 *   <li>{@code totl_sav_amt}  — account types {@code 05},{@code 06} (savings)</li>
 *   <li>{@code totl_acct_amt} — account type {@code 02} (term / fixed deposit)</li>
 *   <li>{@code totl_loan_amt} — account type {@code 01} (loan)</li>
 * </ul>
 * Column → getter binding is by the quoted camelCase aliases in
 * {@link DailyCashReportFunctionRepository#rec2}.
 */
public interface DailyCashRec2Row {
    String getCcyCd();
    BigDecimal getTotlNormAmt();
    BigDecimal getTotlSavAmt();
    BigDecimal getTotlAcctAmt();
    BigDecimal getTotlLoanAmt();
}
