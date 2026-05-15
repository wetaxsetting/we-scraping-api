package com.kosign.wecms.domain.payment.dto;

import java.util.List;

/**
 * Response payload for {@code POST /api/v1/payment/overseas-payment/excel-preview}.
 *
 * <p>Contains all parsed rows (both valid and invalid) plus aggregate
 * counts so the frontend preview dialog can show the Completed / Error
 * summary chips without scanning the row list.
 */
public record OverseasPaymentExcelPreview(
        /** All parsed rows (valid + invalid). */
        List<OverseasPaymentExcelPreviewRow> rows,
        /** Total number of non-empty data rows parsed. */
        int totalCount,
        /** Rows with no validation errors. */
        int completedCount,
        /** Rows with at least one validation error. */
        int errorCount
) { }
