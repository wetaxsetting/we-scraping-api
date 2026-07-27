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
        String  ovrsPymnDetlNo,
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
        /** Beneficiary town/city — display-only, read from the saved
         *  {@code OVRS_PYMN_SUMR_DETL.BNFC_TOWN_NM} (auto-filled from the
         *  SWIFT directory at Contacts time; no re-lookup here). Legacy
         *  {@code comm_0194_001} / {@code pymn_0007_004_r001} parity. */
        String  bnfcTownNm,
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
        /** Transaction sequence — {@code OVRS_PYMN_SUMR_DETL.PAY_TRAN_SEQ}
         *  (legacy R007 {@code PAY_FILE_SEQ_NO}, rendered in the Result detail
         *  "Transaction Seq" column). Null when the file sequence hasn't been
         *  assigned yet (e.g. M-mode rows, pre-dispatch). */
        Integer trnSeq
) {}
