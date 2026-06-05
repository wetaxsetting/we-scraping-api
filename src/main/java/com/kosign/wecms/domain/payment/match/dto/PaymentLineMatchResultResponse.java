package com.kosign.wecms.domain.payment.match.dto;

/**
 * Result of a single domestic payment-line match / unmatch operation.
 * {@code linkMatchYn} is {@code "Y"} after a match, {@code "N"} after an
 * unmatch.
 */
public record PaymentLineMatchResultResponse(
        String scdulPymnNo,
        String scdulPymnDetlNo,
        String linkMatchYn,
        String acctSeqNo,
        String bankTrnsShortId,
        String bankTrnsFullId,
        String matchLogSeq) {
}
