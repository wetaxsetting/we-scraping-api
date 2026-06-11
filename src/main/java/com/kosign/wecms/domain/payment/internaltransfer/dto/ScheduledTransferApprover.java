package com.kosign.wecms.domain.payment.internaltransfer.dto;

/**
 * One approver in a Scheduled Transfer batch's approval chain
 * ({@code EXPT_RESOL_APPRVR_INFM}), in {@code APPRVR_LINE_NO} order.
 * Feeds the legacy {@code pymn_0009_001} approver-cell click popup
 * (Phase 2): name + per-approver status + approval datetime.
 *
 * <p>Java fields stay camelCase; Jackson's global {@code SNAKE_CASE}
 * strategy emits {@code apprvr_nm} / {@code apprvr_stts} / {@code appr_dtm}.
 *
 * @param apprvrNm   approver display name (legacy {@code APPRVR_NM}).
 * @param apprvrStts single-letter status (legacy {@code APPRVR_STTS}):
 *                   {@code P}=Pending, {@code A}=Approved, {@code R}=Rejected,
 *                   {@code W}=Waiting, {@code S}=Blocked/Skipped.
 * @param apprDtm    approval datetime ({@code yyyyMMddHHmmss}) — {@code null}
 *                   until the approver acts (the popup renders "-").
 */
public record ScheduledTransferApprover(
        String apprvrNm,
        String apprvrStts,
        String apprDtm) {
}
