package com.kosign.wecms.domain.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Current-session snapshot returned by {@code GET /api/v1/auth/me}.
 * Carries the same user + tenant identity fields as
 * {@link LoginResponse} minus the token envelope — the frontend calls this
 * on app load to rehydrate session state without having to trust
 * localStorage alone.
 */
public record MeResponse(
        @JsonProperty("USER_ID") String userId,
        @JsonProperty("USER_NM") String userNm,
        @JsonProperty("BIZ_KEY") String bizKey,
        @JsonProperty("LANG_CD") String langCd,
        @JsonProperty("CMPN_NM") String cmpnNm
) { }
