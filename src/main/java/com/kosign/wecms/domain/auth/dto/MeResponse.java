package com.kosign.wecms.domain.auth.dto;

/**
 * Current-session snapshot returned by {@code GET /api/v1/auth/me}.
 * Carries the same user + tenant identity fields as
 * {@link LoginResponse} (authenticated shape) minus the token envelope
 * — the frontend calls this on app load to rehydrate session state
 * without trusting localStorage alone.
 *
 * <p>{@code authGrCd} comes from the JWT claim — {@code "ADMIN"} when
 * the user has a BUSI_ADMN_INFM row for the active company,
 * {@code "USER"} otherwise. {@code prdtType} also comes from the JWT
 * claim — verbatim {@code BUSI_INFM.PRDT_TYPE} letter for the active
 * company (may be null). The token cannot be tampered without
 * invalidating the signature, so both fields are safe to trust here
 * without re-querying the database.
 *
 * Wire names auto-derived from camelCase Java fields via the global
 * SNAKE_CASE Jackson naming strategy.
 */
public record MeResponse(
        String userId,
        String userNm,
        String bizKey,
        String langCd,
        String cmpnNm,
        String authGrCd,
        String prdtType,
        /** Verbatim {@code BUSI_INFM.TAX_ID} for the active company.
         *  Re-queried from the DB on every {@code /me} call (the JWT
         *  doesn't carry it) so a TAX_ID corrected by ops surfaces on
         *  the next session-rehydrate without forcing a re-login.
         *  May be null when the row carries no value — never silently
         *  defaulted. The Lock Screen popup (Figma 83:12764) renders
         *  this in brackets beneath the company name. */
        String taxId
) { }
