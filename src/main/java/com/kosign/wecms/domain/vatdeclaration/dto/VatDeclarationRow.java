package com.kosign.wecms.domain.vatdeclaration.dto;

/**
 * One Dashboard row. Amount fields are integer-VND strings.
 * {@code taxAmount} = ct40 ("Tax Payable [40]"), {@code prevCredit} = ct22
 * ("Prev-period Credit [22]"), {@code carriedForward} = ct43
 * ("Carried Forward [43]").
 */
public record VatDeclarationRow(
        Long sid,
        String periodType,
        Integer periodValue,
        Integer declYear,
        String periodLabel,
        Integer supplNo,
        String declType,
        String applicableCircular,   // "80/2021/TT-BTC"
        String declTypeName,         // "01/GTGT" (form name for the "Declaration Type" column)
        String etaxCode,             // tax-authority e-transaction code; null until submitted (Phase 2)
        String status,
        String prevCredit,           // ct22
        String taxAmount,            // ct40
        String carriedForward,       // ct43
        String modDtm
) {
}
