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

        // Legacy parity (pymn_0007_001_r001_act.jsp getDynamicOvrs:64-83): the
        // "04" status option is a UI marker for "Not Requested" — rows that have
        // NO approval document, i.e. EXPT_RESOL_MSTR.APPR_TYPE_CD IS NULL via the
        // LEFT JOIN miss. The real stored codes (00/01/02/03) are matched with IN.
        // EXPT_RESOL_MSTR.APPR_TYPE_CD never stores '04' on the DB side, so it must
        // be translated to IS NULL — not passed through as a literal IN value.
        List<String> realApprTypeCds = realApprTypeCds(apprTypeCds);
        boolean matchNotRequested   = hasNotRequestedMarker(apprTypeCds);
        boolean filterKeyword       = keyword != null;

        final String apprTypeClause;
        if (!realApprTypeCds.isEmpty() && matchNotRequested) {
            apprTypeClause = " AND (erm.appr_type_cd IN :apprTypeCds OR erm.appr_type_cd IS NULL)";
        } else if (!realApprTypeCds.isEmpty()) {
            apprTypeClause = " AND erm.appr_type_cd IN :apprTypeCds";
        } else if (matchNotRequested) {
            apprTypeClause = " AND erm.appr_type_cd IS NULL";
        } else {
            apprTypeClause = "";
        }
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
                           ELSE erm.appr_sts
                       END AS appr_sts,
                       erm.appr_type_cd,
                       ops.trnf_yn,
                       erm.appr_dv,
                       ops.out_bank_cd,
                       ops.wdrl_act_no,
                       (SELECT bi.rprs_bank_nm FROM bank_infm bi
                         WHERE bi.bank_cd = ops.out_bank_cd
                         ORDER BY bi.start_date DESC LIMIT 1) AS out_bank_nm,
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
                                     String keyword, java.util.List<String> statusLetters,
                                     int offset, int limit, String orderByClause) {
        // orderByClause is a pre-validated whitelist value from OverseasResultSortField — never raw user input
        String order = (orderByClause != null && !orderByClause.isBlank()) ? orderByClause : "reg_dtm DESC";
        String sql = buildResultSql(keyword, statusLetters) + " ORDER BY " + order;
        Query q = em.createNativeQuery(sql);
        bindResultParams(q, bizKey, fromDt, toDt, keyword, statusLetters);
        q.setFirstResult(offset);
        q.setMaxResults(limit);
        return q.getResultList();
    }

    public long countResult(String bizKey, String fromDt, String toDt, String keyword,
                            java.util.List<String> statusLetters) {
        String sql = "SELECT COUNT(*) FROM (" + buildResultSql(keyword, statusLetters) + ") __cnt";
        Query q = em.createNativeQuery(sql);
        bindResultParams(q, bizKey, fromDt, toDt, keyword, statusLetters);
        return ((Number) q.getSingleResult()).longValue();
    }

    /**
     * Per-currency totals — mirrors {@code IDO.OVRS_PYMN_SUMR_R005_CNT}.
     *
     * <p>Legacy parity: the totals query filters by date + keyword + {@code TRNF_YN='Y'}
     * only and deliberately <b>ignores</b> the Completed / In-Progress status filter
     * (legacy {@code pymn_0008_001_r001_act.jsp} feeds the count IDO {@code getDynamic}
     * — date + keyword — not {@code getDynamicStatusFilter}). The footer totals therefore
     * reflect the full date-range result set regardless of the status checkboxes.
     * Date filter is on {@code TRNF_DT} (F1).
     *
     * <p>F3: includes both the E branch ({@code OVRS_PYMN_SUMR}, currency =
     * {@code CCY_CD}, keyword on {@code TITL}) and the M branch
     * ({@code OVRS_PYMN_MSTR}, currency = {@code TRNF_CCY_CD}, keyword on
     * {@code BNFC_NM}) before grouping by currency — mirrors R005_CNT's UNION ALL.
     */
    @SuppressWarnings("unchecked")
    public List<Object[]> sumResultByCurrency(String bizKey, String fromDt, String toDt,
                                              String keyword) {
        String kwE = keyword != null ? " AND ops.titl ILIKE :keyword ESCAPE '!'" : "";
        String kwM = keyword != null ? " AND opm.bnfc_nm ILIKE :keyword ESCAPE '!'" : "";
        String sql = """
                SELECT u.ccy_cd,
                       COALESCE(SUM(u.total_tran_amt), 0),
                       COUNT(*),
                       COALESCE(SUM(u.tot_trns_cnt), 0)
                  FROM (
                      SELECT ops.ccy_cd, ops.total_tran_amt, ops.tot_trns_cnt
                        FROM ovrs_pymn_sumr ops
                       WHERE ops.biz_key = :bizKey
                         AND ops.trnf_yn = 'Y'
                         AND LEFT(ops.trnf_dt, 8) BETWEEN :fromDt AND :toDt
                %s
                      UNION ALL
                      SELECT opm.trnf_ccy_cd AS ccy_cd, opm.total_tran_amt, opm.tot_trns_cnt
                        FROM ovrs_pymn_mstr opm
                       WHERE opm.biz_key = :bizKey
                         AND opm.trnf_yn = 'Y'
                         AND LEFT(opm.trnf_dt, 8) BETWEEN :fromDt AND :toDt
                %s
                  ) u
                 GROUP BY u.ccy_cd
                 ORDER BY u.ccy_cd
                """.formatted(kwE, kwM);
        Query q = em.createNativeQuery(sql);
        q.setParameter("bizKey", bizKey);
        q.setParameter("fromDt", fromDt);
        q.setParameter("toDt",   toDt);
        if (keyword != null) q.setParameter("keyword", keyword);
        return q.getResultList();
    }

    @SuppressWarnings("unchecked")
    public java.util.Optional<Object[]> findResultById(String bizKey, String ovrsPymnNo) {
        // buildResultSql(null, null) ends with the derived-table alias `t`; filter the
        // outer query on t.ovrs_pymn_no. Wide date window so a row is found regardless
        // of its TRNF_DT.
        String sql = buildResultSql(null, null) + " AND t.ovrs_pymn_no = :id LIMIT 1";
        Query q = em.createNativeQuery(sql);
        q.setParameter("bizKey", bizKey);
        q.setParameter("fromDt", "19000101");
        q.setParameter("toDt",   "99991231");
        q.setParameter("id",     ovrsPymnNo);
        List<Object[]> rows = q.getResultList();
        return rows.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(rows.get(0));
    }

    /**
     * Full (unpaged) result list for Excel export. Reuses {@link #buildResultSql}
     * so the export shares the list's {@code TRNF_DT} date filter (F1), computed
     * status filter (F2), File-Name = {@code TITL} (F4), and per-batch counts —
     * what the user sees is exactly what exports.
     *
     * <p>Reads the {@link #buildResultSql} column layout (0-based): see that
     * method's javadoc. Export consumers use:
     * <pre>
     *  0  ovrs_pymn_no     2  file_nm (TITL)   5  reg_dtm (=TRNF_DT → Transfer Date Time)
     *  6  draft_user_nm    7  reg_id           8  out_bank_nm    10  wdrl_act_no
     * 11  trnf_yn         12  doc_no          13  appr_titl      14  pay_date
     * 15  appr_stts (APPR_TYPE_CD)           22  appr_reg_dtm (Request Date/Time)
     * </pre>
     */
    @SuppressWarnings("unchecked")
    public List<Object[]> findResultForExport(String bizKey, String fromDt, String toDt,
                                              String keyword, java.util.List<String> statusLetters) {
        String sql = buildResultSql(keyword, statusLetters) + " ORDER BY reg_dtm DESC";
        Query q = em.createNativeQuery(sql);
        bindResultParams(q, bizKey, fromDt, toDt, keyword, statusLetters);
        return q.getResultList();
    }

    /**
     * Result-list projection — faithful port of {@code IDO.OVRS_PYMN_SUMR_R005}
     * (legacy {@code pymn_0008_001_r001}).
     *
     * <p>F3 (M-mode, 2026-06-19): legacy R005 is a UNION ALL of
     * {@code OVRS_PYMN_SUMR} (type 'E', Excel-batch) and {@code OVRS_PYMN_MSTR}
     * (type 'M', individual/manual). Both branches are now implemented here —
     * the inner derived table {@code t} is {@code E UNION ALL M}. The M branch
     * mirrors R005 lines 127-191: file_nm = {@code BNFC_NM}, ccy = {@code TRNF_CCY_CD},
     * withdrawal bank/acct = the debit-account fields ({@code DBT_ACCT_CD} /
     * {@code DBT_ACCT_NO} — same source as the R006 detail header), keyword
     * search on {@code BNFC_NM}, success/error counts over {@code TB_PAY_FILE_DATA}
     * seq='1' (R005 lines 162-183). The outer SELECT derives {@code PRS_CNT} and
     * the computed {@code TRNF_STTS} (F/P/C) for both branches identically.
     *
     * <p>Both branches filter {@code TRNF_YN='Y'} (dispatched) + {@code TRNF_DT}
     * date range (F1). Status filter applies to the outer computed TRNF_STTS.
     *
     * <p>Column positions (0-based):
     * <pre>
     *  0  ovrs_pymn_no
     *  1  ccy_cd            (E: CCY_CD · M: TRNF_CCY_CD)
     *  2  file_nm           (E: OVRS_PYMN_SUMR.TITL · M: OVRS_PYMN_MSTR.BNFC_NM — R005 FILE_NM, F4)
     *  3  total_tran_amt
     *  4  tot_trns_cnt
     *  5  reg_dtm           (TRNF_DT — legacy R005 aliases TRNF_DT AS REG_DTM, F1)
     *  6  draft_user_nm
     *  7  reg_id
     *  8  out_bank_nm       (E: OUT_BANK_CD bank name · M: DBT_ACCT_CD bank name)
     *  9  out_bank_cd       (E: OUT_BANK_CD · M: DBT_ACCT_CD)
     * 10  wdrl_act_no       (E: WDRL_ACT_NO · M: DBT_ACCT_NO)
     * 11  trnf_yn
     * 12  doc_no
     * 13  appr_titl         (EXPT_RESOL_MSTR.TITL)
     * 14  pay_date          (EXPT_RESOL_MSTR.PAY_DATE)
     * 15  appr_stts         (EXPT_RESOL_MSTR.APPR_TYPE_CD)
     * 16  apprvr_nm         (NULL — complex join deferred)
     * 17  appr_seq_no       (F4b approval-doc key)
     * 18  tot_trns_succ_cnt (F2)
     * 19  tot_trns_error_cnt(F2)
     * 20  prs_cnt           (F2)
     * 21  trnf_stts         (computed F/P/C — F2)
     * 22  appr_reg_dtm      (EXPT_RESOL_MSTR.REG_DTM — export Request Date/Time)
     * 23  ovrs_pymn_type    ('E' | 'M' — export branches the per-line loop on this)
     * </pre>
     *
     * @param statusLetters when non-empty, filters on the computed TRNF_STTS via
     *                      {@code IN (:statuses)} — letters are {@code C}/{@code F}/{@code P}
     *                      (mirrors legacy {@code getDynamicStatusFilter}). Null/empty = all.
     */
    private String buildResultSql(String keyword, java.util.List<String> statusLetters) {
        // E-branch keyword targets OVRS_PYMN_SUMR.TITL; M-branch targets
        // OVRS_PYMN_MSTR.BNFC_NM (legacy R005 getDynamic / getDynamic_). Both bind :keyword.
        String kwE = keyword != null ? " AND ops.titl ILIKE :keyword ESCAPE '!'" : "";
        String kwM = keyword != null ? " AND opm.bnfc_nm ILIKE :keyword ESCAPE '!'" : "";
        // Status filter on the computed TRNF_STTS — repeats the CASE because
        // Postgres forbids referencing an output alias in WHERE. Mirrors legacy
        // pymn_0008_001_r001_act.jsp getDynamicStatusFilter (lines 138-146).
        String statusClause = (statusLetters != null && !statusLetters.isEmpty())
                ? " AND (CASE"
                + " WHEN COALESCE(t.tot_trns_error_cnt,0) = t.tot_trns_cnt THEN 'F'"
                + " WHEN COALESCE(t.tot_trns_error_cnt,0) + COALESCE(t.tot_trns_succ_cnt,0) < t.tot_trns_cnt THEN 'P'"
                + " ELSE 'C' END) IN (:statuses)"
                : "";
        return """
                SELECT t.ovrs_pymn_no,
                       t.ccy_cd,
                       t.file_nm,
                       t.total_tran_amt,
                       t.tot_trns_cnt,
                       t.reg_dtm,
                       t.draft_user_nm,
                       t.reg_id,
                       t.out_bank_nm,
                       t.out_bank_cd,
                       t.wdrl_act_no,
                       t.trnf_yn,
                       t.doc_no,
                       t.appr_titl,
                       t.pay_date,
                       t.appr_stts,
                       NULL AS apprvr_nm,
                       t.appr_seq_no,
                       t.tot_trns_succ_cnt,
                       t.tot_trns_error_cnt,
                       (t.tot_trns_cnt - (COALESCE(t.tot_trns_error_cnt,0) + COALESCE(t.tot_trns_succ_cnt,0))) AS prs_cnt,
                       CASE
                           WHEN COALESCE(t.tot_trns_error_cnt,0) = t.tot_trns_cnt THEN 'F'
                           WHEN COALESCE(t.tot_trns_error_cnt,0) + COALESCE(t.tot_trns_succ_cnt,0) < t.tot_trns_cnt THEN 'P'
                           ELSE 'C'
                       END AS trnf_stts,
                       t.appr_reg_dtm,
                       t.ovrs_pymn_type
                  FROM (
                      /* ── E branch: OVRS_PYMN_SUMR (Excel-batch) ── */
                      SELECT ops.ovrs_pymn_no,
                             'E'                       AS ovrs_pymn_type,
                             ops.ccy_cd,
                             ops.titl                 AS file_nm,
                             ops.total_tran_amt,
                             ops.tot_trns_cnt,
                             ops.trnf_dt              AS reg_dtm,
                             erm.draft_user_nm,
                             ops.reg_id,
                             (SELECT bi.rprs_bank_nm FROM bank_infm bi
                               WHERE bi.bank_cd = ops.out_bank_cd
                               ORDER BY bi.start_date DESC LIMIT 1) AS out_bank_nm,
                             ops.out_bank_cd,
                             ops.wdrl_act_no,
                             ops.trnf_yn,
                             erm.doc_no,
                             erm.titl                 AS appr_titl,
                             erm.pay_date,
                             erm.appr_type_cd         AS appr_stts,
                             ops.appr_seq_no,
                             erm.reg_dtm              AS appr_reg_dtm,
                             (SELECT COUNT(*)
                                FROM ovrs_pymn_sumr_detl spmd
                                LEFT JOIN tb_pay_file_mst tpfm
                                  ON tpfm.biz_key = spmd.biz_key
                                 AND tpfm.tran_dt = ops.pay_tran_dt
                                 AND tpfm.file_seq_no = ops.pay_file_seq_no
                                LEFT JOIN tb_pay_file_data tpfd
                                  ON tpfd.biz_key = spmd.biz_key
                                 AND tpfd.tran_dt = spmd.pay_tran_dt
                                 AND tpfd.file_seq_no = ops.pay_file_seq_no
                                 AND CAST(tpfd.seq AS VARCHAR) = spmd.pay_tran_seq
                                WHERE spmd.biz_key = ops.biz_key
                                  AND spmd.ovrs_pymn_no = ops.ovrs_pymn_no
                                  AND tpfm.proc_stat = '0'
                                  AND TRIM(COALESCE(tpfd.rs_cd,'')) = '0000') AS tot_trns_succ_cnt,
                             (SELECT COUNT(*)
                                FROM ovrs_pymn_sumr_detl spmd
                                LEFT JOIN tb_pay_file_mst tpfm
                                  ON tpfm.biz_key = spmd.biz_key
                                 AND tpfm.tran_dt = ops.pay_tran_dt
                                 AND tpfm.file_seq_no = ops.pay_file_seq_no
                                LEFT JOIN tb_pay_file_data tpfd
                                  ON tpfd.biz_key = spmd.biz_key
                                 AND tpfd.tran_dt = spmd.pay_tran_dt
                                 AND tpfd.file_seq_no = ops.pay_file_seq_no
                                 AND CAST(tpfd.seq AS VARCHAR) = spmd.pay_tran_seq
                                WHERE spmd.biz_key = ops.biz_key
                                  AND spmd.ovrs_pymn_no = ops.ovrs_pymn_no
                                  AND (tpfm.proc_stat = '3'
                                       OR (tpfm.proc_stat = '0' AND TRIM(COALESCE(tpfd.rs_cd,'')) <> '0000'))) AS tot_trns_error_cnt
                        FROM ovrs_pymn_sumr ops
                        LEFT JOIN expt_resol_mstr erm
                          ON ops.biz_key = erm.biz_key AND ops.appr_seq_no = erm.appr_seq_no
                       WHERE ops.biz_key = :bizKey
                         AND ops.trnf_yn = 'Y'
                         AND LEFT(ops.trnf_dt, 8) BETWEEN :fromDt AND :toDt
                %s
                      UNION ALL
                      /* ── M branch: OVRS_PYMN_MSTR (individual/manual) — mirrors R005 L127-191 ── */
                      SELECT opm.ovrs_pymn_no,
                             'M'                       AS ovrs_pymn_type,
                             opm.trnf_ccy_cd          AS ccy_cd,
                             opm.bnfc_nm              AS file_nm,
                             opm.total_tran_amt,
                             opm.tot_trns_cnt,
                             opm.trnf_dt              AS reg_dtm,
                             erm.draft_user_nm,
                             opm.reg_id,
                             (SELECT bi.rprs_bank_nm FROM bank_infm bi
                               WHERE bi.bank_cd = opm.dbt_acct_cd
                               ORDER BY bi.start_date DESC LIMIT 1) AS out_bank_nm,
                             opm.dbt_acct_cd          AS out_bank_cd,
                             opm.dbt_acct_no          AS wdrl_act_no,
                             opm.trnf_yn,
                             erm.doc_no,
                             erm.titl                 AS appr_titl,
                             erm.pay_date,
                             erm.appr_type_cd         AS appr_stts,
                             opm.appr_seq_no,
                             erm.reg_dtm              AS appr_reg_dtm,
                             (SELECT COUNT(*)
                                FROM tb_pay_file_data tpfd
                                WHERE tpfd.biz_key = opm.biz_key
                                  AND tpfd.file_seq_no = opm.pay_file_seq_no
                                  AND tpfd.tran_dt = opm.pay_tran_dt
                                  AND CAST(tpfd.seq AS VARCHAR) = '1'
                                  AND tbfm.proc_stat = '0'
                                  AND TRIM(COALESCE(tpfd.rs_cd,'')) = '0000') AS tot_trns_succ_cnt,
                             (SELECT COUNT(*)
                                FROM tb_pay_file_data tpfd
                                WHERE tpfd.biz_key = opm.biz_key
                                  AND tpfd.file_seq_no = opm.pay_file_seq_no
                                  AND tpfd.tran_dt = opm.pay_tran_dt
                                  AND CAST(tpfd.seq AS VARCHAR) = '1'
                                  AND (tbfm.proc_stat = '3'
                                       OR (tbfm.proc_stat = '0' AND (TRIM(COALESCE(tpfd.rs_cd,'')) <> '0000' OR tpfd.rs_cd IS NULL)))) AS tot_trns_error_cnt
                        FROM ovrs_pymn_mstr opm
                        LEFT JOIN expt_resol_mstr erm
                          ON opm.biz_key = erm.biz_key AND opm.appr_seq_no = erm.appr_seq_no
                        LEFT JOIN tb_pay_file_mst tbfm
                          ON tbfm.biz_key = opm.biz_key
                         AND tbfm.tran_dt = opm.pay_tran_dt
                         AND tbfm.file_seq_no = opm.pay_file_seq_no
                       WHERE opm.biz_key = :bizKey
                         AND opm.trnf_yn = 'Y'
                         AND LEFT(opm.trnf_dt, 8) BETWEEN :fromDt AND :toDt
                %s
                  ) t
                 WHERE 1=1
                %s
                """.formatted(kwE, kwM, statusClause);
    }

    /**
     * M-mode (OVRS_PYMN_MSTR) detail row(s) for the Result detail page — faithful
     * port of {@code IDO.OVRS_PYMN_SUMR_R007} M branch (lines 99-126). An M-mode
     * payment is one remittance, so this returns a single line (no
     * {@code OVRS_PYMN_SUMR_DETL} child table). {@code PROC_STAT}/{@code RS_CD}/
     * {@code RS_MSG}/{@code PROC_DTTM} come from {@code TB_PAY_FILE_MST}/{@code _DATA}
     * (seq='1'), so the FE derives the per-line result status the same way it does
     * for E-mode lines.
     *
     * <p>Also reused by the Result export to emit the M-mode line (R010 M branch).
     *
     * <p>Column positions (0-based):
     * <pre>
     *  0 ovrs_pymn_no   1 hand_trco_key   2 trnf_ccy_cd   3 trnf_amt   4 fee_payer_cd
     *  5 memo           6 chart_acct_cd   7 bnfc_nm       8 bnfc_bank_name
     *  9 bnfc_bank_bic 10 bnfc_acct_no   11 bnfc_addr    12 trnf_yn
     * 13 proc_stat     14 proc_dttm      15 rs_cd        16 rs_msg
     * </pre>
     */
    @SuppressWarnings("unchecked")
    public List<Object[]> findMmodeDetailRows(String bizKey, String ovrsPymnNo) {
        String sql = """
                SELECT opm.ovrs_pymn_no,
                       opm.hand_trco_key,
                       opm.trnf_ccy_cd,
                       opm.trnf_amt,
                       opm.fee_payer_cd,
                       opm.memo,
                       opm.chart_acct_cd,
                       opm.bnfc_nm,
                       opm.bnfc_bank_name,
                       opm.bnfc_bank_bic,
                       opm.bnfc_acct_no,
                       opm.bnfc_addr,
                       opm.trnf_yn,
                       tbfm.proc_stat,
                       tbfm.proc_dttm,
                       TRIM(tpfd.rs_cd) AS rs_cd,
                       tbfm.rs_msg
                  FROM ovrs_pymn_mstr opm
                  LEFT JOIN tb_pay_file_mst tbfm
                    ON tbfm.biz_key = opm.biz_key
                   AND tbfm.tran_dt = opm.pay_tran_dt
                   AND tbfm.file_seq_no = opm.pay_file_seq_no
                  LEFT JOIN tb_pay_file_data tpfd
                    ON tpfd.biz_key = opm.biz_key
                   AND tpfd.tran_dt = opm.pay_tran_dt
                   AND tpfd.file_seq_no = opm.pay_file_seq_no
                   AND CAST(tpfd.seq AS VARCHAR) = '1'
                 WHERE opm.biz_key = :bizKey
                   AND opm.ovrs_pymn_no = :ovrsPymnNo
                """;
        Query q = em.createNativeQuery(sql);
        q.setParameter("bizKey", bizKey);
        q.setParameter("ovrsPymnNo", ovrsPymnNo);
        return q.getResultList();
    }

    private void bindResultParams(Query q, String bizKey, String fromDt, String toDt,
                                  String keyword, java.util.List<String> statusLetters) {
        q.setParameter("bizKey", bizKey);
        q.setParameter("fromDt", fromDt);
        q.setParameter("toDt",   toDt);
        if (keyword != null) q.setParameter("keyword", keyword);
        if (statusLetters != null && !statusLetters.isEmpty()) q.setParameter("statuses", statusLetters);
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
        // Bind only the real stored codes (00/01/02/03). The "04" not-requested
        // marker is expressed as IS NULL in the SQL and carries no bind value —
        // keep this in lock-step with buildUnionSql's apprTypeClause.
        List<String> realApprTypeCds = realApprTypeCds(apprTypeCds);
        if (!realApprTypeCds.isEmpty()) {
            q.setParameter("apprTypeCds", realApprTypeCds);
        }
        if (keyword != null) {
            q.setParameter("keyword", keyword);
        }
    }

    /** Legacy UI marker for "Not Requested" — maps to APPR_TYPE_CD IS NULL, never a stored code. */
    private static final String NOT_REQUESTED_MARKER = "04";

    /** The real stored approval-type codes from the incoming filter (everything except the "04" marker). */
    private static List<String> realApprTypeCds(List<String> raw) {
        if (raw == null) return List.of();
        return raw.stream()
                .filter(c -> c != null)
                .map(String::trim)
                .filter(c -> !c.isEmpty() && !NOT_REQUESTED_MARKER.equals(c))
                .toList();
    }

    /** True when the incoming filter includes the "04" Not-Requested marker (→ APPR_TYPE_CD IS NULL). */
    private static boolean hasNotRequestedMarker(List<String> raw) {
        if (raw == null) return false;
        return raw.stream().anyMatch(c -> c != null && NOT_REQUESTED_MARKER.equals(c.trim()));
    }
}
