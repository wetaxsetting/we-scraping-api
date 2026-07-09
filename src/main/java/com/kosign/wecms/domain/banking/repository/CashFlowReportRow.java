package com.kosign.wecms.domain.banking.repository;

import java.math.BigDecimal;

/**
 * Native-query projection for one row of the legacy Cash Flow report DB
 * function {@code get_cashflow_report(p_biz_key, p_year, p_quarter,
 * p_base_currency, p_usd_rate, p_krw_rate, p_bank_filters)} — see
 * {@link CashFlowReportRepository}.
 *
 * <p>Column → getter mapping is by the quoted camelCase aliases in the
 * repository query (same convention as {@link DailyFinancialHsRow} /
 * {@link DailyCashAccountProjection}). The function emits rows in
 * <b>row-type-major</b> order (all Level-1, then all Level-2, …); the
 * service re-assembles them into the frontend's root-major order using
 * {@link #getGroupKey()} + {@link #getParentL2Code()} — see
 * {@code CashFlowReportService}.
 */
public interface CashFlowReportRow {

    Integer getSortOrder();

    String getDisplayCode();

    String getDisplayName();

    String getItemCode();

    String getItemName();

    /** Opening Balance | Level 1 | Level 2 | Level 3 | Level 1 Subtotal | Sales Profit | End Balance. */
    String getRowType();

    /** Root ACC code for Level 1/2/3/Subtotal; AAAAA-/ZZZZZ- sentinels for Opening/Profit/End. */
    String getGroupKey();

    /** The owning Level-2 code — populated on Level-3 rows only. */
    String getParentL2Code();

    BigDecimal getJanAmount();

    BigDecimal getFebAmount();

    BigDecimal getMarAmount();

    BigDecimal getAprAmount();

    BigDecimal getMayAmount();

    BigDecimal getJunAmount();

    BigDecimal getJulAmount();

    BigDecimal getAugAmount();

    BigDecimal getSepAmount();

    BigDecimal getOctAmount();

    BigDecimal getNovAmount();

    BigDecimal getDecAmount();

    BigDecimal getTotalAmount();

    String getDisplayCurrency();
}
