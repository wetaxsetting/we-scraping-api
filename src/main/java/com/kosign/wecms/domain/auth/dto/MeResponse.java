package com.kosign.wecms.domain.auth.dto;

/**
 * Current-session snapshot returned by {@code GET /api/v1/auth/me}.
 * Carries the same user + tenant identity fields as
 * {@link LoginResponse} minus the token envelope — the frontend calls this
 * on app load to rehydrate session state without having to trust
 * localStorage alone.
 *
 * Wire names auto-derived from camelCase Java fields via the global
 * SNAKE_CASE Jackson naming strategy.
 */
public record MeResponse(
        String userId,
        String userNm,
        String bizKey,
        String langCd,
        String cmpnNm
) { }
