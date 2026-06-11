package com.kosign.wecms.domain.notification.controller;

import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.notification.dto.NotificationListResponse;
import com.kosign.wecms.domain.notification.dto.UnreadCountResponse;
import com.kosign.wecms.domain.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Notification module — REST endpoints for the header bell
 * (slug: notifications, legacy reference {@code comm_0192_007}).
 *
 * <p>Wire format is lowercase snake_case; Java parameter names stay camelCase
 * and {@code @RequestParam(value=…)} pins each to its snake_case wire name.
 * Validation + tenant/user scoping live in the service; the controller is a
 * thin pass-through wrapping the result in {@link ApiResponse}.
 *
 * <p>Authorization: under {@code /api/v1/*} and therefore
 * {@code authenticated()} per {@code SecurityConfig}; every read / write is
 * further scoped to the JWT-bound user + tenant inside the service.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService service;

    /**
     * List the caller's notifications, newest first.
     *
     * @param readType {@code "U"} = unread only; {@code "A"} / omitted = all
     * @param page     zero-based page index (default {@code 0})
     * @param size     rows per page (default
     *                 {@link NotificationService#DEFAULT_PAGE_SIZE}, clamped to
     *                 {@link NotificationService#MAX_PAGE_SIZE})
     */
    @GetMapping
    public ApiResponse<NotificationListResponse> list(
            @RequestParam(value = "read_type", required = false)   String readType,
            @RequestParam(value = "page",      defaultValue = "0") int page,
            @RequestParam(value = "size",      defaultValue = "0") int size) {
        return ApiResponse.ok(service.list(readType, page, size));
    }

    /** Unread-count for the header-bell badge. */
    @GetMapping("/unread-count")
    public ApiResponse<UnreadCountResponse> unreadCount() {
        return ApiResponse.ok(service.unreadCount());
    }

    /** Mark one notification read (idempotent). */
    @PutMapping("/{notificationId}/read")
    public ApiResponse<Void> markRead(@PathVariable Long notificationId) {
        service.markRead(notificationId);
        return ApiResponse.ok(null);
    }

    /** Mark every unread notification for the caller read. */
    @PutMapping("/read-all")
    public ApiResponse<Void> markAllRead() {
        service.markAllRead();
        return ApiResponse.ok(null);
    }
}
