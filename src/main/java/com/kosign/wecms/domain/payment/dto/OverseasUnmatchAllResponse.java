package com.kosign.wecms.domain.payment.dto;

/**
 * Result of an overseas Unmatch-All operation over a batch (legacy
 * {@code comm_0197_001_u001} isOversea). {@code unmatchedCount} is the number
 * of previously-matched detail lines that were cleared.
 */
public record OverseasUnmatchAllResponse(
        String ovrsPymnNo,
        int    unmatchedCount) {
}
