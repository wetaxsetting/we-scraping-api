package com.kosign.wecms.domain.payment.approvalbox.repository;

import com.kosign.wecms.domain.payment.approvalbox.dto.ApprovalBoxProjection;
import com.kosign.wecms.domain.payment.approvalbox.dto.ApprovalBoxTabCounts;
import com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolMstr;
import com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolMstrId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Read + write repository for {@code EXPT_RESOL_MSTR}. Two custom
 * queries cover the Approval Box list page:
 *
 * <ol>
 *   <li>{@link #search} — page slice with the visibility filter applied
 *       inline (admin sees all; non-admin sees rows they uploaded,
 *       requested, or are assigned to approve). Mirrors legacy
 *       {@code resl_0001_001_r001_act.jsp:63-175}.</li>
 *   <li>{@link #countTabs} — single aggregate carrying the four tab
 *       counts. Mirrors legacy
 *       {@code IDO.EXPT_RESOL_MSTR_R003_CNT.xml} but drops the
 *       {@code apprTypeCd} predicate so all tabs update for one filter
 *       combination.</li>
 * </ol>
 *
 * <p>Both queries enforce the six-type predicate (legacy
 * {@code resl_0001_001_r001_act.jsp:78}) and the {@code DEL_YN = 'N'}
 * filter. The visibility filter degenerates to {@code TRUE} for admin
 * callers — Spring's HQL parser evaluates {@code :isAdmin = TRUE} as a
 * scalar predicate without touching the table.
 *
 * <p>Sibling-table joins (BLTB_POST_INFM / OVRS_PYMN_MSTR /
 * SCDUL_PYMN_SUMR / OVRS_PYMN_SUMR / CARD_TRNS_HSTR) are dropped here
 * versus legacy {@code IDO.EXPT_RESOL_MSTR_R003.xml} — those modules
 * own their schema port. See {@code design-backend.md} §13 D1 / D4 / D5.
 */
public interface ExptResolMstrRepository
        extends JpaRepository<ExptResolMstr, ExptResolMstrId> {

    /** Per-tenant MAX(apprSeqNo::bigint) for cross-table allocation.
     *  Used by the overseas-payment request-approval path to ensure the
     *  newly issued APPR_SEQ_NO is above any existing master rows. */
    @Query(value = "SELECT MAX(CAST(appr_seq_no AS BIGINT)) FROM expt_resol_mstr " +
                   "WHERE biz_key = :bizKey AND appr_seq_no ~ '^[0-9]+$'",
           nativeQuery = true)
    Long findMaxApprSeqNoByBizKey(@Param("bizKey") String bizKey);

    /**
     * Page slice with filter, search, sort, pagination, and per-user
     * visibility.
     *
     * <p>Optional filters are passed as {@code null} when the caller
     * wants them skipped — the {@code (:p IS NULL OR ...)} guard keeps
     * the query plan simple.
     *
     * <p>The visibility filter mirrors Redmine #42046 exactly:
     * <ul>
     *   <li>Admin (isAdmin=true) — passes through.</li>
     *   <li>Non-admin — at least one of:
     *     <ul>
     *       <li>caller submitted the approval request
     *           ({@code erm.regId = :userId}), OR</li>
     *       <li>caller is an assigned approver
     *           (EXISTS in {@code EXPT_RESOL_APPRVR_INFM}).</li>
     *     </ul>
     *   </li>
     * </ul>
     *
     * <p>The "uploader" branches against {@code SCDUL_PYMN_SUMR} /
     * {@code OVRS_PYMN_SUMR} / {@code CARD_TRNS_HSTR} from the legacy
     * filter are dropped — those sibling tables aren't owned by this MR.
     * Per-user visibility still holds via the two preserved branches;
     * the dropped branches were strictly additive (more rows surfaced)
     * and reintroduce when the sibling features port.
     */
    @Query("""
           SELECT new com.kosign.wecms.domain.payment.approvalbox.dto.ApprovalBoxProjection(
               erm.apprSeqNo, erm.docNo, erm.titl, erm.apprDv, erm.docNm,
               erm.totPayKhrAmt, erm.payDate, erm.draftUserId, erm.draftUserNm,
               erm.regDtm, erm.apprTypeCd, erm.payEndYn,
               (SELECT COUNT(a) FROM ExptResolApprvrInfm a
                WHERE a.bizKey = erm.bizKey AND a.apprSeqNo = erm.apprSeqNo),
               (SELECT COUNT(a) FROM ExptResolApprvrInfm a
                WHERE a.bizKey = erm.bizKey AND a.apprSeqNo = erm.apprSeqNo AND a.apprvrStts = 'A')
           )
           FROM ExptResolMstr erm
           WHERE erm.bizKey = :bizKey
             AND erm.delYn  = 'N'
             AND erm.apprDv IN ('21','31','32','33','34','35')
             AND erm.draftDate BETWEEN :fromDate AND :toDate
             AND (:apprTypeCd IS NULL OR erm.apprTypeCd = :apprTypeCd)
             AND (:keywordPattern IS NULL OR
                  LOWER(erm.docNm) LIKE :keywordPattern ESCAPE '!' OR
                  LOWER(erm.titl)  LIKE :keywordPattern ESCAPE '!' OR
                  LOWER(erm.docNo) LIKE :keywordPattern ESCAPE '!')
             AND ( :isAdmin = TRUE
                   OR erm.regId = :userId
                   OR EXISTS (SELECT 1 FROM ExptResolApprvrInfm a
                              WHERE a.bizKey = erm.bizKey
                                AND a.apprSeqNo = erm.apprSeqNo
                                AND a.apprvrId = :userId) )
           """)
    Page<ApprovalBoxProjection> search(
            @Param("bizKey")         String bizKey,
            @Param("userId")         String userId,
            @Param("isAdmin")        boolean isAdmin,
            @Param("fromDate")       String fromDate,
            @Param("toDate")         String toDate,
            @Param("apprTypeCd")     String apprTypeCd,
            @Param("keywordPattern") String keywordPattern,
            Pageable pageable);

    /**
     * Tab counts — total rows broken down by {@code apprTypeCd}.
     * Intentionally drops the {@code apprTypeCd} predicate so all four
     * tab chips reflect the same date/search/visibility scope.
     *
     * <p>{@code COALESCE(SUM(...), 0L)} keeps zero counts in shape for
     * the record's primitive {@code long} fields (a SUM over an empty
     * filter set returns NULL otherwise).
     */
    @Query("""
           SELECT new com.kosign.wecms.domain.payment.approvalbox.dto.ApprovalBoxTabCounts(
               COALESCE(SUM(CASE WHEN erm.apprTypeCd = '00' THEN 1L ELSE 0L END), 0L),
               COALESCE(SUM(CASE WHEN erm.apprTypeCd = '03' THEN 1L ELSE 0L END), 0L),
               COALESCE(SUM(CASE WHEN erm.apprTypeCd = '01' THEN 1L ELSE 0L END), 0L),
               COALESCE(SUM(CASE WHEN erm.apprTypeCd = '02' THEN 1L ELSE 0L END), 0L)
           )
           FROM ExptResolMstr erm
           WHERE erm.bizKey = :bizKey
             AND erm.delYn  = 'N'
             AND erm.apprDv IN ('21','31','32','33','34','35')
             AND erm.draftDate BETWEEN :fromDate AND :toDate
             AND (:keywordPattern IS NULL OR
                  LOWER(erm.docNm) LIKE :keywordPattern ESCAPE '!' OR
                  LOWER(erm.titl)  LIKE :keywordPattern ESCAPE '!' OR
                  LOWER(erm.docNo) LIKE :keywordPattern ESCAPE '!')
             AND ( :isAdmin = TRUE
                   OR erm.regId = :userId
                   OR EXISTS (SELECT 1 FROM ExptResolApprvrInfm a
                              WHERE a.bizKey = erm.bizKey
                                AND a.apprSeqNo = erm.apprSeqNo
                                AND a.apprvrId = :userId) )
           """)
    ApprovalBoxTabCounts countTabs(
            @Param("bizKey")         String bizKey,
            @Param("userId")         String userId,
            @Param("isAdmin")        boolean isAdmin,
            @Param("fromDate")       String fromDate,
            @Param("toDate")         String toDate,
            @Param("keywordPattern") String keywordPattern);
}
