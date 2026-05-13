package com.kosign.wecms.domain.auth.dto;

/**
 * One row in the tenant-users picker response. The Approval Line
 * "Add Approver" dropdown is the first consumer; future approval
 * features (per-payment-type approval-execution flows, signer
 * pickers) will reuse the same endpoint and DTO.
 *
 * <p>Mirrors the legacy {@code comm_0192_001_r001} {@code REC1}
 * sub-record (see
 * {@code docs/refactor-wecms/migration/approval-line-add-flow-analysis.md}
 * § 3.2), minus the two fields the Approval Line picker does not
 * consume:
 *
 * <ul>
 *   <li>{@code CLPH_NO} &mdash; legacy decrypts via the
 *       {@code decrypt()} DB function. Dropped to avoid a new
 *       legacy-function dependency; the Approval Line picker has no
 *       phone-number column anyway.</li>
 *   <li>{@code ADMIN_YN} / {@code MOB_YN} &mdash; sub-flags
 *       inherited from the broader {@code USER_BUSI_CONN_INFM_R004}
 *       IDO. Not consumed by the Approval Line picker.</li>
 * </ul>
 *
 * <p>{@code userImgUrl} is null when the underlying
 * {@code USER_BUSI_CONN_INFM.USER_IMG} is null. When present, the
 * value is the slug-based REST URI
 * {@code /api/v1/users/{userId}/image} &mdash; same shape the
 * Approval Line list endpoint surfaces (the streaming endpoint
 * itself is a follow-up MR; the frontend's {@code <Avatar>} fallback
 * tolerates 404).
 */
public record TenantUserRow(
        String userId,
        String userNm,
        String posiNm,
        String deptNm,
        String userImgUrl
) {}
