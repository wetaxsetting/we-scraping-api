package com.kosign.wecms.domain.setting.approvalline.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * One approver in a Create / Update request body. Wire shape
 * (snake_case):
 * <pre>{@code
 * {
 *   "apprvr_id":   "user1@example.com",
 *   "apprvr_nm":   "User One",
 *   "apprvr_posi": "Manager",
 *   "apprvr_dept": "Finance"
 * }
 * }</pre>
 *
 * <p>{@code apprvr_disp_ord} is intentionally absent &mdash; the
 * service assigns 1..N from array position on every write (feature
 * § 5.2 / OQ-4 proposed 2026-05-13). Clients that send it have it
 * silently discarded.
 *
 * <p>{@code apprvr_posi} / {@code apprvr_dept} are optional. When
 * omitted (null or empty), the service persists {@code ""}
 * (never null) to match legacy parity at
 * {@code comm_0222_003_u001_act.jsp:144-148}.
 */
public record ApprovalLineApproverInput(
        @NotBlank @Size(max = 100) String apprvrId,
        @NotBlank @Size(max = 100) String apprvrNm,
        @Size(max = 100)           String apprvrPosi,
        @Size(max = 100)           String apprvrDept
) {}
