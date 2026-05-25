package com.kosign.wecms.domain.payment.cardtransactions.dto;

import java.util.List;

/**
 * Paged envelope for the Card-Link Expenses e-Invoice list endpoint.
 * Mirrors the lightweight envelope every other paged list on the Card
 * Transactions service uses ({@code total_count} + a flat {@code rows}
 * array — no Spring Data {@code Page} surface leaks onto the wire).
 */
public record LinkableEInvoiceListResponse(
        long totalCount,
        List<LinkableEInvoiceRow> rows) {
}
