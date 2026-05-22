package com.kosign.wecms.domain.payment.cardtransactions.dto;

import java.util.List;

/** Paged envelope for the Card-Link Receipt list endpoint. */
public record LinkableReceiptListResponse(
        long totalCount,
        List<LinkableReceiptRow> rows) {
}
