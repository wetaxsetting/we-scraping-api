package com.kosign.wecms.domain.payment.cardtransactions.dto;

import java.util.List;

/**
 * Envelope returned by {@code GET /api/v1/payment/card-transactions}.
 * Carries page metadata, aggregate totals (one envelope replaces the
 * legacy two round-trips for list + counts), and the page slice of
 * ledger rows.
 */
public record CardTransactionListResponse(
        int                        page,
        int                        pageSize,
        long                       totalCount,
        CardTransactionTotals      totals,
        List<CardTransactionRow>   rows) {
}
