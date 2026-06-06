package com.kosign.wecms.domain.payment.internaltransfer.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Payment &gt; Internal Transfer — "Request Approval" submission body.
 * Mirrors the legacy {@code comm_0192_001_c005} payload at
 * {@code WECMS DEV/.../comm_0194_001.ts:1443-1457}, narrowed to the
 * fields the refactor actually consumes today (the full per-leg
 * approval-line snapshot lands when {@code EXPT_RESOL_*} tables are
 * ported in a follow-up).
 *
 * <p>Wire shape (lowercase snake_case per the CTO directive 2026-05-08):
 * <ul>
 *   <li>{@code post_title} — required, user-facing approval title</li>
 *   <li>{@code post_cont}  — optional, free-text description</li>
 *   <li>{@code bank_cd}    — withdrawal bank code (required)</li>
 *   <li>{@code acct_no}    — withdrawal account number (required)</li>
 *   <li>{@code ccy_cd}     — withdrawal currency code (required)</li>
 *   <li>{@code pay_date}   — optional scheduled date {@code yyyyMMdd}</li>
 *   <li>{@code appr_line_no} — approval-line surrogate (required)</li>
 *   <li>{@code approvers}  — snapshot of {@code apprvr_id / apprvr_nm}
 *                            pairs ordered by display order. Sent verbatim
 *                            from the frontend's selected approval line;
 *                            the service trusts the chain (the chain came
 *                            from the same backend's
 *                            {@code GET /api/v1/settings/approval-line}).</li>
 *   <li>{@code files}      — optional uploaded-file metadata (the upload
 *                            itself goes through a separate file endpoint
 *                            today; this list carries the filenames /
 *                            sizes / save-paths the user attached).</li>
 * </ul>
 *
 * <p>JPA naming on the entity ({@code POST_TITLE}, {@code APPR_SEQ_NO},
 * etc.) stays UPPER_SNAKE — schema parity. JSON wire stays lowercase
 * snake_case via the global Jackson {@code SNAKE_CASE} strategy in
 * {@code JacksonConfig}.
 */
public record RequestApprovalRequest(
        @NotBlank
        @Size(max = 200)
        String postTitle,

        @Size(max = 4000)
        String postCont,

        @NotBlank
        @Size(max = 14)
        String bankCd,

        @NotBlank
        @Size(max = 20)
        String acctNo,

        @NotBlank
        @Size(max = 3)
        String ccyCd,

        /** Optional {@code yyyyMMdd}; null / blank skips the column. */
        @Size(max = 8)
        String payDate,

        /**
         * Optional Payroll Type (legacy {@code EXPT_RESOL_MSTR.APPR_FUND_TP}):
         * {@code "0"}=Salary, {@code "1"}=Bonus, {@code "9"}=Other. Sent only by
         * the Payroll Request Approval flow; the Vendor / Expense / Internal
         * Transfer / Overseas siblings omit it (Jackson maps the missing wire
         * key to null), so they remain unaffected. Persisted by
         * {@code PayrollPaymentService.requestApproval}; read back by the Payroll
         * Scheduled + Result lists.
         */
        @Size(max = 1)
        String apprFundTp,

        @NotNull
        Long apprLineNo,

        /** Approver chain snapshot — ordered by display order. */
        @NotNull
        @Size(min = 1, max = 5)
        @Valid
        List<ApproverInput> approvers,

        /** Optional attachment metadata. May be empty. */
        @Valid
        List<FileInput> files
) {

    public record ApproverInput(
            @NotBlank
            @Size(max = 100)
            String apprvrId,

            @NotBlank
            @Size(max = 100)
            String apprvrNm
    ) {}

    public record FileInput(
            @NotBlank
            @Size(max = 500)
            String orgFileNm,

            @NotBlank
            @Size(max = 500)
            String saveFileNm,

            Long fileSize
    ) {}
}
