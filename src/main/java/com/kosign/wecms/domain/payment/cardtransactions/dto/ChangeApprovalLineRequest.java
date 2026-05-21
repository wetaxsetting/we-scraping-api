package com.kosign.wecms.domain.payment.cardtransactions.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Payment &gt; Card Transactions — Change Approval Line request body.
 * Fires when the user picks a different approval line from the kebab
 * menu's "Edit" action on the Approval Details popup. Mirrors legacy
 * {@code comm_0195_001.ts:278} {@code editApproval()} payload (single
 * APPR_LINE_NO swap; the {@code APPR_SEQ_NO} is the path param).
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
