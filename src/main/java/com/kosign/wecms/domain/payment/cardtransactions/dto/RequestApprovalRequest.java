package com.kosign.wecms.domain.payment.cardtransactions.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Payment &gt; Card Transactions &mdash; Request Approval batch
 * request. Mirrors legacy {@code card_0001_001.ts:661-676}
 * (`.RequestApproval` click handler) which posts the selected rows
 * + {@code APPR_DV='33'} (card-transaction approval type) to
 * {@code comm_0194_001.act}.
 *
 * <p>Wire shape (REC3 equivalent — one entry per selected card
 * transaction, carrying the per-card purpose / reporting-account / memo
 * the legacy approval popup posts):
 * <pre>{@code
 *   {
 *     "cards": [
 *       { "card_seq_no": "1234", "chart_acct_cd": "5001", "chart_acct_seq_no": "12",
 *         "busa_usag_cd": "6001", "memo": "Team lunch" },
 *       { "card_seq_no": "1235" }
 *     ],
 *     "appr_line_no": 4321,
 *     "post_title":   "Card expenses Apr",
 *     "post_cont":    "..."
 *   }
 * }</pre>
 *
 * <p>Validation:
 * <ul>
 *   <li>{@code cards} &mdash; at least one entry; each carries a
 *       {@code cardSeqNo}.</li>
 *   <li>{@code apprLineNo} &mdash; the {@code APPR_LINE_INFM.APPR_LINE_NO}
 *       primary key the user picked. Must resolve to an active
 *       approval line in the caller's tenant.</li>
 * </ul>
 *
 * <p>Legacy parity: mirrors {@code card_0001_005_c001} REC3 (per-card
 * {@code CCY_CD}/{@code AMT}/{@code TRSC_DT} come from the
 * {@code CARD_TRNS_HSTR} row server-side, not the wire — the legacy
 * popup re-sent the row's own values). The {@code per-card chart-acct /
 * usage / memo} are patched onto {@code CARD_TRNS_HSTR} and copied into
 * each {@code EXPT_RESOL_DETL} row at submit time (legacy
 * {@code updateCardTrnsHisData} + {@code createExptResolDetl}).
 */
public record RequestApprovalRequest(
        @NotEmpty
        @Valid
        List<CardApprovalItem> cards,
        @NotNull
        @Positive
        Long apprLineNo,
        /** Big title the user typed at the top of the Request Approval
         *  popup. Surfaces verbatim on the Approval Box list +
         *  Approval Details popup ({@code EXPT_RESOL_MSTR.TITL}).
         *  Mirrors the {@code post_title} field shipped by every other
         *  Payment module. */
        String postTitle,
        /** Free-form approval content / requestor note — the textarea
         *  in the Request Approval dialog's "Approval Content" section.
         *  Optional. Surfaces on the Approval Details popup
         *  ({@code EXPT_RESOL_MSTR.CONT}). */
        String postCont,
        /** Scheduled date picker on the Request Approval popup,
         *  {@code yyyyMMdd}. Optional. Surfaces on the Approval Box
         *  list + Approval Details popup
         *  ({@code EXPT_RESOL_MSTR.PAY_DATE}). Mirrors the
         *  {@code pay_date} field shipped by every other Payment
         *  module. */
        @Size(max = 8)
        String payDate) {

    /**
     * One selected card transaction + its per-card approval fields.
     * Mirrors a legacy REC3 row: {@code CARD_SEQ_NO} (which card),
     * {@code CHART_ACCT_CD}/{@code CHART_ACCT_SEQ_NO} (reporting
     * account), {@code BUSA_USAG_CD} (purpose), {@code MEMO}.
     */
    public record CardApprovalItem(
            @NotBlank
            @Size(max = 20)
            String cardSeqNo,

            @Size(max = 20)
            String chartAcctCd,

            @Size(max = 10)
            String chartAcctSeqNo,

            @Size(max = 20)
            String busaUsagCd,

            @Size(max = 1000)
            String memo) {}
}
