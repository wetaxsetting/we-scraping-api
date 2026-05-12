package com.kosign.wecms.domain.setting.vasaccount.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Update-VAS-Account request body. Same shape as
 * {@link VasAccountCreateRequest} — the path segment {@code {vasAcctSeqNo}}
 * carries the row identity. The composite PK
 * {@code (BIZ_KEY, VAS_ACCT_SEQ_NO)} is immutable; only the data columns
 * change.
 *
 * <p>If {@code vas_acct_cd} differs from the loaded row's current value,
 * the service runs a duplicate-check against the new code (mirrors legacy
 * {@code comm_0217_004_u001_act.jsp:117-126}).
 */
public record VasAccountUpdateRequest(

        @NotBlank
        @Size(min = 3, max = 15)
        @Pattern(regexp = "^[A-Za-z0-9_]+$", message = "vas_acct_cd: must be alphanumeric or underscore")
        String vasAcctCd,

        @NotBlank
        @Size(max = 50)
        String vasAcctNm,

        @Size(max = 50)
        String vasAcctEnm,

        /** Optional from the wire — the Edit UI does not surface a
         *  Type selector. When blank/null the service preserves the
         *  existing entity value (no overwrite to null). */
        @Pattern(regexp = "^[012]?$", message = "vas_acct_type: must be 0, 1, or 2")
        String vasAcctType,

        @NotBlank
        @Pattern(regexp = "^[AI]$", message = "stts: must be A or I")
        String stts,

        @Size(max = 200)
        String cont

) { }
