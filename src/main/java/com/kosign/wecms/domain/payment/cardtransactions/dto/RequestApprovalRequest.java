package com.kosign.wecms.domain.payment.cardtransactions.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

/**
 * Payment &gt; Card Transactions &mdash; Request Approval batch
 * request. Mirrors legacy {@code card_0001_001.ts:661-676}
 * (`.RequestApproval` click handler) which posts the selected rows
 * + {@code APPR_DV='33'} (card-transaction approval type) to
 * {@code comm_0194_001.act}.
 *
 * <p>Wire shape:
 * <pre>{@code
 *   {
 *     "card_seq_nos":  ["1234", "1235", "1236"],
 *     "appr_line_no":  4321
 *   }
 * }</pre>
 *
 * <p>Validation:
 * <ul>
 *   <li>{@code cardSeqNos} &mdash; at least one entry.</li>
 *   <li>{@code apprLineNo} &mdash; the {@code APPR_LINE_INFM.APPR_LINE_NO}
 *       primary key the user picked. Must resolve to an active
 *       approval line in the caller's tenant.</li>
 * </ul>
 */
public record RequestApprovalRequest(
        @NotEmpty
        List<String> cardSeqNos,
        @NotNull
        @Positive
        Long apprLineNo) {
}
