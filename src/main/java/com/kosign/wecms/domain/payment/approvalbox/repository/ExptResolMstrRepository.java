package com.kosign.wecms.domain.payment.approvalbox.repository;

import com.kosign.wecms.domain.payment.approvalbox.dto.ApprovalBoxProjection;
import com.kosign.wecms.domain.payment.approvalbox.dto.ApprovalBoxTabCounts;
import com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolMstr;
import com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolMstrId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

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

    /** Look up a non-deleted approval master row by its user-visible document number.
     *  Used by the overseas-payment detail endpoint to resolve a DOC_NO
     *  (VO_YYMMDD_NNN format) sent by the frontend into the APPR_SEQ_NO
     *  needed to find the matching OVRS_PYMN_SUMR row. */
    Optional<ExptResolMstr> findByBizKeyAndDocNoAndDelYn(
            String bizKey, String docNo, String delYn);

    /** Per-tenant MAX(apprSeqNo::bigint) for cross-table allocation.
     *  Used by the overseas-payment request-approval path to ensure the
     *  newly issued APPR_SEQ_NO is above any existing master rows. */
    @Query(value = "SELECT MAX(CAST(appr_seq_no AS BIGINT)) FROM expt_resol_mstr " +
                   "WHERE biz_key = :bizKey AND appr_seq_no ~ '^[0-9]+$'",
           nativeQuery = true)
    Long findMaxApprSeqNoByBizKey(@Param("bizKey") String bizKey);

    /**
     * Returns the MAX trailing numeric sequence extracted from existing
     * {@code DOC_NO} values that share the same tenant + date prefix
     * (e.g. {@code "VO_260512_"}).
     *
     * <p>Used by {@code DocNoGenerator} to compute the next
     * {@code VO_YYMMDD_NNN} value. The regex {@code '[0-9]+$'} strips the
     * padded-zero suffix (e.g. {@code "001"} → {@code 1}); CAST to NUMERIC
     * handles strings of any length. Returns 0 when no rows match so the
     * caller always gets a concrete starting point.
     */
    @Query(value = """
           SELECT COALESCE(MAX(
               CAST(SUBSTRING(doc_no FROM '[0-9]+$') AS NUMERIC)
           ), 0)
           FROM expt_resol_mstr
           WHERE biz_key   = :bizKey
             AND del_yn    = 'N'
             AND UPPER(doc_no) LIKE UPPER(:datePrefix) || '%'
           """, nativeQuery = true)
    Long findMaxDocNoSeqForDate(
            @Param("bizKey")     String bizKey,
            @Param("datePrefix") String datePrefix);

    /**
     * Integer-typed convenience variant of {@link #findMaxDocNoSeqForDate}
     * used by {@code ApprovalBoxFanoutService.nextDocNo} where the daily
     * counter fits in an {@code int} (3-digit cap).
     */
    @Query(value = """
           SELECT COALESCE(MAX(
               CAST(SUBSTRING(doc_no FROM '[0-9]+$') AS INTEGER)
           ), 0)
           FROM expt_resol_mstr
           WHERE biz_key   = :bizKey
             AND del_yn    = 'N'
             AND UPPER(doc_no) LIKE UPPER(:prefix) || '%'
           """, nativeQuery = true)
    Integer findMaxDocNoSeqByBizKeyAndPrefix(
            @Param("bizKey") String bizKey,
            @Param("prefix") String prefix);

    /**
     * Batch (APPR_SEQ_NO, DOC_NO) lookup as a tuple array. Used by
     * {@code CardTransactionsService.resolveDocNos} to fill in the
     * Approval Box document number on each ledger row.
     */
    @Query("""
           SELECT erm.apprSeqNo, erm.docNo
             FROM ExptResolMstr erm
            WHERE erm.bizKey = :bizKey
              AND erm.apprSeqNo IN :apprSeqNos
              AND erm.delYn  = 'N'
           """)
    List<Object[]> findDocNoByApprSeqNos(
            @Param("bizKey")     String bizKey,
            @Param("apprSeqNos") Collection<String> apprSeqNos);

    /**
     * Batch fetch — returns the (apprSeqNo, docNo) pairs for a set of
     * approval sequence numbers in a single query. Used by payment list
     * services to fill in DOC_NO for rows where the write-back to
     * SCDUL_PYMN_SUMR.ERP_FILE_NUM did not occur (seed / legacy data).
     */
    @Query("SELECT erm FROM ExptResolMstr erm " +
           "WHERE erm.bizKey = :bizKey AND erm.apprSeqNo IN :apprSeqNos AND erm.delYn = 'N'")
    List<ExptResolMstr> findByBizKeyAndApprSeqNoIn(
            @Param("bizKey")      String bizKey,
            @Param("apprSeqNos") Collection<String> apprSeqNos);

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
               erm.apprSeqNo, erm.docNo, erm.titl, erm.apprDv, erm.docNm, erm.cont,
               erm.totPayKhrAmt, erm.totPayUsdAmt, erm.payDate, erm.draftUserId, erm.draftUserNm,
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

    /**
     * Cash Flow report support — return the subset of supplied
     * {@code apprSeqNo}s whose {@code APPR_TYPE_CD = '01'} (Completed).
     *
     * <p>Replaces the legacy {@code wb_get_appr_stts(biz_key, reg_id,
     * reg_id, appr_seq_no, appr_type_cd)} PL/pgSQL helper. Because the
     * Cash Flow function always calls that helper with
     * {@code apprvr_id = reg_id}, the IF branch always fires and the
     * helper reduces to a simple {@code appr_type_cd} switch:
     * {@code '01' -> 'C'} (Completed). The Cash Flow function then
     * gates on {@code = 'C'}, so any other code is filtered out.
     *
     * <p>Returns an empty list when the input collection is empty
     * (Spring Data emits {@code WHERE x IN ()} which Postgres rejects;
     * callers MUST guard against that — see
     * {@link com.kosign.wecms.domain.banking.service.cashflow.ApprovalStatusResolver}).
     */
    @Query("""
            SELECT erm.apprSeqNo
              FROM ExptResolMstr erm
             WHERE erm.bizKey = :bizKey
               AND erm.apprSeqNo IN :apprSeqNos
               AND erm.apprTypeCd = '01'
            """)
    List<String> findCompletedApprSeqNos(@Param("bizKey") String bizKey,
                                         @Param("apprSeqNos") Collection<String> apprSeqNos);
}
