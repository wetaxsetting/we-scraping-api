package com.kosign.wecms.common.legacy;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

/**
 * Single funnel for native calls to surviving legacy PL/pgSQL functions during
 * the JEX → Spring cut-over. Every {@code wb_get_next_val(...)} site MUST go
 * through this repository — never via ad-hoc {@code @Query(nativeQuery=true)}
 * scattered across domain repositories. This makes Phase 3 retirement a
 * {@code grep} target.
 *
 * <p>Phase 1 surface: {@link #nextVal(String, String)} and
 * {@link #nextVal(String, String, String, String, String, String, String, String)}
 * which delegate to {@code wb_get_next_val(...)} with up to six trailing
 * parameters (matching the legacy signature).
 *
 * <p>Per Phase 1 scope (seahorse plan §2 + crystalline-honking-sky.md §7.2)
 * only the {@code BIZ_KEY} (busi_infm) and {@code EMPL_NO} (empl_infm) branches
 * are exercised. Other branches return {@code null} from the local-DB
 * V11 reduced function — calling them indicates a Phase 1 scope violation.
 */
@Repository
public class LegacyFunctionRepository {

    @PersistenceContext
    private EntityManager em;

    /** Two-argument shorthand. */
    public String nextVal(String table, String field) {
        return nextVal(table, field, null, null, null, null, null, null);
    }

    /**
     * Calls {@code wb_get_next_val(:t, :f, :p1, :p2, :p3, :p4, :p5, :p6)}.
     * Any parameter not used by the matched branch should be {@code null}.
     */
    public String nextVal(String table, String field,
                          String p1, String p2, String p3,
                          String p4, String p5, String p6) {
        Query q = em.createNativeQuery(
                "SELECT wb_get_next_val(" +
                        "CAST(?1 AS varchar), CAST(?2 AS varchar), " +
                        "CAST(?3 AS varchar), CAST(?4 AS varchar), " +
                        "CAST(?5 AS varchar), CAST(?6 AS varchar), " +
                        "CAST(?7 AS varchar), CAST(?8 AS varchar))");
        q.setParameter(1, table);
        q.setParameter(2, field);
        q.setParameter(3, p1);
        q.setParameter(4, p2);
        q.setParameter(5, p3);
        q.setParameter(6, p4);
        q.setParameter(7, p5);
        q.setParameter(8, p6);
        Object result = q.getSingleResult();
        return result != null ? result.toString() : null;
    }

    /**
     * Settings &gt; Contacts &mdash; allocate the next {@code TRCO_KEY}
     * by delegating to the legacy {@code wb_get_next_val('trco_infm',
     * 'trco_key', &hellip;)} branch (V85 / OQ-4 resolved 2026-05-12).
     * Mirrors {@code SeqHelper.getNextVal(util, idoCon, TrcoInfm.class,
     * "trco_key")} at
     * {@code WECMS DEV/WECMS/src/wabooks/dao/TrcoInfmDAO.java:44}.
     *
     * <p>The DEV branch body is {@code SELECT nextval('sq_trco_key')}
     * &mdash; a global sequence (NOT per-tenant). The
     * {@code biz_key} arg is therefore ignored by the function; it's
     * carried here for symmetry with the other funnel methods and as
     * a hook for any future per-tenant overrides.
     *
     * <p>Retire plan: drop this method and the V85 branch once the
     * legacy WeCMS / WABOOKS systems no longer call
     * {@code wb_get_next_val} for {@code trco_infm} &mdash; see
     * "Surviving legacy functions" in
     * {@code wecms-api-main/CLAUDE.md}.
     */
    public String nextTrcoKey(String bizKey) {
        return nextVal("trco_infm", "trco_key", bizKey, null, null, null, null, null);
    }

