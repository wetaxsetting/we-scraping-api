package com.kosign.wecms.domain.payment.cardtransactions.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Inline-rename payload for the Card Management modal — legacy
 * {@code card_0001_003_u002}. Updates {@code CARD_HLDR_NM} and
 * {@code CARD_NICK_NM} together (the legacy U002 endpoint accepts
 * both in the same call even when only one was edited; the un-edited
 * value rides through unchanged from the row).
 *
 * <p>Per legacy validation, {@code card_hldr_nm} is required;
 * {@code card_nick_nm} is optional (the legacy commented-out validator
 * line for {@code CARD_NICK_NM} confirms this — see
 * {@code card_0001_003_u002_act.jsp:26}).
 */
public record UpdateCardInfoRequest(
        @NotBlank @Size(max = 50) String cardHldrNm,
        @Size(max = 50)           String cardNickNm) {
}
