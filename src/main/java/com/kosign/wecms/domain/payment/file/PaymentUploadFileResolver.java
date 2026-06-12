package com.kosign.wecms.domain.payment.file;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Shared path resolver for firm-banking payment Excel uploads that live
 * under a per-tenant, per-module subtree:
 *
 * <pre>
 * &lt;storage-root&gt;/&lt;biz_key&gt;/&lt;module-slug&gt;/&lt;save_file_nm&gt;
 * </pre>
 *
 * <p>Historically Vendor / Expense / Payroll shared Internal Transfer's
 * {@code internal-transfer} subfolder (the download path was kept
 * "module-agnostic"). They now each own a module slug
 * ({@code vendor-payment} / {@code expense-payment} / {@code payroll})
 * so the upload tree mirrors the sidebar grouping. The storage <em>root</em>
 * stays identical across every payment module — subclasses reuse Internal
 * Transfer's configured root and differ only by slug.
 *
 * <p>{@link #resolveForRead} falls back to the legacy shared subfolder so
 * files written <em>before</em> the per-module split remain downloadable
 * without a manual file move (read-fallback, chosen 2026-06-12). Writes
 * always go to the per-module slug via {@link #resolve}.
 *
 * <p>Path layout + traversal guard mirror
 * {@link com.kosign.wecms.domain.payment.internaltransfer.service.InternalTransferFileResolver}.
 * Existence / readability checks live in the calling service (the
 * resolver stays a near-pure path utility); the one exception is
 * {@link #resolveForRead}, which probes the filesystem to decide which
 * subfolder a pre-split file lives in.
 */
public abstract class PaymentUploadFileResolver {

    private final Path storageRoot;
    private final String slugFolder;

    /** Subfolder these files lived in before the per-module split.
     *  {@code null} disables the read-fallback. */
    private final String legacyFallbackSlug;

    protected PaymentUploadFileResolver(String storageRoot, String slugFolder, String legacyFallbackSlug) {
        this.storageRoot = Paths.get(storageRoot).toAbsolutePath().normalize();
        this.slugFolder = slugFolder;
        this.legacyFallbackSlug = legacyFallbackSlug;
    }

    /**
     * Primary write / read path:
     * {@code <storage-root>/<bizKey>/<slug>/<saveFileNm>}.
     * Used for every upload (write) and for the just-written file's audit
     * record. Reads of potentially pre-split files use
     * {@link #resolveForRead} instead.
     */
    public Path resolve(String bizKey, String saveFileNm) {
        return compose(slugFolder, bizKey, saveFileNm);
    }

    /**
     * Per-tenant + per-module directory the service creates
     * ({@code Files.createDirectories}) once before the first write.
     */
    public Path tenantDirectory(String bizKey) {
        if (bizKey == null || bizKey.isBlank()) {
            throw new IllegalArgumentException("bizKey is required");
        }
        return storageRoot.resolve(bizKey).resolve(slugFolder).toAbsolutePath().normalize();
    }

    /**
     * Resolve for a read: return the per-module path when the file exists
     * there; otherwise fall back to the legacy shared subfolder so a file
     * written before the per-module split stays downloadable. When the
     * file is in neither place the primary path is returned so the
     * caller's existence check surfaces the usual not-found error
     * ({@code WBE2069} / {@code WBE2015}).
     */
    public Path resolveForRead(String bizKey, String saveFileNm) {
        Path primary = compose(slugFolder, bizKey, saveFileNm);
        if (Files.isReadable(primary)) {
            return primary;
        }
        if (legacyFallbackSlug != null) {
            Path legacy = compose(legacyFallbackSlug, bizKey, saveFileNm);
            if (Files.isReadable(legacy)) {
                return legacy;
            }
        }
        return primary;
    }

    /** Test seam — the absolute storage root the resolver was wired with. */
    public Path getStorageRoot() {
        return storageRoot;
    }

    /**
     * Compose + normalise {@code <root>/<bizKey>/<slug>/<saveFileNm>} and
     * assert it stays inside the tenant + slug subtree. Neither input is
     * user-supplied at runtime ({@code bizKey} is the signed-JWT tenant;
     * {@code saveFileNm} is composed by the service from {@code SCDUL_PYMN_NO}
     * + sanitised original filename) — the guard is defence in depth
     * against a future writer mistake.
     */
    private Path compose(String slug, String bizKey, String saveFileNm) {
        if (bizKey == null || bizKey.isBlank()) {
            throw new IllegalArgumentException("bizKey is required");
        }
        if (saveFileNm == null || saveFileNm.isBlank()) {
            throw new IllegalArgumentException("saveFileNm is required");
        }
        Path subtree  = storageRoot.resolve(bizKey).resolve(slug).toAbsolutePath().normalize();
        Path resolved = subtree.resolve(saveFileNm).toAbsolutePath().normalize();
        if (!resolved.startsWith(subtree)) {
            throw new IllegalArgumentException(
                    "Resolved path escapes the tenant subtree: " + resolved);
        }
        return resolved;
    }
}
