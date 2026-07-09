package com.kosign.wecms.domain.payment.notification.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.payment.notification.dto.NotificationListResponse;
import com.kosign.wecms.domain.payment.notification.dto.NotificationProjection;
import com.kosign.wecms.domain.payment.notification.dto.NotificationRow;
import com.kosign.wecms.domain.payment.notification.dto.UnreadCountResponse;
import com.kosign.wecms.domain.payment.notification.repository.BltbNotiInfmRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Notifications popup read/mark service. Ports the legacy
 * {@code comm_0192_007_r001} (list), {@code comm_0192_007_r002}
 * (unread count) and {@code comm_0192_007_u001} (mark read) read/update
 * paths over the refactor's {@code BLTB_NOTI_INFM} +
 * {@code BLTB_NOTI_CONT_INFM} entities.
 *
 * <p><b>Branch N only.</b> Legacy UNIONs a "general notification"
 * ({@code TYPE='G'}) branch over {@code GENL_NOTI_*}; those tables are
 * absent from the refactor legacy-table-inventory, so the refactor scopes
 * to the bulletin/approval branch only — consistent with the write side
 * ({@code ApprovalNotificationService}) which never produces Branch-G
 * rows. See
 * {@code docs/audits/notification-popup-legacy-vs-refactor-analysis.md}.
 *
 * <p><b>Legacy behaviour preserved:</b>
 * <ul>
 *   <li>Per-recipient + per-tenant scoping: {@code RCV_ID} +
 *       {@code BIZ_KEY} from {@link TenantContext}, never the request
 *       ({@code comm_0192_007_r001_act.jsp:44-45}).</li>
 *   <li>Unread = {@code ACTV_YN='N'} ({@code act.jsp:80-82}); count is
 *       always over unread ({@code comm_0192_007_r002_act.jsp:62}).</li>
 *   <li>Sort {@code REG_DTM DESC} ({@code act.jsp:84}) with a
 *       {@code NOTI_SEQ_NO DESC} tiebreaker for stable paging.</li>
 *   <li>{@code SRCH_WD} matches the sender name ({@code act.jsp:75-78}).</li>
 *   <li>Localized {@code TITL}/{@code CONT} joined on the caller's
 *       language; {@code DEL_YN='Y'} rows kept in the list.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    /** Largest page size honoured (global refactor cap). Excess clamped. */
    public static final int MAX_PAGE_SIZE = 100;

    /** Default page size — the legacy popup loads 20 then grows on scroll
     *  ({@code comm_0192_007.ts:64}). */
    public static final int DEFAULT_PAGE_SIZE = 20;

    /** Unread read-type filter token. */
    private static final String READ_TYPE_UNREAD = "U";

    /** {@code ACTV_YN='N'} = unread. */
    private static final String ACTV_YN_UNREAD = "N";

    /** Default ORDER BY — {@code regDtm DESC} (legacy) + {@code notiSeqNo
     *  DESC} tiebreaker so rows sharing a {@code reg_dtm} second keep a
     *  deterministic order across paged requests. */
    private static final Sort DEFAULT_SORT =
            Sort.by(Sort.Order.desc("regDtm"), Sort.Order.desc("notiSeqNo"));

    private static final DateTimeFormatter DTM14 = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final BltbNotiInfmRepository repository;
    private final Clock clock;

    /* =================================================================
     *  List
     * ================================================================= */

    /**
     * Page slice + counts for the current caller. Reads identity from
     * {@link TenantContext}; the wire intentionally has no
     * {@code biz_key} / {@code rcv_id}.
     *
     * @param readType {@code "U"} (unread only) or {@code "A"}/anything
     *                 else (all). Case-insensitive.
     * @param search   optional sender-name keyword (partial, case-insensitive).
     * @param page     zero-based; clamped to {@code >= 0}.
     * @param size     rows per page; defaults to {@link #DEFAULT_PAGE_SIZE}
     *                 when {@code <= 0}, clamped to {@link #MAX_PAGE_SIZE}.
     */
    @Transactional(readOnly = true)
    public NotificationListResponse list(String readType, String search, int page, int size) {
        String bizKey = TenantContext.currentBizKey();
        String rcvId  = TenantContext.currentUserId();
        String langCd = currentLangCode();

        String actvYnFilter   = READ_TYPE_UNREAD.equalsIgnoreCase(trim(readType)) ? ACTV_YN_UNREAD : null;
        String keywordPattern = buildKeywordPattern(search);

        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        int safePage = Math.max(0, page);
        Pageable pageable = PageRequest.of(safePage, safeSize, DEFAULT_SORT);

        Page<NotificationProjection> result =
                repository.search(bizKey, rcvId, langCd, actvYnFilter, keywordPattern, pageable);

        long unreadCount = repository.countUnread(bizKey, rcvId, langCd);

        List<NotificationRow> rows = result.getContent().stream()
                .map(NotificationService::toRow)
                .toList();

        return new NotificationListResponse(
                rows,
                result.getTotalElements(),
                unreadCount,
                result.getNumber(),
                result.getSize(),
                result.getTotalPages());
    }

    /* =================================================================
     *  Unread count
     * ================================================================= */

    /** Unread badge count for the current caller. */
    @Transactional(readOnly = true)
    public UnreadCountResponse unreadCount() {
        long count = repository.countUnread(
                TenantContext.currentBizKey(),
                TenantContext.currentUserId(),
                currentLangCode());
        return new UnreadCountResponse(count);
    }

    /* =================================================================
     *  Mark read
     * ================================================================= */

    /**
     * Mark one notification read. Tenant + recipient scoped; idempotent.
     *
     * @param notificationId {@code NOTI_SEQ_NO} of the caller's row.
     * @throws BusinessException {@code WBE_NOTIFICATION_NOT_FOUND} when no
     *         row matches the current tenant + user (merges "not found" +
     *         "wrong tenant/recipient" — never leaks another user's row).
     */
    @Transactional
    public void markRead(long notificationId) {
        String nowDtm = OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC).format(DTM14);
        int updated = repository.markRead(
                TenantContext.currentBizKey(),
                TenantContext.currentUserId(),
                BigDecimal.valueOf(notificationId),
                TenantContext.currentUserId(),
                nowDtm);
        if (updated == 0) {
            throw new BusinessException("WBE_NOTIFICATION_NOT_FOUND");
        }
    }

    /**
     * Mark every unread notification read for the current caller. Always a
     * success — zero unread rows is a no-op, not an error.
     */
    @Transactional
    public void markAllRead() {
        String nowDtm = OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC).format(DTM14);
        repository.markAllRead(
                TenantContext.currentBizKey(),
                TenantContext.currentUserId(),
                TenantContext.currentUserId(),
                nowDtm);
    }

    /* =================================================================
     *  Helpers
     * ================================================================= */

    /** Caller's language as the 2-char {@code LANG_CD} stored in
     *  {@code BLTB_NOTI_CONT_INFM} ({@code EN}/{@code KO}/{@code KM}/{@code VI});
     *  defaults to {@code EN} when no context language is bound. */
    private static String currentLangCode() {
        LangCd lang = TenantContext.currentLangCd();
        return lang == null ? LangCd.EN.name() : lang.name();
    }

    /**
     * Lower-case + escape LIKE meta-chars + wrap with {@code %…%}, using
     * {@code !} as the escape character — same convention as
     * {@code ErpTrnsHistoryService} / {@code FirmBankingHistoryService}.
     * Returns {@code null} for blank input so the JPQL {@code IS NULL OR}
     * guard skips the predicate (mirrors legacy: no {@code SRCH_WD} ⇒ no
     * {@code ILIKE} clause).
     */
    private static String buildKeywordPattern(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return null;
        StringBuilder b = new StringBuilder(trimmed.length() + 2).append('%');
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == '%' || c == '_' || c == '!') b.append('!');
            b.append(c);
        }
        b.append('%');
        return b.toString().toLowerCase(Locale.ROOT);
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }

    /** Map the repository projection → wire row. {@code notification_id}
     *  mirrors {@code noti_seq_no}; {@code read} is derived from
     *  {@code ACTV_YN}; {@code user_img_url} is emitted only when the
     *  sender has a stored image. */
    private static NotificationRow toRow(NotificationProjection p) {
        Long seqNo = p.notiSeqNo() == null ? null : p.notiSeqNo().longValueExact();
        boolean read = "Y".equalsIgnoreCase(p.actvYn());
        return new NotificationRow(
                seqNo,
                p.bltbCd(),
                seqNo,
                p.titl(),
                p.cont(),
                p.sndId(),
                p.sndName(),
                p.notiJson(),
                p.notiWsvc(),
                p.delYn(),
                read,
                p.actvYn(),
                p.regDtm(),
                p.modDtm(),
                resolveUserImgUrl(p.sndId(), p.userImg()));
    }

    /** Non-blank {@code USER_IMG} ⇒ slug image URL (approval-line
     *  precedent); otherwise {@code null}. The image endpoint itself is
     *  out of scope for this MR. */
    private static String resolveUserImgUrl(String sndId, String userImg) {
        if (sndId == null || userImg == null || userImg.isBlank()) {
            return null;
        }
        return "/api/v1/users/" + sndId + "/image";
    }
}