    /**
     * Settings &gt; Approval Line &mdash; allocate the next per-tenant
     * {@code APPR_LINE_NO} by delegating to the legacy
     * {@code wb_get_next_val('appr_line_infm', 'appr_line_no', biz_key)}
     * branch (V91 / OQ-7 resolved 2026-05-13). Mirrors
     * {@code SeqHelper.getNextVal(util, idoCon, ApprLineInfm.class,
     * "appr_line_no")} at the legacy DAO layer.
     *
     * <p>The DEV branch body is {@code SELECT
     * COALESCE(MAX(appr_line_no::numeric), 0) + 1 FROM appr_line_infm
     * WHERE biz_key = $3} &mdash; per-tenant MAX+1. Concurrency:
     * service wraps the call + INSERT in one {@code @Transactional};
     * PK conflict surfaces as
     * {@code DataIntegrityViolationException}.
     *
     * <p>Returns a numeric text token (e.g. {@code "24"}); the caller
     * parses to {@link Long} before populating the entity.
     *
     * <p>Retire plan: drop this method and the V91 branch once the
     * legacy WeCMS / WABOOKS systems no longer call
     * {@code wb_get_next_val} for {@code appr_line_infm} &mdash; see
     * "Surviving legacy functions" in {@code wecms-api-main/CLAUDE.md}.
     */
    public String nextApprLineNo(String bizKey) {
        return nextVal("appr_line_infm", "appr_line_no", bizKey, null, null, null, null, null);
    }

    /**
     * Settings &gt; Chart of Accounts &mdash; allocate the next per-tenant
     * {@code CHART_ACCT_SEQ_NO} by delegating to the legacy
     * {@code wb_get_next_val('chart_acct_infm', 'chart_acct_seq_no', biz_key)}
     * branch (V95). Mirrors legacy
     * {@code SeqHelper.getNextVal(util, idoCon, ChartAcctInfm.class,
     * "chart_acct_seq_no")} at the JEX DAO layer.
     *
     * <p>The DEV branch body is {@code SELECT
     * COALESCE(MAX(chart_acct_seq_no::numeric), 0) + 1 FROM
     * chart_acct_infm WHERE biz_key = $3} &mdash; per-tenant MAX+1,
     * same pattern as {@code appr_line_no}. Concurrency: service wraps
     * the call + INSERT in one {@code @Transactional}; PK conflict
     * surfaces as {@code DataIntegrityViolationException} and the
     * service retries once before surfacing
     * {@code WBE_CHART_OF_ACCOUNTS_DUPLICATE_CODE}.
     *
     * <p>Returns a numeric text token (e.g. {@code "113"}); the caller
     * stores it verbatim in {@code CHART_ACCT_SEQ_NO} (VARCHAR(14)).
     *
     * <p>Retire plan: drop this method and the V95 branch once the
     * legacy WeCMS / WABOOKS systems no longer call
     * {@code wb_get_next_val} for {@code chart_acct_infm} &mdash; see
     * "Surviving legacy functions" in {@code wecms-api-main/CLAUDE.md}.
     */
    public String nextChartAcctSeqNo(String bizKey) {
        return nextVal("chart_acct_infm", "chart_acct_seq_no", bizKey, null, null, null, null, null);
    }

    /**
     * Banking &gt; Smart Organizer &mdash; allocate the next per-tenant
     * {@code RMRK_SEQ_NO} by delegating to the local
     * {@code wb_get_next_val('rmrk_org_infm', 'rmrk_seq_no', biz_key)}
     * branch (V161). The branch is a refactor convenience &mdash; DEV's
     * function currently returns {@code NULL} for this combination
     * because legacy {@code fnsh_0200_002_c001_act.jsp} relies on the
     * generic {@code SeqHelper.getNextVal(util, idoCon, RmrkOrgInfm.class,
     * "rmrk_seq_no")} path which falls back to per-tenant MAX+1 inside
     * the IDO layer. The refactor wires the same MAX+1 into
     * {@code wb_get_next_val} so the funnel signature stays uniform
     * across every Settings feature.
     *
     * <p>Concurrency: service wraps the call + INSERT in one
     * {@code @Transactional}; PK conflict surfaces as
     * {@code DataIntegrityViolationException} and the service retries
     * once before surfacing the error envelope.
     *
     * <p>Retire plan: drop this method and the V161 branch once the
     * legacy WeCMS / WABOOKS systems no longer call
     * {@code wb_get_next_val} for {@code rmrk_org_infm} &mdash; see
     * "Surviving legacy functions" in
     * {@code wecms-api-main/CLAUDE.md}.
     */
    public String nextRmrkSeqNo(String bizKey) {
        return nextVal("rmrk_org_infm", "rmrk_seq_no", bizKey, null, null, null, null, null);
    }

