package com.kosign.wecms.domain.banking.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record TransactionListResponse(
        @JsonProperty("ROWS")           List<TransactionRow> rows,
        @JsonProperty("TOTAL_COUNT")    int totalCount,
        @JsonProperty("DEPOSIT_TOTAL")  String depositTotal,
        @JsonProperty("WITHDRAW_TOTAL") String withdrawTotal
) { }
