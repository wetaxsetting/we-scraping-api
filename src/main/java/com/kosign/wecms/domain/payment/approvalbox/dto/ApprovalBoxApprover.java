package com.kosign.wecms.domain.payment.approvalbox.dto;

/**
 * One approver step in the chain. Mirrors legacy {@code REC3} sub-record
 * from {@code WSVC.resl_0001_001_r001.xml}: ordered by
 * {@code APPRVR_LINE_NO ASC}, max 5 per document per business rule.
 *
 * <p>{@code apprvrStts} is one of {@code P}/{@code A}/{@code R}/{@code W}/
 * {@code S} (stable UPPERCASE identifier; frontend renders the
 * localised label via {@code approval_box.status.*} i18n keys).
 */
public record ApprovalBoxApprover(
        Integer apprvrLineNo,
        String  apprvrId,
        String  apprvrNm,
        String  apprvrStts,
        String  apprDtm
) { }
