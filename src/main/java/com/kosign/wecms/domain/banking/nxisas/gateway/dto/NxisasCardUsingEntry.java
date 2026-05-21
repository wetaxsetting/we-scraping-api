package com.kosign.wecms.domain.banking.nxisas.gateway.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One row of {@code NxisasCardUsingCardArr.cardUsing} &mdash; a single
 * card transaction returned by the Coocon CARD_USING job. Field names
 * mirror the Coocon CMO class
 * {@code kr.co.coocon.ibasews.cmo.CARD_USING} verbatim (see legacy
 * {@code fnsh_0001_001_c001_act.jsp:1325-1410} for the canonical
 * read shape).
 *
 * <p>Mapped onto {@code CARD_TRNS_HSTR} per the legacy save flow:
 *
 * <ul>
 *   <li>{@code usingDate}      &rarr; {@code TRSC_DT} (yyyyMMdd)</li>
 *   <li>{@code usingTime}      &rarr; {@code TRSC_TM} (HHmmss; optional)</li>
 *   <li>{@code franchise}      &rarr; {@code MERCH_NM}</li>
 *   <li>{@code usingAmt}       &rarr; {@code TRSC_AMT}</li>
 *   <li>{@code currency}       &rarr; {@code CCY_CD}</li>
 *   <li>{@code authCode}       &rarr; {@code AUTH_CODE}</li>
 *   <li>{@code city}           &rarr; {@code CITY}</li>
 *   <li>{@code purchaseStatus} &rarr; {@code PRCH_STTS} (mapped per
 *       bank by {@code CardTransactionSyncService#mapPurchaseStatus})</li>
 *   <li>{@code usingType}      &rarr; {@code USING_TYPE}</li>
 *   <li>{@code cardName}       &rarr; {@code CARD_NM}</li>
 *   <li>{@code cardNum}        &rarr; cross-check against the requested
 *       card (defence-in-depth; the orchestrator already knows the
 *       card from the per-cert loop)</li>
 * </ul>
 *
 * <p>Coocon's CARD_USING response does NOT carry an {@code inoutDv}
 * field &mdash; card transactions are always debits, so the service
 * stamps {@code INOUT_DV='2'} unconditionally. Same for {@code note}
 * &mdash; Coocon doesn't return one, the column stays null.
 *
 * <p>Banks that omit any field send {@code null} &mdash; the mapper
 * writes the entity column as {@code null} too (legacy parity).
 */
@Getter
@Setter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NxisasCardUsingEntry {

    @JsonProperty("cardNum")        private String cardNum;
    @JsonProperty("cardName")       private String cardName;
    @JsonProperty("usingDate")      private String usingDate;
    @JsonProperty("usingTime")      private String usingTime;
    @JsonProperty("franchise")      private String franchise;
    @JsonProperty("usingAmt")       private String usingAmt;
    @JsonProperty("currency")       private String currency;
    @JsonProperty("authCode")       private String authCode;
    @JsonProperty("city")           private String city;
    @JsonProperty("purchaseStatus") private String purchaseStatus;
    @JsonProperty("usingType")      private String usingType;
}
