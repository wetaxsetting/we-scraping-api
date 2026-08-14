package com.kosign.wecms.domain.vatdeclaration.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Create-period request. Wire {@code period_type} is {@code M}/{@code Q};
 * the service maps to the stored {@code month}/{@code quarter} values.
 * Tax-office fields are optional until the Company-Settings source lands.
 *
 * <p>{@code bsnsLineCd} is <b>required</b> ("Business Industry *" on the PO create
 * form, Dictionary {@code WEB_VAT_CRE_006}) but carries no {@code @NotBlank}: the
 * PO ships a dedicated message for it ({@code WEB_VAT_CRE_MSG_004}), so
 * {@code VatDeclarationService.create} throws
 * {@code WBE_VAT_DECL_BSNS_LINE_REQUIRED} instead of letting bean validation
 * collapse it into the generic {@code WBE_VALIDATION}.
 */
public record VatDeclarationCreateRequest(
        @NotNull @Pattern(regexp = "M|Q") String periodType,
        @NotNull Integer periodValue,
        @NotNull @Min(2000) @Max(2100) Integer declYear,
        String bsnsLineCd,
        String bsnsLineNm,
        String officeCode,
        String officeName,
        // Attached-annex checkboxes — one per prototype `.annexChk` (5 total).
        // 'Y'/'N'; null → service default (BKBR/BKMV → Y, the rest → N).
        String annexBkbrYn,
        String annexBkmvYn,
        /** PL 01-6/GTGT (CSSX) — production facility. */
        String annexAllocYn,
        /** PL 01-6/GTGT (VT cước trả sau) — post-paid telecom. */
        String annexAllocTeleYn,
        String annexReduce8Yn
) {
}
