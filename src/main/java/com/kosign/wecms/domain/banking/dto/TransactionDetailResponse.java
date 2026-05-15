package com.kosign.wecms.domain.banking.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Single-transaction detail — extends {@link TransactionRow} with VAS account,
 * counterparty, and confirmation metadata. Wire shape kept in UPPER_SNAKE form
 * for parity with the sibling list endpoint.
 */
public record TransactionDetailResponse(
        @JsonProperty("ACCT_SEQ_NO")          String acctSeqNo,
        @JsonProperty("TRSC_DT")              String trscDt,
        @JsonProperty("TRSC_TM")              String trscTm,
        @JsonProperty("INOUT_DV")             String inoutDv,
        @JsonProperty("DEPOSIT_AMT")          String depositAmt,
        @JsonProperty("WITHDRAW_AMT")         String withdrawAmt,
        @JsonProperty("BAL")                  String bal,
        @JsonProperty("RMRK")                 String rmrk,
        @JsonProperty("RMRK2")                String rmrk2,
        @JsonProperty("BANK_REF_NO")          String bankRefNo,
        @JsonProperty("CCY_CD")               String ccyCd,
        @JsonProperty("CONF_YN")              String confYn,
        @JsonProperty("RCV_PAY_TRCO_KEY")     String rcvPayTrcoKey,
        @JsonProperty("RCV_PAY_TRCO_NM")      String rcvPayTrcoNm,
        @JsonProperty("RCV_PAY_USAG_CD")      String rcvPayUsagCd,
        @JsonProperty("VAS_ACCT_CD")          String vasAcctCd,
        @JsonProperty("CONFIRMED_BY_ID")      String confirmedById,
        @JsonProperty("CONFIRMED_BY_NM")      String confirmedByNm,
        @JsonProperty("CONFIRMED_DTM")        String confirmedDtm
) { }
