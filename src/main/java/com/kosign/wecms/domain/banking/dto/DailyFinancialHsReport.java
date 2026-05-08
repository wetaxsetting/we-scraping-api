package com.kosign.wecms.domain.banking.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Daily Financial HAENGSUNG variant — Sprint 1 STUB.
 *
 * Mirrors {@code rept_0017_001}; the production query joins ACCT_TRNS_HSTR
 * grouped by bank with sub-totals + TOTAL rows. Sprint 1 ships a fixed
 * seeded dataset (deterministic, sourced from V17 seed) until the real
 * ledger query lands in Sprint 2.
 */
public record DailyFinancialHsReport(
        @JsonProperty("AS_OF_DT") String asOfDt,
        @JsonProperty("BANK_CD")  String bankCd,
        @JsonProperty("ROWS")     List<LedgerRow> rows
) {
    public record LedgerRow(
            @JsonProperty("BANK_NM")        String bankNm,
            @JsonProperty("ACCT_NO")        String acctNo,
            @JsonProperty("CCY_CD")         String ccyCd,
            @JsonProperty("RCV_PAY_CD")     String rcvPayCd,
            @JsonProperty("DESCRIPTION_KO") String descriptionKo,
            @JsonProperty("DESCRIPTION_EN") String descriptionEn,
            @JsonProperty("DEBIT")          String debit,
            @JsonProperty("CREDIT")         String credit,
            @JsonProperty("BAL")            String balance,
            @JsonProperty("ROW_TYPE")       String rowType  /* DETAIL | SUBTOTAL | TOTAL */
    ) { }
}
