package com.kosign.wecms.domain.payment.internaltransfer.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Payment &gt; Internal Transfer &mdash; Change Approval Line request body.
 * Fires when the user picks a different approval line from the kebab
 * menu's "Edit" action on the Approval Details popup. Mirrors the Card
 * Transactions {@code ChangeApprovalLineRequest} shape so the
 * cross-module Approval Details handler can post the same body
 * regardless of which Payment module owns the batch.
 *
 * <p>Wire shape:
 * <pre>{@code
 *   { "appr_line_no": 4321 }
 * }</pre>
 */
public record ChangeApprovalLineRequest(
        @NotNull
        @Positive
        Long apprLineNo) {
}
