package com.kosign.wecms.domain.banking.nxisas.gateway.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Coocon CARD_USING job input — "fetch the transaction list for one
 * card over a date range". Mirrors legacy
 * {@code nxisas_cardTranList.ts:200-211} where the JEX frontend
 * builds:
 *
 * <pre>{@code
 *   { cardNum, cardType: "0", startDate, endDate }
 * }</pre>
 *
 * for every bank except {@code 11704020} (Woori), which sends
 * {@code { cardNum, startDate, endDate }} only (no cardType).
 *
 * <p>Wire field names mirror {@link NxisasTransactionJobInput} verbatim
 * — Coocon's adapter expects camelCase {@code cardNum} / {@code
 * cardType} / {@code startDate} / {@code endDate}. {@code startDate}
 * and {@code endDate} are {@code yyyyMMdd} strings (NOT ISO);
 * Coocon's adapter does not parse other formats.
 *
 * <p>{@code cardType} is the legacy card-type sub-discriminator —
 * {@code "0"} for the default (legacy parity with the
 * comm_0218 popup). Optional — omit / null for banks that don't
 * differentiate.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NxisasCardUsingJobInput {

    @JsonProperty("cardNum")    private String cardNum;
    @JsonProperty("cardType")   private String cardType;
    @JsonProperty("startDate")  private String startDate;
    @JsonProperty("endDate")    private String endDate;
}
