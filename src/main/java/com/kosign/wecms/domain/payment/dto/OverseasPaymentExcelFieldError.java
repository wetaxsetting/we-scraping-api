package com.kosign.wecms.domain.payment.dto;

/**
 * One per-field validation error on an overseas payment Excel preview row.
 *
 * <p>{@code field} matches the frontend {@code col.id} on the shared
 * {@code <ExcelUploadDialog>} so the cell can be highlighted verbatim.
 * Values: {@code "currency"}, {@code "amount"}, {@code "feePayer"},
 * {@code "beneficiaryCode"}, {@code "memo"}.
 *
 * <p>{@code message} is a short human-readable description of the
 * validation failure.
 */
public record OverseasPaymentExcelFieldError(
        /** Frontend col.id — camelCase, matches the preview table column key. */
        String field,
        /** Short validation failure description. */
        String message
) { }
