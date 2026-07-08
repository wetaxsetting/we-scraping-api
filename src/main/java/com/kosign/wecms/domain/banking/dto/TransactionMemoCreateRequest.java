package com.kosign.wecms.domain.banking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Add-memo request body for Transaction History VND/FCY. Mirrors legacy
 * {@code comm_0105_001_c002}. The 4-key {@code REF_NO..REF_NO4}
 * ({@code BANK_CD / ACCT_NO / TRSC_DT / ACCT_SEQ_NO}) and the
 * {@code WEB_SVC_ID} are derived server-side from the path
 * ({@code acctSeqNo} + {@code trnsSeqNo}) + the {@code scope} param —
 * never wire-supplied. Only the memo text and optional sticker color
 * come from the client.
 */
public record TransactionMemoCreateRequest(
        /** Free-text comment, max 200 chars (legacy parity). */
        @NotBlank @Size(max = 200)
        String memo,
        /** Optional color tag — legacy sticker token. Server defaults to
         *  {@code fileW} when blank. */
        @Pattern(regexp = "^(fileG|fileB|fileY|fileW|fileR|fileP)$",
                 message = "color must be one of: fileG, fileB, fileY, fileW, fileR, fileP")
        String color) {
}
