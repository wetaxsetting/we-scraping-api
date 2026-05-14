package com.kosign.wecms.domain.banking.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Cash Flow report wire shape — refactor of legacy
 * {@code cash_0001_001_r001}. Translates the legacy nested WSVC OUT
 * (DISP_ORD, DISP_CD, DISP_NM, ITEM_CD, ITEM_NM, ROW_TYPE, GRP_CD,
 * JAN_AMT..DEC_AMT, TOTAL_AMT, DISP_CCY_CD, PARENT_L2_CD) to a flat list
 * of typed rows. Java field names stay camelCase; the global Jackson
 * SNAKE_CASE strategy converts them to lowercase snake_case on the wire.
 *
 * <p>The {@code rowType} value is preserved verbatim from the legacy
 * function output ({@code "Opening Balance"}, {@code "Level 1"},
 * {@code "Level 2"}, {@code "Level 3"}, {@code "Level 1 Subtotal"},
 * {@code "End Balance"}, {@code "Grand Total"}, {@code "SALES PROFIT"},
 * {@code "NON SALES PROFIT"}) so the frontend can branch on it without
 * remapping. Spec §A in the JEX citations.
 */
public record CashFlowReportResponse(
        int year,
        Integer quarter,
        String baseCurrency,
        BigDecimal usdRate,
        BigDecimal krwRate,
        List<CashFlowRow> rows
) {

    public record CashFlowRow(
            String displayOrder,
            String displayCd,
            String displayNm,
            String itemCd,
            String itemNm,
            String rowType,
            String groupCd,
            String parentL2Cd,
            String displayCurrency,
            MonthlyAmounts monthly,
            BigDecimal annualTotal
    ) { }

    public record MonthlyAmounts(
            BigDecimal jan,
            BigDecimal feb,
            BigDecimal mar,
            BigDecimal apr,
            BigDecimal may,
            BigDecimal jun,
            BigDecimal jul,
            BigDecimal aug,
            BigDecimal sep,
            BigDecimal oct,
            BigDecimal nov,
            BigDecimal dec
    ) {
        public static MonthlyAmounts zero() {
            BigDecimal z = BigDecimal.ZERO;
            return new MonthlyAmounts(z, z, z, z, z, z, z, z, z, z, z, z);
        }
    }
}
