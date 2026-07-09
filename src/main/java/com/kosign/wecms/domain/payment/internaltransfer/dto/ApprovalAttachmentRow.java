package com.kosign.wecms.domain.payment.internaltransfer.dto;

/**
 * One row in the {@code attachments[]} array on the Approval Details /
 * Scheduled-Transfer-detail response — surfaces approval attachments
 * registered against the batch's APPR_SEQ_NO so the Approval Details popup
 * can render the file chips.
 *
 * <p>The list is the UNION of two legacy tables (mirrors
 * {@code IDO.EXPT_RESOL_FILE_R002}): {@code EXPT_RESOL_FILE} (requester
 * uploads) and {@code EXPT_RESOL_PAY_EVID_FILE} (payment evidence). The
 * {@link #fileType} discriminator records which table the row came from so a
 * download can resolve the correct table (the two tables use different id
 * column names — {@code FILE_ID} vs {@code EVID_FILE_ID}).
 *
 * <p>Binary stream is fetched separately via the per-module attachment
 * download endpoint; this DTO only carries metadata.
 */
public record ApprovalAttachmentRow(
        /** Per-(biz_key, appr_seq_no) file sequence — used in the
         *  download URL path segment. For {@code EXPT_RESOL_FILE} this is
         *  {@code FILE_ID}; for {@code EXPT_RESOL_PAY_EVID_FILE} it is
         *  {@code EVID_FILE_ID}. Disambiguate with {@link #fileType}. */
        String fileId,
        /** User-supplied filename (preserved verbatim from the upload). */
        String orgFileNm,
        /** Decimal byte count (legacy storage shape — {@code String}).
         *  Null when the upload had no {@code Content-Length}. */
        String fileSize,
        /** Display name of the user who attached the file. */
        String regNm,
        /** {@code yyyyMMddHHmmss} when the attachment was registered. */
        String regDtm,
        /** Source table discriminator: {@link #TYPE_EXPT_RESOL_FILE} or
         *  {@link #TYPE_PAY_EVID_FILE}. The download endpoint reads this to
         *  pick the right legacy table + id column. */
        String fileType) {

    /** {@code fileType} value for a row sourced from {@code EXPT_RESOL_FILE}. */
    public static final String TYPE_EXPT_RESOL_FILE = "EXPT_RESOL_FILE";
    /** {@code fileType} value for a row sourced from {@code EXPT_RESOL_PAY_EVID_FILE}. */
    public static final String TYPE_PAY_EVID_FILE   = "EXPT_RESOL_PAY_EVID_FILE";

    /**
     * Backward-compatible 5-arg constructor — defaults {@code fileType} to
     * {@link #TYPE_EXPT_RESOL_FILE}. Retained so any caller that pre-dates
     * the GAP-ATT-1 change keeps compiling and keeps emitting the original
     * (requester-upload) source.
     */
    public ApprovalAttachmentRow(String fileId, String orgFileNm, String fileSize,
                                 String regNm, String regDtm) {
        this(fileId, orgFileNm, fileSize, regNm, regDtm, TYPE_EXPT_RESOL_FILE);
    }
}
