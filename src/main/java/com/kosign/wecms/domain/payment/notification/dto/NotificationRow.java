package com.kosign.wecms.domain.payment.notification.dto;

/**
 * Single row in the Notifications popup list response. Legacy-faithful v1
 * contract (OD-A in
 * {@code docs/audits/notification-popup-legacy-vs-refactor-analysis.md}):
 * the wire carries the <b>rendered</b> {@link #title} / {@link #content}
 * stored per-language in {@code BLTB_NOTI_CONT_INFM}, NOT an i18n
 * {@code message_code} / {@code message_params} pair — legacy JEX stores
 * already-translated strings, there is no message-code catalogue to derive.
 *
 * <p>Field names auto-snake_case via the global {@code JacksonConfig}
 * naming strategy (no per-field {@code @JsonProperty}). The set covers the
 * frontend's navigation needs:
 * <ul>
 *   <li>{@link #notificationId} / {@link #bltbCd} / {@link #notiSeqNo} —
 *       identity. {@code notification_id} mirrors {@code noti_seq_no} (the
 *       per-tenant sequence) so the existing frontend client's single
 *       numeric id ({@code markNotificationRead(id)}) resolves a row
 *       together with the tenant + recipient from the JWT.</li>
 *   <li>{@link #notiJson} / {@link #notiWsvc} — the legacy navigation
 *       payload + target screen; the frontend decodes these to route on
 *       click (legacy {@code fn_readOneCommNoti}).</li>
 *   <li>{@link #delYn} — soft-delete flag; the frontend shows the
 *       "notification was deleted" dialog ({@code WBI1098}) for {@code 'Y'}
 *       rows instead of navigating.</li>
 *   <li>{@link #read} — convenience boolean ({@code ACTV_YN == 'Y'}); the
 *       raw {@link #actvYn} is also carried for parity.</li>
 * </ul>
 */
public record NotificationRow(

        /** Per-tenant notification sequence — {@code BLTB_NOTI_INFM.NOTI_SEQ_NO}.
         *  Mirrors {@link #notiSeqNo}; doubles as the row id the mark-read
         *  endpoint takes on its path. */
        Long notificationId,

        /** Bulletin board code segment of the legacy composite PK
         *  ({@code BLTB_NOTI_INFM.BLTB_CD}) — carried for completeness /
         *  forensic parity. */
        String bltbCd,

        /** Same numeric value as {@link #notificationId} — the legacy
         *  field name, kept so the frontend can use either. */
        Long notiSeqNo,

        /** Rendered title in the caller's language — {@code BLTB_NOTI_CONT_INFM.TITL}. */
        String title,

        /** Rendered content in the caller's language — {@code BLTB_NOTI_CONT_INFM.CONT}
         *  (legacy stores {@code ""} for approval notifications). */
        String content,

        /** Sender login id — {@code BLTB_NOTI_INFM.SND_ID}. */
        String sndId,

        /** Sender display name — {@code BLTB_NOTI_INFM.SND_NAME}. */
        String sndName,

        /** Navigation payload JSON — {@code BLTB_NOTI_INFM.NOTI_JSON}
         *  (e.g. {@code POST_SEQ_NO} / {@code APPR_DV} / {@code APPR_SEQ_NO}).
         *  Opaque string; the frontend parses it. */
        String notiJson,

        /** Legacy target screen — {@code BLTB_NOTI_INFM.NOTI_WSVC}
         *  (e.g. {@code comm_0193_001.act}). */
        String notiWsvc,

        /** Soft-delete flag — {@code 'Y'} / {@code 'N'}. {@code 'Y'} rows
         *  are still listed (legacy parity). */
        String delYn,

        /** {@code true} when the notification has been read
         *  ({@code ACTV_YN == 'Y'}). */
        boolean read,

        /** Raw read flag — {@code 'N'} unread / {@code 'Y'} read. */
        String actvYn,

        /** Creation timestamp {@code yyyyMMddHHmmss} (UTC) — list sort key;
         *  the frontend formats it. */
        String regDtm,

        /** Last-modified timestamp {@code yyyyMMddHHmmss} (UTC) — stamped on
         *  mark-read (the closest proxy to a read-at timestamp; legacy has
         *  no dedicated read-at column). */
        String modDtm,

        /** Sender-image URL, or {@code null} when the sender has no image.
         *  Slug-based ({@code /api/v1/users/{snd_id}/image}) for consistency
         *  with the approval-line precedent; the image endpoint itself is
         *  out of scope for this MR. */
        String userImgUrl

) { }
