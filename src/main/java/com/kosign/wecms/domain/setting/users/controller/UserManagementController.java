package com.kosign.wecms.domain.setting.users.controller;

import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.setting.users.dto.CheckLoginIdResponse;
import com.kosign.wecms.domain.setting.users.dto.MenuEntitlementResponse;
import com.kosign.wecms.domain.setting.users.dto.MenuEntitlementUpdateRequest;
import com.kosign.wecms.domain.setting.users.dto.UserCreateRequest;
import com.kosign.wecms.domain.setting.users.dto.UserListResponse;
import com.kosign.wecms.domain.setting.users.dto.UserRowResponse;
import com.kosign.wecms.domain.setting.users.dto.UserUpdateRequest;
import com.kosign.wecms.domain.setting.users.service.MenuEntitlementService;
import com.kosign.wecms.domain.setting.users.service.UserImageService;
import com.kosign.wecms.domain.setting.users.service.UserImageService.ImageDownload;
import com.kosign.wecms.domain.setting.users.service.UserManagementService;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
 * Settings &gt; Users — REST endpoints under {@code /api/v1/settings/users}.
 *
 * <ul>
 *   <li>{@code GET    /api/v1/settings/users}              — list (active users +
 *       plan-cap metadata).</li>
 *   <li>{@code GET    /api/v1/settings/users/check-id}     — login-id availability
 *       probe (drives the panel's "Check availability" button).</li>
 *   <li>{@code GET    /api/v1/settings/users/{user_id}}    — single-row read.</li>
 *   <li>{@code POST   /api/v1/settings/users}              — create (plan-cap
 *       enforced, throws {@code WBE_USERS_PLAN_LIMIT_REACHED} on the
 *       6th).</li>
 *   <li>{@code PUT    /api/v1/settings/users/{user_id}}    — update profile +
 *       optional password + admin toggle.</li>
 *   <li>{@code DELETE /api/v1/settings/users/{user_id}}    — per-tenant unlink
 *       (soft-delete {@code USER_BUSI_CONN_INFM} + remove
 *       {@code BUSI_ADMN_INFM}; {@code USER_INFM} master untouched).</li>
 * </ul>
 *
 * <p>Wire format is lowercase snake_case (global Jackson naming
 * strategy). Path / query / body parameters use camelCase in Java;
 * {@code @PathVariable("user_id")} pins the segment to its snake_case
 * URI name.
 *
 * <p>{@code @PreAuthorize} admin-only gating is documented as OQ-1 on
 * the feature spec — pending PO confirmation. JWT authentication is
 * the current gate (configured in {@code SecurityConfig}).
 */
@RestController
@RequestMapping("/api/v1/settings/users")
@RequiredArgsConstructor
@Validated
public class UserManagementController {

    private final UserManagementService service;
    private final MenuEntitlementService menuEntitlementService;
    private final UserImageService userImageService;

    @GetMapping
    public ApiResponse<UserListResponse> list() {
        return ApiResponse.ok(service.list());
    }

    /**
     * Availability probe — must be declared BEFORE {@link #detail(String)}
     * so Spring's path matcher doesn't route {@code /check-id} into the
     * {@code {user_id}} placeholder.
     */
    @GetMapping("/check-id")
    public ApiResponse<CheckLoginIdResponse> checkLoginId(
            @RequestParam("login_id")
            @NotBlank @Size(max = 100)
            String loginId) {
        return ApiResponse.ok(service.checkLoginId(loginId));
    }

    @GetMapping("/{user_id}")
    public ApiResponse<UserRowResponse> detail(
            @PathVariable("user_id") @NotBlank @Size(max = 100) String userId) {
        return ApiResponse.ok(service.detail(userId));
    }

    @PostMapping
    public ApiResponse<UserRowResponse> create(@Valid @RequestBody UserCreateRequest req) {
        return ApiResponse.ok(service.create(req));
    }

    @PutMapping("/{user_id}")
    public ApiResponse<UserRowResponse> update(
            @PathVariable("user_id") @NotBlank @Size(max = 100) String userId,
            @Valid @RequestBody UserUpdateRequest req) {
        return ApiResponse.ok(service.update(userId, req));
    }

    @DeleteMapping("/{user_id}")
    public ApiResponse<Void> delete(
            @PathVariable("user_id") @NotBlank @Size(max = 100) String userId) {
        service.delete(userId);
        return ApiResponse.ok(null);
    }

    // ----------------------------------------------------- menu entitlement

    @GetMapping("/{user_id}/menu-entitlement")
    public ApiResponse<MenuEntitlementResponse> getMenuEntitlement(
            @PathVariable("user_id") @NotBlank @Size(max = 100) String userId) {
        return ApiResponse.ok(menuEntitlementService.load(userId));
    }

    @PutMapping("/{user_id}/menu-entitlement")
    public ApiResponse<MenuEntitlementResponse> updateMenuEntitlement(
            @PathVariable("user_id") @NotBlank @Size(max = 100) String userId,
            @Valid @RequestBody MenuEntitlementUpdateRequest req) {
        return ApiResponse.ok(menuEntitlementService.save(userId, req));
    }

    // ------------------------------------------------------------- user image

    /**
     * Upload a new avatar for the target user. Multipart field name is
     * {@code file}. Service caps at 2 MB and an extension allow-list
     * (JPG / JPEG / PNG / GIF / SVG). On success the response carries
     * the refreshed user row with a non-null {@code user_img_url}
     * pointing at the streaming GET below; the cache-busting suffix on
     * {@code user_img_url} flips on every upload so the browser
     * refreshes the image without manual reload.
     */
    @PostMapping(value = "/{user_id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<UserRowResponse> uploadImage(
            @PathVariable("user_id") @NotBlank @Size(max = 100) String userId,
            @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(userImageService.upload(userId, file));
    }

    /**
     * Clear the target user's avatar. Idempotent — a user with no
     * avatar set still gets a 200 and the unchanged row back.
     */
    @DeleteMapping("/{user_id}/image")
    public ApiResponse<UserRowResponse> deleteImage(
            @PathVariable("user_id") @NotBlank @Size(max = 100) String userId) {
        return ApiResponse.ok(userImageService.delete(userId));
    }

    /**
     * Stream the raw avatar bytes back. Used by the frontend's
     * {@code <img src>} (wrapped in a Blob fetch so the JWT
     * Authorization header travels). {@code WBE2113} when no avatar
     * is set; {@code WBE2114} when the row points at a missing file.
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
}
