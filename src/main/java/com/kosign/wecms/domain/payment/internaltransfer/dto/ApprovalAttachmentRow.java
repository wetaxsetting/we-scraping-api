package com.kosign.wecms.domain.payment.internaltransfer.dto;

/**
 * One row in the {@code attachments[]} array on the
 * Scheduled-Transfer-detail response — surfaces approval-request
 * attachments registered against the batch's APPR_SEQ_NO so the
 * Approval Details popup can render the file chips alongside the
 * batch's original Excel upload.
 *
 * <p>Binary stream is fetched separately via
 * {@code GET /api/v1/payment/internal-transfer/{id}/approval/{apprSeqNo}/attachments/{fileId}};
 * this DTO only carries metadata.
 */
public record ApprovalAttachmentRow(
        /** Per-(biz_key, appr_seq_no) file sequence — used in the
         *  download URL path segment. */
        String fileId,
        /** User-supplied filename (preserved verbatim from the upload). */
        String orgFileNm,
        /** Decimal byte count (legacy storage shape — {@code String}).
         *  Null when the upload had no {@code Content-Length}. */
        String fileSize,
        /** Display name of the user who attached the file
         *  ({@code TenantContext.currentUserNm()} at insert time). */
        String regNm,
        /** {@code yyyyMMddHHmmss} when the attachment was registered. */
        String regDtm) { }
