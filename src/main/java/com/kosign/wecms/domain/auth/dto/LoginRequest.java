package com.kosign.wecms.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Wire body for {@code POST /api/v1/auth/login}.
 *
 * <pre>{@code
 * { "user_id": "...", "user_pw": "...", "biz_key": "..." }  // biz_key optional
 * }</pre>
 *
 * <p>{@code biz_key} is the second step of the two-call login flow:
 * <ol>
 *   <li>The frontend posts only {@code user_id} + {@code user_pw}. If the
 *       user has exactly one active link in USER_BUSI_CONN_INFM, login
 *       finalises immediately with that BIZ_KEY. If they have two or
 *       more, the response carries {@code companies_available} and no
 *       token — the frontend renders the chooser.</li>
 *   <li>After the user picks a company, the frontend re-posts the same
 *       credentials together with {@code biz_key}. The service then
 *       verifies the link exists for the (USER_ID, BIZ_KEY) pair and
 *       issues the JWT.</li>
 * </ol>
 *
 * <p>The BIZ_KEY is validated against the user's actual link rows on
 * every login call — the frontend cannot spoof a tenant it has no
 * USER_BUSI_CONN_INFM row for.
 *
 * Wire names auto-derived from camelCase Java fields via the global
 * SNAKE_CASE Jackson naming strategy.
 */
public record LoginRequest(
        @NotBlank
        @Size(max = 100)
        String userId,

        @NotBlank
        @Size(max = 100)
        String userPw,

        /** Optional. Required only when {@code user_id} has multiple
         *  active USER_BUSI_CONN_INFM rows and the user has selected a
         *  company in the chooser. Pattern matches the legacy
         *  {@code {NTNL_CD}_{CHNL_ID}_{NNNNNN}} shape. */
        @Size(max = 20)
        @Pattern(regexp = "^[A-Z0-9_]+$",
                 message = "biz_key must be a literal BIZ_KEY (no wildcards)")
        String bizKey
) {
    public LoginRequest {
        if (userId != null) userId = userId.trim();
        if (userPw != null) userPw = userPw.trim();
        if (bizKey != null) {
            bizKey = bizKey.trim();
            if (bizKey.isEmpty()) bizKey = null;
        }
    }
}
