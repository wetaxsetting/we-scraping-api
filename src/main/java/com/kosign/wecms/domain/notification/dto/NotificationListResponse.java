package com.kosign.wecms.domain.notification.dto;

import java.util.List;

/**
 * Notification list response — a page slice plus the caller's current unread
 * count (so the header bell can refresh its badge from the same round-trip
 * the dropdown uses). Pagination metadata mirrors the Spring-Data-style shape
 * used by {@code ApprovalBoxListResponse}.
 *
 * <p>Wire names auto-derived from camelCase via the global SNAKE_CASE Jackson
 * naming strategy.
 */
public record NotificationListResponse(
        List<NotificationRow> list,
        long                  totalCount,
        long                  unreadCount,
        int                   pageNumber,
        int                   pageSize,
        int                   totalPages
) { }
