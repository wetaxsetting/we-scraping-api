package com.kosign.wecms.domain.banking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Row-level color request body for Transaction History VND/FCY — sets the
 * sticker color for one bank transaction's whole memo thread. Mirrors
 * legacy {@code comm_0105_001_u003} ({@code MEMO_INFO_U002}): color-only
 * bulk update across the 4-key thread, no author gate. The
 * {@code REF_NO..REF_NO4} keys are derived server-side from the path.
 */
public record TransactionMemoColorRequest(
        @NotBlank
        @Pattern(regexp = "^(fileG|fileB|fileY|fileW|fileR|fileP)$",
                 message = "color must be one of: fileG, fileB, fileY, fileW, fileR, fileP")
        String color) {
}
