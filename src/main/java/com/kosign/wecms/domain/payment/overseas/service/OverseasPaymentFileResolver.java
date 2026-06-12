package com.kosign.wecms.domain.payment.overseas.service;

import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Resolves the physical path for an Overseas Payment batch's uploaded
 * Excel file. Per-tenant + per-module subtree:
 *
 * <pre>
 * &lt;storage-root&gt;/&lt;biz_key&gt;/overseas-payment/&lt;save_file_nm&gt;
 * </pre>
 *
 * <p>Historically Overseas files were written <b>flat</b> directly under
 * the storage root ({@code <root>/<file>}), ignoring the tenant. They now
 * live in a per-tenant subtree like every other payment module.
 * {@link #resolveForRead} falls back to the legacy flat root so files
 * written before the per-tenant split stay downloadable (read-fallback,
 * 2026-06-12) — no manual file move required. Writes always go to the
 * per-tenant path via {@link #resolve}.
 *
 * <p>Path traversal is guarded: {@code bizKey} is the tenant-pinned value
 * from {@code TenantContext}; {@code saveFileNm} is the service-generated
 * filename, never user-supplied at runtime. The resolver still asserts the
 * resolved path stays under its subtree / root as defence-in-depth.
 */
@Component
public class OverseasPaymentFileResolver {

    private final Path storageRoot;

    public OverseasPaymentFileResolver(OverseasPaymentStorageProperties properties) {
        this.storageRoot = Paths.get(properties.storageRoot()).toAbsolutePath().normalize();
    }

    /** Primary write / read path:
     *  {@code <storage-root>/<bizKey>/overseas-payment/<saveFileNm>}. */
    public Path resolve(String bizKey, String saveFileNm) {
        return composeTenant(bizKey, saveFileNm);
    }

    /** Per-tenant + per-module directory the service creates
     *  ({@code Files.createDirectories}) before the first write. */
    public Path tenantDirectory(String bizKey) {
        if (bizKey == null || bizKey.isBlank()) {
            throw new IllegalArgumentException("bizKey is required");
        }
        return storageRoot.resolve(bizKey).resolve(OverseasPaymentStorageProperties.SLUG_FOLDER)
                .toAbsolutePath().normalize();
    }

    /**
     * Read path with backward-compatible fallback: returns the per-tenant
     * path when the file exists there; otherwise falls back to the legacy
     * flat {@code <storage-root>/<saveFileNm>} for files written before the
     * per-tenant split. When the file is in neither place the primary path
     * is returned so the caller's existence check surfaces the usual
     * not-found error.
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

    /** Test seam — the absolute storage root the resolver was wired with. */
    public Path getStorageRoot() {
        return storageRoot;
    }

    private Path composeTenant(String bizKey, String saveFileNm) {
        if (bizKey == null || bizKey.isBlank()) {
            throw new IllegalArgumentException("bizKey is required");
        }
        if (saveFileNm == null || saveFileNm.isBlank()) {
            throw new IllegalArgumentException("saveFileNm is required");
        }
        Path subtree  = storageRoot.resolve(bizKey).resolve(OverseasPaymentStorageProperties.SLUG_FOLDER)
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
