package com.kosign.wecms.domain.payment.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Native SQL repository for the overseas-payment UNION ALL list query.
 *
 * <p>The JEX source (IDO.OVRS_PYMN_SUMR_R002) unions {@code OVRS_PYMN_SUMR}
 * (batch type 'E') and {@code OVRS_PYMN_MSTR} (manual type 'M'), both
 * joined to {@code EXPT_RESOL_MSTR} for approval metadata. A JPQL/Spring
 * Data query cannot express a UNION across two disjoint root entities, so
 * we use a native SQL query via {@link EntityManager}.
 *
 * <p>Approver counts (APPRVR_TOT_CNT, APPRVR_APPR_TOT_CNT) are computed
 * as sub-selects from {@code EXPT_RESOL_APPRVR_INFM}. The full approver
 * list per row is fetched separately by {@link OverseasApproverRepository}.
 */
@Repository
public class OverseasPaymentRepository {

    @PersistenceContext
    private EntityManager em;

    /**
     * Paged UNION ALL list: matches pymn_0007_001_r001_act.jsp filter logic.
     *
     * <p>Column positions (0-based) of the returned Object[] per row:
     * <pre>
     *  0  biz_key
     *  1  ovrs_pymn_type    ('E' | 'M')
     *  2  ovrs_pymn_no
     *  3  ovrs_pymn_type_cd
     *  4  file_nm
     *  5  titl
     *  6  ccy_cd
     *  7  total_tran_amt
     *  8  tot_trns_cnt
     *  9  reg_dtm
     * 10  reg_id
     * 11  appr_seq_no
     * 12  draft_user_id
     * 13  draft_user_nm
     * 14  doc_no
     * 15  appr_sts
     * 16  appr_type_cd
     * 17  trnf_yn
     * 18  appr_dv
     * 19  out_bank_cd
     * 20  wdrl_act_no
     * 21  out_bank_nm
     * 22  bnfc_nm
     * 23  pay_date
     * 24  apprvr_tot_cnt
     * 25  apprvr_appr_tot_cnt
     * </pre>
     *
     * @param bizKey        tenant (required)
     * @param fromDt        start date inclusive, yyyyMMdd (required)
     * @param toDt          end date inclusive, yyyyMMdd (required)
     * @param apprTypeCds   optional approval-status filter list; null = no filter
     * @param keyword       optional ILIKE pattern (pre-wrapped with %, escaped); null = no filter
     * @param offset        SQL OFFSET for pagination
     * @param limit         SQL LIMIT for pagination
     */
    @SuppressWarnings("unchecked")
    public List<Object[]> findScheduled(
            String bizKey,
            String fromDt,
            String toDt,
            List<String> apprTypeCds,
            String keyword,
            int offset,
            int limit,
            String orderByClause) {

        String sql = buildUnionSql(apprTypeCds, keyword, false, orderByClause);
        Query q = em.createNativeQuery(sql);
        bindParams(q, bizKey, fromDt, toDt, apprTypeCds, keyword);
        q.setFirstResult(offset);
        q.setMaxResults(limit);
        return q.getResultList();
    }

    /**
     * Total row count for the same filter set (no pagination).
     * Wraps the UNION ALL in a COUNT(*) so we get one consistent count
     * for both SUMR and MSTR branches.
     */
    public long countScheduled(
            String bizKey,
            String fromDt,
            String toDt,
            List<String> apprTypeCds,
            String keyword) {

        String innerSql = buildUnionSql(apprTypeCds, keyword, true, null);
        String countSql = "SELECT COUNT(*) FROM (" + innerSql + ") __cnt";
        Query q = em.createNativeQuery(countSql);
        bindParams(q, bizKey, fromDt, toDt, apprTypeCds, keyword);
        Number result = (Number) q.getSingleResult();
        return result.longValue();
    }

