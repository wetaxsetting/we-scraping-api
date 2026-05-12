package com.kosign.wecms.domain.setting.environmentsettings.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code PUT /api/v1/settings/environment-settings/{env_set_inf}}.
 * Mirrors legacy {@code comm_0215_001_u001_act.jsp} - single
 * editable field {@code env_set_cont}.
 *
 * <p>Legacy enforces a Y/N whitelist for 31 specific codes (the
 * {@code validateInputEnvYesNo()} list); for codes outside that
 * whitelist (including the firm-banking automation codes
 * {@code 00078} / {@code 00079}) it silently skips Y/N validation
 * and accepts any string up to {@code VARCHAR(1000)}. The refactor
 * mirrors this behavior: the boundary accepts free-text up to 1000
 * characters; the toggle UI on the frontend constrains the value to
 * exactly {@code "Y"} or {@code "N"} for the codes it represents.
 *
 * <p>{@code @NotNull} (rather than {@code @NotBlank}) so a deliberate
 * empty string is permitted - matches legacy where blank values can
 * round-trip through the {@code coalesce(:ENV_SET_CONT, ENV_SET_CONT)}
 * UPDATE.
 */
public record EnvironmentSettingUpdateRequest(
        @NotNull
        @Size(max = 1000)
        String envSetCont
) { }
