package com.kosign.wecms.domain.payment.cardtransactions.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Add Comment request body — one new {@code MEMO_INFM} row attached to
 * a card transaction. Mirrors legacy {@code MEMO_INFM_C001} payload
 * with two simplifications:
 * <ul>
 *   <li>Refactor uses the slug {@code "card-transactions"} for
 *       {@code WEB_SVC_ID} (server-side default, never wire-supplied).</li>
 *   <li>{@code REF_NO2..REF_NO9} stay NULL for Card Transactions
 *       (single-key parent — {@code CARD_SEQ_NO}).</li>
 * </ul>
 */
public record CreateMemoRequest(
        /** {@code CARD_TRNS_HSTR.CARD_SEQ_NO} the memo attaches to. */
        @NotBlank @Size(max = 50)
        String refNo,
        /** Free-text comment, max 200 chars (legacy parity). */
        @NotBlank @Size(max = 200)
        String memo,
        /** Optional color tag — legacy {@code ColorCd}. Server defaults
         *  to {@code "white"} when blank. */
        @Pattern(regexp = "^(white|blue|green|yellow|orange|red|gray|purple)$",
                 message = "color must be one of: white, blue, green, yellow, orange, red, gray, purple")
        String color) {
}
