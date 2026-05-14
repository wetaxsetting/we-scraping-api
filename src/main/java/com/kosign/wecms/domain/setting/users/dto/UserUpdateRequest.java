package com.kosign.wecms.domain.setting.users.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code PUT /api/v1/settings/users/{userId}}.
 *
 * <p>{@code userId} is immutable — it lives only on the URI path,
 * never in the body. The body carries every other editable field plus
 * an OPTIONAL {@code user_pw} (admin password reset). When
 * {@code user_pw} is {@code null} or blank, the existing hash is left
 * untouched; when supplied it must pass the same strength rules as
 * {@link UserCreateRequest}.
 */
public record UserUpdateRequest(
        @NotBlank @Size(max = 100)
        String userNm,

        @NotBlank @Size(max = 100) @Email
        String eml,

        @NotBlank @Size(max = 100)
        String clphNo,

        @NotBlank @Size(max = 100)
        String posiNm,

        @NotBlank @Size(max = 100)
        String deptNm,

        boolean setAsAdmin,

        @Size(min = 8, max = 100)
        @Pattern(regexp = ".*[^A-Za-z0-9].*",
                 message = "Password must contain a non-alphanumeric character")
        String userPw
) { }
