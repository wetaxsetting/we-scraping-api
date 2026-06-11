package com.kosign.wecms.domain.payment.match.dto;

/**
 * Result of an Unmatch-All operation over a batch (legacy
 * {@code comm_0197_001_u001}). {@code unmatchedCount} is the number of
 * previously-matched detail lines that were cleared.
 */
public record PaymentLineUnmatchAllResponse(
        String scdulPymnNo,
        int    unmatchedCount) {
}
