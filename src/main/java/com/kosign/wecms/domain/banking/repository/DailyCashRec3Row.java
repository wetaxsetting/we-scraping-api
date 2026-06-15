package com.kosign.wecms.domain.banking.repository;

import java.math.BigDecimal;

/**
 * Native-query projection for one per-currency cash-flow row returned by the
 * legacy DB function {@code rept_0900_001_rec3(p_biz_key, p_req_date,
 * p_req_date2)} (legacy WSVC {@code rept_0009_001_r001} REC3 — cash detail).
 * {@code rec3} reads {@code CASH_REPY_MSTR}: {@code bef_date_bal} is the
 * opening balance (signed sum of cash movements strictly before the request
 * date), {@code bal} the closing balance (signed sum up to and including it),
 * and the send/receive counts/amounts are the same-day cash movements.
 *
 * <p>{@code CASH_REPY_MSTR} is empty in the R&amp;D DB, so this projection
 * returns no rows there — a data condition, not a code fault; the service maps
 * an empty result to an empty currency-flow list. Column → getter binding is
 * by the quoted camelCase aliases in
 * {@link DailyCashReportFunctionRepository#rec3}.
 */
public interface DailyCashRec3Row {
    String getCcyCd();
    BigDecimal getBefDateBal();
    BigDecimal getBal();
    BigDecimal getSndCnt();
    BigDecimal getSndAmt();
    BigDecimal getRcvCnt();
    BigDecimal getRcvAmt();
}
