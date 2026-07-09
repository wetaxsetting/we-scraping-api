package com.kosign.wecms.domain.setting.users.service;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configurable storage root for the Settings &gt; Users avatar upload.
 * Files live in a per-tenant subtree
 * {@code <storage-root>/<biz_key>/user-image/<save_file_nm>} (2026-06-12;
 * previously flat directly under the root). The opaque HMAC-derived save
 * filename is retained as defence-in-depth on top of the per-tenant
 * directory isolation.
 *
 * <p>Default lives in {@code application.yml} ({@code ./tmp/user-image}
 * for CI); developers override via env var
 * {@code WECMS_USER_IMAGE_STORAGE_ROOT}.
 */
@ConfigurationProperties(prefix = "wecms.user-image")
public record UserImageStorageProperties(

        /** Filesystem root under which each tenant's {@code user-image}
         *  subtree lives. */
        @NotBlank
        String storageRoot

) {
    /** Per-tenant subfolder — the route slug. */
    public static final String SLUG_FOLDER = "user-image";
}
