package com.kosign.wecms.domain.banking.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response envelope for {@code POST .../match} and {@code DELETE .../match}.
 * Echoes the canonical link state after the write so the frontend can
 * refresh its row chrome without a follow-up GET.
 */
public record MatchResultResponse(
        @JsonProperty("ACCT_SEQ_NO")    String acctSeqNo,
        @JsonProperty("LINK_MATCH_YN")  String linkMatchYn,
        @JsonProperty("PAY_TRNS_ID")    String payTrnsId,
        @JsonProperty("MATCH_LOG_SEQ")  String matchLogSeq
) { }
