package com.kosign.wecms.domain.banking.repository;

import java.math.BigDecimal;

/**
 * Native-query projection for one per-currency total-cash row returned by the
 * legacy DB function {@code rept_0900_001_rec4(p_biz_key, p_req_date,
 * p_req_date2)} (legacy WSVC {@code rept_0009_001_r001} REC4 — total cash).
 * {@code rec4} aggregates {@code rec3} by currency: {@code totl_cash} is the
 * sum of the per-currency closing cash balance. The service joins this onto
 * the {@code rec2} currency summary to populate {@code total_cash}.
 *
 * <p>Sourced from {@code CASH_REPY_MSTR} (via {@code rec3}); empty in the
 * R&amp;D DB, so {@code total_cash} resolves to zero there. Column → getter
 * binding is by the quoted camelCase aliases in
 * {@link DailyCashReportFunctionRepository#rec4}.
 */
public interface DailyCashRec4Row {
    String getCcyCd();
    BigDecimal getTotlCash();
}
