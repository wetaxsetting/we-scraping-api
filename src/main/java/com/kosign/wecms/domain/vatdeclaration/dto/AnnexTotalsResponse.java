package com.kosign.wecms.domain.vatdeclaration.dto;

import java.util.List;

/**
 * Annex totals payload — per-section subtotals plus one grand total
 * ("Total Output" row). Sourced live from the synced e-invoice tables.
 */
public record AnnexTotalsResponse(
        String annexType,
        List<AnnexTaxGroup> groups,
        AnnexTaxGroup grandTotal
) {
}