    /**
     * Per-currency totals — mirrors IDO.OVRS_PYMN_SUMR_R003 in JEX.
     * Returns Object[] per currency: [ccy_cd, totl_amt, row_cnt, trans_cnt].
     */
    @SuppressWarnings("unchecked")
    public List<Object[]> sumByCurrency(
            String bizKey,
            String fromDt,
            String toDt,
            List<String> apprTypeCds,
            String keyword) {

        String innerSql = buildUnionSql(apprTypeCds, keyword, true, null);
        String sql = """
                SELECT ccy_cd,
                       COALESCE(SUM(total_tran_amt), 0) AS totl_amt,
                       COUNT(*)                          AS row_cnt,
                       COALESCE(SUM(tot_trns_cnt), 0)   AS trans_cnt
                  FROM (%s) __totals
                 GROUP BY ccy_cd
                 ORDER BY ccy_cd
                """.formatted(innerSql);
        Query q = em.createNativeQuery(sql);
        bindParams(q, bizKey, fromDt, toDt, apprTypeCds, keyword);
        return q.getResultList();
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    /**
     * Builds the UNION ALL SQL. When {@code forCount} is true the SELECT list
     * is trimmed to the columns needed for counting/totalling; otherwise the
     * full projection is included.
     *
     * <p>The appr_type_cd / keyword predicates are conditionally appended
     * in both SUMR and MSTR branches to match the JEX dynamic-SQL pattern
     * from pymn_0007_001_r001_act.jsp.
     */
    private String buildUnionSql(List<String> apprTypeCds, String keyword, boolean forCount, String orderByClause) {

        boolean filterApprType = apprTypeCds != null && !apprTypeCds.isEmpty();
        boolean filterKeyword  = keyword != null;

        String apprTypeClause = filterApprType ? " AND erm.appr_type_cd IN :apprTypeCds" : "";
        String sumrKeyword    = filterKeyword  ? " AND ops.titl ILIKE :keyword ESCAPE '!'" : "";
        String mstrKeyword    = filterKeyword  ? " AND opm.bnfc_nm ILIKE :keyword ESCAPE '!'" : "";

        // Sub-selects for approver counts (mirrors the json_build_object sub-query in JEX R002)
        String apprvrTotCnt = """
                (SELECT COUNT(*) FROM expt_resol_apprvr_infm
                  WHERE biz_key = ops.biz_key AND appr_seq_no = ops.appr_seq_no)
                """;
        String apprvrApprCnt = """
                (SELECT COUNT(*) FROM expt_resol_apprvr_infm
                  WHERE biz_key = ops.biz_key AND appr_seq_no = ops.appr_seq_no
                    AND apprvr_stts = 'A')
                """;
        String apprvrTotCntM = """
                (SELECT COUNT(*) FROM expt_resol_apprvr_infm
                  WHERE biz_key = opm.biz_key AND appr_seq_no = opm.appr_seq_no)
                """;
        String apprvrApprCntM = """
                (SELECT COUNT(*) FROM expt_resol_apprvr_infm
                  WHERE biz_key = opm.biz_key AND appr_seq_no = opm.appr_seq_no
                    AND apprvr_stts = 'A')
                """;

        String sumrBranch = """
                SELECT ops.biz_key,
                       'E'                      AS ovrs_pymn_type,
                       ops.ovrs_pymn_no,
                       ops.ovrs_pymn_type_cd,
                       ops.org_file_nm          AS file_nm,
                       erm.titl,
                       ops.ccy_cd,
                       ops.total_tran_amt,
                       ops.tot_trns_cnt,
                       ops.reg_dtm,
                       ops.reg_id,
                       ops.appr_seq_no,
                       erm.draft_user_id,
                       erm.draft_user_nm,
                       erm.doc_no,
                       CASE
                           WHEN erm.appr_type_cd = '01' THEN 'C'
                           WHEN erm.appr_type_cd = '02' THEN 'X'
                           WHEN erm.appr_type_cd = '03' THEN 'I'
                           WHEN erm.appr_type_cd = '00' THEN 'P'
                           ELSE COALESCE(ops.appr_stts, erm.appr_sts)
                       END AS appr_sts,
                       erm.appr_type_cd,
                       ops.trnf_yn,
                       erm.appr_dv,
                       ops.out_bank_cd,
                       ops.wdrl_act_no,
                       ops.out_bank_nm,
                       NULL                     AS bnfc_nm,
                       erm.pay_date,
                       %s AS apprvr_tot_cnt,
                       %s AS apprvr_appr_tot_cnt
                  FROM ovrs_pymn_sumr ops
                  LEFT JOIN expt_resol_mstr erm
                    ON ops.biz_key = erm.biz_key AND ops.appr_seq_no = erm.appr_seq_no
                 WHERE ops.biz_key = :bizKey
                   AND LEFT(ops.reg_dtm, 8) BETWEEN :fromDt AND :toDt
                """.formatted(apprvrTotCnt, apprvrApprCnt)
                + apprTypeClause + sumrKeyword;

        String mstrBranch = """
                SELECT opm.biz_key,
                       'M'                      AS ovrs_pymn_type,
                       opm.ovrs_pymn_no,
                       'M'                      AS ovrs_pymn_type_cd,
                       CONCAT(opm.bnfc_nm, ' ', opm.bnfc_acct_no) AS file_nm,
                       erm.titl,
                       opm.trnf_ccy_cd          AS ccy_cd,
                       opm.total_tran_amt,
                       opm.tot_trns_cnt,
                       opm.reg_dtm,
                       opm.reg_id,
                       opm.appr_seq_no,
                       erm.draft_user_id,
                       erm.draft_user_nm,
                       erm.doc_no,
                       CASE
                           WHEN erm.appr_type_cd = '01' THEN 'C'
                           WHEN erm.appr_type_cd = '02' THEN 'X'
                           WHEN erm.appr_type_cd = '03' THEN 'I'
                           WHEN erm.appr_type_cd = '00' THEN 'P'
                           ELSE erm.appr_sts
                       END AS appr_sts,
                       erm.appr_type_cd,
                       opm.trnf_yn,
                       erm.appr_dv,
                       opm.bnfc_bank_cd         AS out_bank_cd,
                       opm.bnfc_acct_no         AS wdrl_act_no,
                       opm.bnfc_bank_name       AS out_bank_nm,
                       opm.bnfc_nm,
                       erm.pay_date,
                       %s AS apprvr_tot_cnt,
                       %s AS apprvr_appr_tot_cnt
                  FROM ovrs_pymn_mstr opm
                  LEFT JOIN expt_resol_mstr erm
                    ON opm.biz_key = erm.biz_key AND opm.appr_seq_no = erm.appr_seq_no
                 WHERE opm.biz_key = :bizKey
                   AND LEFT(opm.reg_dtm, 8) BETWEEN :fromDt AND :toDt
                """.formatted(apprvrTotCntM, apprvrApprCntM)
                + apprTypeClause + mstrKeyword;

        String union = sumrBranch + "\n UNION ALL\n" + mstrBranch;

        if (forCount) {
            return union;
        }
        // orderByClause is a pre-validated whitelist value from OverseasScheduledSortField — never raw user input
        String order = (orderByClause != null && !orderByClause.isBlank()) ? orderByClause : "reg_dtm DESC";
        return union + "\n ORDER BY " + order;
    }

    // ─── single-row fetch ─────────────────────────────────────────────────────

    /**
     * Fetch a single scheduled-payment row by {@code OVRS_PYMN_NO}.
     * Reuses the UNION ALL projection so the mapper in the service is shared.
     *
     * <p>ORDER BY is stripped before wrapping as a derived table — it is unnecessary
     * for a single-row primary-key lookup and can cause issues when Hibernate processes
     * the outer WHERE clause against an ordered subquery.
     *
     * @return the raw Object[] row, or an empty Optional when not found
     */
    @SuppressWarnings("unchecked")
    public java.util.Optional<Object[]> findScheduledById(String bizKey, String ovrsPymnNo) {
        String innerSql = buildUnionSql(null, null, false, null);
        // Strip trailing ORDER BY — not needed for a single-row ID lookup and ORDER BY
        // inside a derived table causes unnecessary complexity for the query planner.
        int orderByIdx = innerSql.lastIndexOf("\n ORDER BY ");
        String noOrderSql = orderByIdx >= 0 ? innerSql.substring(0, orderByIdx) : innerSql;
        String sql = "SELECT * FROM (" + noOrderSql + ") __r WHERE __r.ovrs_pymn_no = :id LIMIT 1";
        Query q = em.createNativeQuery(sql);
        q.setParameter("bizKey", bizKey);
        q.setParameter("fromDt", "19000101");
        q.setParameter("toDt",   "99991231");
        q.setParameter("id",     ovrsPymnNo);
        List<Object[]> rows = q.getResultList();
        return rows.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(rows.get(0));
    }

    // ─── result list (payment-result) ─────────────────────────────────────────

    /**
     * Paged result list — {@code OVRS_PYMN_SUMR} joined with {@code EXPT_RESOL_MSTR}.
     * Includes all rows regardless of {@code TRNF_YN}; the service maps status from that field.
     *
     * <p>Column positions (0-based):
     * <pre>
     *  0  ovrs_pymn_no
     *  1  ccy_cd
     *  2  org_file_nm
     *  3  total_tran_amt
     *  4  tot_trns_cnt
     *  5  reg_dtm
     *  6  draft_user_nm
     *  7  reg_id
     *  8  out_bank_nm
     *  9  out_bank_cd
     * 10  wdrl_act_no
     * 11  trnf_yn
     * 12  doc_no
     * 13  appr_titl   (EXPT_RESOL_MSTR.TITL)
     * </pre>
     */
    @SuppressWarnings("unchecked")
    public List<Object[]> findResult(String bizKey, String fromDt, String toDt,
                                     String keyword, String trnfYn, int offset, int limit,
                                     String orderByClause) {
        // orderByClause is a pre-validated whitelist value from OverseasResultSortField — never raw user input
        String order = (orderByClause != null && !orderByClause.isBlank()) ? orderByClause : "ops.reg_dtm DESC";
        String sql = buildResultSql(keyword, trnfYn) + " ORDER BY " + order;
        Query q = em.createNativeQuery(sql);
        bindResultParams(q, bizKey, fromDt, toDt, keyword, trnfYn);
        q.setFirstResult(offset);
        q.setMaxResults(limit);
        return q.getResultList();
    }

    public long countResult(String bizKey, String fromDt, String toDt, String keyword, String trnfYn) {
        String sql = "SELECT COUNT(*) FROM (" + buildResultSql(keyword, trnfYn) + ") __cnt";
        Query q = em.createNativeQuery(sql);
        bindResultParams(q, bizKey, fromDt, toDt, keyword, trnfYn);
        return ((Number) q.getSingleResult()).longValue();
    }

    @SuppressWarnings("unchecked")
    public List<Object[]> sumResultByCurrency(String bizKey, String fromDt, String toDt,
                                              String keyword, String trnfYn) {
        String inner = buildResultSql(keyword, trnfYn);
        String sql = """
                SELECT ccy_cd,
                       COALESCE(SUM(total_tran_amt), 0),
                       COUNT(*),
                       COALESCE(SUM(tot_trns_cnt), 0)
                  FROM (%s) __t
                 GROUP BY ccy_cd
                 ORDER BY ccy_cd
                """.formatted(inner);
        Query q = em.createNativeQuery(sql);
        bindResultParams(q, bizKey, fromDt, toDt, keyword, trnfYn);
        return q.getResultList();
    }

    @SuppressWarnings("unchecked")
    public java.util.Optional<Object[]> findResultById(String bizKey, String ovrsPymnNo) {
        String sql = buildResultSql(null, null) + " AND ops.ovrs_pymn_no = :id LIMIT 1";
        Query q = em.createNativeQuery(sql);
        q.setParameter("bizKey", bizKey);
        q.setParameter("fromDt", "19000101");
        q.setParameter("toDt",   "99991231");
        q.setParameter("id",     ovrsPymnNo);
        List<Object[]> rows = q.getResultList();
        return rows.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(rows.get(0));
    }

    /**
     * Full (unpaged) result list for Excel export — extends {@link #buildResultSql}
     * with three extra columns needed by {@link com.kosign.wecms.domain.payment.service.OverseasPaymentResultExcelWriter}.
     *
     * <p>Column positions (0-based):
     * <pre>
     *  0  ovrs_pymn_no
     *  1  ccy_cd
     *  2  org_file_nm
     *  3  total_tran_amt   (not needed per-row; kept for parity)
     *  4  tot_trns_cnt     (not needed per-row; kept for parity)
     *  5  reg_dtm          → Transfer Date Time (col A)
     *  6  draft_user_nm    → Requestor (col R)
     *  7  reg_id           → Requestor fallback (col R)
     *  8  out_bank_nm      → Withdrawal Bank (col F)
     *  9  wdrl_act_no      → Account No. (col G)
     * 10  trnf_yn          → Status (col C)
     * 11  doc_no           → Approval Ref. (col U)
     * 12  appr_titl        → Approval Title (col V)
     * 13  appr_reg_dtm     → Request Date/Time (col D)
     * 14  appr_type_cd     → Approval Status (col T)
     * 15  pay_date         → Scheduled Date (col S)
     * </pre>
     */
    @SuppressWarnings("unchecked")
    public List<Object[]> findResultForExport(String bizKey, String fromDt, String toDt,
                                              String keyword, String trnfYn) {
        String sql = buildResultExportSql(keyword, trnfYn) + " ORDER BY ops.reg_dtm DESC";
        Query q = em.createNativeQuery(sql);
        bindResultParams(q, bizKey, fromDt, toDt, keyword, trnfYn);
        return q.getResultList();
    }

    private String buildResultExportSql(String keyword, String trnfYn) {
        String kw   = keyword != null ? " AND ops.titl ILIKE :keyword ESCAPE '!'" : "";
        String trnf = trnfYn  != null ? " AND ops.trnf_yn = :trnfYn" : "";
        return """
                SELECT ops.ovrs_pymn_no,
                       ops.ccy_cd,
                       ops.org_file_nm,
                       ops.total_tran_amt,
                       ops.tot_trns_cnt,
                       ops.reg_dtm,
                       erm.draft_user_nm,
                       ops.reg_id,
                       ops.out_bank_nm,
                       ops.wdrl_act_no,
                       ops.trnf_yn,
                       erm.doc_no,
                       erm.titl        AS appr_titl,
                       erm.reg_dtm     AS appr_reg_dtm,
                       erm.appr_type_cd,
                       erm.pay_date
                  FROM ovrs_pymn_sumr ops
                  LEFT JOIN expt_resol_mstr erm
                    ON ops.biz_key = erm.biz_key AND ops.appr_seq_no = erm.appr_seq_no
                 WHERE ops.biz_key = :bizKey
                   AND LEFT(ops.reg_dtm, 8) BETWEEN :fromDt AND :toDt
                """ + kw + trnf;
    }

    /**
     * Result-list projection.
     *
     * <p>Column positions (0-based):
     * <pre>
     *  0  ovrs_pymn_no
     *  1  ccy_cd
     *  2  org_file_nm
     *  3  total_tran_amt
     *  4  tot_trns_cnt
     *  5  reg_dtm
     *  6  draft_user_nm
     *  7  reg_id
     *  8  out_bank_nm
     *  9  out_bank_cd
     * 10  wdrl_act_no
     * 11  trnf_yn
     * 12  doc_no
     * 13  appr_titl   (EXPT_RESOL_MSTR.TITL)
     * 14  pay_date    (EXPT_RESOL_MSTR.PAY_DATE)
     * 15  appr_stts   (EXPT_RESOL_MSTR.APPR_TYPE_CD)
     * 16  apprvr_nm   (NULL — complex join deferred)
     * </pre>
     */
    private String buildResultSql(String keyword, String trnfYn) {
        String kw    = keyword != null ? " AND ops.titl ILIKE :keyword ESCAPE '!'" : "";
        String trnf  = trnfYn  != null ? " AND ops.trnf_yn = :trnfYn" : "";
        return """
                SELECT ops.ovrs_pymn_no,
                       ops.ccy_cd,
                       ops.org_file_nm,
                       ops.total_tran_amt,
                       ops.tot_trns_cnt,
                       ops.reg_dtm,
                       erm.draft_user_nm,
                       ops.reg_id,
                       ops.out_bank_nm,
                       ops.out_bank_cd,
                       ops.wdrl_act_no,
                       ops.trnf_yn,
                       erm.doc_no,
                       erm.titl          AS appr_titl,
                       erm.pay_date,
                       erm.appr_type_cd  AS appr_stts,
                       NULL              AS apprvr_nm
                  FROM ovrs_pymn_sumr ops
                  LEFT JOIN expt_resol_mstr erm
                    ON ops.biz_key = erm.biz_key AND ops.appr_seq_no = erm.appr_seq_no
                 WHERE ops.biz_key = :bizKey
                   AND LEFT(ops.reg_dtm, 8) BETWEEN :fromDt AND :toDt
                """ + kw + trnf;
    }

    private void bindResultParams(Query q, String bizKey, String fromDt, String toDt,
                                  String keyword, String trnfYn) {
        q.setParameter("bizKey", bizKey);
        q.setParameter("fromDt", fromDt);
        q.setParameter("toDt",   toDt);
        if (keyword != null) q.setParameter("keyword", keyword);
        if (trnfYn  != null) q.setParameter("trnfYn",  trnfYn);
    }

    // ─── common helpers ───────────────────────────────────────────────────────

    /** Binds the common named parameters used in both count and list queries. */
    private void bindParams(Query q,
                            String bizKey,
                            String fromDt,
                            String toDt,
                            List<String> apprTypeCds,
                            String keyword) {
        q.setParameter("bizKey", bizKey);
        q.setParameter("fromDt", fromDt);
        q.setParameter("toDt",   toDt);
        if (apprTypeCds != null && !apprTypeCds.isEmpty()) {
            q.setParameter("apprTypeCds", apprTypeCds);
        }
        if (keyword != null) {
            q.setParameter("keyword", keyword);
        }
    }
}
