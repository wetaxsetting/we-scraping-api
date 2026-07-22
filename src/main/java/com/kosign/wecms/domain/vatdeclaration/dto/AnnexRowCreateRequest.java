package com.kosign.wecms.domain.vatdeclaration.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Add a manual annex row. {@code annexType} = BKBR / BKMV / ALLOC / REDUCE8.
 * Amount fields are VND strings (nullable). Server sets {@code hand_reg_yn='Y'}.
 */
public record AnnexRowCreateRequest(
        @NotBlank String annexType,
        String ioDv,
        String invNo,
        String invSymbol,
        String invDt,
        String trcoTaxCode,
        String trcoNm,
        String prodNm,
        String splyAmt,
        String vatRate,
        String vatAmt,
        String reducedAmt,
        String allocRatio
) {
}
