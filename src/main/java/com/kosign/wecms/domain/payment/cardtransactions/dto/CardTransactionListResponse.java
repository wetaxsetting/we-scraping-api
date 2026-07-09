package com.kosign.wecms.domain.payment.cardtransactions.dto;

import java.util.List;

/**
 * Envelope returned by {@code GET /api/v1/payment/card-transactions}.
 * Carries page metadata, aggregate totals (one envelope replaces the
 * legacy two round-trips for list + counts), the per-link-status tab
 * counts, and the page slice of ledger rows.
 *
 * <p>{@code totalCount} is the row count for the <b>current</b> filter
 * (including the active {@code link_yn} tab); {@code linkCounts} carries
 * the All / Linked / Unlinked breakdown computed <b>without</b> the tab
 * filter so the frontend can label all three tabs at once.
 */
public record CardTransactionListResponse(
        int                        page,
        int                        pageSize,
        long                       totalCount,
        CardTransactionTotals      totals,
        CardTransactionLinkCounts  linkCounts,
        List<CardTransactionRow>   rows) {
}
