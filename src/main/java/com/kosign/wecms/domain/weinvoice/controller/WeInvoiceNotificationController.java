package com.kosign.wecms.domain.weinvoice.controller;

import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.weinvoice.dto.NotificationListResponse;
import com.kosign.wecms.domain.weinvoice.dto.NotificationReadAllRequest;
import com.kosign.wecms.domain.weinvoice.dto.NotificationReadRequest;
import com.kosign.wecms.domain.weinvoice.dto.UnreadCountResponse;
import com.kosign.wecms.domain.weinvoice.service.WeInvoiceNotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Header bell — notifications per tax code (new invoices, XML / PDF obtained, GDT and mailbox login
 * failures). Owner = the JWT user; read state per user. The two writes answer HTTP 204 without body.
 * Record: docs/features/addon-service/weinvoice/weinvoice-notifications-2026-11-12.md.
 */
@RestController
@RequestMapping("/api/v1/weinvoice/notifications")
@RequiredArgsConstructor
@Validated
public class WeInvoiceNotificationController {

    private final WeInvoiceNotificationService notifications;

    /** {@code filter} mst (default; needs {@code company_mst}) · all · action; {@code size} default 20, max 100. */
    @GetMapping
    public ApiResponse<NotificationListResponse> list(
            @RequestParam(name = "company_mst", required = false) String companyMst,
            @RequestParam(name = "filter", required = false) String filter,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ApiResponse.ok(notifications.list(companyMst, filter, page, size));
    }

    /** Unread across every tax code of the user; the bell polls it every 60 s. */
    @GetMapping("/unread-count")
    public ApiResponse<UnreadCountResponse> unreadCount() {
        return ApiResponse.ok(notifications.unreadCount());
    }

    /** Marks up to 100 ids read; unknown ids are ignored. HTTP 204. */
    @PostMapping("/read")
    public ResponseEntity<Void> read(@Valid @RequestBody NotificationReadRequest request) {
        notifications.markRead(request.ids());
        return ResponseEntity.noContent().build();
    }

    /** Body optional: {@code company_mst} → that tax code, none → every tax code. HTTP 204. */
    @PostMapping("/read-all")
    public ResponseEntity<Void> readAll(@RequestBody(required = false) NotificationReadAllRequest request) {
        notifications.markAllRead(request == null ? null : request.companyMst());
        return ResponseEntity.noContent().build();
    }
}
