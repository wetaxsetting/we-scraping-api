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
 * itself, and the per-group subtotals it used to receive were only ever
 * computed over the same page slice. No information is lost.
 *
 * <p>{@code grandTotalReducedAmt} is deliberately computed over the FULL
 * filtered set, not the page, so the footer total stays stable while paging.
 */
public record AnnexInvoiceListResponse(
        List<AnnexRow> rows,
        long totalCount,
        int totalPages,
        int pageNumber,
        int pageSize,
        String grandTotalReducedAmt
) {
}
