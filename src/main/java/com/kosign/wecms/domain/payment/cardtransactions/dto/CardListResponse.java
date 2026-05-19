package com.kosign.wecms.domain.payment.cardtransactions.dto;

import java.util.List;

/**
 * Envelope returned by
 * {@code GET /api/v1/payment/card-transactions/cards} — the All-Cards
 * dropdown for the filter bar. No pagination; legacy ledger expects the
 * full per-tenant set (typical row count single-digit per tenant per
 * the DEV stats: 14 rows across all tenants).
 */
public record CardListResponse(List<ActiveCard> cards) {
}
