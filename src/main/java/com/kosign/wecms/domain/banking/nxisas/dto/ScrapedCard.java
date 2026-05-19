package com.kosign.wecms.domain.banking.nxisas.dto;

/**
 * One scraped card row returned to the frontend after a successful
 * CARD_INFO Coocon job. Wire format (lowercase snake_case via the
 * global Jackson naming strategy): {@code card_num}, {@code card_type},
 * etc.
 *
 * <p>Field set mirrors {@link com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasCardInfoEntry}
 * but as a wire-friendly record — frontend renders this verbatim in the
 * "Confirm Cards" popup. After the user ticks which cards to register,
 * the frontend POSTs the selected subset to
 * {@code POST /api/v1/payment/card-transactions/cards} where the
 * service maps each row into {@code CARD_MSTR}.
 *
 * <p>Field naming aligns with the {@code CreateCardsRequest.ScrapedCardItem}
 * shape so the frontend can pipe scrape-output rows straight into the
 * save request body (one-to-one field map, no client-side reshaping).
 */
public record ScrapedCard(
        String cardNo,
        String cardTypeCd,
        String cardNm,
        String cardHldrNm,
        String issuDt,
        String expiDt,
        String nxisasAcctType,
        String pymnAcctNo) {
}
