package com.kosign.wecms.domain.payment.cardtransactions.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Row-level color request body — sets the sticker color for a card
 * transaction's whole memo thread. Mirrors legacy
 * {@code comm_0105_001_u003} ({@code MEMO_INFO_U002}): color-only bulk
 * update by {@code (bizKey, webSvcId, refNo)}, no author gate. The
 * color is a property of the parent row, not the individual memo.
 */
public record UpdateMemoColorRequest(
        /** {@code CARD_TRNS_HSTR.CARD_SEQ_NO} whose memo thread is recolored. */
        @NotBlank @Size(max = 50)
        String refNo,
        /** Legacy sticker token — {@code fileG/fileB/fileY/fileW/fileR/fileP}. */
        @NotBlank
        @Pattern(regexp = "^(fileG|fileB|fileY|fileW|fileR|fileP)$",
                 message = "color must be one of: fileG, fileB, fileY, fileW, fileR, fileP")
        String color) {
}
