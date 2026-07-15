package com.kosign.wecms.domain.vatdeclaration.dto;

/**
 * One Dashboard row. {@code taxAmount} is indicator {@code ct40}
 * (the "Số tiền nộp" column).
 */
public record VatDeclarationRow(
        Long sid,
        String periodType,
        Integer periodValue,
        Integer declYear,
        String periodLabel,
        Integer supplNo,
        String declType,
        String status,
        String taxAmount,
        String modDtm
) {
}
