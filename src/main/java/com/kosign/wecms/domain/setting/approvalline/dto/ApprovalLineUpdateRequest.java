package com.kosign.wecms.domain.setting.approvalline.dto;

import com.kosign.wecms.domain.setting.approvalline.ApprovalLineConstants;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Update-approval-line request body. Same shape as
 * {@link ApprovalLineCreateRequest} &mdash; the PK
 * ({@code appr_line_no}) comes from the URL path.
 *
 * <p>Server-side diff-sync semantics: the service computes the set
 * difference between existing detail rows and incoming approvers,
 * UPDATEs rows present in both (with new {@code apprvr_disp_ord} +
 * snapshot fields), INSERTs net-new rows, and DELETEs net-removed
 * rows. Mirrors legacy {@code comm_0222_003_u001_act.jsp:104-194}.
 */
public record ApprovalLineUpdateRequest(
        @NotBlank @Size(max = 100)
        String apprLineNm,

        @NotNull
        @Size(min = 1, max = ApprovalLineConstants.MAX_APPROVERS)
        @Valid
        List<ApprovalLineApproverInput> approvers
) {}
