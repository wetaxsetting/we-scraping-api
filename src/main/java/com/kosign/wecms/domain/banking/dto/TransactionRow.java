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
        @JsonProperty("LINK_MATCH_YN")  String linkMatchYn
) { }
