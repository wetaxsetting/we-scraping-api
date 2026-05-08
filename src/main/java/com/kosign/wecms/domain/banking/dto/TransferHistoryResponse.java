package com.kosign.wecms.domain.banking.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record TransferHistoryResponse(
        @JsonProperty("ROWS")        List<TransferRow> rows,
        @JsonProperty("TOTAL_COUNT") int totalCount,
        @JsonProperty("TOTALS")      List<CurrencyTotal> totals
) {
    public record CurrencyTotal(
            @JsonProperty("CCY_CD") String ccyCd,
            @JsonProperty("COUNT")  int count,
            @JsonProperty("AMOUNT") String amount
    ) { }
}
