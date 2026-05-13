package com.kosign.wecms.domain.payment.approvalbox.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Bulk-approve request body. Carries the {@code APPR_SEQ_NO} of every
 * row the caller wants to approve in one batch. The upper bound mirrors
 * the page-size cap so a single browser action can never exceed one
 * page-worth of approvals.
 *
 * <p>Per-row failures are partial — see
 * {@link ApprovalBoxBulkApproveResponse}.
 */
public record ApprovalBoxBulkApproveRequest(

        @NotNull
        @NotEmpty
        @Size(max = 100)
        List<String> apprSeqNos

) { }
