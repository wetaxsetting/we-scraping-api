package com.kosign.wecms.domain.notification.service;

import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.notification.entity.Notification;
import com.kosign.wecms.domain.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Isolated DB writer for approval-request notifications. The actual INSERTs
 * live here, in their OWN {@code REQUIRES_NEW} transaction, so they commit (or
 * roll back) independently of the caller's approval-submission transaction.
 *
 * <h2>Why a separate bean</h2>
 * The safety boundary needs TWO things that cannot coexist on one method:
 * <ol>
 *   <li>the write must run in a {@code REQUIRES_NEW} transaction (a Spring
 *       proxy concern — only applied on a <em>cross-bean</em> call), and</li>
 *   <li>a caller must be able to catch <em>everything</em> the write can throw,
 *       <b>including the {@code UnexpectedRollbackException} Spring raises at the
 *       {@code REQUIRES_NEW} commit boundary</b> when an INSERT marked the inner
 *       transaction rollback-only.</li>
 * </ol>
 * A single method with an internal try/catch (the original Phase-1 shape)
 * cannot satisfy (2): the commit happens in the transactional proxy <em>after</em>
 * the method body returns, so the body's catch never sees the rollback
 * exception — it propagates into the caller's approval transaction. Splitting
 * the write into this bean lets {@link NotificationService#notifyApprovalRequested}
 * wrap the whole proxied call (body + commit) in one try/catch, making the
 * isolation airtight.
 *
 * <p>This bean depends only on the repository + clock — never on
 * {@link NotificationService} — so there is no bean cycle.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationApprovalWriter {

    /** Producer discriminators. */
    static final String NOTI_TYPE_APPROVAL_REQUEST  = "APPROVAL_REQUEST";
    static final String NOTI_TYPE_APPROVAL_APPROVED = "APPROVAL_APPROVED";
    static final String NOTI_TYPE_APPROVAL_REJECTED = "APPROVAL_REJECTED";

    /** i18n keys the frontend resolves for each notification kind. */
    static final String MSG_APPROVAL_REQUESTED = "notification.approval.requested";
    static final String MSG_APPROVAL_APPROVED  = "notification.approval.approved";
    static final String MSG_APPROVAL_REJECTED  = "notification.approval.rejected";

    /** Refactor slug route the bell navigates to on click (Approval Box). */
    static final String LINK_APPROVAL_BOX = "/payment/approval-box";

    /** Active (non-deleted) row marker. */
    private static final String DEL_YN_ACTIVE = "N";

    /** UTC {@code yyyyMMddHHmmss} — same shape the approval fan-out stamps. */
    private static final DateTimeFormatter DTM14 = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final NotificationRepository repository;
    private final Clock                  clock;

    /**
     * Fan an approval-REQUEST notification out to every (non-self) approver
     * (Phase 1 producer). Own {@code REQUIRES_NEW} transaction; lets every
     * exception propagate — {@link NotificationService} owns the catch.
     *
     * @return the number of notification rows inserted.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int writeApprovalRequest(String paymentTypeSlug,
                                    String apprSeqNo,
                                    String docNo,
                                    String title,
                                    List<NotificationService.Recipient> recipients) {
        return fanout(NOTI_TYPE_APPROVAL_REQUEST, MSG_APPROVAL_REQUESTED,
                paymentTypeSlug, apprSeqNo, docNo, title, recipients);
    }

    /**
     * Fan an approve / reject DECISION notification out to every (non-self)
     * recipient — the legacy {@code EXPT_RESOL_APPRVR_INFM_R008} set (other
     * approvers on the line + business admins), resolved by the caller.
     * {@code approved} selects the noti-type + message-code pair. Own
     * {@code REQUIRES_NEW} transaction; lets every exception propagate so
     * {@link NotificationService} owns the catch.
     *
     * @return the number of notification rows inserted.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int writeApprovalDecision(boolean approved,
                                     String paymentTypeSlug,
                                     String apprSeqNo,
                                     String docNo,
                                     String title,
                                     List<NotificationService.Recipient> recipients) {
        return fanout(
                approved ? NOTI_TYPE_APPROVAL_APPROVED : NOTI_TYPE_APPROVAL_REJECTED,
                approved ? MSG_APPROVAL_APPROVED        : MSG_APPROVAL_REJECTED,
                paymentTypeSlug, apprSeqNo, docNo, title, recipients);
    }

    /**
     * Shared fan-out: insert one notification per distinct, non-self
     * recipient. The acting user (read from {@link TenantContext}) is the
     * sender and is always excluded. Same-bean private helper invoked from the
     * {@code REQUIRES_NEW} public methods (the transaction is owned by the
     * public entry point invoked cross-bean from {@link NotificationService}).
     */
    private int fanout(String notiType,
                       String messageCode,
                       String paymentTypeSlug,
                       String apprSeqNo,
                       String docNo,
                       String title,
                       List<NotificationService.Recipient> recipients) {
        if (recipients == null || recipients.isEmpty()) {
            return 0;
        }
        String bizKey   = TenantContext.currentBizKey();
        String senderId = TenantContext.currentUserId();
        String senderNm = TenantContext.currentUserNm();
        if (senderNm == null) senderNm = senderId;
        String now = OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC).format(DTM14);

        String linkTarget = LINK_APPROVAL_BOX + "?appr_seq_no=" + apprSeqNo;

        List<Notification> toSave = new ArrayList<>(recipients.size());
        for (NotificationService.Recipient r : recipients) {
            if (r == null || r.userId() == null || r.userId().isBlank()) {
                continue;
            }
            // Never notify the acting user about their own action.
            if (r.userId().equals(senderId)) {
                continue;
            }
            Notification n = new Notification();
            n.setBizKey(bizKey);
            n.setRecipientId(r.userId());
            n.setNotiType(notiType);
            n.setSourceId(apprSeqNo);
            n.setMessageCode(messageCode);
            n.setMessageParams(approvalParams(senderNm, docNo, title, paymentTypeSlug));
            n.setLinkTarget(linkTarget);
            n.setReadAt(null);              // unread
            n.setDelYn(DEL_YN_ACTIVE);
            n.setRegId(senderId);
            n.setRegDtm(now);
            n.setModId(senderId);
            n.setModDtm(now);
            toSave.add(n);
        }
        if (toSave.isEmpty()) {
            return 0;
        }
        repository.saveAll(toSave);
        return toSave.size();
    }

    /** Flat i18n param map for {@link #MSG_APPROVAL_REQUESTED}. Keys are
     *  snake_case verbatim — Jackson does not rewrite Map keys, so the wire
     *  contract is fixed here. Null values are dropped. */
    private static Map<String, String> approvalParams(String senderNm, String docNo,
                                                       String title, String paymentTypeSlug) {
        Map<String, String> params = new LinkedHashMap<>();
        if (senderNm != null)        params.put("sender_name", senderNm);
        if (docNo != null)           params.put("doc_no", docNo);
        if (title != null)           params.put("title", title);
        if (paymentTypeSlug != null) params.put("payment_type", paymentTypeSlug);
        return params;
    }
}
