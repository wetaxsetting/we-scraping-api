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
 * <p>Sibling-table <em>display</em> joins (BLTB_POST_INFM / OVRS_PYMN_MSTR)
 * are dropped here versus legacy {@code IDO.EXPT_RESOL_MSTR_R003.xml} —
 * those modules own their schema port. The sibling-table <em>visibility</em>
 * EXISTS branches (SCDUL_PYMN_SUMR / OVRS_PYMN_SUMR / CARD_TRNS_HSTR
 * uploader = current user) ARE applied — restored 2026-06-16 to match
 * legacy {@code resl_0001_001_r001_act.jsp:116-162} (Redmine #42046), so a
 * non-admin who uploaded a batch but is neither the approval requester nor
 * an assigned approver still sees the document. See {@code design-backend.md}
 * §13 D1 / D4 / D5.
 */
public interface ExptResolMstrRepository
        extends JpaRepository<ExptResolMstr, ExptResolMstrId> {

    /** Look up a non-deleted approval master row by its user-visible document number.
     *  Used by the overseas-payment detail endpoint to resolve a DOC_NO
     *  (VO_YYMMDD_NNN format) sent by the frontend into the APPR_SEQ_NO
     *  needed to find the matching OVRS_PYMN_SUMR row. */
    Optional<ExptResolMstr> findByBizKeyAndDocNoAndDelYn(
            String bizKey, String docNo, String delYn);

    // Approval-status derivation now routes through the legacy DB function
    // wb_get_appr_stts(...) via LegacyFunctionRepository.getApprStts /
    // apprSttsByApprSeqNos (Option A, 2026-06-10) — the prior Java-side
    // APPR_TYPE_CD finders (findApprTypeCd / findApprTypeCdByApprSeqNos) were
    // removed in favour of the single legacy source of truth.

    // APPR_SEQ_NO is now allocated through the single
    // LegacyFunctionRepository.nextApprSeqNo funnel (V140 #1 — the GLOBAL
    // sq_appr_seq_no sequence shared across all Payment modules), replacing the
    // former cross-table MAX+1 finder.

    // DOC_NO is now allocated through the single wb_get_next_val funnel
    // (LegacyFunctionRepository.nextDocNo → V140 #39, env-set-driven). The former
    // Java-side MAX-extraction finders (findMaxDocNoSeqForDate / findMaxDocNoSeqByBizKeyAndPrefix)
    // were removed in the 2026-06-10 Phase-1 funnel migration — no caller remains.

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
     *           (EXISTS in {@code EXPT_RESOL_APPRVR_INFM}), OR</li>
     *       <li>caller uploaded the underlying payment batch — EXISTS in
     *           {@code SCDUL_PYMN_SUMR} / {@code OVRS_PYMN_SUMR} /
     *           {@code CARD_TRNS_HSTR} with {@code reg_id = :userId}
     *           (restored 2026-06-16; the upload event can be performed by a
     *           different user than the approval requester, so without these
     *           the uploader loses visibility — see Redmine #42046).</li>
     *     </ul>
     *   </li>
     * </ul>
     */
    @Query("""
           SELECT new com.kosign.wecms.domain.payment.approvalbox.dto.ApprovalBoxProjection(
               erm.apprSeqNo, erm.docNo, erm.titl, erm.apprDv, erm.docNm, erm.cont,
               erm.totPayKhrAmt, erm.totPayUsdAmt, erm.totPayVndAmt, erm.payDate, erm.draftUserId, erm.draftUserNm,
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
                  LOWER(erm.docNm) LIKE :keywordLike ESCAPE '!' OR
                  LOWER(erm.titl)  LIKE :keywordLike ESCAPE '!' OR
                  LOWER(erm.docNo) LIKE :keywordLike ESCAPE '!')
             AND ( :isAdmin = TRUE
                   OR erm.regId = :userId
                   OR EXISTS (SELECT 1 FROM ExptResolApprvrInfm a
                              WHERE a.bizKey = erm.bizKey
                                AND a.apprSeqNo = erm.apprSeqNo
                                AND a.apprvrId = :userId)
                   OR EXISTS (SELECT 1 FROM ScdulPymnSumr sps
                              WHERE sps.bizKey = erm.bizKey
                                AND sps.apprSeqNo = erm.apprSeqNo
                                AND sps.regId = :userId)
                   OR EXISTS (SELECT 1 FROM OvrsPymnSumr ops
                              WHERE ops.bizKey = erm.bizKey
                                AND ops.apprSeqNo = erm.apprSeqNo
                                AND ops.regId = :userId)
                   OR EXISTS (SELECT 1 FROM CardTrnsHstr cth
                              WHERE cth.bizKey = erm.bizKey
                                AND cth.apprSeqNo = erm.apprSeqNo
                                AND cth.regId = :userId) )
           """)
    Page<ApprovalBoxProjection> search(
            @Param("bizKey")         String bizKey,
            @Param("userId")         String userId,
            @Param("isAdmin")        boolean isAdmin,
            @Param("fromDate")       String fromDate,
            @Param("toDate")         String toDate,
            @Param("apprTypeCd")     String apprTypeCd,
            @Param("keywordPattern") String keywordPattern,
            @Param("keywordLike")    String keywordLike,
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
                  LOWER(erm.docNm) LIKE :keywordLike ESCAPE '!' OR
                  LOWER(erm.titl)  LIKE :keywordLike ESCAPE '!' OR
                  LOWER(erm.docNo) LIKE :keywordLike ESCAPE '!')
             AND ( :isAdmin = TRUE
                   OR erm.regId = :userId
                   OR EXISTS (SELECT 1 FROM ExptResolApprvrInfm a
                              WHERE a.bizKey = erm.bizKey
                                AND a.apprSeqNo = erm.apprSeqNo
                                AND a.apprvrId = :userId)
                   OR EXISTS (SELECT 1 FROM ScdulPymnSumr sps
                              WHERE sps.bizKey = erm.bizKey
                                AND sps.apprSeqNo = erm.apprSeqNo
                                AND sps.regId = :userId)
                   OR EXISTS (SELECT 1 FROM OvrsPymnSumr ops
                              WHERE ops.bizKey = erm.bizKey
                                AND ops.apprSeqNo = erm.apprSeqNo
                                AND ops.regId = :userId)
                   OR EXISTS (SELECT 1 FROM CardTrnsHstr cth
                              WHERE cth.bizKey = erm.bizKey
                                AND cth.apprSeqNo = erm.apprSeqNo
                                AND cth.regId = :userId) )
           """)
    ApprovalBoxTabCounts countTabs(
            @Param("bizKey")         String bizKey,
            @Param("userId")         String userId,
            @Param("isAdmin")        boolean isAdmin,
            @Param("fromDate")       String fromDate,
            @Param("toDate")         String toDate,
            @Param("keywordPattern") String keywordPattern,
            @Param("keywordLike")    String keywordLike);

    /**
     * Footer-totals amount basis — one tuple
     * {@code (APPR_SEQ_NO, APPR_DV, TOT_PAY_KHR_AMT, TOT_PAY_USD_AMT,
     * TOT_PAY_VND_AMT)} per row matching the date / search / visibility
     * scope, with the {@code apprTypeCd} tab predicate intentionally
     * dropped (same scope as {@link #countTabs} → tab-unfiltered).
     *
     * <p>The service resolves each row's currency + card-aware amount
     * (same path as {@code ApprovalBoxService.toRow}) and buckets the
     * sums by {@code CCY_CD} into the footer's USD / VND / KRW totals —
     * mirrors legacy {@code IDO.EXPT_RESOL_MSTR_R003_CNT.xml:36-39}
     * which sums over the same tab-unfiltered set. Bounded by the unpaged
     * read; the Approval Box is date-range scoped so the set stays small.
     */
    @Query("""
           SELECT erm.apprSeqNo, erm.apprDv, erm.totPayKhrAmt, erm.totPayUsdAmt, erm.totPayVndAmt
           FROM ExptResolMstr erm
           WHERE erm.bizKey = :bizKey
             AND erm.delYn  = 'N'
             AND erm.apprDv IN ('21','31','32','33','34','35')
             AND erm.draftDate BETWEEN :fromDate AND :toDate
             AND (:keywordPattern IS NULL OR
                  LOWER(erm.docNm) LIKE :keywordLike ESCAPE '!' OR
                  LOWER(erm.titl)  LIKE :keywordLike ESCAPE '!' OR
                  LOWER(erm.docNo) LIKE :keywordLike ESCAPE '!')
             AND ( :isAdmin = TRUE
                   OR erm.regId = :userId
                   OR EXISTS (SELECT 1 FROM ExptResolApprvrInfm a
                              WHERE a.bizKey = erm.bizKey
                                AND a.apprSeqNo = erm.apprSeqNo
                                AND a.apprvrId = :userId)
                   OR EXISTS (SELECT 1 FROM ScdulPymnSumr sps
                              WHERE sps.bizKey = erm.bizKey
                                AND sps.apprSeqNo = erm.apprSeqNo
                                AND sps.regId = :userId)
                   OR EXISTS (SELECT 1 FROM OvrsPymnSumr ops
                              WHERE ops.bizKey = erm.bizKey
                                AND ops.apprSeqNo = erm.apprSeqNo
                                AND ops.regId = :userId)
                   OR EXISTS (SELECT 1 FROM CardTrnsHstr cth
                              WHERE cth.bizKey = erm.bizKey
                                AND cth.apprSeqNo = erm.apprSeqNo
                                AND cth.regId = :userId) )
           """)
    List<Object[]> findAmountBasisForTotals(
            @Param("bizKey")         String bizKey,
            @Param("userId")         String userId,
            @Param("isAdmin")        boolean isAdmin,
            @Param("fromDate")       String fromDate,
            @Param("toDate")         String toDate,
            @Param("keywordPattern") String keywordPattern,
            @Param("keywordLike")    String keywordLike);

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
