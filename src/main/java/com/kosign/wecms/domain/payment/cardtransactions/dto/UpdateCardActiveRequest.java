package com.kosign.wecms.domain.payment.cardtransactions.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request body for the Card Management toggle switch — legacy
 * {@code card_0001_003_u001}. Flips {@code ACTV_YN} on one card row.
 */
public record UpdateCardActiveRequest(
        @NotBlank @Pattern(regexp = "^[YN]$",
                message = "actv_yn must be 'Y' or 'N'")
        String actvYn) {
}
