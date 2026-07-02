package com.kosign.wecms.domain.payment.cardtransactions.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Edit-memo request body — replaces one memo's text. Mirrors legacy
 * {@code comm_0105_001_u002} (text-only update; color is row-level and
 * changed via {@link UpdateMemoColorRequest}). Author-only on the
 * service side ({@code WBE_MEMO_NOT_AUTHOR}).
 */
public record UpdateMemoRequest(
        /** New comment text, max 200 chars (legacy parity). */
        @NotBlank @Size(max = 200)
        String memo) {
}
