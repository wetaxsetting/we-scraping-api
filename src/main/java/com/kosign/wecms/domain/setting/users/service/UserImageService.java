package com.kosign.wecms.domain.setting.users.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.auth.entity.UserBusiConnInfm;
import com.kosign.wecms.domain.auth.repository.UserBusiConnInfmRepository;
import com.kosign.wecms.domain.setting.users.dto.UserRowResponse;
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
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Settings &gt; Users — avatar upload / delete / stream.
 *
 * <p>Storage column: {@code USER_BUSI_CONN_INFM.USER_IMG} — per-tenant
 * link column (length 500), mirroring how
 * {@link UserManagementService#detail(String)} already reads
 * {@code link.getUserImg()}.
 *
 * <p>Physical files live in a per-tenant subtree under
 * {@code <storage-root>/<biz_key>/user-image/<save_file_nm>} (2026-06-12;
 * previously flat directly under the root — reads still fall back there
 * via {@link UserImageResolver#resolveForRead}); the save filename is a
 * server-generated opaque token (256-bit SHA-256 over
 * {@code bizKey} + {@code userId} + {@code System.currentTimeMillis()}
 * + 16 random bytes from {@link SecureRandom}, base64-url encoded
 * without padding, extension appended). Mirrors the
 * {@link com.kosign.wecms.domain.setting.preferences.service.PreferencesService}
 * pattern.
 *
 * <p>The public URL returned on
 * {@link UserRowResponse#userImgUrl()} points at a streaming GET
 * (cache-busted by the save filename) so the frontend never touches
 * the disk layout directly.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserImageService {

    /** Wire-side public URI pattern. The {@code v=} query carries the
     *  save filename so a browser cache cannot survive a re-upload. */
    static final String PUBLIC_URI_PREFIX = "/api/v1/settings/users/";
    static final String PUBLIC_URI_SUFFIX = "/image";

    /** 2 MB cap per user avatar — matches the company-logo cap. */
    public static final long MAX_IMAGE_SIZE_BYTES = 2L * 1024L * 1024L;

    // F-02: SVG is NOT allowed. An inline-served SVG can carry <script> and
    // execute in the WeCMS origin (stored XSS). Raster image formats only.
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif");

    private static final Map<String, String> MIME_BY_EXT = Map.of(
            "jpg",  "image/jpeg",
            "jpeg", "image/jpeg",
            "png",  "image/png",
            "gif",  "image/gif");

    private static final String DEL_N = "N";

    private static final SecureRandom RNG = new SecureRandom();

    private final UserBusiConnInfmRepository userBusiConnRepo;
    private final UserManagementService      userManagementService;
    private final UserImageResolver          resolver;

    /* =================================================================
     *  Upload / delete
     * ================================================================= */

    /**
     * Replace the target user's avatar inside the current tenant.
     * Validates the upload (size + extension), writes the new bytes
     * to a fresh server-generated filename, updates
     * {@code USER_BUSI_CONN_INFM.USER_IMG}, then best-effort-deletes
     * the previous file from disk.
     *
     * <p>"Write new before delete old" ordering means a transient IO
     * failure leaves the previous image intact rather than blanking
     * the avatar while the DB is in an inconsistent state.
     */
    @Transactional
    public UserRowResponse upload(String userId, MultipartFile file) {
        String bizKey  = TenantContext.currentBizKey();
        String actorId = TenantContext.currentUserId();
        storeImage(bizKey, userId, actorId, file);
        return userManagementService.detail(userId);
    }

    /**
     * Remove the target user's avatar inside the current tenant.
     * Clears {@code USER_BUSI_CONN_INFM.USER_IMG} and best-effort-
     * deletes the disk file. Idempotent — calling it on a user with
     * no avatar set returns the unchanged row.
     */
    @Transactional
    public UserRowResponse delete(String userId) {
        String bizKey  = TenantContext.currentBizKey();
        String actorId = TenantContext.currentUserId();
        clearImage(bizKey, userId, actorId);
        return userManagementService.detail(userId);
    }

    /* =================================================================
     *  Self-service (header profile menu → pencil "edit photo")
     * ================================================================= */

    /**
     * Upload the <b>signed-in user's own</b> avatar. The target is always
     * {@link TenantContext#currentUserId()} — a user can only change their
     * OWN photo; an admin changing someone else's photo goes through the
     * ADMIN-gated {@link #upload(String, MultipartFile)}. This mirrors
     * legacy {@code szon_0012_003_u002_act.jsp}, which forces {@code USER_ID}
     * to the login user for non-admins.
     *
     * <p>Returns {@link UserImageResult} carrying the streaming URL on the
     * shared <b>non-admin</b> GET ({@code /api/v1/users/{id}/image}) so the
     * header {@code <UserAvatar>} — a normal USER without the
     * {@code settings-core-users} grant — can fetch it (the
     * {@code /settings/users/...} twin is admin-gated). The {@code ?v=}
     * cache-buster flips on every upload.
     */
    @Transactional
    public UserImageResult uploadForCurrentUser(MultipartFile file) {
        String bizKey = TenantContext.currentBizKey();
        String userId = TenantContext.currentUserId();
        String saveFileNm = storeImage(bizKey, userId, userId, file);
        return new UserImageResult(selfPublicUrl(userId, saveFileNm));
    }

    /**
     * Clear the <b>signed-in user's own</b> avatar. Idempotent — returns a
     * {@code null} url whether or not a photo was set.
     */
    @Transactional
    public UserImageResult deleteForCurrentUser() {
        String bizKey = TenantContext.currentBizKey();
        String userId = TenantContext.currentUserId();
        clearImage(bizKey, userId, userId);
        return new UserImageResult(null);
    }

    /* =================================================================
     *  Core store / clear — shared by the admin + self-service paths
     * ================================================================= */

    /**
     * Validate + persist a new avatar for {@code userId} inside the current
     * tenant, returning the generated save filename. "Write new before
     * delete old" ordering means a transient IO failure leaves the previous
     * image intact rather than blanking the avatar while the DB is in an
     * inconsistent state.
     */
    private String storeImage(String bizKey, String userId, String actorId, MultipartFile file) {
        UserBusiConnInfm link = userBusiConnRepo
                .findByUserIdAndBizKeyAndDelYn(userId, bizKey, DEL_N)
                .orElseThrow(() -> new BusinessException("WBE_USERS_NOT_FOUND"));

        String orgFileNm = validateFilename(file);
        long size = validateSize(file);
        String extension = extensionOf(orgFileNm);
        validateExtension(extension);

        String previous   = link.getUserImg();
        String saveFileNm = generateOpaqueFilename(bizKey, userId, extension);

        Path destination = resolver.resolve(bizKey, saveFileNm);
        try {
            Files.createDirectories(destination.getParent());
            file.transferTo(destination);
        } catch (IOException ex) {
            log.error("Failed to store user image userId={} bizKey={}", userId, bizKey, ex);
            throw new BusinessException("WBE2111");
        }

        link.setUserImg(saveFileNm);
        link.setModId(actorId);
        // mod_dtm is bumped by BaseEntity.@PreUpdate via userBusiConnRepo.save()
        // — explicit save() not strictly required for managed entities
        // but kept for symmetry with PreferencesService.
        userBusiConnRepo.save(link);

        if (previous != null && !previous.isBlank() && !previous.equals(saveFileNm)) {
            deleteQuietly(resolver.resolveForRead(bizKey, previous));
        }

        log.info("user image uploaded userId={} bizKey={} saveFileNm={} sizeBytes={}",
                userId, bizKey, saveFileNm, size);
        return saveFileNm;
    }

    /** Clear {@code USER_BUSI_CONN_INFM.USER_IMG} for {@code userId} and
     *  best-effort-delete the disk file. Idempotent. */
    private void clearImage(String bizKey, String userId, String actorId) {
        UserBusiConnInfm link = userBusiConnRepo
                .findByUserIdAndBizKeyAndDelYn(userId, bizKey, DEL_N)
                .orElseThrow(() -> new BusinessException("WBE_USERS_NOT_FOUND"));

        String previous = link.getUserImg();
        if (previous != null && !previous.isBlank()) {
            link.setUserImg(null);
            link.setModId(actorId);
            userBusiConnRepo.save(link);
            deleteQuietly(resolver.resolveForRead(bizKey, previous));
            log.info("user image deleted userId={} bizKey={} saveFileNm={}",
                    userId, bizKey, previous);
        }
    }

    /* =================================================================
     *  Streaming GET
     * ================================================================= */

    /**
     * Resolve the physical avatar file for streaming. Missing image →
     * {@code WBE2113} (frontend treats as "no avatar set"). Missing
     * physical file → {@code WBE2114}.
     */
    @Transactional(readOnly = true)
    public ImageDownload open(String userId) {
        String bizKey = TenantContext.currentBizKey();
        UserBusiConnInfm link = userBusiConnRepo
                .findByUserIdAndBizKeyAndDelYn(userId, bizKey, DEL_N)
                .orElseThrow(() -> new BusinessException("WBE_USERS_NOT_FOUND"));

        String saveFileNm = link.getUserImg();
        if (saveFileNm == null || saveFileNm.isBlank()) {
            throw new BusinessException("WBE2113");
        }
        Path path = resolver.resolveForRead(bizKey, saveFileNm);
        if (!Files.isReadable(path) || !Files.isRegularFile(path)) {
            log.warn("user image file missing on disk userId={} saveFileNm={}",
                    userId, saveFileNm);
            throw new BusinessException("WBE2114");
        }
        long bytes;
        try {
            bytes = Files.size(path);
        } catch (IOException ex) {
            throw new BusinessException("WBE2114");
        }
        String contentType = MIME_BY_EXT.getOrDefault(
                extensionOf(saveFileNm), "application/octet-stream");
        return new ImageDownload(path, contentType, bytes);
    }

    /** Controller-facing carrier for the resolved avatar file. */
    public record ImageDownload(Path path, String contentType, long size) { }

    /** Self-service response — carries the refreshed streaming URL
     *  ({@code user_img_url} on the wire) for the header profile menu. */
    public record UserImageResult(String userImgUrl) { }

    /** Public URL composer — wire field {@code user_img_url} resolves
     *  to this when the row has a saved filename. Used by the
     *  {@link UserManagementService} mapping path and (eventually) the
     *  tenant-user picker. */
    public static String publicUrl(String userId, String saveFileNm) {
        if (saveFileNm == null || saveFileNm.isBlank()) return null;
        return PUBLIC_URI_PREFIX + userId + PUBLIC_URI_SUFFIX + "?v=" + saveFileNm;
    }

    /** Self-service streaming URL — resolves to the shared <b>non-admin</b>
     *  GET ({@code /api/v1/users/{id}/image}) so a USER-role caller can
     *  fetch their own avatar. The {@link #publicUrl} twin points at the
     *  ADMIN/{@code settings-core-users}-gated Settings endpoint and would
     *  403 an ordinary user's header avatar. */
    public static String selfPublicUrl(String userId, String saveFileNm) {
        if (saveFileNm == null || saveFileNm.isBlank()) return null;
        return "/api/v1/users/" + userId + PUBLIC_URI_SUFFIX + "?v=" + saveFileNm;
    }

    /* =================================================================
     *  Internal helpers — mirror PreferencesService
     * ================================================================= */

    private static String validateFilename(MultipartFile file) {
        if (file == null) throw new BusinessException("WBE2115");
        String original = file.getOriginalFilename();
        if (original == null) throw new BusinessException("WBE2115");
        String trimmed = original;
        int slash = Math.max(trimmed.lastIndexOf('/'), trimmed.lastIndexOf('\\'));
        if (slash >= 0) trimmed = trimmed.substring(slash + 1);
        trimmed = trimmed.trim();
        if (trimmed.isEmpty()) throw new BusinessException("WBE2115");
        return trimmed;
    }

    private static long validateSize(MultipartFile file) {
        long size = file.getSize();
        if (size <= 0) {
            throw new BusinessException("WBE2116");
        }
        if (size > MAX_IMAGE_SIZE_BYTES) {
            throw new BusinessException(
                    "WBE2117", (MAX_IMAGE_SIZE_BYTES / (1024 * 1024)) + " MB");
        }
        return size;
    }

    private static String generateOpaqueFilename(String bizKey, String userId, String extension) {
        byte[] seed = new byte[16];
        RNG.nextBytes(seed);
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm not available", ex);
        }
        md.update((bizKey == null ? "" : bizKey).getBytes(StandardCharsets.UTF_8));
        md.update((userId == null ? "" : userId).getBytes(StandardCharsets.UTF_8));
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
                    "WBE2118", String.join(", ", ALLOWED_EXTENSIONS));
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            log.warn("Failed to delete user image at {}", path, ex);
        }
    }
}
