package com.kosign.wecms.domain.banking.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response envelope for {@code GET .../transactions/{trnsSeqNo}/match-candidates}.
 * Mirrors the legacy {@code comm_0196_002_r001} output.
 */
public record MatchCandidateListResponse(
        @JsonProperty("ROWS")        List<MatchCandidateRow> rows,
        @JsonProperty("TOTAL_COUNT") int totalCount
) { }
