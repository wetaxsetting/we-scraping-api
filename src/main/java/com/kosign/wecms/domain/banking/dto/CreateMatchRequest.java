package com.kosign.wecms.domain.banking.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code POST .../transactions/{trnsSeqNo}/match}. The
 * frontend supplies the selected payment's {@code payTrnsId} (legacy
 * composite {@code SCDUL_PYMN_NO + "_" + SCDUL_PYMN_DETL_NO}) + the
 * optional user-entered memo.
 */
public record CreateMatchRequest(
        @NotBlank
        @Size(max = 50)
        @JsonProperty("PAY_TRNS_ID")  String payTrnsId,

        /** Always {@code 'S'} in the first refactor slice — overseas
         *  candidates are deferred. The field is validated server-side
         *  so a future {@code 'O'} client can ship without an API change. */
        @NotBlank
        @Size(max = 1)
        @JsonProperty("PAY_TYPE_CD")  String payTypeCd,

        @Size(max = 500)
        @JsonProperty("MEMO")         String memo
) { }
