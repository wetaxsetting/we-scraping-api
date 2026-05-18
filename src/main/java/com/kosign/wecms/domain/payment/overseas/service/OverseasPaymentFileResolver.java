package com.kosign.wecms.domain.payment.overseas.service;

import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Resolves the physical path for an Overseas Payment batch's
 * uploaded Excel file. Files are stored flat directly under the
 * configured storage root — no tenant or feature subfolder:
 *
 * <pre>
 * &lt;storage-root&gt;/&lt;save_file_nm&gt;
 * </pre>
 */
@Component
public class OverseasPaymentFileResolver {

    private final Path storageRoot;

    public OverseasPaymentFileResolver(OverseasPaymentStorageProperties properties) {
        this.storageRoot = Paths.get(properties.storageRoot()).toAbsolutePath().normalize();
    }

    /**
     * Compose the absolute path for {@code <storage-root>/<saveFileNm>}.
     * Guards against path-traversal: the resolved path must stay inside
     * the storage root.
     *
     * @throws IllegalArgumentException if saveFileNm is blank or the resolved
     *         path escapes the storage root.
     */
    public Path resolve(String bizKey, String saveFileNm) {
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

    /** Returns the storage root — the directory the service writes into. */
    public Path tenantDirectory(String bizKey) {
        return storageRoot;
    }

    public Path getStorageRoot() {
        return storageRoot;
    }
}
