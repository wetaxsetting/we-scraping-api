package com.kosign.wecms.domain.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Wire body for {@code POST /api/v1/auth/login}:
 * <pre>{@code
 * {
 *   "access_token": "...",
 *   "token_type":   "Bearer",
 *   "expires_in":   3600,
 *   "user_id":      "...",
 *   "user_nm":      "...",
 *   "biz_key":      "...",
 *   "lang_cd":      "EN",
 *   "cmpn_nm":      "..."
 * }
 * }</pre>
 *
 * Most field names auto-derive from camelCase via the global
 * SNAKE_CASE Jackson naming strategy. {@code accessToken} is the
 * single explicit override — the wire name is {@code access_token}
 * (OAuth-style) rather than the strict snake of the Java field.
 */
public record LoginResponse(
        @JsonProperty("access_token") String accessToken,
        String tokenType,
        long   expiresIn,
        String userId,
        String userNm,
        String bizKey,
        String langCd,
        String cmpnNm
) { }
