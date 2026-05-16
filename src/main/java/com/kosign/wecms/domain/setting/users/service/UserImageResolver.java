package com.kosign.wecms.domain.setting.users.service;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.stereotype.Component;

/**
 * Resolves the physical path for a Settings &gt; Users avatar file.
 * Flat layout matches the {@link com.kosign.wecms.domain.setting.preferences.service.PreferencesLogoResolver}
 * precedent — every tenant's files live directly under the storage root:
 *
 * <pre>
 * &lt;storage-root&gt;/&lt;save_file_nm&gt;
 * </pre>
 *
 * <p>Cross-tenant safety comes from the opaque save filename (256-bit
 * HMAC over {@code bizKey} + {@code userId} + timestamp + 16 random
 * bytes), not from per-tenant directory isolation. The resolver still
 * asserts the resolved path stays under the root as defence-in-depth.
 */
@Component
public class UserImageResolver {

    private final Path storageRoot;

    public UserImageResolver(UserImageStorageProperties properties) {
        this.storageRoot = Paths.get(properties.storageRoot()).toAbsolutePath().normalize();
    }

    /** Compose {@code <storage-root>/<saveFileNm>}. */
    public Path resolve(String saveFileNm) {
        if (saveFileNm == null || saveFileNm.isBlank()) {
            throw new IllegalArgumentException("saveFileNm is required");
        }
        Path resolved = storageRoot.resolve(saveFileNm).toAbsolutePath().normalize();
        if (!resolved.startsWith(storageRoot)) {
            throw new IllegalArgumentException(
                    "Resolved path escapes the storage root: " + resolved);
        }
        return resolved;
    }

    /** The storage root — the service creates this directory before
     *  the first write. */
    public Path root() {
        return storageRoot;
    }
}
