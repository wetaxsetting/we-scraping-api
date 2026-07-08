package com.kosign.wecms.domain.banking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Edit-memo request body for Transaction History VND/FCY — replaces one
 * memo's text. Mirrors legacy {@code comm_0105_001_u002} (text-only;
 * color is row-level and changed via {@link TransactionMemoColorRequest}).
 * Author-only on the service side ({@code WBE_MEMO_NOT_AUTHOR}).
 */
public record TransactionMemoUpdateRequest(
        @NotBlank @Size(max = 200)
        String memo) {
}
