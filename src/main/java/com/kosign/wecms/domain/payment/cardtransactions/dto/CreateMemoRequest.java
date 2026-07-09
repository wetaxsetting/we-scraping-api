package com.kosign.wecms.domain.payment.cardtransactions.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Add Comment request body — one new {@code MEMO_INFM} row attached to
 * a card transaction. Mirrors legacy {@code comm_0105_001_c002} payload:
 * <ul>
 *   <li>{@code WEB_SVC_ID} is server-side ({@code "card_0001_001"}) —
 *       never wire-supplied.</li>
 *   <li>{@code REF_NO2..REF_NO9} stay NULL for Card Transactions
 *       (single-key parent — {@code CARD_SEQ_NO}).</li>
 *   <li>{@code color} uses the legacy {@code getMemoPopup2} sticker
 *       tokens ({@code fileG/fileB/fileY/fileW/fileR/fileP}) so the value
 *       round-trips with memos the live JEX system wrote for the same row.</li>
 * </ul>
 */
public record CreateMemoRequest(
        /** {@code CARD_TRNS_HSTR.CARD_SEQ_NO} the memo attaches to. */
        @NotBlank @Size(max = 50)
        String refNo,
        /** Free-text comment, max 200 chars (legacy parity). */
        @NotBlank @Size(max = 200)
        String memo,
        /** Optional color tag — legacy sticker token. Server defaults
         *  to {@code "fileW"} when blank. */
        @Pattern(regexp = "^(fileG|fileB|fileY|fileW|fileR|fileP)$",
                 message = "color must be one of: fileG, fileB, fileY, fileW, fileR, fileP")
        String color) {
}
