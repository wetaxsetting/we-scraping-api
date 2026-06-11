package com.kosign.wecms.domain.banking.dto;

import java.util.List;

/**
 * Daily Financial HAENGSUNG variant — Sprint 1 STUB.
 *
 * Mirrors {@code rept_0017_001}; the production query joins ACCT_TRNS_HSTR
 * grouped by bank with sub-totals + TOTAL rows. Sprint 1 ships a fixed
 * seeded dataset (deterministic, sourced from V17 seed) until the real
 * ledger query lands in Sprint 2.
 *
 * <p>Wire is lowercase snake_case via the global Jackson SNAKE_CASE strategy.
 * Note the {@code balance} component serialises as {@code balance} (not
 * {@code bal}) since the wire name follows the camelCase field.
 */
public record DailyFinancialHsReport(
        String asOfDt,
        String bankCd,
        List<LedgerRow> rows
) {
    public record LedgerRow(
            String bankNm,
            String acctNo,
            String ccyCd,
            String rcvPayCd,
            String descriptionKo,
            String descriptionEn,
            String debit,
            String credit,
            String balance,
            String rowType  /* DETAIL | SUBTOTAL | TOTAL */
    ) { }
}
