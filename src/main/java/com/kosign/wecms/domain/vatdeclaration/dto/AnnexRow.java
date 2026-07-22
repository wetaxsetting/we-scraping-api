package com.kosign.wecms.domain.vatdeclaration.dto;

/**
 * One annex line ({@code TAX_DECLARATION_ANNEX_ITEMS}). Amount fields are
 * VND strings; {@code allocRatio} is ALLOC-only, {@code reducedAmt} REDUCE8-only.
 */
public record AnnexRow(
        Long id,
        String annexType,
        String ioDv,
        String srcDocId,
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
        String allocRatio,
        String handRegYn
) {
}
