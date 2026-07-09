package com.kosign.wecms.domain.payment.approvalbox.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Body of the Approval Details "Edit" save — the JSON {@code request} part of
 * the multipart {@code PUT …/{apprSeqNo}/edit}. Mirrors legacy
 * {@code comm_0192_013_u002} ({@code comm_0195_001.ts editApproval()}): one
 * call updates the title + approval content, rotates the approver line, adds
 * new attachment files (the multipart {@code attachments} parts), and deletes
 * removed attachment rows ({@link #deletedAttachments}).
 *
 * <p>Shared by the Approval Box and Corporate Card edit endpoints — the edit
 * surface lives entirely on the shared {@code EXPT_RESOL_MSTR} /
 * {@code EXPT_RESOL_APPRVR_INFM} / {@code EXPT_RESOL_FILE} tables, so there is
 * no card-specific shape (legacy parity — no card branch in u002).
 *
 * <p>Wire is lowercase snake_case (global Jackson strategy): {@code title},
 * {@code cont}, {@code appr_line_no}, {@code deleted_attachments[].file_id},
 * {@code deleted_attachments[].file_type}.
 */
public record EditApprovalRequest(

        /** New title → {@code EXPT_RESOL_MSTR.TITL} (legacy {@code POST_TITLE},
         *  {@code .text()} of {@code #approval_title}). Null leaves the title
         *  untouched. */
        @Size(max = 200, message = "title: max 200 chars")
        String title,

        /** New approval content → {@code EXPT_RESOL_MSTR.CONT} (legacy
         *  {@code POST_CONT}, {@code .html()} of {@code #approval_input}). Null
         *  leaves the content untouched. {@code CONT} is a {@code TEXT} column —
         *  no length cap. */
        String cont,

        /** Picked approval line — when present (and &gt; 0) the approver chain is
         *  replaced with this line's approvers and the master resets to Pending
         *  (legacy REC1/REC2 rebuild). Null leaves the existing chain in place. */
        @Positive(message = "appr_line_no: must be positive")
        Long apprLineNo,

        /** Existing attachments the user removed (legacy REC4). Each entry routes
         *  to the correct legacy table by {@link DeletedAttachment#fileType}. */
        @Valid
        List<DeletedAttachment> deletedAttachments) {

    /**
     * One attachment to delete — its per-(biz_key, appr_seq_no) file id plus the
     * source-table discriminator so the writer routes to {@code EXPT_RESOL_FILE}
     * (id column {@code FILE_ID}) or {@code EXPT_RESOL_PAY_EVID_FILE} (id column
     * {@code EVID_FILE_ID}). Mirrors the {@code file_id} + {@code file_type} the
     * attachment-list endpoint already hands the frontend.
     */
    public record DeletedAttachment(
            @NotBlank(message = "file_id: is required")
            String fileId,

            @Pattern(regexp = "^(EXPT_RESOL_FILE|EXPT_RESOL_PAY_EVID_FILE)$",
                     message = "file_type: invalid value")
            String fileType) {
    }
}
