package com.kosign.wecms.domain.weinvoice.dto;

import java.util.List;

/**
 * Result of {@code POST /invoices/{id}/files}.
 *
 * @param status    {@code ok} (stored), {@code needs_confirm} (nothing stored — resend with the
 *                  listed {@code confirm} values) or {@code duplicate} (the record already has
 *                  an XML, nothing stored)
 * @param reasons   why confirmation is needed: {@code pdf_not_matched} (confirm
 *                  {@code attach_anyway}), {@code pdf_exists} (confirm {@code replace}),
 *                  {@code xml_key_mismatch} (confirm {@code attach_anyway})
 * @param file      the stored file when {@code ok}
 * @param requestId engine import queued for an uploaded XML
 */
public record FileUploadResult(String status, List<String> reasons, InvoiceFileItem file, String requestId) {
}
