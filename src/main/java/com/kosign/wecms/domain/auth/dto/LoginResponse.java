package com.kosign.wecms.domain.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Wire body for {@code POST /api/v1/auth/login}. Two-shape response —
 * the {@code companies_available} field discriminates.
 *
 * <p><b>Operational expectation.</b> Current WeCMS operation treats one
 * user as belonging to exactly one active company (see
 * {@code wecms-api/CLAUDE.md} §"Operational model"). The
 * authenticated shape below is therefore the **normal production
 * response**; the chooser shape is an exceptional / test-demo path
 * that only fires when a user has ≥ 2 active USER_BUSI_CONN_INFM
 * links. Frontends should treat the chooser branch as a safe fallback,
 * not a routine UX surface.
 *
 * <pre>{@code
 * // Authenticated (single-link user, or multi-link user finalising with biz_key)
 * {
 *   "access_token": "...",
 *   "token_type":   "Bearer",
 *   "expires_in":   3600,
 *   "user_id":      "...",
 *   "user_nm":      "...",
 *   "biz_key":      "VN_VNM0004_000001",
 *   "lang_cd":      "EN",
 *   "cmpn_nm":      "WeCMS R&D Tenant",
 *   "auth_gr_cd":   "ADMIN",
 *   "prdt_type":    "F"
 * }
 *
 * // Chooser (multi-link user, first call without biz_key)
 * {
 *   "user_id":     "...",
 *   "user_nm":     "...",
 *   "companies_available": [
 *     { "biz_key": "VN_VNM0004_000001", "cmpn_nm": "WeCMS R&D Tenant",      "auth_gr_cd": "ADMIN", "prdt_type": "F" },
 *     { "biz_key": "VN_VNM0004_000014", "cmpn_nm": "HAENGSUNG ELECTRONICS", "auth_gr_cd": "USER",  "prdt_type": "H" }
 *   ]
 * }
 * }</pre>
 *
 * <p>Null fields are dropped from the wire via {@link JsonInclude} so the
 * chooser branch ships a clean shape (no spurious {@code access_token: null}).
 *
 * <p>Most field names auto-derive from camelCase via the global
 * SNAKE_CASE Jackson naming strategy. {@code accessToken} is the single
 * explicit override — the wire name is {@code access_token}
 * (OAuth-style) rather than the strict snake of the Java field.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LoginResponse(
        @JsonProperty("access_token") String accessToken,
        String tokenType,
        Long   expiresIn,
        String userId,
        String userNm,
        String bizKey,
        String langCd,
        String cmpnNm,
        /** {@code "ADMIN"} or {@code "USER"} — derived from BUSI_ADMN_INFM
         *  per (BIZ_KEY, USER_ID) at login finalize. Null in the chooser
         *  response (per-company authority is on each {@link CompanyOption}). */
        String authGrCd,
        /** Verbatim {@code BUSI_INFM.PRDT_TYPE} letter for the
         *  selected company (any legacy code, may be null). Null in the
         *  chooser response — per-company product type is on each
         *  {@link CompanyOption}. */
        String prdtType,
        /** Verbatim {@code BUSI_INFM.TAX_ID} for the selected company —
         *  the user-facing company tax / business registration number
         *  (e.g. Vietnam Mã số thuế). Rendered in the Lock Screen popup
         *  (Figma 83:12764). Null in the chooser response (no company
         *  is selected yet) and null when the row carries no value —
         *  never silently defaulted. The internal {@link #bizKey} is
         *  the WeCMS tenant identifier and is NEVER user-facing. */
        String taxId,
        /** Non-null + length ≥ 2 when the user must pick a company. Null
         *  in the authenticated response. */
        List<CompanyOption> companiesAvailable
) {

    /** Authenticated response — single-link user or chooser finalize. */
    public static LoginResponse authenticated(
            String accessToken, String tokenType, long expiresIn,
            String userId, String userNm, String bizKey, String langCd,
            String cmpnNm, String authGrCd, String prdtType, String taxId) {
        return new LoginResponse(
                accessToken, tokenType, expiresIn,
                userId, userNm, bizKey, langCd, cmpnNm, authGrCd, prdtType, taxId,
                null);
    }

    /** Chooser response — multi-link user, first call without biz_key. */
    public static LoginResponse chooser(String userId, String userNm,
                                        List<CompanyOption> companies) {
        return new LoginResponse(
                null, null, null,
                userId, userNm, null, null, null, null, null, null,
                companies);
    }
}
