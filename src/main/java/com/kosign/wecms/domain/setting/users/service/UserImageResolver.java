package com.kosign.wecms.domain.setting.users.service;

import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Resolves the physical path for a Settings &gt; Users avatar file.
 * Per-tenant + per-module subtree:
 *
 * <pre>
 * &lt;storage-root&gt;/&lt;biz_key&gt;/user-image/&lt;save_file_nm&gt;
 * </pre>
 *
 * <p>Historically avatars were written <b>flat</b> directly under the
 * storage root, with cross-tenant safety resting solely on the opaque
 * HMAC save filename. They now live in a per-tenant subtree; the opaque
 * filename is retained as defence-in-depth. {@link #resolveForRead} falls
 * back to the legacy flat root so files written before the per-tenant
 * split stay viewable / deletable (read-fallback, 2026-06-12) — no manual
 * move required. Writes always go to the per-tenant path via
 * {@link #resolve}.
 */
@Component
public class UserImageResolver {

    private final Path storageRoot;

    public UserImageResolver(UserImageStorageProperties properties) {
        this.storageRoot = Paths.get(properties.storageRoot()).toAbsolutePath().normalize();
    }

    /** Primary write / read path:
     *  {@code <storage-root>/<bizKey>/user-image/<saveFileNm>}. */
    public Path resolve(String bizKey, String saveFileNm) {
        return composeTenant(bizKey, saveFileNm);
    }

    /** Per-tenant + per-module directory the service creates before the
     *  first write. */
    public Path tenantDirectory(String bizKey) {
        if (bizKey == null || bizKey.isBlank()) {
            throw new IllegalArgumentException("bizKey is required");
        }
        return storageRoot.resolve(bizKey).resolve(UserImageStorageProperties.SLUG_FOLDER)
                .toAbsolutePath().normalize();
    }

    /**
     * Read / delete path with backward-compatible fallback: returns the
     * per-tenant path when the file exists there; otherwise falls back to
     * the legacy flat {@code <storage-root>/<saveFileNm>} for files written
     * before the per-tenant split. When the file is in neither place the
     * primary path is returned so the caller's existence check surfaces the
     * usual not-found error.
     */
    public Path resolveForRead(String bizKey, String saveFileNm) {
        Path primary = composeTenant(bizKey, saveFileNm);
        if (Files.isReadable(primary)) {
            return primary;
        }
        Path legacyFlat = composeFlat(saveFileNm);
        if (Files.isReadable(legacyFlat)) {
            return legacyFlat;
        }
        return primary;
    }

    private Path composeTenant(String bizKey, String saveFileNm) {
        if (bizKey == null || bizKey.isBlank()) {
            throw new IllegalArgumentException("bizKey is required");
        }
        if (saveFileNm == null || saveFileNm.isBlank()) {
            throw new IllegalArgumentException("saveFileNm is required");
        }
        Path subtree  = storageRoot.resolve(bizKey).resolve(UserImageStorageProperties.SLUG_FOLDER)
                .toAbsolutePath().normalize();
        Path resolved = subtree.resolve(saveFileNm).toAbsolutePath().normalize();
        if (!resolved.startsWith(subtree)) {
            throw new IllegalArgumentException(
                    "Resolved path escapes the tenant subtree: " + resolved);
        }
        return resolved;
    }

    private Path composeFlat(String saveFileNm) {
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
}
