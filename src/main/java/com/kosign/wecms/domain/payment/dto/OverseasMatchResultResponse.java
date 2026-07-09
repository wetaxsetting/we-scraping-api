package com.kosign.wecms.domain.payment.dto;

/**
 * Result of a single Overseas payment-line match / unmatch operation
 * (legacy {@code comm_0197_001_c001} / {@code comm_0197_002_u001} isOversea).
 * {@code linkMatchYn} is {@code "Y"} after a match, {@code "N"} after an
 * unmatch. Mirrors the domestic {@code PaymentLineMatchResultResponse} with
 * the overseas key fields.
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
