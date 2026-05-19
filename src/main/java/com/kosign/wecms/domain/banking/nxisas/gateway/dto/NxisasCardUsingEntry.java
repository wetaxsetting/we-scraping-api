package com.kosign.wecms.domain.banking.nxisas.gateway.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One row of {@code NxisasCardUsingJobResult.tranList} — a single
 * card transaction returned by the Coocon CARD_USING job. Field
 * names mirror the Coocon adapter output for VN bank card scrapes
 * (verified against the legacy {@code _v2nxisas.callNxisas} output
 * shape).
 *
 * <p>Mapped onto {@code CARD_TRNS_HSTR} per the legacy
 * {@code fnsh_0001_001_c001} insert flow:
 *
 * <ul>
 *   <li>{@code tranDate}     → {@code TRSC_DT} (yyyyMMdd)</li>
 *   <li>{@code tranTime}     → {@code TRSC_TM} (HHmmss; optional)</li>
 *   <li>{@code merchant}     → {@code MERCH_NM}</li>
 *   <li>{@code amount}       → {@code TRSC_AMT}</li>
 *   <li>{@code currency}     → {@code CCY_CD}</li>
 *   <li>{@code inoutDv}      → {@code INOUT_DV} ("1" / "2")</li>
 *   <li>{@code authCode}     → {@code AUTH_CODE}</li>
 *   <li>{@code city}         → {@code CITY}</li>
 *   <li>{@code prchStts}     → {@code PRCH_STTS}</li>
 *   <li>{@code usingType}    → {@code USING_TYPE}</li>
 * </ul>
 *
 * <p>Banks that omit a field send {@code null} — the mapper writes
 * the entity column as {@code null} too (legacy parity).
 */
@Getter
@Setter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NxisasCardUsingEntry {

    @JsonProperty("tranDate")  private String tranDate;
    @JsonProperty("tranTime")  private String tranTime;
    @JsonProperty("merchant")  private String merchant;
    @JsonProperty("amount")    private String amount;
    @JsonProperty("currency")  private String currency;
    @JsonProperty("inoutDv")   private String inoutDv;
    @JsonProperty("authCode")  private String authCode;
    @JsonProperty("city")      private String city;
    @JsonProperty("prchStts")  private String prchStts;
    @JsonProperty("usingType") private String usingType;
    @JsonProperty("note")      private String note;
}
