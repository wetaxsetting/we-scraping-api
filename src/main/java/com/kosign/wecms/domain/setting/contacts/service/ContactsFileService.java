package com.kosign.wecms.domain.setting.contacts.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.auth.entity.UserInfm;
import com.kosign.wecms.domain.auth.repository.UserInfmRepository;
import com.kosign.wecms.domain.setting.contacts.dto.ContactFileRow;
import com.kosign.wecms.domain.setting.contacts.entity.TrcoInfm;
import com.kosign.wecms.domain.setting.contacts.entity.TrcoInfoFile;
import com.kosign.wecms.domain.setting.contacts.repository.TrcoInfmRepository;
import com.kosign.wecms.domain.setting.contacts.repository.TrcoInfoFileRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Settings &gt; Contacts &mdash; attachment upload / list / download /
 * delete. Mirrors legacy {@code comm_0184_002_c001} (file inserts) +
 * the {@code TrcoInfoFileDAO} write paths, mapped onto the refactor's
 * slug-named REST surface
 * ({@code /api/v1/settings/contacts/{trcoKey}/files}).
 *
 * <p><b>Tenant scoping</b>: every operation resolves the parent
 * {@code TRCO_INFM} row via {@link TrcoInfmRepository#findByTrcoKeyAndBizKey}
 * before touching the attachment row. {@code TRCO_INFO_FILE} carries no
 * {@code BIZ_KEY} column &mdash; tenancy is inherited from the parent
 * contact, and the parent-row check is the only enforcement point.
 *
 * <p><b>File ID allocation</b> goes through
 * {@link LegacyFunctionRepository#nextTrcoInfoFileFileId(String)}
 * (V99 {@code wb_get_next_val} branch &mdash; per-{@code TRCO_KEY}
 * MAX+1). One-shot retry on PK collision mirrors the
 * {@link ContactsService#create} pattern.
 *
 * <p><b>Title defaults</b> &mdash; the legacy popup always supplies a
 * title; the refactor UI does not. To preserve the legacy data shape
 * (NOT NULL column) the service stamps a default at insert time:
 * {@code FILE_ID = "1"} &rarr; {@code "Business Registration"} (mirrors
 * the legacy first-file convention); every subsequent file &rarr;
 * {@code "New document"}.
 *
 * <p><b>Storage</b> &mdash; physical files live under
 * {@code <storage-root>/<biz_key>/contacts/<UUID + .<ext>>} via
 * {@link ContactsFileResolver}. The save filename is server-generated
 * (UUID) so an attacker cannot guess paths and a future ZIP export
 * cannot collide on duplicate user filenames within a single contact.
 *
 * <p><b>Size / extension caps</b>: 10 MB per file enforced here +
 * {@code spring.servlet.multipart.max-file-size} in
 * {@code application.yml}; extension allow-list pinned to the JEX
 * legacy set (Office docs + common image formats + PDF).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContactsFileService {

    /** 10 MB cap per file. Mirrors the
     *  {@code spring.servlet.multipart.max-file-size} ceiling so the
     *  service-layer check produces a friendly envelope before the
     *  container truncates the upload. */
    public static final long MAX_FILE_SIZE_BYTES = 10L * 1024L * 1024L;

    /** Allow-list of permitted file extensions (lower-case, no leading
     *  dot). Mirrors the legacy JEX set; tighter than the OS default
     *  to keep executables out of the upload surface. */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif",
            "pdf",
            "doc", "docx",
            "xls", "xlsx",
            "ppt", "pptx");

    /** Legacy first-file convention &mdash; the first attachment on a
     *  contact is the Business Registration scan. */
    private static final String TITLE_FIRST_FILE = "Business Registration";

    /** Default for every subsequent file. */
    private static final String TITLE_OTHER_FILE = "New document";

    /** {@code yyyyMMddHHmmss} UTC &mdash; legacy audit-column shape. */
    private static final DateTimeFormatter DTM14 =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final TrcoInfmRepository contactRepo;
    private final TrcoInfoFileRepository fileRepo;
    private final UserInfmRepository userRepo;
    private final ContactsFileResolver fileResolver;
    private final LegacyFunctionRepository legacyFn;

    /* =================================================================
     *  Upload
     * ================================================================= */

    /**
     * Store one uploaded file under the parent contact. Validates
     * tenant ownership, extension, and size; allocates the next
     * {@code FILE_ID}; writes the bytes to disk; inserts the metadata
     * row. One-shot retry on PK collision &mdash; see
     * {@link ContactsService#create} for the precedent.
     *
     * @throws BusinessException {@code WBE_CONTACTS_NOT_FOUND} when the
     *         parent contact is missing or in a different tenant;
     *         {@code WBE_CONTACTS_FILE_NAME_INVALID} when the upload
     *         carries no filename or only whitespace;
     *         {@code WBE_CONTACTS_FILE_EMPTY} on a zero-byte upload;
     *         {@code WBE_CONTACTS_FILE_TOO_LARGE} when the upload
     *         exceeds {@link #MAX_FILE_SIZE_BYTES};
     *         {@code WBE_CONTACTS_FILE_TYPE_NOT_ALLOWED} when the
     *         extension is not in {@link #ALLOWED_EXTENSIONS};
     *         {@code WBE_CONTACTS_FILE_STORAGE_FAILED} on IO failure.
     */
    @Transactional
    public ContactFileRow upload(String trcoKey, MultipartFile file) {
        TrcoInfm parent = requireParent(trcoKey);
        String orgFileNm = validateFilename(file);
        long size = validateSize(file);
        String extension = extensionOf(orgFileNm);
        validateExtension(extension);

        try {
            return persistUpload(parent, file, orgFileNm, extension, size);
        } catch (DataIntegrityViolationException ex) {
            log.warn("FILE_ID collision on first attempt for trcoKey={}, retrying", trcoKey);
            return persistUpload(parent, file, orgFileNm, extension, size);
        }
    }

    private ContactFileRow persistUpload(
            TrcoInfm parent,
            MultipartFile file,
            String orgFileNm,
            String extension,
            long size) {

        String trcoKey = parent.getTrcoKey();
        String bizKey  = parent.getBizKey();
        String fileId  = legacyFn.nextTrcoInfoFileFileId(trcoKey);
        if (fileId == null || fileId.isBlank()) {
            // Defensive — the legacy function returns null only when
            // the table/field pair has no branch match, which should
            // be impossible here. Surface as a storage failure rather
            // than silently inserting an empty PK.
            throw new BusinessException("WBE_CONTACTS_FILE_STORAGE_FAILED");
        }

        String saveFileNm = UUID.randomUUID().toString() + "." + extension;
        String userId     = TenantContext.currentUserId();
        String userNm     = resolveUserName(userId);
        String now        = utcNowDtm14();
        String title      = "1".equals(fileId) ? TITLE_FIRST_FILE : TITLE_OTHER_FILE;

        Path tenantRoot = fileResolver.tenantRoot(bizKey);
        Path destination = fileResolver.resolve(bizKey, saveFileNm);
        try {
            Files.createDirectories(tenantRoot);
            file.transferTo(destination);
        } catch (IOException ex) {
            log.error("Failed to store contacts attachment trcoKey={} fileId={}",
                    trcoKey, fileId, ex);
            throw new BusinessException("WBE_CONTACTS_FILE_STORAGE_FAILED");
        }

        TrcoInfoFile row = new TrcoInfoFile();
        row.setTrcoKey(trcoKey);
        row.setFileId(fileId);
        row.setOrgFileNm(orgFileNm);
        row.setSaveFileNm(saveFileNm);
        row.setFileSize(Long.toString(size));
        row.setTrcoInfmTitl(title);
        row.setRegId(userId);
        row.setRegDtm(now);
        row.setRegNm(userNm);
        row.setModId(userId);
        row.setModDtm(now);
        try {
            fileRepo.save(row);
        } catch (DataIntegrityViolationException ex) {
            // Roll back the disk write so the retry path doesn't leak
            // an orphan file under the tenant folder.
            deleteQuietly(destination);
            throw ex;
        }
        return toRow(row);
    }

    /* =================================================================
     *  List
     * ================================================================= */

    @Transactional(readOnly = true)
    public List<ContactFileRow> list(String trcoKey) {
        requireParent(trcoKey);
        return fileRepo.findByTrcoKeyOrderByFileIdAsc(trcoKey).stream()
                .map(ContactsFileService::toRow)
                .toList();
    }

    /* =================================================================
     *  Download
     * ================================================================= */

    /**
     * Resolve the physical file behind a {@code (trcoKey, fileId)}
     * pair for streaming. Verifies tenant ownership of the parent
     * contact before consulting the file table. Missing row or wrong
     * tenant collapse to {@code WBE_CONTACTS_FILE_NOT_FOUND}; missing
     * physical file surfaces as {@code WBE_CONTACTS_FILE_UNAVAILABLE}.
     */
    @Transactional(readOnly = true)
    public DownloadResource openForDownload(String trcoKey, String fileId) {
        TrcoInfm parent = requireParent(trcoKey);
        TrcoInfoFile row = fileRepo.findByTrcoKeyAndFileId(trcoKey, fileId)
                .orElseThrow(() -> new BusinessException("WBE_CONTACTS_FILE_NOT_FOUND"));

        Path path = fileResolver.resolve(parent.getBizKey(), row.getSaveFileNm());
        if (!Files.isReadable(path) || !Files.isRegularFile(path)) {
            throw new BusinessException("WBE_CONTACTS_FILE_UNAVAILABLE");
        }
        long bytes;
        try {
            bytes = Files.size(path);
        } catch (IOException ex) {
            throw new BusinessException("WBE_CONTACTS_FILE_UNAVAILABLE");
        }
        return new DownloadResource(path, row.getOrgFileNm(), bytes);
    }

    /** Controller-facing carrier for the resolved download. */
    public record DownloadResource(Path path, String orgFileNm, long fileSize) { }

    /* =================================================================
     *  Delete
     * ================================================================= */

    /**
     * Hard-delete the metadata row + physical file. Tenant ownership
     * is verified against the parent contact first; the row is
     * deleted before the disk file so a partial failure leaves the
     * disk file orphaned (next list call won't show it) rather than
     * the DB row orphaned (which the user would still see).
     */
    @Transactional
    public void delete(String trcoKey, String fileId) {
        TrcoInfm parent = requireParent(trcoKey);
        TrcoInfoFile row = fileRepo.findByTrcoKeyAndFileId(trcoKey, fileId)
                .orElseThrow(() -> new BusinessException("WBE_CONTACTS_FILE_NOT_FOUND"));

        Path path = fileResolver.resolve(parent.getBizKey(), row.getSaveFileNm());
        fileRepo.deleteByTrcoKeyAndFileId(trcoKey, fileId);
        deleteQuietly(path);
    }

    /* =================================================================
     *  Internal helpers
     * ================================================================= */

    private TrcoInfm requireParent(String trcoKey) {
        String bizKey = TenantContext.currentBizKey();
        return contactRepo.findByTrcoKeyAndBizKey(trcoKey, bizKey)
                .filter(t -> t.getTrcoStts() == null || !"C".equals(t.getTrcoStts()))
                .orElseThrow(() -> new BusinessException("WBE_CONTACTS_NOT_FOUND"));
    }

    private String resolveUserName(String userId) {
        if (userId == null || userId.isBlank()) return userId;
        return userRepo.findById(userId)
                .map(UserInfm::getUserNm)
                .orElse(userId);
    }

    private static String validateFilename(MultipartFile file) {
        if (file == null) {
            throw new BusinessException("WBE_CONTACTS_FILE_NAME_INVALID");
        }
        String original = file.getOriginalFilename();
        if (original == null) {
            throw new BusinessException("WBE_CONTACTS_FILE_NAME_INVALID");
        }
        // Strip any path traversal noise (e.g. some clients send
        // "C:\\Users\\…\\foo.pdf" as the original filename).
        String trimmed = original;
        int slash = Math.max(trimmed.lastIndexOf('/'), trimmed.lastIndexOf('\\'));
        if (slash >= 0) trimmed = trimmed.substring(slash + 1);
        trimmed = trimmed.trim();
        if (trimmed.isEmpty()) {
            throw new BusinessException("WBE_CONTACTS_FILE_NAME_INVALID");
        }
        return trimmed;
    }

    private static long validateSize(MultipartFile file) {
        long size = file.getSize();
        if (size <= 0) {
            throw new BusinessException("WBE_CONTACTS_FILE_EMPTY");
        }
        if (size > MAX_FILE_SIZE_BYTES) {
            throw new BusinessException(
                    "WBE_CONTACTS_FILE_TOO_LARGE",
                    (MAX_FILE_SIZE_BYTES / (1024 * 1024)) + " MB");
        }
        return size;
    }

    private static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static void validateExtension(String extension) {
        if (extension.isEmpty() || !ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(
                    "WBE_CONTACTS_FILE_TYPE_NOT_ALLOWED",
                    String.join(", ", ALLOWED_EXTENSIONS));
        }
    }

    private static ContactFileRow toRow(TrcoInfoFile e) {
        return new ContactFileRow(
                e.getFileId(),
                e.getOrgFileNm(),
                e.getFileSize(),
                e.getRegDtm(),
                e.getRegNm(),
                e.getTrcoInfmTitl());
    }

    private static String utcNowDtm14() {
        return OffsetDateTime.now(ZoneOffset.UTC).format(DTM14);
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            // Best-effort — leave the orphan file for a sysadmin to
            // reap later; never fail the user-facing call.
            log.warn("Failed to delete contacts attachment at {}", path, ex);
        }
    }
}
