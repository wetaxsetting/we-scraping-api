package com.kosign.wecms.domain.notification.dto;

/**
 * Header-bell badge payload — the caller's count of unread, non-deleted
 * notifications. Wire field {@code total_count} via the global SNAKE_CASE
 * Jackson naming strategy.
 */
public record UnreadCountResponse(long totalCount) { }
