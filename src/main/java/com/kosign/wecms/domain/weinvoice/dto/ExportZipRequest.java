package com.kosign.wecms.domain.weinvoice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * {@code POST /invoices/export/zip} (UI Spec 3.6).
 *
 * @param mode    {@code flat}, {@code by_month}, {@code by_partner}, {@code by_month_partner},
 *                {@code per_invoice}; blank = {@code flat}
 * @param include any of {@code xml}, {@code pdf}, {@code summary} (Excel list inside the ZIP);
 *                {@code rendered} is accepted and skipped — the rendered view is built in the browser
 */
public record ExportZipRequest(
        @NotBlank String companyMst,
        @NotEmpty @Size(max = 2000) List<String> ids,
        String mode,
        List<String> include) {
}
