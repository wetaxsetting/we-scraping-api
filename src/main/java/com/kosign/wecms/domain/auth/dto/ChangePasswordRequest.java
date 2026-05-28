package com.kosign.wecms.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Wire body for {@code POST /api/v1/auth/change-password}.
 *
 * <pre>{@code
 * { "current_password": "...", "new_password": "...", "confirm_password": "..." }
 * }</pre>
 *
 * <p>Mirrors the legacy {@code comm_0400_001_u001} inputs
 * (USER_PW_PRE / USER_PW / USER_PW2). All three fields are required
 * (legacy {@code ValidationHelper.validateRequired}); bean validation
 * raises {@code WBE_VALIDATION} when any is blank, before the service
 * runs. {@code @Size(max = 100)} matches the legacy WSVC length cap and
 * the {@code USER_INFM.USER_PW} column width.
 *
 * <p>Wire names auto-derive from the camelCase Java fields via the global
 * SNAKE_CASE Jackson strategy.
 */
public record ChangePasswordRequest(
        @NotBlank
        @Size(max = 100)
        String currentPassword,

        @NotBlank
        @Size(max = 100)
        String newPassword,

        @NotBlank
        @Size(max = 100)
        String confirmPassword
) {
    public ChangePasswordRequest {
        if (currentPassword != null) currentPassword = currentPassword.trim();
        if (newPassword != null)     newPassword     = newPassword.trim();
        if (confirmPassword != null) confirmPassword = confirmPassword.trim();
    }
}
