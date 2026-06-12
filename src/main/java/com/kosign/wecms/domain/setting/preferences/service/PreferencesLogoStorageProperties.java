package com.kosign.wecms.domain.setting.preferences.service;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configurable storage root for the Settings &gt; Preferences company
 * logo. Resolves to a per-tenant subtree
 * {@code <storage-root>/<biz_key>/preferences/<save_file_nm>} (2026-06-12;
 * previously flat directly under the root). The opaque, HMAC-derived save
 * filename is retained as defence-in-depth on top of the per-tenant
 * directory isolation.
 *
 * <p>Default lives in {@code application.yml} ({@code ./tmp/preferences}
 * for CI); developers override via env var
 * {@code WECMS_PREFERENCES_STORAGE_ROOT}.
 */
@ConfigurationProperties(prefix = "wecms.preferences")
public record PreferencesLogoStorageProperties(

        /** Filesystem root under which each tenant's {@code preferences}
         *  logo subtree lives. */
        @NotBlank
        String storageRoot

) {
    /** Per-tenant subfolder — the route slug. */
    public static final String SLUG_FOLDER = "preferences";
}
