package com.kosign.wecms.domain.setting.approvalline.dto;

/**
 * One approver in an approval line's chain. Wire shape (snake_case):
 * <pre>{@code
 * {
 *   "apprvr_disp_ord": 1,
 *   "apprvr_id":       "user1@example.com",
 *   "apprvr_nm":       "User One",
 *   "apprvr_posi":     "Manager",
 *   "apprvr_dept":     "Finance",
 *   "user_img_url":    null
 * }
 * }</pre>
 *
 * <p>{@code userImgUrl} is {@code null} when the approver has no
 * stored image; the frontend's existing {@code <Avatar>} fallback
 * renders an icon in that case. When present, the value is the
 * shared slug-based REST URI
 * {@code /api/v1/users/{apprvrId}/image} (the streaming endpoint
 * itself is out of scope for this MR &mdash; see feature § 4.5
 * &amp; OQ-1).
 */
public record ApprovalLineApproverRow(
        Integer apprvrDispOrd,
        String  apprvrId,
        String  apprvrNm,
        String  apprvrPosi,
        String  apprvrDept,
        String  userImgUrl
) {}
