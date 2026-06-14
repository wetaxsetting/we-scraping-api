package com.kosign.wecms.domain.payment.notification.dto;

import java.util.List;

/**
 * Notifications popup list response. Matches the frontend wire shape
 * ({@code wecms-web/lib/types/api.ts ApiNotificationListResponse}) — page
 * slice plus the two counts the bell needs.
 *
 * <p>{@link #totalCount} is the total for the <em>current</em>
 * {@code read_type} filter (so on the Unread tab it equals
 * {@link #unreadCount}); {@link #unreadCount} is always the unread total
 * for the badge regardless of tab. Pagination metadata follows the
 * Spring-Data zero-based convention used across the refactor
 * ({@link com.kosign.wecms.domain.setting.erptransactionhistory.dto.ErpTrnsHistoryListResponse}).
 *
 * <p>Wire names auto-derived from camelCase via the global SNAKE_CASE
 * Jackson naming strategy.
 */
public record NotificationListResponse(

        /** Rows on the requested page. */
        List<NotificationRow> list,

        /** Total rows for the current {@code read_type} filter. */
        long totalCount,

        /** Unread total for the bell badge (always {@code ACTV_YN='N'}). */
        long unreadCount,

        /** Zero-based page index (Spring Data convention). */
        int pageNumber,

        /** Rows per page; echoed back from the request. */
        int pageSize,

        /** Total page count for the current filter. */
        int totalPages

) { }
