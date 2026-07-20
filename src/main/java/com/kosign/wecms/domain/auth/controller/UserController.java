package com.kosign.wecms.domain.auth.controller;

import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.auth.dto.TenantUserListResponse;
import com.kosign.wecms.domain.auth.service.UserDirectoryService;
import com.kosign.wecms.domain.setting.users.service.UserImageService;
import com.kosign.wecms.domain.setting.users.service.UserImageService.ImageDownload;
import com.kosign.wecms.domain.setting.users.service.UserImageService.UserImageResult;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.Duration;

/**
 * Tenant-user directory endpoint &mdash; drives every approver /
 * signer picker in the refactor. Mirrors the read path of legacy
 * {@code comm_0192_001_r001} ("Inquiry Approver").
 *
 * <ul>
 *   <li>{@code GET /api/v1/users}
 *       &mdash; list active users for the current tenant (optional
 *       case-insensitive name substring filter).</li>
 *   <li>{@code GET /api/v1/users/{user_id}/image}
 *       &mdash; stream the user's avatar bytes. The shared,
 *       non-admin counterpart of the admin-gated
 *       {@code /api/v1/settings/users/{id}/image}: every approver /
 *       signer / notification-sender avatar URL already points here
 *       ({@code ApprovalLineService}, {@code NotificationService},
 *       {@code UserDirectoryService} all emit
 *       {@code /api/v1/users/{id}/image}). Reuses the exact
 *       tenant-scoped resolution + storage layout as the Settings
 *       Users avatar via {@link UserImageService#open(String)}.</li>
 * </ul>
 *
 * <p>Wire format is lowercase snake_case. The {@code q} query
 * parameter caps at 100 chars (legacy {@code USER_NM} length cap)
 * via the class-level {@link Validated} + {@link Size}; missing /
 * blank values fetch the entire tenant directory.
 *
 * <p>JWT authentication is the only gate. No {@code @PreAuthorize}
 * &mdash; legacy WSVC carries {@code NORM_USER_ONLY_YN = Y} only,
 * and WTA users never reach this app surface anyway. Viewing a
 * tenant colleague's avatar is not a privilege-escalation vector
 * (the bytes are tenant-scoped in {@link UserImageService#open});
 * gating it behind the Settings-Users admin permission would 403 the
 * ordinary users who view approval lines and payment approval popups.
 *
 * <p>Legacy reference + design rationale:
 * {@code docs/refactor-wecms/migration/approval-line-add-flow-analysis.md}.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Validated
public class UserController {

    private final UserDirectoryService userDirectoryService;
    private final UserImageService userImageService;

    /**
     * Tenant-user directory for picker / lookup surfaces.
     *
     * @param q optional case-insensitive substring filter against
     *          {@code USER_INFM.USER_NM}. {@code null} / blank returns
     *          the entire active directory for the tenant.
     */
    @GetMapping
    public ApiResponse<TenantUserListResponse> list(
            @RequestParam(name = "q", required = false)
            @Size(max = 100)
            String q) {
        return ApiResponse.ok(userDirectoryService.list(q));
    }

    /**
     * Stream the tenant user's avatar bytes. Shared, non-admin
     * counterpart of {@code UserManagementController.streamImage} — it
     * reuses the same {@link UserImageService#open(String)} resolution
     * (tenant-scoped {@code USER_BUSI_CONN_INFM.USER_IMG} lookup +
     * per-tenant storage subtree) so there is one avatar-storage
     * contract, not two. Consumed by every approver / signer /
     * notification-sender {@code <UserAvatar>} on the frontend (the URL
     * is wrapped in a JWT-bearing Blob fetch so the Authorization
     * header travels — a plain {@code <img src>} would omit it).
     *
     * <p>{@code WBE2113} when no avatar is set (frontend degrades to
     * the initials disc); {@code WBE2114} when the row points at a
     * missing file.
     */
    @GetMapping("/{user_id}/image")
    public ResponseEntity<InputStreamResource> streamImage(
            @PathVariable("user_id") @NotBlank @Size(max = 100) String userId) throws IOException {
        ImageDownload image = userImageService.open(userId);
        InputStream stream = Files.newInputStream(image.path());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .contentLength(image.size())
                .header("X-Content-Type-Options", "nosniff")
                // ?v=<save filename> on the wire URL flips after every
                // upload, so a private cache cannot survive a re-upload.
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(new InputStreamResource(stream));
    }

    /**
     * Self-service avatar upload for the <b>signed-in user</b> (header
     * profile menu &rarr; pencil "edit photo"). Multipart part name is
     * {@code file}.
     *
     * <p>JWT-only, no {@code @PreAuthorize}: the target is always
     * {@link com.kosign.wecms.common.tenant.TenantContext#currentUserId()},
     * so a user can only change their OWN photo. An admin changing another
     * user's photo goes through the ADMIN-gated
     * {@code POST /api/v1/settings/users/{id}/image}. Mirrors legacy
     * {@code szon_0012_003_u002_act.jsp} (forces {@code USER_ID} to the
     * login user for non-admins).
     *
     * <p>Service caps at 2 MB and a JPG / JPEG / PNG / GIF allow-list
     * (SVG rejected, F-02). On success the response carries the refreshed
     * {@code user_img_url} pointing at the non-admin streaming GET above,
     * cache-busted so the header avatar refreshes without a manual reload.
     */
    @PostMapping(value = "/me/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<UserImageResult> uploadMyImage(@RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(userImageService.uploadForCurrentUser(file));
    }

    /**
     * Clear the signed-in user's own avatar. Idempotent — a user with no
     * avatar set still gets a 200 and a {@code null} {@code user_img_url}.
     */
    @DeleteMapping("/me/image")
    public ApiResponse<UserImageResult> deleteMyImage() {
        return ApiResponse.ok(userImageService.deleteForCurrentUser());
    }
}
