package com.kosign.wecms.domain.weinvoice.dto;

/** {@code GET /notifications/unread-count} — unread notifications across all tax codes of the user. */
public record UnreadCountResponse(long unreadCount) {
}
