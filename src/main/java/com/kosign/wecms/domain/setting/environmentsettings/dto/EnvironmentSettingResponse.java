package com.kosign.wecms.domain.setting.environmentsettings.dto;

/**
 * Wire response for {@code GET /api/v1/settings/environment-settings/{env_set_inf}}
 * and {@code PUT} of the same path. Mirrors the legacy
 * {@code comm_0600_001_r001} output shape: a single
 * {@code (env_set_inf, env_set_cont)} pair.
 *
 * <p>Wire format: lowercase snake_case via the global Jackson
 * SNAKE_CASE strategy. Java fields stay camelCase.
 *
 * <p>{@code envSetCont} carries the COALESCE(BUSI override, SYS
 * default) value on read - matches legacy IDO
 * {@code BUSI_ENV_SET_INFM_R002}. Returns {@code null} only when the
 * SYS row's default itself is {@code null} (the column is nullable).
 * The frontend treats anything other than the exact string
 * {@code "Y"} as off / not set.
 */
public record EnvironmentSettingResponse(
        String envSetInf,
        String envSetCont
) { }
