package com.kosign.wecms.domain.banking.dto;

/**
 * Response envelope for {@code POST .../match} and {@code DELETE .../match}.
 * Echoes the canonical link state after the write so the frontend can
 * refresh its row chrome without a follow-up GET.
 *
 * <p>Wire is lowercase snake_case via the global Jackson SNAKE_CASE strategy.
 */
public record MatchResultResponse(
        String acctSeqNo,
        String linkMatchYn,
        String payTrnsId,
        String matchLogSeq
) { }
