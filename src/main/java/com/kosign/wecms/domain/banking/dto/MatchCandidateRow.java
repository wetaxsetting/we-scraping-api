package com.kosign.wecms.domain.banking.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One row in the Smart Organizer candidate list — a payment transaction
 * that could match the bank transaction the popup is anchored on.
 *
 * <p>Mirrors the legacy {@code comm_0196_002_r001} record shape, scoped
 * to domestic scheduled payments in the first refactor slice (overseas
 * {@code OVRS_PYMN} is deferred per scope decision 2026-05-16).
 *
 * <p>{@code payTrnsId} is the composite identifier the match-create
 * endpoint uses to identify a candidate: legacy uses {@code
 * SCDUL_PYMN_NO + "_" + SCDUL_PYMN_DETL_NO} for domestic detail-level
 * matches. The frontend ships this verbatim in the match request.
 */
public record MatchCandidateRow(
        @JsonProperty("PAY_TRNS_ID")         String payTrnsId,
        /** {@code 'S'} = domestic scheduled (the only variant in the first slice). */
        @JsonProperty("PAY_TYPE_CD")         String payTypeCd,
        @JsonProperty("SCDUL_PYMN_NO")       String scdulPymnNo,
        @JsonProperty("SCDUL_PYMN_DETL_NO")  String scdulPymnDetlNo,
        @JsonProperty("SCDUL_PYMN_TYPE_CD")  String scdulPymnTypeCd,
        @JsonProperty("TRNF_DT")             String trnfDt,
        @JsonProperty("TRNF_DTM")            String trnfDtm,
        @JsonProperty("TRAN_AMT")            String tranAmt,
        @JsonProperty("TRNF_CCY_CD")         String trnfCcyCd,
        @JsonProperty("RCPT_NM")             String rcptNm,
        @JsonProperty("BNFC_BANK_CD")        String bnfcBankCd,
        @JsonProperty("BNFC_BANK_NM")        String bnfcBankNm,
        @JsonProperty("BNFC_ACCT_NO")        String bnfcAcctNo,
        @JsonProperty("TRAN_USAG")           String tranUsag,
        @JsonProperty("TITL")                String titl
) { }
