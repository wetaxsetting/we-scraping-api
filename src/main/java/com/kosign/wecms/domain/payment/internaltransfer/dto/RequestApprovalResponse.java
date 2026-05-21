package com.kosign.wecms.domain.payment.internaltransfer.dto;

/**
 * Payment "Request Approval" response.
 * Carries the newly-stamped {@code APPR_SEQ_NO} so the frontend can
 * pivot the row's UI state without a second fetch.
 *
 * <p>{@code docNo} is populated only by the overseas-payment flow
 * (format {@code VO_YYMMDD_NNN}); other payment types leave it null.
 *
 * <p>Wire shape: {@code { "appr_seq_no": "...", "doc_no": "..." }}
 * (snake_case via the global Jackson naming strategy; {@code doc_no}
 * is omitted when null via {@code @JsonInclude(NON_NULL)}).
 */
public record RequestApprovalResponse(
        String apprSeqNo,
        String docNo
) {}
