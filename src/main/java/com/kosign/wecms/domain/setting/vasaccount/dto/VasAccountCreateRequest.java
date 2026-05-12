package com.kosign.wecms.domain.setting.vasaccount.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Create-VAS-Account request body. Bean-validation enforces shape;
 * the service runs the business-level duplicate check.
 *
 * <p>{@code biz_key} is intentionally absent — tenant comes from the
 * JWT-populated {@link com.kosign.wecms.common.tenant.TenantContext}.
 *
 * <p>{@code vas_acct_cd} adds a {@code @Pattern} alphanumeric guard on
 * top of the legacy length-only check; existing tenant data is all
 * alphanumeric and the frontend prefix-tree builder assumes it. Refactor
 * enhancement called out in
 * {@code docs/refactor-wecms/features/vas-account.md §4}.
 *
 * <p>{@code cont} caps at 200 (DB schema authority — DN-3 closed
 * 2026-05-11). Legacy WSVC's {@code length=500} reservation is treated
 * as over-allocation.
 */
public record VasAccountCreateRequest(

        @NotBlank
        @Size(min = 3, max = 15)
        @Pattern(regexp = "^[A-Za-z0-9_]+$", message = "vas_acct_cd: must be alphanumeric or underscore")
        String vasAcctCd,

        @NotBlank
        @Size(max = 50)
        String vasAcctNm,

        @Size(max = 50)
        String vasAcctEnm,

        /** Optional from the wire — the UI does not surface a Type
         *  selector. When blank/null on Create the service defaults to
         *  {@code "0"} (Dual). When blank/null on Update the service
         *  preserves the existing entity value. */
        @Pattern(regexp = "^[012]?$", message = "vas_acct_type: must be 0, 1, or 2")
        String vasAcctType,

        @NotBlank
        @Pattern(regexp = "^[AI]$", message = "stts: must be A or I")
        String stts,

        @Size(max = 200)
        String cont

) { }
