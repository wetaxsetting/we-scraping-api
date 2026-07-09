package com.kosign.wecms.domain.payment.notification.repository;

import com.kosign.wecms.domain.payment.notification.dto.NotificationProjection;
import com.kosign.wecms.domain.payment.notification.entity.BltbNotiInfm;
import com.kosign.wecms.domain.payment.notification.entity.BltbNotiInfmId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

/** Repository for {@code BLTB_NOTI_INFM} (per-recipient notification). */
public interface BltbNotiInfmRepository extends JpaRepository<BltbNotiInfm, BltbNotiInfmId> {

    /** Withdraw cleanup — soft-delete ({@code DEL_YN='Y'}) every
     *  notification under a bulletin post (legacy
     *  {@code comm_0192_004_d001} patches {@code BLTB_NOTI_INFM.DEL_YN='Y'},
     *  it does NOT hard-delete; the per-language {@code BLTB_NOTI_CONT_INFM}
     *  rows are left untouched, matching legacy). */
    @Modifying
    @Query("""
           UPDATE BltbNotiInfm n
           SET n.delYn = 'Y', n.modId = :modId, n.modDtm = :modDtm
           WHERE n.bizKey = :bizKey AND n.bltbCd = :bltbCd AND n.postSeqNo = :postSeqNo
           """)
    int softDeleteByPost(@Param("bizKey") String bizKey,
                         @Param("bltbCd") String bltbCd,
                         @Param("postSeqNo") BigDecimal postSeqNo,
                         @Param("modId") String modId,
                         @Param("modDtm") String modDtm);

    /* =================================================================
     *  Notifications popup read path — Branch N only (BLTB_NOTI_INFM).
     *  Mirrors legacy comm_0192_007_r001 / r002 (IDO BLTB_NOTI_INFM_R002)
     *  restricted to the BLTB branch — the GENL_NOTI_* UNION branch is
     *  intentionally NOT ported (those tables are absent from the refactor
     *  legacy-table-inventory; see
     *  docs/audits/notification-popup-legacy-vs-refactor-analysis.md §2.4).
     * ================================================================= */

    /**
     * Page slice for the bell popup. INNER JOIN to {@code BLTB_NOTI_CONT_INFM}
     * on the caller's language mirrors the legacy IDO
     * ({@code IDO.BLTB_NOTI_INFM_R002.xml:89-93} —
     * {@code JOIN BLTB_NOTI_CONT_INFM BNCI ... AND BNCI.lang_cd = ?}): a
     * notification with no content row for the caller's language is absent
     * from the list, exactly as in JEX. The legacy write side
     * ({@code ApprovalNotificationService}) seeds EN / KO / VI content for
     * every notification, so EN / KO / VI callers always match.
     *
     * <p>Sender image is resolved through a LEFT JOIN to
     * {@code USER_BUSI_CONN_INFM.USER_IMG} keyed on {@code (SND_ID, BIZ_KEY)}
     * — the HQL ad-hoc {@code ON} join (no declared JPA relationship; the
     * tables share no FK) mirrors the legacy correlated subquery
     * ({@code IDO.BLTB_NOTI_INFM_R002.xml:74-77}). LEFT so a notification
     * from a sender with no image (or {@code SYSTEM}) still lists.
     *
     * <p>{@code DEL_YN='Y'} rows are deliberately NOT excluded — legacy
     * keeps soft-deleted notifications in the list and flags them client
     * side ({@code WBI1098}); the service surfaces {@code del_yn} so the
     * frontend can reproduce that.
     *
     * <p>Filters follow the {@code (:p IS NULL OR ...)} convention so one
     * query covers every permutation. {@code actvYnFilter} is {@code "N"}
     * for the Unread tab and {@code null} for All. {@code keywordPattern}
     * is built service-side (lowercased + LIKE meta-chars escaped + wrapped
     * with {@code %…%}) and matched against {@code SND_NAME} — legacy
     * {@code T.snd_name ILIKE '%'||?||'%'} ({@code comm_0192_007_r001_act.jsp:75-78}).
     */
    @Query("""
           SELECT new com.kosign.wecms.domain.payment.notification.dto.NotificationProjection(
               n.bltbCd, n.notiSeqNo, c.titl, c.cont, n.sndId, n.sndName,
               n.notiJson, n.notiWsvc, n.delYn, n.actvYn, n.regDtm, n.modDtm, ubc.userImg
           )
           FROM BltbNotiInfm n
           JOIN BltbNotiContInfm c
             ON c.bizKey = n.bizKey AND c.bltbCd = n.bltbCd
                AND c.notiSeqNo = n.notiSeqNo AND c.langCd = :langCd
           LEFT JOIN UserBusiConnInfm ubc
             ON ubc.bizKey = n.bizKey AND ubc.userId = n.sndId
           WHERE n.bizKey = :bizKey
             AND n.rcvId = :rcvId
             AND (:actvYnFilter IS NULL OR n.actvYn = :actvYnFilter)
             AND (:keywordPattern IS NULL OR LOWER(n.sndName) LIKE :keywordPattern ESCAPE '!')
           """)
    Page<NotificationProjection> search(
            @Param("bizKey")         String bizKey,
            @Param("rcvId")          String rcvId,
            @Param("langCd")         String langCd,
            @Param("actvYnFilter")   String actvYnFilter,
            @Param("keywordPattern") String keywordPattern,
            Pageable pageable);

