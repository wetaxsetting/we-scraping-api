package com.kosign.wecms.domain.payment.cardtransactions.service;

import com.kosign.wecms.common.file.SecureFileDownload;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.stereotype.Component;

/**
 * Resolves the physical path for a card-transaction receipt / e-Invoice
 * attachment. Same shape as {@code ContactsFileResolver} / ERP resolver,
 * different slug folder:
 *
 * <pre>
 * &lt;storage-root&gt;/&lt;biz_key&gt;/card-transactions/&lt;save_file_nm&gt;
 * </pre>
 *
 * <p>Traversal guarded via the shared {@link SecureFileDownload#assertWithinRoot}
 * primitive — {@code bizKey} is the tenant-pinned value and {@code saveFileNm}
 * is a server-generated UUID, but the resolved path is still asserted to stay
 * under the tenant subtree as defence-in-depth.
 */
@Component
public class CardTransactionsFileResolver {

    private final Path storageRoot;

    public CardTransactionsFileResolver(CardTransactionsFileStorageProperties properties) {
        this.storageRoot = Paths.get(properties.storageRoot()).toAbsolutePath().normalize();
    }

    /** Compose {@code <storage-root>/<bizKey>/card-transactions/<saveFileNm>}. */
    public Path resolve(String bizKey, String saveFileNm) {
        if (bizKey == null || bizKey.isBlank()) {
            throw new IllegalArgumentException("bizKey is required");
        }
        if (saveFileNm == null || saveFileNm.isBlank()) {
            throw new IllegalArgumentException("saveFileNm is required");
        }
        Path tenantRoot = tenantRoot(bizKey);
        Path resolved = tenantRoot.resolve(saveFileNm).toAbsolutePath().normalize();
        SecureFileDownload.assertWithinRoot(tenantRoot, resolved);
        return resolved;
    }

    /** Tenant root — used to create the directory before the first write. */
    public Path tenantRoot(String bizKey) {
        if (bizKey == null || bizKey.isBlank()) {
            throw new IllegalArgumentException("bizKey is required");
        }
        return storageRoot
                .resolve(bizKey)
                .resolve(CardTransactionsFileStorageProperties.SLUG_FOLDER)
                .toAbsolutePath().normalize();
    }
}
