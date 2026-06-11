package com.kosign.wecms.domain.notification.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.notification.dto.NotificationListResponse;
import com.kosign.wecms.domain.notification.dto.NotificationRow;
import com.kosign.wecms.domain.notification.dto.UnreadCountResponse;
import com.kosign.wecms.domain.notification.entity.Notification;
import com.kosign.wecms.domain.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Notification module service — Phase 1 backend foundation
 * (slug: notifications, legacy reference {@code comm_0192_007}).
 *
 * <h2>Read side (header bell)</h2>
 * {@link #list} / {@link #unreadCount} / {@link #markRead} /
 * {@link #markAllRead}. Every path scopes on
 * {@link TenantContext#currentBizKey()} + {@link TenantContext#currentUserId()}
 * so a caller only ever sees / mutates their own notifications. A row owned
 * by another user or tenant surfaces as {@code WBE_NOT_FOUND} — never leaked.
 *
 * <h2>Write side (v1 producer)</h2>
 * {@link #notifyApprovalRequested} is the only producer in v1: when a payment
 * approval request is submitted it fans one notification out per approver in
 * the chain. This method is a NON-transactional safety wrapper — it delegates
 * the inserts to {@link NotificationApprovalWriter#writeApprovalRequest} (which
 * owns the {@code REQUIRES_NEW} transaction) and catches every throwable,
 * including the {@code UnexpectedRollbackException} raised at the inner-tx
 * commit boundary. A notification failure can therefore NEVER roll back a valid
 * approval submission (PO decision 2026-06-02, "best-effort / isolated"). The
 * rare cost is an orphan notification if the parent submit later rolls back —
 * acceptable for an in-app bell, and the click-navigation already tolerates a
 * deleted source. Hardening to async/outbox is a follow-up phase.
 *
 * <p>The producer takes the payment-type <em>slug</em> (e.g.
 * {@code "internal_transfer"}), not the {@code ApprDv} enum, so this module
 * carries no dependency on the {@code approvalbox} package.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE     = 100;

    /** Active (non-deleted) row marker. */
    private static final String DEL_YN_ACTIVE = "N";

    /** {@code read_type=U} selects the unread tab; anything else = all. */
    private static final String READ_TYPE_UNREAD = "U";

    /** Default list order — newest first, id tie-broken for stable paging. */
    private static final Sort DEFAULT_SORT =
            Sort.by(Sort.Order.desc("regDtm"), Sort.Order.desc("notificationId"));

    /** UTC {@code yyyyMMddHHmmss} — same shape the approval fan-out stamps
     *  on {@code REG_DTM}, so string ORDER BY stays chronological across
     *  the two writers. */
    private static final DateTimeFormatter DTM14 = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final NotificationRepository      repository;
    private final NotificationApprovalWriter  approvalWriter;
    private final Clock                       clock;

    /* ================================================================
     *  Read side
     * ================================================================ */

    /**
     * List the caller's notifications.
     *
     * @param readType {@code "U"} = unread only; {@code "A"} / null = all.
     * @param page     zero-based page index (negative clamps to 0).
     * @param size     rows per page (≤ 0 → {@link #DEFAULT_PAGE_SIZE};
     *                 clamped to {@link #MAX_PAGE_SIZE}).
     */
    @Transactional(readOnly = true)
    public NotificationListResponse list(String readType, int page, int size) {
        String bizKey = TenantContext.currentBizKey();
        String userId = TenantContext.currentUserId();

        int pageSize   = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        int pageNumber = Math.max(page, 0);
        Pageable pageable = PageRequest.of(pageNumber, pageSize, DEFAULT_SORT);

        boolean unreadOnly = READ_TYPE_UNREAD.equalsIgnoreCase(readType);
        Page<Notification> result = unreadOnly
                ? repository.findByBizKeyAndRecipientIdAndDelYnAndReadAtIsNull(
                        bizKey, userId, DEL_YN_ACTIVE, pageable)
                : repository.findByBizKeyAndRecipientIdAndDelYn(
                        bizKey, userId, DEL_YN_ACTIVE, pageable);

        long unread = repository.countByBizKeyAndRecipientIdAndDelYnAndReadAtIsNull(
                bizKey, userId, DEL_YN_ACTIVE);

        List<NotificationRow> rows = result.getContent().stream()
                .map(NotificationRow::from)
                .toList();

        return new NotificationListResponse(
                rows, result.getTotalElements(), unread,
                result.getNumber(), result.getSize(), result.getTotalPages());
    }

    /** Unread-count for the header-bell badge. */
    @Transactional(readOnly = true)
    public UnreadCountResponse unreadCount() {
        long count = repository.countByBizKeyAndRecipientIdAndDelYnAndReadAtIsNull(
                TenantContext.currentBizKey(), TenantContext.currentUserId(), DEL_YN_ACTIVE);
        return new UnreadCountResponse(count);
    }

    /**
     * Mark one notification read. Idempotent — a no-op if already read.
     *
     * @throws BusinessException {@code WBE_VALIDATION} when {@code id} is
     *         null; {@code WBE_NOT_FOUND} when the id is missing, deleted,
     *         or not owned by the caller's user + tenant.
     */
    @Transactional
    public void markRead(Long id) {
        if (id == null) {
            throw new BusinessException("WBE_VALIDATION", "notification_id: required");
        }
        String bizKey = TenantContext.currentBizKey();
        String userId = TenantContext.currentUserId();

        Notification n = repository
                .findByNotificationIdAndBizKeyAndRecipientIdAndDelYn(id, bizKey, userId, DEL_YN_ACTIVE)
                .orElseThrow(() -> new BusinessException("WBE_NOT_FOUND", "Notification not found"));

        if (n.getReadAt() == null) {
            String now = nowDtm();
            n.setReadAt(now);
            n.setModId(userId);
            n.setModDtm(now);
            repository.save(n);
        }
    }

    /**
     * Mark every unread notification for the caller read.
     *
     * @return the number of rows flipped (0 when nothing was unread).
     */
    @Transactional
    public int markAllRead() {
        String bizKey = TenantContext.currentBizKey();
        String userId = TenantContext.currentUserId();
        int updated = repository.markAllRead(bizKey, userId, nowDtm());
        log.debug("notifications mark-all-read bizKey={} userId={} updated={}", bizKey, userId, updated);
        return updated;
    }

    /* ================================================================
     *  Write side — v1 producer
     * ================================================================ */

    /**
     * Fan an approval-request notification out to every approver in the chain.
     *
     * <p><b>Best-effort + airtight isolation.</b> The actual INSERTs run in
     * {@link NotificationApprovalWriter#writeApprovalRequest} under a
     * {@code REQUIRES_NEW} transaction; this wrapper is intentionally
     * NON-transactional and catches every throwable the proxied call can raise
     * — <em>including</em> the {@code UnexpectedRollbackException} Spring throws
     * at the {@code REQUIRES_NEW} commit boundary when a failed INSERT marked
     * the inner transaction rollback-only. A notification failure is therefore
     * logged and swallowed here and can NEVER propagate into — let alone roll
     * back — the caller's approval-submission transaction (PO decision
     * 2026-06-02, "best-effort / isolated"). The only residual cost is an
     * orphan notification if the parent submit itself later rolls back —
     * acceptable for an in-app bell, and the click-navigation tolerates a
     * deleted source.
     *
     * <p>The requesting user (read from {@link TenantContext}) is the sender
     * and is excluded from the recipient set — a requestor who is also on their
     * own approval line does not notify themselves (enforced in the writer).
     *
     * @param paymentTypeSlug stable type slug (e.g. {@code "internal_transfer"},
     *                        {@code "vendor"}, {@code "corporate_card"}); stored
     *                        in {@code message_params.payment_type} for the
     *                        frontend i18n template.
     * @param apprSeqNo       the approval sequence number — the notification's
     *                        {@code source_id} + click target.
     * @param docNo           user-facing document number ({@code VO_YYMMDD_NNN}).
     * @param title           the requestor's approval title.
     * @param recipients      ordered approver chain (id + display name).
     */
    public void notifyApprovalRequested(String paymentTypeSlug,
                                        String apprSeqNo,
                                        String docNo,
                                        String title,
                                        List<Recipient> recipients) {
        try {
            int created = approvalWriter.writeApprovalRequest(
                    paymentTypeSlug, apprSeqNo, docNo, title, recipients);
            if (created > 0) {
                log.info("notifications approval-request fanout apprSeqNo={} type={} recipients={}",
                        apprSeqNo, paymentTypeSlug, created);
            }
        } catch (Exception ex) {
            // Airtight isolation: swallow EVERYTHING the REQUIRES_NEW write can
            // raise (incl. UnexpectedRollbackException at its commit boundary)
            // so the approval submission always commits regardless.
            log.warn("notifications approval-request fanout skipped — approval still committed "
                            + "(apprSeqNo={} type={}): {}",
                    apprSeqNo, paymentTypeSlug, ex.getMessage(), ex);
        }
    }

    /**
     * Fan an approve / reject DECISION notification out to the legacy
     * {@code EXPT_RESOL_APPRVR_INFM_R008} recipient set (other approvers on
     * the line + business admins, resolved by the caller — acting approver and
     * requester excluded upstream). {@code approved} picks the approved vs.
     * rejected message. Best-effort + isolated: swallows EVERYTHING the
     * {@code REQUIRES_NEW} writer can raise (incl. {@code UnexpectedRollbackException}
     * at its commit boundary) so the approve / reject transaction always
     * commits regardless.
     *
     * @param approved   {@code true} = approved (any step), {@code false} = rejected.
     * @param recipients the resolved R008 recipient set (id + name).
     */
    public void notifyApprovalDecision(boolean approved, String paymentTypeSlug,
                                       String apprSeqNo, String docNo, String title,
                                       List<Recipient> recipients) {
        try {
            int created = approvalWriter.writeApprovalDecision(
                    approved, paymentTypeSlug, apprSeqNo, docNo, title, recipients);
            if (created > 0) {
                log.info("notifications approval-decision fanout approved={} apprSeqNo={} type={} recipients={}",
                        approved, apprSeqNo, paymentTypeSlug, created);
            }
        } catch (Exception ex) {
            log.warn("notifications approval-decision fanout skipped — approval action still committed "
                            + "(approved={} apprSeqNo={} type={}): {}",
                    approved, apprSeqNo, paymentTypeSlug, ex.getMessage(), ex);
        }
    }

    private String nowDtm() {
        return OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC).format(DTM14);
    }

    /** Approver-row reference passed by each payment producer — id + display
     *  name. Decoupled from the per-module {@code ApproverInput} records so
     *  this module depends on no payment package. */
    public record Recipient(String userId, String userNm) { }
}
