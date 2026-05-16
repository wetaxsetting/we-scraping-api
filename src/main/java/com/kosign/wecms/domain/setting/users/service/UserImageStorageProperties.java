package com.kosign.wecms.domain.setting.users.service;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configurable storage root for the Settings &gt; Users avatar
 * upload. Flat layout per the {@link com.kosign.wecms.domain.setting.preferences}
 * precedent — every tenant's user-image files live directly under the
 * storage root with an opaque HMAC-derived save filename, so cross-tenant
 * collisions are prevented by name unforgeability rather than directory
 * isolation.
 *
 * <p>Default lives in {@code application.yml} ({@code ./tmp/user-image}
 * for CI); developers override via env var
 * {@code WECMS_USER_IMAGE_STORAGE_ROOT}.
 */
@ConfigurationProperties(prefix = "wecms.user-image")
public record UserImageStorageProperties(

        /** Filesystem path where every tenant's user-image files live (flat). */
        @NotBlank
        String storageRoot

) {
}
