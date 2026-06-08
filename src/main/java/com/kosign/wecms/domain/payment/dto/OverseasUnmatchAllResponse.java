package com.kosign.wecms.domain.payment.dto;

/**
 * Result of an Overseas Unmatch-All operation over a batch (legacy
 * {@code comm_0197_001_u001} {@code isOversea} path). {@code unmatchedCount}
 * is the number of previously-matched detail lines that were cleared.
 */
public record OverseasUnmatchAllResponse(
        String ovrsPymnNo,
        int    unmatchedCount) {
}
