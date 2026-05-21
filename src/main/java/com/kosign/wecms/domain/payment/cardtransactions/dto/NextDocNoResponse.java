package com.kosign.wecms.domain.payment.cardtransactions.dto;

/**
 * Payment &gt; Card Transactions &mdash; preview of the next
 * {@code VO_YYMMDD_NNN} document number that will be assigned when
 * the user submits the Request Approval popup.
 *
 * <p>The value is a preview only &mdash; it is computed via
 * {@link com.kosign.wecms.domain.payment.approvalbox.service.ApprovalBoxFanoutService#nextDocNo}
 * without reserving the counter, so if another user submits a
 * Request Approval between this preview and the caller's own
 * submit, the actual assigned value will increment past this one.
 * The frontend uses this purely to populate the read-only
 * "Document No." field on the popup; the authoritative value
 * surfaces on the Approval Details popup after submit.
 *
 * <p>Card has no per-batch parent row (no {@code SCDUL_PYMN_NO}
 * like every other Payment module), so this preview is the only
 * way the Request Approval form can show a meaningful Document No.
 * before the row is persisted.
 */
public record NextDocNoResponse(String docNo) {
}
