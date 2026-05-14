package com.kosign.wecms.domain.setting.preferences.dto;

/**
 * GET /api/v1/settings/preferences response. Mirrors legacy
 * {@code comm_0215_001_r001} — single-row company profile read.
 *
 * <p>The two preference toggles ({@code use_purpose} = env code
 * {@code 00077}, {@code use_vas_account} = env code {@code 00074}) are
 * NOT included in this payload. They are owned by the generic
 * {@code /api/v1/settings/environment-settings/{code}} endpoint pair —
 * the frontend reads them once on panel mount and writes them on the
 * fly when the user flips a switch. Mixing them into this read would
 * fork two persistence paths into one, which V69 (Environment Settings
 * MR) intentionally avoided.
 *
 * <p>{@code logoUrl} is null until the company-logo upload endpoint
 * lands (deferred — Preferences panel currently shows a placeholder
 * SVG until then).
 */
public record PreferencesResponse(
        String vietnameseName,
        String displayName,
        String taxId,
        String representativeName,
        String representativeEmail,
        String telNo,
        String faxNo,
        PreferencesAddress address,
        String logoUrl,
        String modifiedBy,
        String modifiedAt) {
}
