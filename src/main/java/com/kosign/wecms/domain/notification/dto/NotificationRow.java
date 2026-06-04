package com.kosign.wecms.domain.notification.dto;

import com.kosign.wecms.domain.notification.entity.Notification;

import java.util.Map;

/**
 * One row in the notification list response. The visible title/body are not
 * carried here — the frontend renders them from {@link #messageCode} +
 * {@link #messageParams} via the {@code notification.*} i18n keys (PO
 * decision, {@code docs/refactor-wecms/features/notifications.md}).
 *
 * <p>Wire names auto-derived from camelCase via the global SNAKE_CASE Jackson
 * naming strategy ({@code notificationId} → {@code notification_id}, etc.).
 * {@code messageParams} serialises as a nested JSON object whose keys are
 * passed through verbatim (Jackson does not rewrite Map keys).
 */
public record NotificationRow(

        /** Row id — path id for {@code PUT /notifications/{id}/read}. */
        Long notificationId,

        /** Producer discriminator ({@code APPROVAL_REQUEST} in v1). */
        String notiType,

        /** Referenced source id ({@code APPR_SEQ_NO} for approval requests). */
        String sourceId,

        /** i18n key the frontend resolves to the visible message. */
        String messageCode,

        /** Interpolation params for {@link #messageCode}. */
        Map<String, String> messageParams,

        /** Refactor slug route (+ params) the bell navigates to on click. */
        String linkTarget,

        /** {@code true} once the notification has been read. */
        boolean read,

        /** Read timestamp ({@code yyyyMMddHHmmss}); {@code null} when unread. */
        String readAt,

        /** Creation timestamp ({@code yyyyMMddHHmmss}) — list sort key. */
        String regDtm
) {

    /** Project a persisted entity onto the wire row. */
    public static NotificationRow from(Notification n) {
        return new NotificationRow(
                n.getNotificationId(),
                n.getNotiType(),
                n.getSourceId(),
                n.getMessageCode(),
                n.getMessageParams(),
                n.getLinkTarget(),
                n.getReadAt() != null,
                n.getReadAt(),
                n.getRegDtm());
    }
}
