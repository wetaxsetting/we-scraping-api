package com.kosign.wecms.domain.weinvoice.common;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.file.SecureFileDownload;
import com.kosign.wecms.domain.weinvoice.config.WeInvoiceProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Files WeInvoice serves or keeps.
 *
 * <ul>
 *   <li><b>Engine output</b> ({@code WECMS_WEINVOICE_ENGINE_OUTPUT_DIR}) — XML, PDF and mail
 *       files the engine saved; the engine tables store paths relative to it. Read only.</li>
 *   <li><b>WeInvoice storage</b> ({@code WECMS_WEINVOICE_STORAGE_ROOT}) — uploads. Written
 *       as {@code <owner_id>/<area>/<yyyy>/<MM>/<uuid>.<ext>}; the engine reads uploads from the
 *       same root, so both processes must see the same directory.</li>
 * </ul>
 * Every resolved path goes through {@link SecureFileDownload#assertWithinRoot}, so a
 * relative path stored in a table cannot escape its root.
 */
@Component
public class StoredFiles {

    private static final Pattern EXT = Pattern.compile("[a-z0-9]{1,8}");

    private final Path engineRoot;
    private final Path storageRoot;

    public StoredFiles(WeInvoiceProperties props) {
        this.engineRoot = props.enabled() ? Path.of(props.engineOutputDir()) : null;
        this.storageRoot = props.enabled() ? Path.of(props.storageRoot()) : null;
    }

    /** Engine file by its stored relative path; {@code WBE3010} when it is not on disk. */
    public Path engineFile(String relative) {
        return existing(engineRoot, relative);
    }

    /** Uploaded file by its stored relative path; {@code WBE3010} when it is not on disk. */
    public Path storedFile(String relative) {
        return existing(storageRoot, relative);
    }

    /** Size of an engine file, or {@code null} when it is missing (listing must not fail on one lost file). */
    public Long engineFileSize(String relative) {
        if (engineRoot == null || relative == null) return null;
        try {
            Path p = resolve(engineRoot, relative);
            return Files.isRegularFile(p) ? Files.size(p) : null;
        } catch (IOException | IllegalArgumentException e) {
            return null;
        }
    }

    /** Saves bytes under the storage root and returns where and what was written. */
    public Saved save(String ownerId, String area, String originalName, byte[] bytes) {
        if (storageRoot == null) {
            throw new BusinessException("WBE3000");
        }
        LocalDate today = LocalDate.now();
        String relative = String.join("/", safeSegment(ownerId), area,
                String.valueOf(today.getYear()), String.format("%02d", today.getMonthValue()),
                UUID.randomUUID() + extensionOf(originalName));
        Path target = resolve(storageRoot, relative);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store uploaded file", e);
        }
        return new Saved(relative, bytes.length, sha256(bytes));
    }

    /**
     * Saves an invoice's own XML / PDF in the engine's layout:
     * {@code <owner_id>/invoices/<company MST>/<yyyy>/<MM>/<seller MST>/<fileName>} (invoice date). A file
     * already there (an earlier upload of the same invoice) is kept: the new one gets {@code _2}, {@code _3} …
     */
    public Saved saveInvoiceFile(String ownerId, String companyMst, String issueDate, String sellerMst,
                                 String fileName, byte[] bytes) {
        if (storageRoot == null) {
            throw new BusinessException("WBE3000");
        }
        String yyyy = String.valueOf(LocalDate.now().getYear());
        String mm = String.format("%02d", LocalDate.now().getMonthValue());
        if (issueDate != null && issueDate.matches("\\d{4}-\\d{2}.*")) {
            yyyy = issueDate.substring(0, 4);
            mm = issueDate.substring(5, 7);
        }
        String dir = String.join("/", safeSegment(ownerId), "invoices", safeSegment(companyMst), yyyy, mm,
                safeSegment(sellerMst));
        String ext = extensionOf(fileName);
        String stem = safeSegment(ext.isEmpty() ? fileName : fileName.substring(0, fileName.length() - ext.length()));
        for (int n = 1; n < 1000; n++) {
            String relative = dir + "/" + stem + (n == 1 ? "" : "_" + n) + ext;
            Path target = resolve(storageRoot, relative);
            try {
                Files.createDirectories(target.getParent());
                Files.write(target, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                return new Saved(relative, bytes.length, sha256(bytes));
            } catch (java.nio.file.FileAlreadyExistsException e) {
                // next suffix
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to store uploaded file", e);
            }
        }
        throw new IllegalStateException("Too many uploads of " + stem);
    }

    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** {@code ".pdf"}-style lower-case extension, or {@code ""} when absent or unusual. */
    public static String extensionOf(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return "";
        String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        return EXT.matcher(ext).matches() ? "." + ext : "";
    }

    /** Last path segment of a stored path, used as a download name. */
    public static String baseName(String relative) {
        if (relative == null) return null;
        String s = relative.replace('\\', '/');
        int slash = s.lastIndexOf('/');
        return slash < 0 ? s : s.substring(slash + 1);
    }

    private Path existing(Path root, String relative) {
        if (root == null) {
            throw new BusinessException("WBE3000");
        }
        Path p;
        try {
            p = resolve(root, relative);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("WBE3010");
        }
        if (!Files.isRegularFile(p)) {
            throw new BusinessException("WBE3010");
        }
        return p;
    }

    private static Path resolve(Path root, String relative) {
        if (relative == null || relative.isBlank()) {
            throw new IllegalArgumentException("empty path");
        }
        Path p = root.resolve(relative.replace('\\', '/')).normalize();
        SecureFileDownload.assertWithinRoot(root, p);
        return p;
    }

    private static String safeSegment(String s) {
        return s == null ? "_" : s.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    /** Result of {@link #save}: relative path, size, SHA-256 hex. */
    public record Saved(String relativePath, long size, String sha256) {
    }
}
