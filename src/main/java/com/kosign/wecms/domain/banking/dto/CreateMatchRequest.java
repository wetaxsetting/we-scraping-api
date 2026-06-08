package com.kosign.wecms.domain.banking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code POST .../transactions/{trnsSeqNo}/match}. The
 * frontend supplies the selected payment's {@code payTrnsId} (legacy
 * composite {@code SCDUL_PYMN_NO + "_" + SCDUL_PYMN_DETL_NO}) + the
 * optional user-entered memo.
 *
 * <p>Wire is lowercase snake_case via the global Jackson SNAKE_CASE strategy
 * (inbound {@code pay_trns_id} / {@code pay_type_cd} / {@code memo}).
 */
public record CreateMatchRequest(
        @NotBlank
        @Size(max = 50)
        String payTrnsId,

        /** Always {@code 'S'} in the first refactor slice — overseas
         *  candidates are deferred. The field is validated server-side
         *  so a future {@code 'O'} client can ship without an API change. */
        @NotBlank
        @Size(max = 1)
        String payTypeCd,

        @Size(max = 500)
        String memo
) { }
