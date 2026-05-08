package com.kosign.wecms.domain.banking.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TransferRow(
        @JsonProperty("SEQ_NO")        String seqNo,
        @JsonProperty("REG_DT")        String regDt,
        @JsonProperty("REG_TM")        String regTm,
        @JsonProperty("OUT_BANK_CD")   String outBankCd,
        @JsonProperty("OUT_BANK_NM")   String outBankNm,
        @JsonProperty("OUT_ACCT_NO")   String outAcctNo,
        @JsonProperty("OUT_CCY_CD")    String outCcyCd,
        @JsonProperty("IN_BANK_CD")    String inBankCd,
        @JsonProperty("IN_BANK_NM")    String inBankNm,
        @JsonProperty("IN_ACCT_NO")    String inAcctNo,
        @JsonProperty("IN_CCY_CD")     String inCcyCd,
        @JsonProperty("CCY_CD")        String ccyCd,
        @JsonProperty("TRSC_AMT")      String trscAmt,
        @JsonProperty("FEE")           String fee,
        @JsonProperty("RMRK")          String rmrk,
        @JsonProperty("TRNS_STTS")     String trnsStts,
        @JsonProperty("TRNS_MSG")      String trnsMsg,
        @JsonProperty("STATUS")        String status
) { }
