package com.kosign.wecms.domain.einvoice.dto;

import java.util.List;

/**
 * Wire response for {@code GET /api/v1/addon/einvoices}. Pagination
 * metadata follows the Account Inquiry shape — {@code total_count} is
 * the unpaged filtered total; {@code page_number} / {@code page_size}
 * / {@code total_pages} reflect the requested slice.
 *
 * <p>{@code totals[]} is computed from the FULL filtered dataset by
 * a sibling unpaged aggregation query (rule IR-6) so footer numbers
 * stay stable across pagination. Each row carries count + total /
 * tax / pretax sums for the currency.
 *
 * Wire names auto-derived from camelCase Java fields via the global
 * SNAKE_CASE Jackson naming strategy.
 */
public record EInvoiceListResponse(
        List<EInvoiceRow> rows,
        long totalCount,
        int totalPages,
        int pageNumber,
        int pageSize,
        List<CurrencyTotal> totals
) {
    public record CurrencyTotal(
            String ccyCd,
            int count,
            String amount,
            String taxAmount,
            String pretaxAmount
    ) { }
}
