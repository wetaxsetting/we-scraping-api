package com.kosign.wecms.domain.setting.approvalline.dto;

import com.kosign.wecms.domain.setting.approvalline.ApprovalLineConstants;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Create-approval-line request body. Wire shape (snake_case):
 * <pre>{@code
 * {
 *   "appr_line_nm": "Approval Line A",
 *   "approvers": [
 *     { "apprvr_id": "u1@x", "apprvr_nm": "U1", "apprvr_posi": "...", "apprvr_dept": "..." },
 *     { "apprvr_id": "u2@x", "apprvr_nm": "U2", "apprvr_posi": "...", "apprvr_dept": "..." }
 *   ]
 * }
 * }</pre>
 *
 * <p>Bean validation guards: name 1-100 chars (DB column cap),
 * approvers list size 1..5 (legacy WBW1164 cap pinned in
 * {@link ApprovalLineConstants#MAX_APPROVERS}). Duplicate-approver
 * detection and tenant-link verification are service-layer checks.
 *
 * <p>{@code @Min(1)} on the list size enforces the legacy
 * {@code validateRecord(REC)} non-empty guard at
 * {@code comm_0222_002_c001_act.jsp:30}, surfaced as
 * {@code WBE_APPROVAL_LINE_EMPTY_APPROVERS}.
 */
public record ApprovalLineCreateRequest(
        @NotBlank @Size(max = 100)
        String apprLineNm,

        @NotNull
        @Size(min = 1, max = ApprovalLineConstants.MAX_APPROVERS)
        @Valid
        List<ApprovalLineApproverInput> approvers
) {}
