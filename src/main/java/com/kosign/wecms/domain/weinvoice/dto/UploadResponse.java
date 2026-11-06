package com.kosign.wecms.domain.weinvoice.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * An upload batch (screen 09) and the outcome of each file (UI Spec 3.2).
 *
 * @param status request status: {@code queued}, {@code running}, {@code done}, {@code partial},
 *               {@code failed}, {@code cancelled}
 */
public record UploadResponse(
        String uploadId,
        String companyMst,
        String requestId,
        String status,
        String error,
        String createdAt,
        List<FileItem> files) {

    /**
     * @param outcome   {@code null} while not processed, then {@code new}, {@code merged},
     *                  {@code duplicate}, {@code foreign} (MST not managed), {@code error},
     *                  {@code pdf_pending} (PDF waiting for its XML)
     * @param pdfStatus {@code has} / {@code missing} for the invoice the file landed on
     */
    public record FileItem(int fileNo, String name, long size, String outcome, String message, String invoiceId,
                           String sellerTaxId, String buyerTaxId, String partnerName, BigDecimal total,
                           String pdfStatus, String processedAt) {
    }
}
