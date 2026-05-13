package com.kosign.wecms.domain.payment.internaltransfer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for
 * {@code POST /api/v1/payment/internal-transfer/{id}/dispatch}.
 * Mirrors legacy {@code WSVC.pymn_0009_001_c001.xml:in}.
 *
 * <p>Carries the Transfer Currency the user chose on the detail screen
 * — drives the cut-off-time selection (NAPAS vs CITAD vs SWIFT) and the
 * downstream API-path vs File-path routing.
 */
public record DispatchRequest(

        @NotBlank
        @Size(min = 3, max = 3)
        @Pattern(regexp = "^[A-Z]{3}$",
                 message = "trnf_ccy_cd: must be a 3-letter ISO 4217 currency code")
        String trnfCcyCd) { }
