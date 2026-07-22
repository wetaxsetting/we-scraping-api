package com.kosign.wecms.domain.vatdeclaration.dto;

/** Per-annex-type totals, returned after add / delete of an annex row. */
public record AnnexSummary(
        String annexType,
        int rowCount,
        String totalSplyAmt,
        String totalReducedAmt,
        String totalVatAmt
) {
}
