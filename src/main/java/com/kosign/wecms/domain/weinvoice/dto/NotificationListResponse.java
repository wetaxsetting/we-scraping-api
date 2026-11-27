package com.kosign.wecms.domain.weinvoice.dto;

import java.util.List;

/**
 * {@code GET /notifications}. {@code unread_count} counts the unread items of the same filter (all pages);
 * {@code page} is zero-based.
 */
public record NotificationListResponse(
        List<NotificationItem> items,
        long unreadCount,
        long totalCount,
        int page,
        int size) {
}
