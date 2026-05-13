package com.kosign.wecms.domain.payment.approvalbox.dto;

/**
 * Approve / Reject response — final state of the document after the
 * action commits. Frontend uses this to update the row badge without
 * a full list refetch.
 *
 * <ul>
 *   <li>{@link #apprStts} — derived state after the action
 *       ({@code P}/{@code I}/{@code C}/{@code R}).</li>
 *   <li>{@link #apprvrApprTotCnt} / {@link #apprvrTotCnt} — current
 *       progress (e.g. {@code 2/3}).</li>
 * </ul>
 */
public record ApprovalBoxActionResponse(
        String apprSeqNo,
        String apprStts,
        long   apprvrApprTotCnt,
        long   apprvrTotCnt
) { }
