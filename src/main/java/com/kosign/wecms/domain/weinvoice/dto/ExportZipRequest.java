package com.kosign.wecms.domain.weinvoice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * {@code POST /invoices/export/zip} — "Tải XML tất cả / đã chọn (n)" (UI Spec v1.1 §3.6).
 *
 * @param ids     the ticked invoices; empty / absent = every invoice under {@code filter} (UI Spec v1.1 §3.6)
 * @param filter  the list filter (its {@code company_mst} is replaced by {@code company_mst} of this body)
 * @param mode    {@code flat}, {@code by_month}, {@code by_partner}, {@code by_month_partner},
 *                {@code per_invoice}; blank = {@code flat}
 * @param include any of {@code xml}, {@code pdf}, {@code summary} (Excel list inside the ZIP);
 *                {@code rendered} is accepted and skipped — the rendered view is built in the browser
 */
public record ExportZipRequest(
        @NotBlank String companyMst,
        @Size(max = 20_000) List<String> ids,
        InvoiceFilter filter,
        String mode,
        List<String> include) {
}
