package com.kosign.wecms.domain.banking.smartorganizer.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Native-SQL data access for the Smart Organizer "eligible transactions"
 * read (rule-matched, unconfirmed). The dynamic count/page query was moved
 * verbatim out of {@code SmartOrganizerService} (architecture cleanup) —
 * SQL semantics, tenant binding, the rule lateral-join, the usage-group
 * direction filter, and pagination are unchanged.
 *
 * <p>Mirrors legacy {@code ACCT_TRNS_HSTR_R028}. Tenant-scoped on
 * {@code h.biz_key = :bizKey} (every query). The caller resolves the sort
 * column through the {@code SmartOrganizerSortField} whitelist and passes
 * only the validated SQL expression in — raw user input never reaches the
 * {@code ORDER BY}.
 */
@Repository
public class SmartOrganizerEligibleRepository {

    @PersistenceContext
    private EntityManager em;

    // R01 — reproduce the LEGACY matching precedence (ACCT_TRNS_HSTR_R028.xml:86-93).
    // By SQL precedence the legacy `... AND (Exact) OR (Contains)` leaves the Contains
    // arm UNgated by direction, so a Contains rule matches EITHER transaction direction.
    // We reproduce that quirk by gating the direction predicate on the Exact arm only.
    // BIZ_KEY is kept AND-ed on BOTH arms — the legacy cross-tenant precedence leak is
    // deliberately NOT reproduced (tenant-isolation invariant; see remediation plan R01).
    // Lowest DISP_ORD wins (LIMIT 1); RMRK_SEQ_NO is a deterministic tiebreaker that does
    // not change the winner when DISP_ORD is unique (the legacy intent).
    private static final String RULE_JOIN =
            " JOIN LATERAL ( " +
            "   SELECT r.rmrk_seq_no, r.disp_ord, r.match_type, " +
            "          r.rcv_pay_dv, r.use_usag_cd, r.vas_acct_seq_no, " +
            "          r.trco_key, r.chart_acct_cd, r.chart_acct_seq_no " +
            "     FROM rmrk_org_infm r " +
            "    WHERE r.biz_key = h.biz_key " +
            "      AND ( ( r.rcv_pay_dv = CASE WHEN h.inout_dv = '1' THEN 'RCV' ELSE 'PAY' END " +
            "              AND r.match_type = 'E' AND r.rmrk = h.rmrk ) " +
            "         OR ( r.match_type <> 'E' AND h.rmrk ILIKE '%' || r.rmrk || '%' ) ) " +
            "    ORDER BY r.disp_ord ASC, r.rmrk_seq_no ASC " +
            "    LIMIT 1 " +
            " ) r ON TRUE ";

    /**
     * Count of eligible (rule-matched, unconfirmed) transactions for the
     * selected account in the date window. Tenant-scoped + non-admin filters
     * identical to {@link #pageEligible}.
     */
    public long countEligible(String bizKey, String bankCd, String acctNo,
                              String fromDt, String toDt, String search) {
        String countSql = "SELECT COUNT(*) FROM acct_trns_hstr h " + RULE_JOIN + buildWhere(search);
        Query countQ = em.createNativeQuery(countSql);
        bind(countQ, bizKey, bankCd, acctNo, fromDt, toDt, search);
        return ((Number) countQ.getSingleResult()).longValue();
    }

