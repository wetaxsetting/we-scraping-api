package com.kosign.wecms.domain.payment.internaltransfer.dto;

/**
 * Payment &gt; Internal Transfer — "Request Approval" response.
 * Carries the newly-stamped {@code APPR_SEQ_NO} so the frontend can
 * pivot the row's UI state without a second fetch (Delete pill hides,
 * Transfer button gates on approval status, status badge appears).
 *
 * <p>Wire shape: {@code { "appr_seq_no": "..." }} (snake_case via the
 * global Jackson naming strategy).
 */
public record RequestApprovalResponse(
        String apprSeqNo
) {}
