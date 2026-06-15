package com.kosign.wecms.domain.payment.notification.controller;

import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.payment.notification.dto.NotificationListResponse;
import com.kosign.wecms.domain.payment.notification.dto.UnreadCountResponse;
import com.kosign.wecms.domain.payment.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Notifications popup REST API — the header bell. Ports the legacy
 * {@code comm_0192_007} WSVC family (list / unread-count / mark-read /
 * mark-all-read) to Spring, scoped to the BLTB (approval/bulletin) branch
 * of notifications. The General-notification ({@code TYPE='G'}) branch is
 * intentionally not ported — its {@code GENL_NOTI_*} tables are out of the
 * refactor legacy-table-inventory (see
 * {@code docs/audits/notification-popup-legacy-vs-refactor-analysis.md}).
 *
 * <p>Wire format is lowercase snake_case; the controller is a thin
 * pass-through that wraps the service result in {@link ApiResponse}. The
 * caller's tenant + recipient are read from the JWT-backed
 * {@code TenantContext} inside the service — never from the request.
 *
 * <p><b>Authorization:</b> every endpoint is under {@code /api/v1/*} and is
 * therefore {@code authenticated()} per {@code SecurityConfig}. The bell is
 * a global header surface shown to every authenticated user regardless of
 * menu grants, so it carries no per-screen {@code @menuAuthz} gate — same
 * rationale as {@code ScreenOptionController} / {@code NavigationController}.
 * It is enumerated in {@code EndpointAuthorizationCoverageTest}'s
 * allow-list so the omission is explicit, not accidental.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService service;

    /**
     * List the caller's notifications. Mirrors legacy
     * {@code comm_0192_007_r001}.
     *
     * @param readType {@code U} (unread tab) or {@code A} (all tab,
     *                 default). Anything other than {@code U} ⇒ all.
     * @param page     zero-based page index (default {@code 0}).
     * @param size     rows per page (default
     *                 {@link NotificationService#DEFAULT_PAGE_SIZE},
     *                 clamped to {@link NotificationService#MAX_PAGE_SIZE}).
     * @param search   optional sender-name keyword (partial, case-insensitive).
     */
    @GetMapping
    public ApiResponse<NotificationListResponse> list(
            @RequestParam(value = "read_type", defaultValue = "A") String readType,
            @RequestParam(value = "page",      defaultValue = "0") int page,
            @RequestParam(value = "size",      defaultValue = "0") int size,
            @RequestParam(value = "search",    required = false)   String search) {
        return ApiResponse.ok(service.list(readType, search, page, size));
    }

    /**
     * Unread count for the header-bell badge. Mirrors legacy
     * {@code comm_0192_007_r002}.
     */
    @GetMapping("/unread-count")
    public ApiResponse<UnreadCountResponse> unreadCount() {
        return ApiResponse.ok(service.unreadCount());
    }

    /**
     * Mark one notification read. Mirrors legacy
     * {@code comm_0192_007_u001} (single REC). Tenant + recipient scoped;
     * idempotent. Unknown / other-tenant id ⇒
     * {@code WBE_NOTIFICATION_NOT_FOUND}.
     */
    @PutMapping("/{notificationId}/read")
    public ApiResponse<Void> markRead(@PathVariable long notificationId) {
        service.markRead(notificationId);
        return ApiResponse.ok((Void) null);
    }

    /**
     * Mark every unread notification read for the caller ("Read All").
     * Mirrors legacy {@code comm_0192_007_u001} with the full list as
     * {@code REC[]}.
     */
    @PutMapping("/read-all")
    public ApiResponse<Void> markAllRead() {
        service.markAllRead();
        return ApiResponse.ok((Void) null);
    }
}
