package com.kosign.wecms.domain.banking.dto;

import java.util.List;

/**
 * Response envelope for {@code GET .../transactions/{trnsSeqNo}/match-candidates}.
 * Mirrors the legacy {@code comm_0196_002_r001} output.
 *
 * <p>Wire is lowercase snake_case via the global Jackson SNAKE_CASE strategy
 * ({@code rows} / {@code total_count}).
 */
public record MatchCandidateListResponse(
        List<MatchCandidateRow> rows,
        int totalCount
) { }
