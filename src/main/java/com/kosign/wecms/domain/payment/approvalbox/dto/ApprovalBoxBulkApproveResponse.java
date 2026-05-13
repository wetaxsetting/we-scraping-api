package com.kosign.wecms.domain.payment.approvalbox.dto;

import java.util.List;

/**
 * Result of a bulk-approve action. Per-row success / failure is reported
 * separately so the frontend can render a precise breakdown (e.g.
 * "2 approved, 1 failed: not your turn").
 *
 * <ul>
 *   <li>{@link #successes} — final state for every row that committed
 *       successfully (same shape as {@link ApprovalBoxActionResponse}).</li>
 *   <li>{@link #failures} — per-row failure with the business code.</li>
 * </ul>
 */
public record ApprovalBoxBulkApproveResponse(
        List<ApprovalBoxActionResponse> successes,
        List<Failure>                   failures
) {
    public record Failure(String apprSeqNo, String code, String message) { }
}
