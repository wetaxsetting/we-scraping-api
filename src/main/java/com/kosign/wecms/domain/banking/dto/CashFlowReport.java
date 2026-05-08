package com.kosign.wecms.domain.banking.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Cash Flow report — Sprint 1 STUB. Returns a deterministic seeded grid
 * matching the static-UI shape in
 * {@code components/banking/reports/cash-flow/types.ts}. Real BCS-batch
 * port is Sprint 2/3.
 */
public record CashFlowReport(
        @JsonProperty("MONTHS")          List<String> months,
        @JsonProperty("OPENING_BAL")     List<String> openingBalance,
        @JsonProperty("GROUPS")          List<Group> groups,
        @JsonProperty("SALES_PROFIT")    List<String> salesProfit,
        @JsonProperty("NON_SALES_PROFIT")List<String> nonSalesProfit,
        @JsonProperty("END_BAL")         List<String> endBalance
) {
    public record Group(
            @JsonProperty("ID")        String id,
            @JsonProperty("LABEL_KO")  String labelKo,
            @JsonProperty("LABEL_EN")  String labelEn,
            @JsonProperty("SUBGROUPS") List<Subgroup> subgroups,
            @JsonProperty("SUB_TOTALS")List<String> subTotals
    ) { }

    public record Subgroup(
            @JsonProperty("ID")       String id,
            @JsonProperty("LABEL_KO") String labelKo,
            @JsonProperty("LABEL_EN") String labelEn,
            @JsonProperty("DETAILS")  List<DetailRow> details
    ) { }

    public record DetailRow(
            @JsonProperty("ID")        String id,
            @JsonProperty("ITEM_CD")   String itemCd,
            @JsonProperty("ITEM_NM")   String itemNm,
            @JsonProperty("MONTHS")    List<String> months
    ) { }
}
