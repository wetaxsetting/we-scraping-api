package com.kosign.wecms.domain.vatdeclaration.dto;

import java.util.List;

/**
 * Annex invoice modal payload — page of rows grouped by product name;
 * {@code totalCount} + {@code grandTotalReducedAmt} span the full filtered set.
 */
public record AnnexInvoiceListResponse(
        List<AnnexInvoiceGroup> groups,
        long totalCount,
        String grandTotalReducedAmt
) {
}
