package com.kosign.wecms.domain.vatdeclaration.dto;

/**
 * One sub-section total of an annex form — for BKBR the fixed 01-1/GTGT
 * sections (KKKNT / 0% / 5% / 8% / 10% / KCT); reused for the grand-total row
 * ({@code groupCd = "TOTAL"}). Amounts are integer-VND strings.
 */
public record AnnexTaxGroup(
        String groupCd,        // raw EINV_TAXRATE bucket, or "TOTAL" for the grand row
        String taxRateLabel,   // display label (same as groupCd for a rate; null on TOTAL)
        long rowCount,
        String totalSplyAmt,   // revenue / value excl. tax
        String totalReducedAmt, // 2-point reduction (8% lines only)
        String totalVatAmt     // VAT
) {
}
