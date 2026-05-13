package com.kosign.wecms.domain.setting.contacts.service;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.stereotype.Component;

/**
 * Resolves the physical path for a Contacts attachment row. Same shape
 * as {@code ErpTrnsHistoryFileResolver}, different slug folder.
 *
 * <pre>
 * &lt;storage-root&gt;/&lt;biz_key&gt;/contacts/&lt;save_file_nm&gt;
 * </pre>
 *
 * <p>Path traversal is guarded — {@code bizKey} is the
 * tenant-pinned value from {@code TenantContext} and
 * {@code saveFileNm} is the server-generated UUID-style filename,
 * never user-supplied at runtime. The resolver still asserts the
 * resolved path stays under the tenant subtree as defence-in-depth.
 */
@Component
public class ContactsFileResolver {

    private final Path storageRoot;

    public ContactsFileResolver(ContactsFileStorageProperties properties) {
        this.storageRoot = Paths.get(properties.storageRoot()).toAbsolutePath().normalize();
    }

    /** Compose {@code <storage-root>/<bizKey>/contacts/<saveFileNm>}. */
    public Path resolve(String bizKey, String saveFileNm) {
        if (bizKey == null || bizKey.isBlank()) {
            throw new IllegalArgumentException("bizKey is required");
        }
        if (saveFileNm == null || saveFileNm.isBlank()) {
            throw new IllegalArgumentException("saveFileNm is required");
        }
        Path tenantRoot = storageRoot
                .resolve(bizKey)
                .resolve(ContactsFileStorageProperties.SLUG_FOLDER)
                .toAbsolutePath().normalize();
        Path resolved = tenantRoot.resolve(saveFileNm).toAbsolutePath().normalize();
        if (!resolved.startsWith(tenantRoot)) {
            throw new IllegalArgumentException(
                    "Resolved path escapes the tenant subtree: " + resolved);
        }
        return resolved;
    }

    /** The tenant root — useful for the storage service when it has
     *  to create the directory before the first write. */
    public Path tenantRoot(String bizKey) {
        if (bizKey == null || bizKey.isBlank()) {
            throw new IllegalArgumentException("bizKey is required");
        }
        return storageRoot
                .resolve(bizKey)
                .resolve(ContactsFileStorageProperties.SLUG_FOLDER)
                .toAbsolutePath().normalize();
    }

    public Path getStorageRoot() {
        return storageRoot;
    }
}
