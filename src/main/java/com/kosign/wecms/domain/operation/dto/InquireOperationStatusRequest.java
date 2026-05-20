package com.kosign.wecms.domain.operation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Wire request for {@code POST /api/v1/addon/operation-status/inquire}
 * (the "Get Business Info" popup).
 *
 * <p>Mirrors legacy {@code aosv_0001_002_r001} input — single Tax ID
 * lookup against the GDT business-registration API. Validation
 * regex matches the legacy guard at
 * {@code aosv_0001_002_r001_act.jsp:36} — 10 digits, optionally
 * followed by {@code -NNN} branch suffix.
 */
public record InquireOperationStatusRequest(
        @NotBlank
        @Pattern(regexp = "^\\d{10}(-\\d{3})?$",
                 message = "tax_code must be 10 digits, optionally with -NNN branch")
        String taxCode
) { }
