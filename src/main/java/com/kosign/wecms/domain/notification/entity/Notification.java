package com.kosign.wecms.domain.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

/**
 * One in-app notification, one row per recipient (fan-out model).
 * Refactor-native unified store — see {@code V238__notification_schema.sql}
 * for why this is NOT a column-for-column legacy port of
 * {@code BLTB_NOTI_INFM} / {@code GENL_NOTI_*}.
 *
 * <p>Read state lives in {@link #readAt}: {@code null} = unread, a
 * {@code yyyyMMddHHmmss} string = the moment it was marked read. The
 * visible text is never stored here — {@link #messageCode} +
 * {@link #messageParams} are resolved by the frontend i18n layer (PO
 * decision 2026-05-... in {@code docs/refactor-wecms/features/notifications.md}).
 *
 * <p>Does NOT extend {@code BaseEntity}: like the other refactor tables
 * (memo_infm, approval-line) the audit columns are {@code VARCHAR(14)}
 * {@code yyyyMMddHHmmss} strings stamped explicitly by the service, not
 * {@code OffsetDateTime}. {@code REG_*} carries the requesting user who
 * triggered the fan-out; {@code MOD_*} is touched on mark-read.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "NOTIFICATION")
public class Notification {

    /** Global IDENTITY surrogate — see migration header for the rationale
     *  (avoids the per-tenant MAX+1 race in the best-effort fan-out). All
     *  reads still scope on {@code (bizKey, recipientId)}. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "NOTIFICATION_ID")
    private Long notificationId;

    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    @Column(name = "RECIPIENT_ID", length = 100, nullable = false)
    private String recipientId;

    /** Producer discriminator — v1 writes only {@code "APPROVAL_REQUEST"}. */
    @Column(name = "NOTI_TYPE", length = 30, nullable = false)
    private String notiType;

    /** Referenced source id — {@code EXPT_RESOL_MSTR.APPR_SEQ_NO} for
     *  approval-request notifications. */
    @Column(name = "SOURCE_ID", length = 50)
    private String sourceId;

    /** i18n key the frontend resolves (e.g. {@code notification.approval.requested}). */
    @Column(name = "MESSAGE_CODE", length = 100, nullable = false)
    private String messageCode;

    /** Flat interpolation params for {@link #messageCode}. Mapped to the
     *  {@code MESSAGE_PARAMS jsonb} column via Hibernate's JSON type. Keys
     *  are stored verbatim (Jackson's snake_case strategy does not rewrite
     *  Map keys) so the producer writes snake_case keys directly. */
    @Column(name = "MESSAGE_PARAMS")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, String> messageParams;

    /** Refactor slug route (+ query params) the bell navigates to on click. */
    @Column(name = "LINK_TARGET", length = 500)
    private String linkTarget;

    /** {@code null} = unread; {@code yyyyMMddHHmmss} = read timestamp. */
    @Column(name = "READ_AT", length = 14)
    private String readAt;

    /** Soft-delete flag. v1 never sets {@code 'Y'}; reads filter {@code 'N'}. */
    @Column(name = "DEL_YN", length = 1, nullable = false)
    private String delYn;

    @Column(name = "REG_ID", length = 100, nullable = false)
    private String regId;

    @Column(name = "REG_DTM", length = 14, nullable = false)
    private String regDtm;

    @Column(name = "MOD_ID", length = 100, nullable = false)
    private String modId;

    @Column(name = "MOD_DTM", length = 14, nullable = false)
    private String modDtm;
}
