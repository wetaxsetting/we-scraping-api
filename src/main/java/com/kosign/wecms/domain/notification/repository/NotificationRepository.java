package com.kosign.wecms.domain.notification.repository;

import com.kosign.wecms.domain.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Read + write repository for {@code NOTIFICATION}. Every query scopes on
 * {@code (bizKey, recipientId)} — the caller's tenant + user id from
 * {@link com.kosign.wecms.common.tenant.TenantContext} — so a request can
 * never read or mutate another user's (or another tenant's) notifications.
 *
 * <p>{@code DEL_YN = 'N'} is enforced on every read path; the soft-delete
 * archive feature (Phase 4) flips the flag without changing these queries.
 */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** All-tab list — every non-deleted notification for the caller,
     *  newest first (the {@link Pageable} carries the
     *  {@code reg_dtm DESC, notification_id DESC} sort). */
    Page<Notification> findByBizKeyAndRecipientIdAndDelYn(
            String bizKey, String recipientId, String delYn, Pageable pageable);

    /** Unread-tab list — non-deleted notifications the caller has not yet
     *  read ({@code read_at IS NULL}). */
    Page<Notification> findByBizKeyAndRecipientIdAndDelYnAndReadAtIsNull(
            String bizKey, String recipientId, String delYn, Pageable pageable);

    /** Header-bell badge — count of unread, non-deleted notifications. */
    long countByBizKeyAndRecipientIdAndDelYnAndReadAtIsNull(
            String bizKey, String recipientId, String delYn);

    /** Single-row fetch for mark-read, scoped to the owner so a caller
     *  cannot mark another user's notification read. Returns empty (→
     *  {@code WBE_NOT_FOUND}) when the id is missing, deleted, owned by
     *  another user, or belongs to another tenant. */
    Optional<Notification> findByNotificationIdAndBizKeyAndRecipientIdAndDelYn(
            Long notificationId, String bizKey, String recipientId, String delYn);

    /**
     * Mark-all-read — stamp {@code read_at} (+ {@code mod_*}) on every
     * currently-unread, non-deleted notification for the caller. Returns the
     * number of rows flipped so the service can report / log it.
     */
    @Modifying(clearAutomatically = true)
    @Query("""
           UPDATE Notification n
              SET n.readAt = :now, n.modId = :userId, n.modDtm = :now
            WHERE n.bizKey      = :bizKey
              AND n.recipientId = :userId
              AND n.delYn       = 'N'
              AND n.readAt IS NULL
           """)
    int markAllRead(@Param("bizKey") String bizKey,
                    @Param("userId") String userId,
                    @Param("now")    String now);
}
