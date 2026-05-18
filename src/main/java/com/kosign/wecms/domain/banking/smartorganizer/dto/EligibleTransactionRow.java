package com.kosign.wecms.domain.banking.smartorganizer.dto;

import java.math.BigDecimal;

/**
 * One row in the Smart Organizer eligibility list — a bank transaction
 * whose {@code RMRK} matched a {@code RMRK_ORG_INFM} rule and which is
 * not yet confirmed in {@code ACCT_TRNS_RCV_PAY_INFM}. Mirrors legacy
 * {@code IDO.ACCT_TRNS_HSTR_R028.xml} output projection.
 *
 * <p>The matched rule's classification targets ({@code chartAcctCd},
 * {@code useUsagCd}, {@code vasAcctSeqNo}, {@code trcoKey}) ride along
 * so the frontend can pre-populate the table cells without a second
 * round-trip. When the user confirms, those values flow back through
 * the {@link ConfirmRequest} bulk-save path.
 */
public record EligibleTransactionRow(
        String acctSeqNo,
        String bankCd,
        String acctNo,
        String trscDt,
        String trscTm,
        String inoutDv,
        BigDecimal inAmt,
        BigDecimal outAmt,
        String rmrk,

        /** Matched rule's surrogate — frontend uses it to look up the
         *  source rule if the user wants to edit before confirming. */
        Long matchedRmrkSeqNo,
        String chartAcctCd,
        String chartAcctSeqNo,
        String useUsagCd,
        Long vasAcctSeqNo,
        String trcoKey
) {
}
