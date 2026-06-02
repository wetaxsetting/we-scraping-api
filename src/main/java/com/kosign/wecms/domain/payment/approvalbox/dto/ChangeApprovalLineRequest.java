package com.kosign.wecms.domain.payment.approvalbox.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Body for {@code PUT /api/v1/payment/approval-box/{apprSeqNo}/approval-line}.
 *
 * <p>Carries the {@code APPR_LINE_NO} the user picked in the Approval
 * Details "Edit" inline picker. Wire field is {@code appr_line_no}
 * (global SNAKE_CASE strategy maps it from the camelCase record
 * component). Mirrors the card counterpart
 * {@code cardtransactions.dto.ChangeApprovalLineRequest} so the
 * non-card Edit flow shares one request shape across the Payment
 * modules.
 */
public record ChangeApprovalLineRequest(
        @NotNull(message = "appr_line_no: is required")
        @Positive(message = "appr_line_no: must be positive")
        Long apprLineNo) {
}
