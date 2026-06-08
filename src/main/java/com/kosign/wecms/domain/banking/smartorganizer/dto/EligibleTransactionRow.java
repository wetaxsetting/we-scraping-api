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
 *
 * <p><b>R06 (FCY) + R07 (display names) remediation (2026-06-06):</b>
 * the row now also carries the account currency ({@code ccyCd}), the
 * running balance ({@code bal}), the bank + nickname display, and the
 * resolved display names for the matched rule's classification targets
 * ({@code chartAcctNm}, {@code dispUsagNm}, {@code dispVasAcctNm},
 * {@code dispTrcoNm}) — the legacy R028 returned all of these
 * (R028.xml:54-83, 111-133). {@code chartAcctLevelNm} is reserved for
 * the full hierarchy breadcrumb (resolved as the leaf name for now —
 * see remediation plan R07 follow-up).
 */
public record EligibleTransactionRow(
        String acctSeqNo,
        String bankCd,
        String bankNm,
        String acctNo,
        String acctNickNm,
        String trscDt,
        String trscTm,
        String inoutDv,
        BigDecimal inAmt,
        BigDecimal outAmt,
        BigDecimal bal,
        String ccyCd,
        String rmrk,

        /** Matched rule's surrogate — frontend uses it to look up the
         *  source rule if the user wants to edit before confirming. */
        Long matchedRmrkSeqNo,
        String chartAcctCd,
        String chartAcctSeqNo,
        String chartAcctNm,
        String chartAcctLevelNm,
        String useUsagCd,
        String dispUsagNm,
        Long vasAcctSeqNo,
        String dispVasAcctNm,
        String trcoKey,
        String dispTrcoNm
) {
}
