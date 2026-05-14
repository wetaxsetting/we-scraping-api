package com.kosign.wecms.domain.setting.preferences.service;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configurable storage root for the Settings &gt; Preferences company
 * logo. Resolves to a <b>flat</b> {@code <storage-root>/<save_file_nm>}
 * layout — no per-tenant or per-slug subfolders. Cross-tenant collisions
 * are prevented by the opaque, HMAC-derived save filename rather than
 * by directory isolation.
 *
 * <p>Default lives in {@code application.yml} ({@code ./tmp/preferences}
 * for CI); developers override via env var
 * {@code WECMS_PREFERENCES_STORAGE_ROOT}.
 */
@ConfigurationProperties(prefix = "wecms.preferences")
public record PreferencesLogoStorageProperties(

        /** Filesystem path where every tenant's logo files live (flat). */
        @NotBlank
        String storageRoot

) {
}
