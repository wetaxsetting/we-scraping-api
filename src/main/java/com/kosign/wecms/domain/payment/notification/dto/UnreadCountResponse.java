package com.kosign.wecms.domain.payment.notification.dto;

/**
 * Unread-count response for the bell badge. Matches the frontend wire
 * shape ({@code wecms-web/lib/types/api.ts ApiUnreadCountResponse}).
 * Mirrors legacy {@code comm_0192_007_r002}'s {@code TOT_CNT}
 * (the legacy {@code TOT_APPRVR_CNT} field is dead — never populated by
 * the action — and is intentionally not carried).
 */
public record UnreadCountResponse(long totalCount) { }
