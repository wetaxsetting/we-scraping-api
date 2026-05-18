package com.kosign.wecms.domain.banking.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TransactionRow(
        @JsonProperty("ACCT_SEQ_NO")    String acctSeqNo,
        @JsonProperty("TRSC_DT")        String trscDt,
        @JsonProperty("TRSC_TM")        String trscTm,
        @JsonProperty("INOUT_DV")       String inoutDv,
        @JsonProperty("DEPOSIT_AMT")    String depositAmt,
        @JsonProperty("WITHDRAW_AMT")   String withdrawAmt,
        @JsonProperty("BAL")            String bal,
        @JsonProperty("RMRK")           String rmrk,
        @JsonProperty("CONF_YN")        String confYn,
        @JsonProperty("CONFIRMED_BY")   String confirmedBy,
        @JsonProperty("CONFIRMED_DTM")  String confirmedDtm,
        /** Smart Organizer match state — {@code 'Y'} when the row is
         *  matched to a payment in {@code BUSI_MATCH_LOG} (LOCKED_YN='Y'). */
        @JsonProperty("LINK_MATCH_YN")  String linkMatchYn,
        /** Reporting Account (chart of accounts) currently assigned to
         *  the bank transaction. Null when unassigned — the picker
         *  cell renders the "Select Account" placeholder. */
        @JsonProperty("CHART_ACCT_CD")     String chartAcctCd,
        @JsonProperty("CHART_ACCT_SEQ_NO") String chartAcctSeqNo,
        /** Purpose (USE_USAG_CD) currently assigned to this transaction.
         *  Lives on {@code ACCT_TRNS_RCV_PAY_INFM.RCV_PAY_USAG_CD}; null
         *  when unassigned — picker cell shows the "Select Purpose"
         *  placeholder. */
        @JsonProperty("USE_USAG_CD")       String useUsagCd,
        /** VAS Account currently assigned to this transaction.
         *  Lives on {@code ACCT_TRNS_RCV_PAY_INFM.VAS_ACCT_CD}; null
         *  when unassigned — picker cell shows the "Select" placeholder. */
        @JsonProperty("VAS_ACCT_CD")          String vasAcctCd,
        /** Contact key (RCV_PAY_TRCO_KEY) currently assigned to this
         *  transaction. Lives on
         *  {@code ACCT_TRNS_RCV_PAY_INFM.RCV_PAY_TRCO_KEY}. */
        @JsonProperty("RCV_PAY_TRCO_KEY")     String rcvPayTrcoKey,
        /** Contact display-name snapshot at pick time
         *  ({@code RCV_PAY_TRCO_NM}). */
        @JsonProperty("RCV_PAY_TRCO_NM")      String rcvPayTrcoNm
) { }