    /**
     * Settings &gt; Contacts &gt; Attachments &mdash; allocate the next
     * per-contact {@code FILE_ID} by delegating to the legacy
     * {@code wb_get_next_val('trco_info_file', 'file_id', trco_key)}
     * branch (V99, scope-expanded 2026-05-13). Mirrors
     * {@code SeqHelper.getNextVal(util, idoCon, TrcoInfoFile.class,
     * "file_id", trcoKey)} at the legacy DAO layer
     * ({@code TrcoInfoFileDAO.create:34}).
     *
     * <p>The DEV branch body is {@code SELECT
     * COALESCE(MAX(file_id::numeric), 0) + 1 FROM trco_info_file
     * WHERE trco_key = $3} &mdash; per-contact MAX+1 (NOT per-tenant;
     * the {@code TRCO_INFO_FILE} table has no {@code BIZ_KEY} column,
     * tenancy is inherited from the parent {@code TRCO_INFM} row).
     * Concurrency: service wraps the call + INSERT in one
     * {@code @Transactional}; PK conflict surfaces as
     * {@code DataIntegrityViolationException} and the service retries
     * once before surfacing the error envelope.
     *
     * <p>Returns a numeric text token (e.g. {@code "1"} for the first
     * attachment, {@code "2"} for the second).
     *
     * <p>Retire plan: drop this method and the V99 branch once the
     * legacy WeCMS / WABOOKS systems no longer call
     * {@code wb_get_next_val} for {@code trco_info_file} &mdash; see
     * "Surviving legacy functions" in {@code wecms-api-main/CLAUDE.md}.
     */
    public String nextTrcoInfoFileFileId(String trcoKey) {
        return nextVal("trco_info_file", "file_id", trcoKey, null, null, null, null, null);
    }

    /**
     * Settings &gt; Contacts &mdash; resolve the display bank name
     * for a NAPAS or CITAD code in the given language by delegating
     * to the legacy {@code wb_get_napas_bank_nm_or_citad_bank_nm(
     * code, lang_cd)} function (V86 / OQ-7 resolved 2026-05-12).
     * Mirrors the inline call in
     * {@code IDO.TRCO_INFM_R016.xml:63}.
     *
     * <p>Pass either the {@code NAPAS_BANK_CD} or the
     * {@code CITAD_BANK_CD} for {@code code} &mdash; the legacy
     * function dispatches internally against {@code tb_firm_bank_code}.
     * Pass the lowercase language code ({@code "en"}, {@code "vi"}).
     * The DEV function returns {@code NULL} for {@code "ko"} /
     * {@code "km"} (no branch).
     *
     * <p>Null-safe: returns {@code null} when {@code code} is null or
     * blank, short-circuiting before the DB round-trip.
     *
     * <p>Retire plan: drop this method and the V86 function once
     * the NAPAS + CITAD code space is fully reconciled inside
     * {@code BANK_INFM}; replace the call site with
     * {@code BankCdResolver} at that point.
     */
    public String resolveNapasOrCitadBankName(String code, String langCd) {
        if (code == null || code.isBlank()) {
            return null;
        }
        Query q = em.createNativeQuery(
                "SELECT wb_get_napas_bank_nm_or_citad_bank_nm(" +
                        "CAST(?1 AS varchar), CAST(?2 AS varchar))");
        q.setParameter(1, code);
        q.setParameter(2, langCd);
        Object result = q.getSingleResult();
        return result != null ? result.toString() : null;
    }
}