    /**
     * Unread badge count. Mirrors legacy {@code comm_0192_007_r002}
     * ({@code IDO.BLTB_NOTI_INFM_R002_CNT}) restricted to the BLTB branch:
     * the count is over the same content INNER JOIN as {@link #search} so
     * the badge and the Unread tab agree exactly — a notification with no
     * content row in the caller's language is invisible to both. Always
     * counts {@code ACTV_YN='N'} ({@code comm_0192_007_r002_act.jsp:62}).
     */
    @Query("""
           SELECT COUNT(n)
           FROM BltbNotiInfm n
           JOIN BltbNotiContInfm c
             ON c.bizKey = n.bizKey AND c.bltbCd = n.bltbCd
                AND c.notiSeqNo = n.notiSeqNo AND c.langCd = :langCd
           WHERE n.bizKey = :bizKey
             AND n.rcvId = :rcvId
             AND n.actvYn = 'N'
           """)
    long countUnread(@Param("bizKey")  String bizKey,
                     @Param("rcvId")   String rcvId,
                     @Param("langCd")  String langCd);

    /**
     * Mark a single notification read for the caller. Mirrors legacy
     * {@code comm_0192_007_u001} for {@code TYPE='N'} — sets
     * {@code ACTV_YN='Y'} + modifier audit columns
     * ({@code comm_0192_007_u001_act.jsp:117-120}).
     *
     * <p>Scoped to {@code (BIZ_KEY, RCV_ID, NOTI_SEQ_NO)} so a caller can
     * only ever flip their own row — {@code BIZ_KEY} + {@code RCV_ID} come
     * from {@link com.kosign.wecms.common.tenant.TenantContext}, never the
     * request. The {@code BLTB_CD} segment of the PK is not required: per
     * tenant, {@code NOTI_SEQ_NO} is allocated through the single
     * {@code wb_get_next_val} funnel and is unique within the recipient's
     * rows, so {@code (BIZ_KEY, RCV_ID, NOTI_SEQ_NO)} pins exactly one row.
     *
     * <p>Idempotent: an already-read row ({@code ACTV_YN='Y'}) is updated
     * again (Y→Y) and still reports one affected row, so a double-click
     * does not surface a spurious not-found.
     *
     * @return rows updated; {@code 0} ⇒ no such row for this tenant+user
     *         (the service maps that to {@code WBE_NOTIFICATION_NOT_FOUND}).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
           UPDATE BltbNotiInfm n
           SET n.actvYn = 'Y', n.modId = :modId, n.modDtm = :modDtm
           WHERE n.bizKey = :bizKey AND n.rcvId = :rcvId AND n.notiSeqNo = :notiSeqNo
           """)
    int markRead(@Param("bizKey")    String bizKey,
                 @Param("rcvId")     String rcvId,
                 @Param("notiSeqNo") BigDecimal notiSeqNo,
                 @Param("modId")     String modId,
                 @Param("modDtm")    String modDtm);

    /**
     * Mark every unread notification read for the caller ("Read All" —
     * legacy {@code comm_0192_007_u001} with the full list as {@code REC[]}).
     * Scoped to {@code (BIZ_KEY, RCV_ID)} with {@code ACTV_YN='N'} so it
     * only ever touches the caller's own unread rows.
     *
     * @return rows updated (0 when nothing was unread — still a success).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
           UPDATE BltbNotiInfm n
           SET n.actvYn = 'Y', n.modId = :modId, n.modDtm = :modDtm
           WHERE n.bizKey = :bizKey AND n.rcvId = :rcvId AND n.actvYn = 'N'
           """)
    int markAllRead(@Param("bizKey") String bizKey,
                    @Param("rcvId")  String rcvId,
                    @Param("modId")  String modId,
                    @Param("modDtm") String modDtm);
}
