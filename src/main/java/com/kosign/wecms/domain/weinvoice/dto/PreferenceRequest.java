package com.kosign.wecms.domain.weinvoice.dto;

import jakarta.validation.constraints.Pattern;

import java.util.Map;

/** {@code PUT /me/preferences}; {@code null} fields keep their stored value. */
public record PreferenceRequest(
        @Pattern(regexp = "(?i)vi|en|ko", message = "must be vi, en or ko") String langCd,
        String defaultCompanyMst,
        Map<String, Object> shortcuts) {
}
