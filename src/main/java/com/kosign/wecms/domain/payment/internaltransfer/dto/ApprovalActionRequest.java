package com.kosign.wecms.domain.payment.internaltransfer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Payment &gt; Internal Transfer — approve / reject the active approval
 * request on one batch. Posted by the
 * {@code <ApprovalDetailsDialog>} footer's Approve / Reject buttons
 * (legacy {@code comm_0195_001} lines 916-919 → {@code bltb_0001_003_u001}).
 *
 * <p>Phase A scope: master-level stamp only. The single endpoint mirrors
 * the legacy single-step contract — when the user clicks Approve / Reject
 * the master's {@code APPR_STTS} flips immediately to {@code "C"} /
 * {@code "X"}. The per-approver chain that produces intermediate
 * {@code "P"} / {@code "I"} states lands when the
 * {@code EXPT_RESOL_APPRVR_INFM} table is ported.
 *
 * <p>Wire shape: {@code { "action": "APPROVE" | "REJECT", "comment": "..." }}
 * — both fields snake_case via the global Jackson naming strategy (the
 * Java field {@code action} stays a single token because the snake
 * conversion is a no-op for single-segment camelCase). {@code comment}
 * is currently accepted-and-audited but not persisted on the master;
 * the BLTB_POST_INFM comment write lands with the per-approver chain
 * port.
 */
public record ApprovalActionRequest(
        @NotBlank
        @Pattern(regexp = "^(APPROVE|REJECT)$",
                 message = "action must be APPROVE or REJECT")
        String action,

        @Size(max = 1000)
        String comment
) {
    /** True when the action stamps {@code APPR_STTS = "C"}. */
    public boolean isApprove() { return "APPROVE".equals(action); }

    /** True when the action stamps {@code APPR_STTS = "X"}. */
    public boolean isReject()  { return "REJECT".equals(action); }
}
