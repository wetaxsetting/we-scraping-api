package com.kosign.wecms.domain.weinvoice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * {@code POST /invoices/export/pdf-jobs} — "Tải PDF (n) ▾" (UI Spec v1.1 §3.12).
 *
 * @param companyMst working MST
 * @param ids        the ticked invoices; empty / absent = every invoice under {@code filter} (§3.6)
 * @param filter     the list filter (scope when nothing is ticked; also gives the period of the ZIP name)
 * @param mode       {@code merge} · {@code flat} · {@code rendered} · {@code pdf_xml} · {@code by_company} · {@code by_month}
 */
public record PdfJobRequest(
        @NotBlank String companyMst,
        @Size(max = 20_000) List<String> ids,
        InvoiceFilter filter,
        @NotBlank String mode) {
}
