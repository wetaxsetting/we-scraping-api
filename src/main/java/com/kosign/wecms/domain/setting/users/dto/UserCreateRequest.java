package com.kosign.wecms.domain.setting.users.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/v1/settings/users}.
 *
 * <p>Validation:
 * <ul>
 *   <li>{@code user_id} — required, ≤ 100 chars (matches
 *       {@code USER_INFM.USER_ID} length).</li>
 *   <li>{@code user_pw} — required, 8..100 chars, must contain at
 *       least one non-alphanumeric character (refactor parity with
 *       the frontend Zod schema).</li>
 *   <li>{@code user_nm}, {@code eml}, {@code clph_no},
 *       {@code posi_nm}, {@code dept_nm} — required text fields.</li>
 *   <li>{@code eml} — additional {@code @Email} format check.</li>
 *   <li>{@code set_as_admin} — boolean (defaults to {@code false}
 *       when omitted; Jackson on a primitive record component
 *       deserialises a missing JSON field as {@code false}).</li>
 * </ul>
 *
 * <p>Wire is lowercase snake_case via the global Jackson naming
 * strategy ({@code user_id}, {@code user_pw}, {@code set_as_admin}).
 */
public record UserCreateRequest(
        @NotBlank @Size(max = 100)
        String userId,

        @NotBlank
        @Size(min = 8, max = 100)
        @Pattern(regexp = ".*[^A-Za-z0-9].*",
                 message = "Password must contain a non-alphanumeric character")
        String userPw,

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

        boolean setAsAdmin
) { }
