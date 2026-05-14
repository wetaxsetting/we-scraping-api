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
            int limit) {

        String sql = buildUnionSql(apprTypeCds, keyword, false);
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

        String innerSql = buildUnionSql(apprTypeCds, keyword, true);
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

        String innerSql = buildUnionSql(apprTypeCds, keyword, true);
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
    private String buildUnionSql(List<String> apprTypeCds, String keyword, boolean forCount) {

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
                       ops.titl                 AS file_nm,
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
                       erm.appr_sts,
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
                       erm.appr_sts,
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
        // Add ORDER BY for paged list (mirrors JEX DYNAMIC_2 default sort: reg_dtm DESC)
        return union + "\n ORDER BY reg_dtm DESC";
    }

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
