package com.kosign.wecms.domain.setting.erptransactionhistory.service;

import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Resolves the physical path for a row's attached file. Path layout
 * follows the slug convention (OQ-3, resolved 2026-05-11):
 *
 * <pre>
 * &lt;storage-root&gt;/&lt;biz_key&gt;/erp-transaction-history/&lt;save_file_nm&gt;
 * </pre>
 *
 * <p>The service-layer caller passes the row's {@code biz_key} (always
 * the tenant-scoped value from {@code TenantContext}) and
 * {@code save_file_nm}; the resolver concatenates them onto the
 * configured {@link ErpTrnsHistoryStorageProperties#storageRoot}. Path
 * traversal is guarded by the {@link #resolve} caller — every input is
 * either a literal {@code BIZ_KEY} pinned by config (so cannot contain
 * {@code "../"}) or the {@code SAVE_FILE_NM} column whose value comes
 * from a trusted INSERT path (never user-supplied at runtime). Defence
 * in depth: the resolver normalises and asserts the resolved path
 * stays under the tenant subtree, throwing if it does not.
 *
 * <p>Existence + readability checks live in the calling service so
 * the resolver stays a pure path-composition utility (single
 * responsibility). The service maps a missing / unreadable file to
 * {@code WBE2015}.
 */
@Component
public class ErpTrnsHistoryFileResolver {

    private final Path storageRoot;

    public ErpTrnsHistoryFileResolver(ErpTrnsHistoryStorageProperties properties) {
        this.storageRoot = Paths.get(properties.storageRoot()).toAbsolutePath().normalize();
    }

    /**
     * Compose the absolute path for {@code <storage-root>/<bizKey>/<slug>/<saveFileNm>}.
     * Normalises the result and asserts it stays inside the tenant
     * subtree (rejects path-traversal attempts even though neither
     * input is user-supplied at runtime).
     *
     * @throws IllegalArgumentException if either input is blank or if
     *         the resolved path escapes the tenant subtree.
     */
    public Path resolve(String bizKey, String saveFileNm) {
        if (bizKey == null || bizKey.isBlank()) {
            throw new IllegalArgumentException("bizKey is required");
        }
        if (saveFileNm == null || saveFileNm.isBlank()) {
            throw new IllegalArgumentException("saveFileNm is required");
        }

        Path tenantRoot = storageRoot.resolve(bizKey).resolve(ErpTrnsHistoryStorageProperties.SLUG_FOLDER)
                .toAbsolutePath().normalize();
        Path resolved   = tenantRoot.resolve(saveFileNm).toAbsolutePath().normalize();

        // Defence in depth — `Path.resolve` happily accepts ".." in the
        // file-name component. Reject anything that escapes the tenant
        // subtree so a future writer mistake can't surface as a path-
        // traversal exposure.
        if (!resolved.startsWith(tenantRoot)) {
            throw new IllegalArgumentException(
                    "Resolved path escapes the tenant subtree: " + resolved);
        }
        return resolved;
    }

    /**
     * Per-tenant directory the writer creates on first append. Exposed
     * separately from {@link #resolve(String, String)} so a service can
     * call {@code Files.createDirectories(...)} once and then write
     * multiple files without re-computing the parent each time. Mirrors
     * {@code InternalTransferFileResolver.tenantDirectory(...)}.
     */
    public Path tenantDirectory(String bizKey) {
        if (bizKey == null || bizKey.isBlank()) {
            throw new IllegalArgumentException("bizKey is required");
        }
        return storageRoot.resolve(bizKey).resolve(ErpTrnsHistoryStorageProperties.SLUG_FOLDER)
                .toAbsolutePath().normalize();
    }

    /** Test seam — exposes the absolute storage root the resolver
     *  was wired with. */
    public Path getStorageRoot() {
        return storageRoot;
    }
}
