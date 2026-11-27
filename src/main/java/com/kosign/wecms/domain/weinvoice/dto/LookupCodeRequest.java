package com.kosign.wecms.domain.weinvoice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code PUT /invoices/{id}/lookup-code}. {@code code} accepts the code or the whole lookup
 * link (UI Spec 3.4).
 */
public record LookupCodeRequest(
        @NotBlank @Size(max = 1000) String code,
        @Size(max = 100) String companyCode) {
}
