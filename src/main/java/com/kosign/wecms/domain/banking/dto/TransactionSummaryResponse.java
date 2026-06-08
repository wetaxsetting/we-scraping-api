package com.kosign.wecms.domain.banking.dto;

/**
 * Aggregate summary for the current Transaction History filter set —
 * count + confirmed/unconfirmed + in/out totals. Wire shape is lowercase
 * snake_case (global Jackson SNAKE_CASE strategy), matching the sibling
 * {@link TransactionListResponse}.
 */
public record TransactionSummaryResponse(
        int totalCount,
        int confirmedCount,
        int unconfirmedCount,
        String depositTotal,
        String withdrawTotal
) { }
