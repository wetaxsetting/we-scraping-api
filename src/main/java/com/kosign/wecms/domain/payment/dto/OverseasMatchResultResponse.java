package com.kosign.wecms.domain.payment.dto;

/**
 * Result of a single Overseas payment-line match / unmatch operation.
 * {@code linkMatchYn} is {@code "Y"} after a match, {@code "N"} after an
 * unmatch. Mirrors the domestic {@code PaymentLineMatchResultResponse},
 * keyed per overseas line by {@code OVRS_PYMN_DETL_NO}.
 */
public record OverseasMatchResultResponse(
        String ovrsPymnNo,
        String ovrsPymnDetlNo,
        String linkMatchYn,
        String acctSeqNo,
        String bankTrnsShortId,
        String bankTrnsFullId,
        String matchLogSeq) {
}
