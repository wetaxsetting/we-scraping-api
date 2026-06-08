package com.kosign.wecms.domain.banking.dto;

public record TransactionRow(
        String acctSeqNo,
        String trscDt,
        String trscTm,
        String inoutDv,
        String depositAmt,
        String withdrawAmt,
        String bal,
        String rmrk,
        String confYn,
        String confirmedBy,
        String confirmedDtm,
        /** Smart Organizer match state — {@code 'Y'} when the row is
         *  matched to a payment in {@code BUSI_MATCH_LOG} (LOCKED_YN='Y'). */
        String linkMatchYn,
        /** Reporting Account (chart of accounts) currently assigned to
         *  the bank transaction. Null when unassigned — the picker
         *  cell renders the "Select Account" placeholder. */
        String chartAcctCd,
        String chartAcctSeqNo,
        /** Purpose (USE_USAG_CD) currently assigned to this transaction.
         *  Lives on {@code ACCT_TRNS_RCV_PAY_INFM.RCV_PAY_USAG_CD}; null
         *  when unassigned — picker cell shows the "Select Purpose"
         *  placeholder. */
        String useUsagCd,
        /** VAS Account currently assigned to this transaction.
         *  Lives on {@code ACCT_TRNS_RCV_PAY_INFM.VAS_ACCT_CD}; null
         *  when unassigned — picker cell shows the "Select" placeholder. */
        String vasAcctCd,
        /** Contact key (RCV_PAY_TRCO_KEY) currently assigned to this
         *  transaction. Lives on
         *  {@code ACCT_TRNS_RCV_PAY_INFM.RCV_PAY_TRCO_KEY}. */
        String rcvPayTrcoKey,
        /** Contact display-name snapshot at pick time
         *  ({@code RCV_PAY_TRCO_NM}). */
        String rcvPayTrcoNm,
        /** Human-readable transaction id ({@code ACCT_TRNS_HSTR.BANK_TRNS_SHORT_ID})
         *  shown in the legacy on-screen grid's Transaction ID column.
         *  Audit BUG-2 — previously absent from the list row. */
        String bankTrnsShortId,
        /** Matched payment file name ({@code SCDUL_PYMN_SUMR.ORG_FILE_NM}),
         *  resolved via {@code BUSI_MATCH_LOG.PAY_TRNS_ID} for matched rows;
         *  null for unmatched rows. Audit BUG-2. */
        String payOrgFileNm,
        /** Match timestamp ({@code BUSI_MATCH_LOG.MATCHED_AT}, raw
         *  {@code yyyyMMddHHmmss}); null for unmatched rows. The frontend
         *  mapper formats it for display. Audit BUG-2. */
        String matchedAt,
        /** Matched-by user display name (resolved from
         *  {@code BUSI_MATCH_LOG.MATCHED_BY} via {@code USER_INFM}); null
         *  for unmatched rows. Audit BUG-2 (legacy shows USER_NM, not the id). */
        String matchedBy
) { }
