package com.kosign.wecms.domain.setting.preferences.service;

import com.kosign.wecms.common.auditlog.service.AuditLogContext;
import com.kosign.wecms.common.auditlog.service.AuditLogService;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.auth.entity.BusiInfm;
import com.kosign.wecms.domain.auth.repository.BusiInfmRepository;
import com.kosign.wecms.domain.setting.preferences.dto.PreferencesAddress;
import com.kosign.wecms.domain.setting.preferences.dto.PreferencesResponse;
import com.kosign.wecms.domain.setting.preferences.dto.PreferencesUpdateRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;

/**
 * Settings &gt; Preferences — company-profile read + update + logo
 * upload / delete / stream. Mirrors legacy {@code comm_0215_001_r001}
 * (read) and {@code comm_0215_001_u001} (update) for the
 * {@code BUSI_INFM} half of the legacy WSVC; the {@code REC[]}
 * environment-settings half stays on the generic
 * {@code EnvironmentSettingsService} (Use Purpose code {@code 00077},
 * Use VAS Account code {@code 00074}).
 *
 * <p>Tenant scoping: {@code BIZ_KEY} is the {@code BUSI_INFM} PK and
 * comes from {@link TenantContext#currentBizKey()} — never trusted
 * from request input.
 *
 * <p>Audit: writes one event per company-profile Update via the shared
 * {@link AuditLogService} with {@code wsvc_id = "preferences"} (slug,
 * not the legacy JEX page id — per workspace identifier-naming rule).
 * Logo upload / delete do <b>not</b> write audit rows by themselves —
 * they only mutate a single file column on {@code BUSI_INFM} and the
 * legacy JEX runtime does not audit those flows either.
 *
 * <p>Logo storage: physical files live in a per-tenant subtree under
 * {@code <storage-root>/<biz_key>/preferences/<save_file_nm>} (2026-06-12;
 * previously flat directly under the root — reads still fall back there
 * via {@link PreferencesLogoResolver#resolveForRead}). The opaque save
 * filename (256-bit HMAC-SHA-256 over {@code bizKey} +
 * {@code System.currentTimeMillis()} + 16 random bytes, encoded
 * base64-url, with the legacy file extension appended). The hash is
 * unforgeable without the random seed; two tenants therefore cannot
 * predict or collide on each other's filenames. {@code BUSI_INFM.CMPN_LOGO}
 * stores this opaque save name; the public {@code logo_url} on the
 * wire points at the streaming GET endpoint
 * ({@code /api/v1/settings/preferences/logo}) so the frontend never
 * touches the disk layout directly.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PreferencesService {

    /** Audit log slug — see class Javadoc. Matches the
     *  refactor identifier-naming convention adopted 2026-05-09. */
    static final String AUDIT_WSVC_ID = "preferences";

    /** {@code REF_NO} for the audit entry — the legacy
     *  {@code BUSI_INFM} entity name. */
    private static final String REF_NO_BUSI_INFM = "BUSI_INFM";

    /** i18n keys for the audit event. */
    private static final String AUDIT_ACTIVITY_KEY      = "audit.preferences.activity";
    private static final String AUDIT_UPDATE_DETAIL_KEY = "audit.preferences.update.detail";

    /** ISO-8601 with offset — what the wire ships for
     *  {@code modified_at}. */
    private static final DateTimeFormatter ISO_OFFSET = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    /** Public REST URI for the streaming logo GET. Surfaced on the wire
     *  via {@link PreferencesResponse#logoUrl()} when the row has a
     *  logo filename stored; the frontend renders it as the
     *  {@code <img src>}. Kept as a relative path so the api host /
     *  port / context-path stays the frontend's concern. */
    private static final String LOGO_PUBLIC_URI = "/api/v1/settings/preferences/logo";

    /** 2 MB cap per logo upload. Smaller than the contacts 10 MB cap
     *  because a company logo never needs to be larger than this.
     *  Mirrors what {@code spring.servlet.multipart.max-file-size}
     *  would otherwise enforce; checking here surfaces a friendly
     *  envelope before the container rejects. */
    public static final long MAX_LOGO_SIZE_BYTES = 2L * 1024L * 1024L;

    /** Allow-list of permitted logo extensions (lower-case, no leading
     *  dot). Raster images only. F-02: SVG is excluded — an inline-served
     *  SVG can carry &lt;script&gt; and execute as stored XSS in the
     *  WeCMS origin. */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif");

    /** MIME type emitted on the download response per extension. The
     *  frontend trusts our Content-Type header so we map deterministically
     *  rather than re-detecting via probeContentType. */
    private static final java.util.Map<String, String> MIME_BY_EXT = java.util.Map.of(
            "jpg",  "image/jpeg",
            "jpeg", "image/jpeg",
            "png",  "image/png",
            "gif",  "image/gif");

    /** Source of randomness for the opaque save filename. Thread-safe;
     *  one shared instance is sufficient. */
    private static final SecureRandom RNG = new SecureRandom();

    private final BusiInfmRepository      busiInfmRepository;
    private final AuditLogService         auditLogService;
    private final PreferencesLogoResolver logoResolver;

    /**
     * Read the current tenant's company profile. Throws when the row
     * is missing — should never happen because every active session
     * must resolve to a {@code BUSI_INFM} row at login (the
     * {@code AuthService} fan-out fails earlier with {@code WBE1009}).
     */
    @Transactional(readOnly = true)
    public PreferencesResponse get() {
        String bizKey = requireBizKey();
        BusiInfm row = busiInfmRepository.findById(bizKey)
                .orElseThrow(() -> {
                    log.warn("preferences get: BUSI_INFM missing for bizKey={}", bizKey);
                    return new BusinessException("WBE2080");
                });
        return toResponse(row);
    }

    /**
     * Save the company profile for the current tenant. Mirrors legacy
     * {@code comm_0215_001_u001_act.jsp:48-95} — pure UPDATE on the
     * single {@code BUSI_INFM} row scoped to {@code BIZ_KEY}.
     *
     * <p>Logo column ({@code CMPN_LOGO}) is intentionally NOT touched
     * here — it has its own dedicated upload / delete endpoints so the
     * profile save can stay JSON-only.
     */
    @Transactional
    public PreferencesResponse update(PreferencesUpdateRequest req) {
        if (req.address() == null) {
            throw new BusinessException("WBE_VALIDATION", "address: is required");
        }
        String bizKey = requireBizKey();
        BusiInfm row = busiInfmRepository.findById(bizKey)
                .orElseThrow(() -> {
                    log.warn("preferences update: BUSI_INFM missing for bizKey={}", bizKey);
                    return new BusinessException("WBE2080");
                });

        row.setCmpnNm(req.vietnameseName().trim());
        row.setCmpnEnm(trimToNull(req.displayName()));
        row.setTaxId(req.taxId().trim());
        row.setRpprNm(trimToNull(req.representativeName()));
        row.setEml(req.representativeEmail().trim());
        row.setTelNo(trimToNull(req.telNo()));
        row.setFxno(trimToNull(req.faxNo()));
        row.setRprsBsunPostAdrs(req.address().postAddress().trim());
        row.setRprsBsunDtlAdrs(trimToNull(req.address().detailAddress()));
        row.setRprsBsunCity(req.address().city().trim());
        row.setRprsBsunSt(req.address().state().trim());
        row.setModId(TenantContext.currentUserId());
        // mod_dtm is bumped by BaseEntity.@PreUpdate.

        BusiInfm saved = busiInfmRepository.save(row);

        auditLogService.append(AuditLogContext.builder()
                .wsvcId(AUDIT_WSVC_ID)
                .refNo(REF_NO_BUSI_INFM)
                .refNo1(bizKey)
                .activityKey(AUDIT_ACTIVITY_KEY)
                .detailKey(AUDIT_UPDATE_DETAIL_KEY)
                .build());

        log.info("preferences updated bizKey={} taxId={} eml={}",
                bizKey, saved.getTaxId(), saved.getEml());
        return toResponse(saved);
    }

    /* =================================================================
     *  Logo upload / delete / stream
     * ================================================================= */

    /**
     * Replace the current tenant's company logo. Validates the upload
     * (size + extension), writes the new bytes to a fresh
     * server-generated filename, updates {@code BUSI_INFM.CMPN_LOGO},
     * and finally best-effort-deletes the previous file from disk.
     *
     * <p>The "write new before delete old" ordering means a transient
     * IO failure leaves the previous logo intact rather than blanking
     * the company display while the database is in an inconsistent
     * state.
     */
    @Transactional
    public PreferencesResponse uploadLogo(MultipartFile file) {
        String bizKey = requireBizKey();
        BusiInfm row = busiInfmRepository.findById(bizKey)
                .orElseThrow(() -> new BusinessException("WBE2080"));

        String orgFileNm = validateFilename(file);
        long size = validateSize(file);
        String extension = extensionOf(orgFileNm);
        validateExtension(extension);

        String previous   = row.getCmpnLogo();
        String saveFileNm = generateOpaqueFilename(bizKey, extension);

        Path destination = logoResolver.resolve(bizKey, saveFileNm);
        try {
            Files.createDirectories(destination.getParent());
            file.transferTo(destination);
        } catch (IOException ex) {
            log.error("Failed to store preferences logo bizKey={}", bizKey, ex);
            throw new BusinessException("WBE2081");
        }

        row.setCmpnLogo(saveFileNm);
        row.setModId(TenantContext.currentUserId());
        BusiInfm saved = busiInfmRepository.save(row);

        // Best-effort cleanup of the previous file — never fail the
        // user-facing call. An orphaned file is harmless; a stale DB
        // pointer would render a broken image.
        if (previous != null && !previous.isBlank() && !previous.equals(saveFileNm)) {
            deleteQuietly(logoResolver.resolveForRead(bizKey, previous));
        }

        log.info("preferences logo uploaded bizKey={} saveFileNm={} sizeBytes={}",
                bizKey, saveFileNm, size);
        return toResponse(saved);
    }

    /**
     * Remove the current tenant's company logo. Clears
     * {@code BUSI_INFM.CMPN_LOGO} and best-effort-deletes the disk
     * file. Idempotent — calling it on a tenant with no logo set
     * returns silently.
     */
    @Transactional
    public PreferencesResponse deleteLogo() {
        String bizKey = requireBizKey();
        BusiInfm row = busiInfmRepository.findById(bizKey)
                .orElseThrow(() -> new BusinessException("WBE2080"));

        String previous = row.getCmpnLogo();
        if (previous != null && !previous.isBlank()) {
            row.setCmpnLogo(null);
            row.setModId(TenantContext.currentUserId());
            busiInfmRepository.save(row);
            deleteQuietly(logoResolver.resolveForRead(bizKey, previous));
            log.info("preferences logo deleted bizKey={} saveFileNm={}", bizKey, previous);
        }
        return toResponse(busiInfmRepository.findById(bizKey).orElseThrow(
                () -> new BusinessException("WBE2080")));
    }

    /**
     * Resolve the physical logo for streaming. Used by the streaming
     * GET endpoint. Missing logo → {@code WBE2082} (matches the wire
     * contract — frontend treats it as "no logo set"). Missing
     * physical file → {@code WBE2083}.
     */
    @Transactional(readOnly = true)
    public LogoDownload openLogo() {
        String bizKey = requireBizKey();
        BusiInfm row = busiInfmRepository.findById(bizKey)
                .orElseThrow(() -> new BusinessException("WBE2080"));
        String saveFileNm = row.getCmpnLogo();
        if (saveFileNm == null || saveFileNm.isBlank()) {
            throw new BusinessException("WBE2082");
        }
        Path path = logoResolver.resolveForRead(bizKey, saveFileNm);
        if (!Files.isReadable(path) || !Files.isRegularFile(path)) {
            log.warn("preferences logo file missing on disk bizKey={} saveFileNm={}",
                    bizKey, saveFileNm);
            throw new BusinessException("WBE2083");
        }
        long bytes;
        try {
            bytes = Files.size(path);
        } catch (IOException ex) {
            throw new BusinessException("WBE2083");
        }
        String contentType = MIME_BY_EXT.getOrDefault(
                extensionOf(saveFileNm), "application/octet-stream");
        return new LogoDownload(path, contentType, bytes);
    }

    /** Controller-facing carrier for the resolved logo. */
    public record LogoDownload(Path path, String contentType, long size) { }

    /* =================================================================
     *  Internal helpers
     * ================================================================= */

    private static String requireBizKey() {
        String bizKey = TenantContext.currentBizKey();
        if (bizKey == null || bizKey.isBlank()) {
            throw new BusinessException("WBE9999");
        }
        return bizKey;
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String validateFilename(MultipartFile file) {
        if (file == null) throw new BusinessException("WBE2084");
        String original = file.getOriginalFilename();
        if (original == null) throw new BusinessException("WBE2084");
        String trimmed = original;
        int slash = Math.max(trimmed.lastIndexOf('/'), trimmed.lastIndexOf('\\'));
        if (slash >= 0) trimmed = trimmed.substring(slash + 1);
        trimmed = trimmed.trim();
        if (trimmed.isEmpty()) throw new BusinessException("WBE2084");
        return trimmed;
    }

    private static long validateSize(MultipartFile file) {
        long size = file.getSize();
        if (size <= 0) {
            throw new BusinessException("WBE2085");
        }
        if (size > MAX_LOGO_SIZE_BYTES) {
            throw new BusinessException(
                    "WBE2086", (MAX_LOGO_SIZE_BYTES / (1024 * 1024)) + " MB");
        }
        return size;
    }

    /**
     * Build the opaque save filename. Format:
     * {@code <43-char base64-url SHA-256 digest>.<ext>}.
     *
     * <p>The digest is SHA-256 over the concatenation of
     * {@code bizKey} + {@code System.currentTimeMillis()} +
     * 16 random bytes drawn from {@link SecureRandom}. The seed bytes
     * dominate the entropy budget (128 bits of randomness) so two
     * uploads from the same tenant in the same millisecond still
     * produce divergent filenames; the {@code bizKey} prefix only adds
     * tenant flavour to the input space without leaking the value on
     * disk. The base64-url encoding (no padding) keeps the result
     * filesystem-safe across Windows / POSIX without any path-encoding
     * concerns.
     *
     * <p>SHA-256 is treated as a one-way hash here, not as encryption —
     * the original filename was never sensitive, so there is no plain
     * value to recover. We never decrypt the save name; the database
     * row stores exactly what was written to disk.
     */
    private static String generateOpaqueFilename(String bizKey, String extension) {
        byte[] seed = new byte[16];
        RNG.nextBytes(seed);
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 ships with every JRE — this is a "JVM is broken"
            // case, not a runtime fault.
            throw new IllegalStateException("SHA-256 algorithm not available", ex);
        }
        md.update((bizKey == null ? "" : bizKey).getBytes(StandardCharsets.UTF_8));
        md.update(Long.toString(System.currentTimeMillis()).getBytes(StandardCharsets.UTF_8));
        md.update(seed);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(md.digest());
        return token + "." + extension;
    }

    private static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) return "";
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static void validateExtension(String extension) {
        if (extension.isEmpty() || !ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(
                    "WBE2087", String.join(", ", ALLOWED_EXTENSIONS));
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            log.warn("Failed to delete preferences logo at {}", path, ex);
        }
    }

    private static PreferencesResponse toResponse(BusiInfm e) {
        PreferencesAddress address = new PreferencesAddress(
                e.getRprsBsunPostAdrs(),
                e.getRprsBsunDtlAdrs(),
                e.getRprsBsunCity(),
                e.getRprsBsunSt());
        String logoUrl = (e.getCmpnLogo() == null || e.getCmpnLogo().isBlank())
                ? null
                : LOGO_PUBLIC_URI + "?v=" + e.getCmpnLogo();
        return new PreferencesResponse(
                e.getCmpnNm(),
                e.getCmpnEnm(),
                e.getTaxId(),
                e.getRpprNm(),
                e.getEml(),
                e.getTelNo(),
                e.getFxno(),
                address,
                logoUrl,
                e.getModId(),
                e.getModDtm() == null ? null : e.getModDtm().format(ISO_OFFSET));
    }
}
