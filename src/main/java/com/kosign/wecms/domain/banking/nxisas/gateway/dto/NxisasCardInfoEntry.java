package com.kosign.wecms.domain.banking.nxisas.gateway.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One row of {@code NxisasCardInfoJobResult.cardList}. Field names
 * mirror the legacy {@code CardInfoOutput} DTO shape returned by
 * Coocon adapters (e.g. {@code GLOBAL/VNM/BANK_VCB} card scrape).
 *
 * <p>{@code cardNum} is the masked card number the bank's scrape
 * returns (e.g. {@code 4111-****-****-2308}); the user picks which
 * cards to register on the "Confirm Cards" popup and the frontend
 * POSTs the selected subset to
 * {@code POST /api/v1/payment/card-transactions/cards}, which writes
 * each row's {@code cardNum} verbatim into {@code CARD_MSTR.CARD_NO}.
 *
 * <p>{@code cardType} is the legacy card type code (e.g.
 * {@code 01} = credit, {@code 02} = debit) — stored into
 * {@code CARD_MSTR.CARD_TYPE_CD}. {@code issueDate} / {@code expireDate}
 * are {@code yyyyMMdd} strings; both optional (some banks don't return
 * issue date for older cards).
 *
 * <p>{@code holderName} / {@code cardName} / {@code paymentAcctNum}
 * are echo fields; the registration step writes them through to the
 * matching {@code CARD_MSTR} columns.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NxisasCardInfoEntry {

    @JsonProperty("cardNum")        private String cardNum;
    @JsonProperty("cardType")       private String cardType;
    @JsonProperty("cardName")       private String cardName;
    @JsonProperty("holderName")     private String holderName;
    @JsonProperty("issueDate")      private String issueDate;
    @JsonProperty("expireDate")     private String expireDate;
    @JsonProperty("paymentAcctNum") private String paymentAcctNum;
    @JsonProperty("acctType")       private String acctType;
}
