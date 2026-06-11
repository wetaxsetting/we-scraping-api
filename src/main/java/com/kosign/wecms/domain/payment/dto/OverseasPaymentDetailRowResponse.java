package com.kosign.wecms.domain.payment.dto;

import java.math.BigDecimal;

/**
 * Per-line beneficiary row returned by the batch-detail read endpoint.
 * Separate from {@link OverseasPaymentExcelDetailRow} (upload DTO) so
 * read-side fields (e.g. {@code trnf_yn}) can be added without polluting
 * the upload validation contract.
 *
 * <p>Returned inside {@link OverseasPaymentDetailResponse#rows()}.
 */
public record OverseasPaymentDetailRowResponse(
        Integer trnSeq,
        String  bnfcCode,
        String  ccyCd,
        BigDecimal trnfAmt,
        String  feePayerCd,
        String  memo,
        String  rprtAcctCd,
        String  bnfcNm,
        String  bnfcBankNm,
        String  bicCd,
        String  bnfcAcctNo,
        String  bnfcAddr,
        /** {@code "Y"} = transferred (success), {@code "N"} = pending. */
        String  trnfYn,
        /** Phase 2 — dispatch result fields populated from
         *  {@code TB_PAY_FILE_MST} / {@code TB_PAY_FILE_DATA} via
         *  {@link com.kosign.wecms.domain.payment.dispatch.service.DispatchResultResolver#resolveOverseas}.
         *  All null until the bank-side daemon updates the dispatch
         *  rows; the frontend Result detail page renders the neutral
         *  state when null. */
        String  procStat,
        String  procStatKey,
        String  procDttm,
        String  rsltDttm,
        String  rsCd,
        String  rsMsg,
        /** OVR-G1 Phase B — per-line banking-match read state, sourced from
         *  {@code OVRS_PYMN_DTLS} (V248 columns). Additive + backward compatible:
         *  appended after the dispatch-result fields so older positional callers
         *  are unaffected. {@code linkMatchYn} is {@code "N"} until a match
         *  lands (Phase C+ write path); the other three are null when unmatched.
         *  Read-only — no match/unmatch logic is performed here. */
        String  linkMatchYn,
        String  acctSeqNo,
        String  bankTrnsShortId,
        String  bankTrnsFullId,
        /** OVR-G1 Phase D — per-line match IDENTITY (legacy
         *  {@code OVRS_PYMN_DETL_NO}). The Phase-C match endpoints are keyed on
         *  this, so the Result-Detail read path must expose it for the frontend
         *  to call match / unmatch. Additive + backward compatible (appended). */
        String  ovrsPymnDetlNo
) {}
