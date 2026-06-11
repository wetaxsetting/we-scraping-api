package com.kosign.wecms.domain.operation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Wire request for {@code POST /api/v1/addon/operation-status} — the
 * Inquire-popup "Save" action. Persists the GDT-looked-up registration
 * into {@code OPRT_STTS_INFO} (create-only; a duplicate
 * {@code (BIZ_KEY, TAXCODE)} returns {@code WBE_OPERATION_STATUS_DUPLICATE}).
 *
 * <p>Mirrors legacy {@code aosv_0001_002_c001_act.jsp} required-field set
 * (lines 27-33): {@code TAXCODE}, {@code COMPANYSTATUS}, {@code OFFICEADD},
 * {@code SEARCHDATETIME} must be present.
 *
 * <p>{@code REPRESENTATIVEFIRSTNAME} is in the legacy required list, but legacy
 * {@code ValidationHelper.validateRequired} only checks the key is present — an
 * empty string passes. GDT legitimately returns a blank representative for some
 * tax codes (e.g. ceased branches), and the legacy popup re-sends that blank on
 * Save, so the value must be allowed empty. It carries no {@code @NotBlank} here;
 * {@code companyNm} is not in the action's required check at all.
 *
 * <p>{@code taxCode} re-validates the same {@code ^\d{10}(-\d{3})?$}
 * contract the Inquire endpoint enforces (the frontend masks the input,
 * but the server is authoritative). {@code searchDatetime} is the GDT
 * "as-of" timestamp in {@code yyyyMMddHHmmss} form, echoed back from the
 * preceding Inquire response.
 *
 * <p>Tenant ({@code BIZ_KEY}) is resolved from {@code TenantContext},
 * never from the wire — same rule as every other tenant-scoped write.
 */
public record CreateOperationStatusRequest(
        @NotBlank
        @Pattern(regexp = "^\\d{10}(-\\d{3})?$",
                 message = "tax code must be 10 digits, optionally with -NNN branch")
        String taxCode,

        String companyNm,

        @NotBlank
        String companyStatus,

        @NotBlank
        String officeAdd,

        @NotBlank
        @Pattern(regexp = "^\\d{14}$",
                 message = "search datetime must be 14 digits (yyyyMMddHHmmss)")
        String searchDatetime,

        // No @NotBlank — GDT may return this blank; legacy allows it (see Javadoc).
        String representativeFirstName
) { }
