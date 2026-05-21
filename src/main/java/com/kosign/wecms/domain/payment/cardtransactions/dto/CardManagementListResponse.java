package com.kosign.wecms.domain.payment.cardtransactions.dto;

import java.util.List;

/**
 * Envelope for the Card Management Setting modal list endpoint
 * ({@code GET /api/v1/payment/card-transactions/cards/management}).
 *
 * <p>Sprint 0 ships a flat envelope with the full filtered slice; the
 * legacy modal uses infinite-scroll pagination by bumping {@code PAGE_PER_CNT}
 * += 10 on bottom-reach. Typical tenants have under 15 cards (DEV-wide
 * count is 14 across all tenants), so a single up-front load capped at
 * 100 rows is cheaper than wiring real pagination. Add classic
 * {@code page} / {@code size} if a tenant grows past 100 cards.
 */
public record CardManagementListResponse(
        long                      totalCount,
        List<CardManagementRow>   rows) {
}
