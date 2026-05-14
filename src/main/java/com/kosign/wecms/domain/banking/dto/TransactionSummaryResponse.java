package com.kosign.wecms.domain.banking.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Aggregate summary for the current Transaction History filter set —
 * count + confirmed/unconfirmed + in/out totals. Wire shape stays in
 * UPPER_SNAKE @JsonProperty form for parity with the sibling
 * {@link TransactionListResponse} (still pre-snake-case migration).
 */
public record TransactionSummaryResponse(
        @JsonProperty("TOTAL_COUNT")        int totalCount,
        @JsonProperty("CONFIRMED_COUNT")    int confirmedCount,
        @JsonProperty("UNCONFIRMED_COUNT")  int unconfirmedCount,
        @JsonProperty("DEPOSIT_TOTAL")      String depositTotal,
        @JsonProperty("WITHDRAW_TOTAL")     String withdrawTotal
) { }
