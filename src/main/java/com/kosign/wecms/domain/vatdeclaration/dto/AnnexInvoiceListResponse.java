package com.kosign.wecms.domain.vatdeclaration.dto;

import java.util.List;

/**
 * Wire response for {@code GET /api/v1/addon/vat-declarations/{sid}/annex/invoices}.
 *
 * <p>Flat paged list — pagination metadata follows the established shape
 * ({@code EInvoiceListResponse} / {@code ApprovalBoxListResponse}):
 * {@code total_count} is the unpaged filtered total, while
 * {@code page_number} / {@code page_size} / {@code total_pages} describe the
 * returned slice.
 *
 * <p>The former accordion grouping was dropped: every row still carries
 * {@code prod_nm}, so a client that wants the product accordion groups the page
 * itself.
 *
 * <p>{@code taxGroups} + {@code grandTotal} are the modal footer, folded into
 * this response (no separate /totals endpoint). Both are computed over the FULL
 * filtered set — one aggregate pass by VAT rate — so the section subtotals and
 * the "Total Output" row stay stable while the rows page. {@code totalCount}
 * and the grand total are derived from that same aggregate, so the footer and
 * the rows can never disagree. {@code taxGroups} is ordered into the fixed
 * 01-1/GTGT section sequence (KKKNT / 0% / 5% / 8% / 10% / KCT, unknown last);
 * {@code grandTotal.groupCd = "TOTAL"} and its {@code totalReducedAmt} is the
 * overall reduction (Resolution 204/2025).
 */
public record AnnexInvoiceListResponse(
        List<AnnexRow> rows,
        long totalCount,
        int totalPages,
        int pageNumber,
        int pageSize,
        List<AnnexTaxGroup> taxGroups,
        AnnexTaxGroup grandTotal
) {
}