    /**
     * One page of eligible transactions with the matched rule's classification
     * targets projected. {@code orderByExpr} MUST be a whitelist-validated SQL
     * expression (from {@code SmartOrganizerSortField.sqlExpression()}), never
     * raw client input. Returns the raw {@code Object[]} rows in the projection
     * order the caller maps in {@code resolveRows(...)}.
     */
    public List<Object[]> pageEligible(String bizKey, String bankCd, String acctNo,
                                       String fromDt, String toDt, String search,
                                       String orderByExpr, boolean ascending,
                                       int offset, int limit) {
        String orderBy = " ORDER BY " + orderByExpr
                + (ascending ? " ASC" : " DESC")
                + ", h.acct_seq_no ASC ";

        // R06 (ccy_cd) + R07 (bal) added to the projection; display names are resolved
        // from the classification codes in resolveRows(...) on the service side.
        String selectSql =
                "SELECT h.acct_seq_no, h.bank_cd, h.acct_no, " +
                "       h.trsc_dt, h.trsc_tm, h.inout_dv, " +
                "       CASE WHEN h.inout_dv = '1' THEN h.trsc_amt ELSE NULL END AS in_amt, " +
                "       CASE WHEN h.inout_dv = '2' THEN h.trsc_amt ELSE NULL END AS out_amt, " +
                "       h.bal, h.ccy_cd, h.rmrk, " +
                "       r.rmrk_seq_no, r.chart_acct_cd, r.chart_acct_seq_no, " +
                "       r.use_usag_cd, r.vas_acct_seq_no, r.trco_key " +
                "  FROM acct_trns_hstr h " + RULE_JOIN + buildWhere(search) + orderBy;

        Query q = em.createNativeQuery(selectSql);
        bind(q, bizKey, bankCd, acctNo, fromDt, toDt, search);
        q.setFirstResult(offset);
        q.setMaxResults(limit);

        @SuppressWarnings("unchecked")
        List<Object[]> raw = q.getResultList();
        return raw;
    }

    /** Shared WHERE — identical to the former inline service builder. */
    private static String buildWhere(String search) {
        StringBuilder where = new StringBuilder();
        where.append(" WHERE h.biz_key = :bizKey ");
        // R11 — restrict to the selected account (BIZ_KEY + BANK_CD + ACCT_NO).
        where.append("   AND h.bank_cd = :bankCd ");
        where.append("   AND h.acct_no = :acctNo ");
        where.append("   AND h.trsc_dt BETWEEN :fromDt AND :toDt ");
        // Loan / time-deposit accounts excluded (legacy parity).
        where.append("   AND EXISTS ( ");
        where.append("       SELECT 1 FROM acct_mstr m ");
        where.append("        WHERE m.biz_key = h.biz_key ");
        where.append("          AND m.bank_cd = h.bank_cd ");
        where.append("          AND m.acct_no = h.acct_no ");
        where.append("          AND m.acct_type_cd IN ('03','04','05','06') ");
        where.append("   ) ");
        // Not yet confirmed.
        where.append("   AND NOT EXISTS ( ");
        where.append("       SELECT 1 FROM acct_trns_rcv_pay_infm c ");
        where.append("        WHERE c.biz_key = h.biz_key ");
        where.append("          AND c.acct_seq_no = h.acct_seq_no ");
        where.append("   ) ");
        if (search != null && !search.isBlank()) {
            where.append("   AND h.rmrk ILIKE '%' || :search || '%' ");
        }

        // R02 — usage-group eligibility filter (legacy getDynamic1,
        // fnsh_0200_001_r001_act.jsp:100-112). When the winning rule classifies
        // via a usage (USE_USAG_CD present), that usage's group must agree with
        // the bank row direction: INOUT_DV '1' (deposit) → USAG_GRP_CD '4',
        // '2' (withdrawal) → '6'. OPTION B: gated on `r.use_usag_cd IS NOT NULL`
        // so VAS-only / chart-only rules remain eligible and skip the group check.
        where.append("   AND ( r.use_usag_cd IS NULL OR EXISTS ( ");
        where.append("       SELECT 1 FROM busi_usag_set_infm u ");
        where.append("        WHERE u.biz_key = h.biz_key ");
        where.append("          AND u.use_usag_cd = r.use_usag_cd ");
        where.append("          AND u.usag_grp_cd = CASE WHEN h.inout_dv = '1' THEN '4' ELSE '6' END ");
        where.append("   ) ) ");
        return where.toString();
    }

    /** Shared parameter binding — identical to the former inline service binds. */
    private static void bind(Query q, String bizKey, String bankCd, String acctNo,
                             String fromDt, String toDt, String search) {
        q.setParameter("bizKey", bizKey);
        q.setParameter("bankCd", bankCd);
        q.setParameter("acctNo", acctNo);
        q.setParameter("fromDt", fromDt);
        q.setParameter("toDt", toDt);
        if (search != null && !search.isBlank()) {
            q.setParameter("search", search.trim());
        }
    }
}
