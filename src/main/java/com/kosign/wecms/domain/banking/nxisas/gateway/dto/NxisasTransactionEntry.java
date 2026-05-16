package com.kosign.wecms.domain.banking.nxisas.gateway.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One row of {@link NxisasTransactionJobResult#getTransaction()}.
 * Mirrors legacy {@code kr.co.coocon.ibasews.cmo.TRANS_FOREIGN}
 * (which {@code TRANSACTION_FIXED_DEPOSIT} extends without adding
 * any fields).
 *
 * <p>String-typed wire fields — Coocon returns numerics as strings
 * with locale-specific separators; conversion to {@code BigDecimal}
 * happens at the consumer (mirrors legacy `NumberUtils.createBigDecimal`).
 *
 * <p>{@code debit} and {@code credit} are mutually exclusive on
 * a given row: a withdrawal populates {@code debit}, a deposit
 * populates {@code credit}. Sign convention varies per bank — legacy
 * checks {@code isNegative} on the value and flips the in/out flag
 * accordingly.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NxisasTransactionEntry {

    @JsonProperty("date")          private String date;          // yyyyMMdd
    @JsonProperty("time")          private String time;          // HHmmss
    @JsonProperty("debit")         private String debit;
    @JsonProperty("credit")        private String credit;
    @JsonProperty("balance")       private String balance;
    @JsonProperty("currency")      private String currency;
    @JsonProperty("remarks")       private String remarks;
    @JsonProperty("remarks2")      private String remarks2;
    @JsonProperty("transType")     private String transType;
    @JsonProperty("bankReference") private String bankReference;
}
