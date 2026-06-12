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
        String  rsMsg
) {}
