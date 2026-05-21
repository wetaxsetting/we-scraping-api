package com.kosign.wecms.domain.payment.cardtransactions.dto;

/**
 * Payment &gt; Card Transactions &mdash; Request Approval batch
 * response.
 *
 * <ul>
 *   <li>{@code updated} &mdash; count of rows whose {@code APPR_SEQ_NO}
 *       got stamped this call.</li>
 *   <li>{@code skipped} &mdash; count of input rows that already
 *       carried an {@code APPR_SEQ_NO} (front-end guards against
 *       this but the server defends in depth).</li>
 *   <li>{@code apprSeqNo} &mdash; the assigned approval sequence
 *       number; same value lands on every updated row so the
 *       frontend can render a single "Approval Ref" badge per
 *       batch.</li>
 * </ul>
 */
public record RequestApprovalResponse(
        int    updated,
        int    skipped,
        String apprSeqNo) {
}
