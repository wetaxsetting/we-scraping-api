package com.kosign.wecms.domain.payment.cardtransactions.dto;

import java.util.List;

/**
 * Paged envelope for {@code GET /api/v1/payment/card-transactions/receipts}
 * (the caller's own receipts — mobile receipt channel).
 *
 * <p>Mirrors {@link LinkableReceiptListResponse}'s shape so the two receipt
 * lists page identically on the client.
 */
public record OwnReceiptListResponse(
        long totalCount,
        List<OwnReceiptRow> rows) {
}
