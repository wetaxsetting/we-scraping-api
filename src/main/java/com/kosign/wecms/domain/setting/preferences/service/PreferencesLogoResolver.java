package com.kosign.wecms.domain.setting.preferences.service;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.stereotype.Component;

/**
 * Resolves the physical path for the Settings &gt; Preferences company
 * logo. Layout is intentionally <b>flat</b> — every tenant's files live
 * directly under the storage root:
 *
 * <pre>
 * &lt;storage-root&gt;/&lt;save_file_nm&gt;
 * </pre>
 *
 * <p>Cross-tenant safety comes from the opaque save filename
 * ({@link PreferencesService} derives it from an HMAC of {@code bizKey}
 * + timestamp + secure random), not from per-tenant directory
 * isolation. {@code saveFileNm} is therefore the only knob; the
 * resolver still asserts the resolved path stays under the root as
 * defence-in-depth.
 */
@Component
public class PreferencesLogoResolver {

    private final Path storageRoot;

    public PreferencesLogoResolver(PreferencesLogoStorageProperties properties) {
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

    /** The storage root — used by the service to create the directory
     *  before the first write. */
    public Path root() {
        return storageRoot;
    }
}
