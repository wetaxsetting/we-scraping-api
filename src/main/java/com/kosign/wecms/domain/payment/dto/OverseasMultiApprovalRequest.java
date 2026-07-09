package com.kosign.wecms.domain.payment.dto;

import com.kosign.wecms.domain.payment.internaltransfer.dto.RequestApprovalRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Payment &gt; Overseas Payment &mdash; multi-payment ("M" type) Request
 * Approval body.
 *
 * <p>Mirrors the legacy {@code comm_0192_001_c005} {@code OVRS_PYMN_TYPE='M'}
 * branch ({@code createExptResolDetlForOverseas} + {@code updateOvrsPymnMstrData}),
 * which approves a <b>list</b> of {@code OVRS_PYMN_MSTR} rows (the legacy
 * REC4 set) under a single {@code EXPT_RESOL_MSTR}. The single-summary
 * ("E" type) path keeps its existing
 * {@code POST /overseas-payment/{id}/request-approval} endpoint; this DTO
 * backs the new {@code POST /overseas-payment/request-approval-multi}.
 *
 * <p>Per-payment {@code CCY_CD} / {@code AMT} come from each
 * {@code OVRS_PYMN_MSTR} row server-side (legacy REC4 carried
 * {@code TRNF_CCY_CD}/{@code TRNF_AMT}, sourced from the same rows) — the
 * wire only carries which payments to approve plus the shared approval
 * header.
 *
 * <p>Wire shape (lowercase snake_case):
 * <pre>{@code
 *   {
 *     "ovrs_pymn_nos": ["00000000000123", "00000000000124"],
 *     "appr_line_no":  4321,
 *     "post_title":    "Overseas batch Apr",
 *     "post_cont":     "...",
 *     "pay_date":      "20260415",
 *     "approvers":     [ { "apprvr_id": "u1", "apprvr_nm": "User One" } ]
 *   }
 * }</pre>
 */
public record OverseasMultiApprovalRequest(

        /** The {@code OVRS_PYMN_MSTR.OVRS_PYMN_NO} keys to approve together
         *  (legacy REC4 set). At least one entry; each must resolve to an
         *  un-approved master row in the caller's tenant. */
        @NotEmpty
        List<String> ovrsPymnNos,

        @NotNull
        Long apprLineNo,

        @Size(max = 200)
        String postTitle,

        @Size(max = 4000)
        String postCont,

        /** Optional {@code yyyyMMdd}; null / blank skips the column. */
        @Size(max = 8)
        String payDate,

        /** Approver chain snapshot — ordered by display order. Reuses the
         *  Internal Transfer {@code ApproverInput} shape (apprvr_id /
         *  apprvr_nm) shared across every Payment module. */
        @NotNull
        @Size(min = 1, max = 5)
        @Valid
        List<RequestApprovalRequest.ApproverInput> approvers) {
}
