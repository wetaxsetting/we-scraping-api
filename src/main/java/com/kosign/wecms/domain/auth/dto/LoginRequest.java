package com.kosign.wecms.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Wire body: <pre>{@code { "user_id": "...", "user_pw": "..." }}</pre>
 *
 * Wire names auto-derived from camelCase Java fields via the global
 * SNAKE_CASE Jackson naming strategy.
 */
public record LoginRequest(
        @NotBlank
        @Size(max = 50)
        String userId,

        @NotBlank
        @Size(max = 100)
        String userPw
) {
    public LoginRequest {
        if (userId != null) userId = userId.trim();
        if (userPw != null) userPw = userPw.trim();
    }
}
