package com.kosign.wecms.domain.payment.approvalbox.dto;

/**
 * One approver step in the chain. Mirrors legacy {@code REC3} sub-record
 * from {@code WSVC.resl_0001_001_r001.xml}: ordered by
 * {@code APPRVR_LINE_NO ASC}, max 5 per document per business rule.
 *
 * <p>{@code apprvrStts} is one of {@code P}/{@code A}/{@code R}/{@code W}/
 * {@code S} (stable UPPERCASE identifier; frontend renders the
 * localised label via {@code approval_box.status.*} i18n keys).
 *
 * <p>{@code userImgUrl} is {@code null} when the approver has no stored
 * image; otherwise it is the shared, non-admin REST URI
 * {@code /api/v1/users/{apprvrId}/image} (same shape the Approval Line
 * chain and notifications emit). The frontend wraps it in a JWT-bearing
 * Blob fetch and renders the shared {@code <UserAvatar>}, falling back
 * to the initials disc when null.
 */
public record ApprovalBoxApprover(
        Integer apprvrLineNo,
        String  apprvrId,
        String  apprvrNm,
        String  apprvrStts,
        String  apprDtm,
        String  userImgUrl
) { }
