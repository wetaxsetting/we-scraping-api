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
        String  bnfcActNo,
        String  bnfcAddr,
        /** {@code "Y"} = transferred (success), {@code "N"} = pending. */
        String  trnfYn
) {}
