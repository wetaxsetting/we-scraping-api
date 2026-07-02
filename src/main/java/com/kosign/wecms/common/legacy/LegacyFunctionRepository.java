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
     * {@code wecms-api/CLAUDE.md}.
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
     * "Surviving legacy functions" in {@code wecms-api/CLAUDE.md}.
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
     * "Surviving legacy functions" in {@code wecms-api/CLAUDE.md}.
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
     * {@code wecms-api/CLAUDE.md}.
     */
    public String nextRmrkSeqNo(String bizKey) {
        return nextVal("rmrk_org_infm", "rmrk_seq_no", bizKey, null, null, null, null, null);
    }

    /**
     * Payment &gt; Card Transactions Sync &mdash; allocate the next per-tenant
     * {@code CARD_SEQ_NO} for {@code CARD_TRNS_HSTR} via the local
     * {@code wb_get_next_val('card_trns_hstr', 'card_seq_no', biz_key)}
     * branch (V190). Mirrors {@code acct_trns_hstr.acct_seq_no} exactly:
     * per-tenant MAX+1, retry-once discipline on PK collisions.
     *
     * <p>Used by {@code CardTransactionSyncService.applyResults} when
     * inserting scraped {@code CARD_USING} transactions into the
     * ledger. Concurrency: service wraps the call + INSERT in one
     * {@code @Transactional}; PK conflict surfaces as
     * {@code DataIntegrityViolationException} and the orchestrator
     * retries the single row before surfacing the error envelope.
     *
     * <p>Returns a numeric text token (e.g. {@code "1234"}); the caller
     * stores it verbatim in {@code CARD_TRNS_HSTR.CARD_SEQ_NO}
     * (VARCHAR(20)).
     *
     * <p>Retire plan: drop this method and the V190 branch once the
     * legacy WeCMS / WABOOKS systems no longer call
     * {@code wb_get_next_val} for {@code card_trns_hstr} &mdash; see
     * "Surviving legacy functions" in
     * {@code wecms-api-main/CLAUDE.md}.
     */
    public String nextCardSeqNo(String bizKey) {
        return nextVal("card_trns_hstr", "card_seq_no", bizKey, null, null, null, null, null);
    }

    /**
     * Memo (polymorphic) &mdash; allocate the next {@code MEMO_INFM.SEQ_NO}
     * by delegating to the legacy {@code wb_get_next_val('memo_infm',
     * 'seq_no', biz_key, web_svc_id)} branch. Mirrors legacy
     * {@code MemoInfmDAO.create} &rarr; {@code SeqHelper.getNextVal(util,
     * idoCon, MemoInfm.class, "seq_no", bizKey, webSvcId)} at
     * {@code WECMS DEV/WECMS/src/wabooks/dao/MemoInfmDAO.java:62}.
     *
     * <p>The live JEX branch body is {@code SELECT
     * COALESCE(MAX(seq_no::numeric), 0) + 1 FROM memo_infm WHERE
     * biz_key = $3 AND web_svc_id = $4} &mdash; per-(tenant, screen)
     * {@code MAX+1}. {@code MEMO_INFM} is a single polymorphic table
     * shared by every memo-enabled screen, discriminated by
     * {@code WEB_SVC_ID}, so the ceiling is scoped by both keys.
     * Byte-for-byte identical to the value the former Java-side
     * {@code findMaxSeqNoByBizKeyAndWebSvcId + 1} produced &mdash; this
     * retires funnel exception #2 (the earlier "MEMO_INFM not in PROD
     * active list" premise was wrong; legacy has the branch).
     *
     * <p>Concurrency: lock-free {@code MAX+1}, same as legacy. The
     * caller wraps the call + INSERT in one {@code @Transactional} and
     * retries once on {@code DataIntegrityViolationException} (PK
     * {@code (biz_key, web_svc_id, seq_no)} collision) &mdash; equivalent
     * to legacy, which relies on the same PK to reject a colliding
     * insert.
     *
     * <p>Returns a numeric text token (e.g. {@code "6"}); the caller
     * parses to {@link java.math.BigDecimal}.
     *
     * <p>The {@code memo_infm} branch already exists in the refactor
     * {@code wb_get_next_val} function (added directly to the live
     * function; no dedicated Flyway migration).
     *
     * <p>Retire plan: drop this method and the {@code memo_infm} branch
     * once the legacy WeCMS / WABOOKS systems no longer call
     * {@code wb_get_next_val} for {@code memo_infm} &mdash; see
     * "Surviving legacy functions" in {@code wecms-api/CLAUDE.md}.
     */
    public String nextMemoInfmSeqNo(String bizKey, String webSvcId) {
        return nextVal("memo_infm", "seq_no", bizKey, webSvcId, null, null, null, null);
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
     * "Surviving legacy functions" in {@code wecms-api/CLAUDE.md}.
     */
    public String nextTrcoInfoFileFileId(String trcoKey) {
        return nextVal("trco_info_file", "file_id", trcoKey, null, null, null, null, null);
    }

    // ------------------------------------------------------------------
    // V140 centralisation — next-value helpers for the tables whose Spring
    // code previously used Java-side MAX+1 / per-tenant ceilings. Each mirrors
    // the legacy SeqHelper.getNextVal(util, idoCon, <Entity>.class, "<col>")
    // path and routes through the V140 wb_get_next_val branch so the refactor
    // never mints a key the live JEX DB would mint differently. Retirement:
    // same window as the rest of this funnel (see wecms-api/CLAUDE.md
    // "Surviving legacy functions").
    // ------------------------------------------------------------------

    /** Common &gt; Audit Log: AUDIT_LOGS_INFM.AUDIT_LOGS_SEQ_NO — per (biz_key, wsvc_id, lang_cd) MAX+1 (V140 #17). */
    public String nextAuditLogsSeqNo(String bizKey, String wsvcId, String langCd) {
        return nextVal("audit_logs_infm", "audit_logs_seq_no", bizKey, wsvcId, langCd, null, null, null);
    }

    /**
     * Settings &gt; Purpose Settings: BUSI_USAG_SET_INFM.USE_USAG_CD (V140 #18 / #19).
     * Pass the {@code usagGrpCd}; an empty / null group routes to the plain
     * 10-digit {@code 0000000001..} sequence variant. Returns a zero-padded code.
     */
    public String nextUseUsagCd(String bizKey, String usagGrpCd) {
        return nextVal("busi_usag_set_infm", "use_usag_cd", bizKey, usagGrpCd == null ? "" : usagGrpCd,
                null, null, null, null);
    }

    /** Banking &gt; Sync History: EXT_TRAN_CALL_HIS.SEQ_NO — per (svc_id, ref_date) MAX+1 (V140 #21). */
    public String nextExtTranCallHisSeqNo(String svcId, String refDate) {
        return nextVal("ext_tran_call_his", "seq_no", svcId, refDate, null, null, null, null);
    }

    /** Banking &gt; Own-Account Transfer: NAPAS_REMIT_TRNS_HSTR.SEQ_NO — per-tenant MAX+1 (V140 #22). */
    public String nextNapasRemitSeqNo(String bizKey) {
        return nextVal("napas_remit_trns_hstr", "seq_no", bizKey, null, null, null, null, null);
    }

    /** Banking &gt; Bank Credentials: BANK_CERT_INFO.SEQ_NO — per-tenant MAX+1 (V140 #23). */
    public String nextBankCertSeqNo(String bizKey) {
        return nextVal("bank_cert_info", "seq_no", bizKey, null, null, null, null, null);
    }

    /** Payment &gt; Banking Match: BUSI_MATCH_LOG.MATCH_LOG_SEQ — per-tenant MAX+1 (V140 #24). */
    public String nextBusiMatchLogSeq(String bizKey) {
        return nextVal("busi_match_log", "match_log_seq", bizKey, null, null, null, null, null);
    }

    /** Settings &gt; VAS Account: BUSI_VAS_ACCT_INFM.VAS_ACCT_SEQ_NO — per-tenant MAX+1 (V140 #25). */
    public String nextVasAcctSeqNo(String bizKey) {
        return nextVal("busi_vas_acct_infm", "vas_acct_seq_no", bizKey, null, null, null, null, null);
    }

    /** Settings &gt; Login Credentials (GDT): GDT_CERT_INFO.SEQ_NO — per-tenant MAX+1 (V140 #26). */
    public String nextGdtCertSeqNo(String bizKey) {
        return nextVal("gdt_cert_info", "seq_no", bizKey, null, null, null, null, null);
    }

    /** Auth &gt; Change Password: USER_PW_CHG_HIS.REG_SEQ_NO — per-user MAX+1 (V140 #27). */
    public String nextUserPwChgRegSeqNo(String userId) {
        return nextVal("user_pw_chg_his", "reg_seq_no", userId, null, null, null, null, null);
    }

    /** Settings &gt; ERP Transaction History: ERP_TRNS_HSTR.SEQ_NO — per-tenant MAX+1 (V140 #28). */
    public String nextErpTrnsSeqNo(String bizKey) {
        return nextVal("erp_trns_hstr", "seq_no", bizKey, null, null, null, null, null);
    }

    /** Payment &gt; Card Transactions: BUSA_DOC_RCV_PAY_HIS.RCV_PAY_HIS_SEQ_NO — per (biz_key, doc_id) MAX+1 (V140 #29). */
    public String nextBusaDocRcvPayHisSeqNo(String bizKey, String docId) {
        return nextVal("busa_doc_rcv_pay_his", "rcv_pay_his_seq_no", bizKey, docId, null, null, null, null);
    }

    /** Banking &gt; Transaction History / Smart Organizer: ACCT_TRNS_RCV_PAY_INFM.RCV_PAY_SEQ_NO — per (biz_key, acct_seq_no) MAX+1 (V140 #14). */
    public String nextAcctTrnsRcvPaySeqNo(String bizKey, String acctSeqNo) {
        return nextVal("acct_trns_rcv_pay_infm", "rcv_pay_seq_no", bizKey, acctSeqNo, null, null, null, null);
    }

    /** Banking &gt; Transaction Sync: ACCT_TRNS_HSTR.ACCT_SEQ_NO — per-tenant MAX+1 (V140 #13). */
    public String nextAcctTrnsHstrAcctSeqNo(String bizKey) {
        return nextVal("acct_trns_hstr", "acct_seq_no", bizKey, null, null, null, null, null);
    }

    /**
     * Settings &gt; Bank Account (registration): {@code ACCT_MSTR.PRN_SEQ} —
     * per-tenant {@code MAX(prn_seq)+1} print/display order. Funnels the V140
     * branch (#47) that was copied VERBATIM from the legacy JEX
     * {@code wb_get_next_val} body; it mirrors
     * {@code SeqHelper.getNextVal(util, idoCon, AcctMstr.class, "prn_seq", bizKey)}
     * invoked by the legacy {@code AcctMstrDAO.create()} (the
     * {@code fnsh_0101_001_c001} scrape-registration path).
     *
     * <p>The V140 branch was previously DORMANT (Option B, 2026-06-10) — wired
     * here so newly-registered accounts receive a {@code PRN_SEQ} instead of
     * {@code NULL}, restoring legacy parity and a deterministic Account-Inquiry
     * / Bank-Account default sort ({@code PRN_SEQ ASC}). No schema change: the
     * branch already exists in the refactor function.
     *
     * <p>Concurrency / batch: this is a lock-free {@code MAX+1} read, so callers
     * registering several accounts in one transaction seed ONCE and increment in
     * Java across the batch (the not-yet-flushed rows are invisible to the
     * function) — the same seed-once pattern used by the file/detail helpers.
     *
     * <p>Returns a numeric text token (e.g. {@code "1"}); the caller parses to
     * {@link Integer}. Retire with the rest of the funnel.
     */
    public String nextAcctMstrPrnSeq(String bizKey) {
        return nextVal("acct_mstr", "prn_seq", bizKey, null, null, null, null, null);
    }

    /** Settings &gt; Contacts / e-Invoice auto-register: TRCO_ACCT_INFO.TRCO_ACCT_KEY — per (biz_key, trco_key) MAX+1 (V140 #31). */
    public String nextTrcoAcctKey(String bizKey, String trcoKey) {
        return nextVal("trco_acct_info", "trco_acct_key", bizKey, trcoKey, null, null, null, null);
    }

    /**
     * Settings &gt; Contacts &mdash; allocate the next {@code TRCO_ADDR_KEY}
     * for an address row by delegating to the legacy
     * {@code wb_get_next_val('trco_addr_infm', 'trco_addr_key', biz_key, trco_key)}
     * branch. Mirrors legacy {@code TrcoAddrInfmDAO.create:73}
     * ({@code SeqHelper.getNextVal(util, idoCon, TrcoAddrInfm.class, "trco_addr_key",
     * bizKey, trcoKey)}).
     *
     * <p>The live JEX branch body is {@code SELECT
     * COALESCE(MAX(trco_addr_key::numeric), 0) + 1 FROM trco_addr_infm
     * WHERE biz_key = $3 AND trco_key = $4} &mdash; per-(tenant, contact)
     * {@code MAX+1}, the same shape as {@link #nextTrcoAcctKey}. Closes the
     * OD-1 client-key gap (the refactor previously persisted the frontend
     * placeholder verbatim, risking a PK collision with existing UAT/PROD
     * rows).
     *
     * <p>Concurrency / batch: lock-free {@code MAX+1}, so callers creating
     * several addresses in one transaction {@code saveAndFlush} between rows
     * so the next read sees the prior insert (same seed pattern as accounts).
     *
     * <p>Returns a numeric text token (e.g. {@code "1"}, {@code "2"}); the
     * caller stores it verbatim in {@code TRCO_ADDR_INFM.TRCO_ADDR_KEY}.
     */
    public String nextTrcoAddrKey(String bizKey, String trcoKey) {
        return nextVal("trco_addr_infm", "trco_addr_key", bizKey, trcoKey, null, null, null, null);
    }

    /**
     * Settings &gt; Contacts &mdash; allocate the next {@code TRCO_OVRS_KEY}
     * for an overseas-beneficiary row by delegating to the legacy
     * {@code wb_get_next_val('trco_ovrs_infm', 'trco_ovrs_key', biz_key)}
     * branch. Mirrors legacy {@code TrcoOvrsInfmDAO.create}
     * ({@code SeqHelper.getNextVal(util, idoCon, TrcoOvrsInfm.class, "trco_ovrs_key")}).
     *
     * <p>The live JEX branch body is {@code SELECT
     * COALESCE(MAX(trco_ovrs_key::numeric), 0) + 1 FROM trco_ovrs_infm
     * WHERE biz_key = $3} &mdash; <strong>per-tenant</strong> {@code MAX+1}
     * (NOT per-contact; the legacy scope is {@code biz_key} only, so
     * {@code trco_ovrs_key} is unique across the whole tenant). Closes the
     * OD-1 client-key gap.
     *
     * <p>Concurrency / batch: lock-free {@code MAX+1}, so callers creating
     * several overseas rows in one transaction {@code saveAndFlush} between
     * rows so the next read sees the prior insert.
     *
     * <p>Returns a numeric text token; the caller stores it verbatim in
     * {@code TRCO_OVRS_INFM.TRCO_OVRS_KEY}.
     */
    public String nextTrcoOvrsKey(String bizKey) {
        return nextVal("trco_ovrs_infm", "trco_ovrs_key", bizKey, null, null, null, null, null);
    }

    /** Payment &gt; Overseas: OVRS_PYMN_SUMR_DETL.OVRS_PYMN_DETL_NO — per-tenant MAX+1 over (mstr UNION detl) (V140 #30). */
    public String nextOvrsPymnDetlNo(String bizKey) {
        return nextVal("ovrs_pymn_sumr_detl", "ovrs_pymn_detl_no", bizKey, null, null, null, null, null);
    }

    /** Banking &gt; Exchange Rate batch: BANK_EXCH_RATE_INFM.EXCH_SEQ_NO — per (biz_key, bank_cd, ref_date, ccy_cd) MAX+1 (V140 #32). */
    public String nextBankExchRateExchSeqNo(String bizKey, String bankCd, String refDate, String ccyCd) {
        return nextVal("bank_exch_rate_infm", "exch_seq_no", bizKey, bankCd, refDate, ccyCd, null, null);
    }

    /**
     * Payment &gt; shared approval sequence: EXPT_RESOL_MSTR.APPR_SEQ_NO (V140 #1).
     * JEX backs this with the GLOBAL {@code sq_appr_seq_no} sequence — NOT a
     * per-tenant ceiling — so it is shared across every payment type
     * (expt_resol / scdul_pymn_sumr / ovrs_pymn_sumr). {@code bizKey} is carried
     * for funnel symmetry but ignored by the branch. Returns a numeric text token.
     */
    public String nextApprSeqNo(String bizKey) {
        return nextVal("expt_resol_mstr", "appr_seq_no", bizKey, null, null, null, null, null);
    }

    /** Payment &gt; Dispatch: TB_PAY_TARGET.TRAN_SEQ — global {@code firm_seq_no} sequence (V140 #2). */
    public String nextTbPayTargetTranSeq() {
        return nextVal("tb_pay_target", "tran_seq", null, null, null, null, null, null);
    }

    /**
     * Payment &gt; Dispatch: {@code TB_FIRM_TRAN.TRAN_SEQ} for the standalone
     * depositor-inquiry (201) leg (C1). Funnels the pre-existing
     * {@code wb_get_next_val('tb_firm_tran','tran_seq')} branch (V140 #3), which
     * draws the SAME global {@code firm_seq_no} sequence as
     * {@link #nextTbPayTargetTranSeq()} — byte-for-byte identical to legacy
     * {@code comm.Sequence.getSeq()} ({@code FIRM_SEQ_NO_R001 → nextval('firm_seq_no')},
     * {@code Sequence.java:21-34}). Unlike the 202 path (which reuses the originating
     * pay-target {@code TRAN_SEQ} for dedup), the inquiry mints a fresh sequence per
     * call (legacy WB_00000100 allocates a new {@code inq_seq} every invocation), so no
     * idempotency dedup is intended for inquiries.
     *
     * <p>Returns a numeric text token; the caller stores it verbatim as the
     * {@code TB_FIRM_TRAN} PK component (10-char varchar).
     *
     * <p>Retire plan: same window as the other {@code firm_seq_no} funnel methods —
     * drop once the legacy WeCMS / WABOOKS systems no longer call
     * {@code wb_get_next_val} for the firm-transaction sequence.
     */
    public String nextTbFirmTranSeq() {
        return nextVal("tb_firm_tran", "tran_seq", null, null, null, null, null, null);
    }

    /** Payment &gt; Dispatch: TB_PAY_FILE_MST.FILE_SEQ_NO — global {@code firm_file_seq_no} sequence (V140 #4). */
    public String nextTbPayFileMstFileSeqNo() {
        return nextVal("tb_pay_file_mst", "file_seq_no", null, null, null, null, null, null);
    }

    /** Add-on &gt; e-Invoice: TRCO_ACTV_HSTR.ACTV_SEQ — global {@code sq_actv_seq} sequence (V140 #6). */
    public String nextTrcoActvSeq() {
        return nextVal("trco_actv_hstr", "actv_seq", null, null, null, null, null, null);
    }

    /**
     * Payment &gt; Scheduled Payment: SCDUL_PYMN_SUMR.SCDUL_PYMN_NO — per-tenant
     * plain-int MAX+1 (V140 #36). Replaces the former date-prefixed
     * {@code InternalTransferKeyGenerator} counter (OD-2 reversal, 2026-06-09) so the
     * refactor mints the same {@code scdul_pymn_no} the live JEX DB would for the
     * tenant. Shared by every scheduled-payment module (Vendor / Expense / Payroll /
     * Internal Transfer) via {@code InternalTransferKeyGenerator.nextScdulPymnNo}.
     */
    public String nextScdulPymnNo(String bizKey) {
        return nextVal("scdul_pymn_sumr", "scdul_pymn_no", bizKey, null, null, null, null, null);
    }

    /**
     * Payment &gt; Scheduled Payment (detail): SCDUL_PYMN_SUMR_DETL.SCDUL_PYMN_DETL_NO —
     * per-tenant plain-int MAX+1 (V140 #38). Returns the next value for the tenant; the
     * scheduled-payment create flows call this ONCE per document and increment in Java
     * across the batch (seed-once pattern, same as {@code OverseasPaymentExcelService}),
     * so detail numbers within a document are contiguous without a per-line DB round-trip.
     */
    public String nextScdulPymnDetlNo(String bizKey) {
        return nextVal("scdul_pymn_sumr_detl", "scdul_pymn_detl_no", bizKey, null, null, null, null, null);
    }

    /**
     * Payment &gt; Approval / Resolution: EXPT_RESOL_MSTR.DOC_NO — env-set-driven
     * {@code {prefix}_{date}_{seq}} (V140 #39: BUSI/SYS_ENV_SET_INFM 00070..00073, UTC date,
     * per-tenant daily MAX+1 of the trailing numeric part). Shared by every payment module's
     * Request-Approval / draft path (via {@code DocNoGenerator} and
     * {@code ApprovalBoxFanoutService.nextDocNo}). DOC_NO has no unique constraint (PK is
     * (BIZ_KEY, APPR_SEQ_NO)), so the MAX+1 race is accepted legacy parity — no retry needed.
     */
    public String nextDocNo(String bizKey) {
        return nextVal("expt_resol_mstr", "doc_no", bizKey, null, null, null, null, null);
    }

    /**
     * Payment &gt; Approval / Resolution (attachment): EXPT_RESOL_FILE.FILE_ID — per
     * (biz_key, appr_seq_no) MAX+1 (V140 #42). Callers seed ONCE per request and increment
     * in Java across the attachment batch (seed-once pattern) — no per-file DB round-trip.
     * FILE_ID is part of the PK (BIZ_KEY, APPR_SEQ_NO, FILE_ID); since {@code apprSeqNo} is
     * freshly allocated per Request-Approval submission, the seed is always 1 for a new
     * approval (MAX over a brand-new appr_seq_no = 0), so output stays {@code 1, 2, 3, …}.
     */
    public String nextExptResolFileId(String bizKey, String apprSeqNo) {
        return nextVal("expt_resol_file", "file_id", bizKey, apprSeqNo, null, null, null, null);
    }

    /**
     * Payment &gt; Dispatch (file payload): TB_PAY_FILE_DATA.SEQ — per (biz_key, file_seq_no)
     * MAX+1 (V140 #20). Dispatch executors seed ONCE per dispatch and increment in Java across
     * the file rows (seed-once pattern) — no per-row DB round-trip. SEQ is part of the PK
     * (TRAN_DT, BIZ_KEY, FILE_SEQ_NO, SEQ); since {@code fileSeqNo} is freshly allocated per
     * dispatch (global firm_file_seq_no), the seed is always 1 (MAX over a brand-new
     * file_seq_no = 0), so output stays {@code 1, 2, 3, …}.
     */
    public String nextTbPayFileDataSeq(String bizKey, String fileSeqNo) {
        return nextVal("tb_pay_file_data", "seq", bizKey, fileSeqNo, null, null, null, null);
    }

    /**
     * Payment &gt; Overseas (summary): OVRS_PYMN_SUMR.OVRS_PYMN_NO — per-tenant plain-int MAX+1
     * over (OVRS_PYMN_MSTR UNION OVRS_PYMN_SUMR) (V140 #63). Replaces the former date-prefixed
     * {@code OverseasPaymentKeyGenerator} (<YYYYMMDD><6-digit seq>) — the MR-2C funnel migration
     * (2026-06-10), the overseas equivalent of the MR-1 SCDUL_PYMN_NO reversal. OVRS_PYMN_NO is
     * the PK of OVRS_PYMN_SUMR and the FK carried onto every OVRS_PYMN_SUMR_DETL row; because the
     * funnel is a lock-free MAX+1, concurrent creates can race the PK — callers wrap the create in
     * a boundary-level one-shot retry (see {@code OverseasPaymentExcelService.submit}).
     */
    public String nextOvrsPymnNo(String bizKey) {
        return nextVal("ovrs_pymn_sumr", "ovrs_pymn_no", bizKey, null, null, null, null, null);
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

    /**
     * Banking &mdash; resolve the SHORT display bank name for a {@code BANK_CD}
     * by delegating to the legacy {@code wb_get_bank_nm(bank_cd, 's')} function,
     * with the legacy {@code wb_get_bank_nm(bank_cd)} formal name as the
     * COALESCE fallback. Returns {@code null} only when neither resolves
     * (unknown / blank code) so the caller can fall back to the raw code.
     *
     * <p>Mirrors the Account Inquiry / Transaction History grid binding:
     * legacy {@code IDO.ACCT_MSTR_R004.xml:77} ({@code BANK_NM}) and
     * {@code IDO.ACCT_TRNS_HSTR_R016.xml:31} render {@code wb_get_bank_nm(_,'s')}
     * in the visible cell (formal {@code wb_get_bank_nm(_)} is the tooltip).
     * The {@code 's'} branch reads the date-effective short name from
     * {@code BANK_INFM_DETL} ({@code BANK_DETL_INFO_CD='10'}); the formal
     * branch reads {@code BANK_INFM.RPRS_BANK_NM}. No new DB object — both
     * function overloads and {@code BANK_INFM_DETL} already exist in the
     * refactor DB.
     *
     * <p>Retire plan: drop this method (and switch the resolver to a pure
     * {@code BANK_INFM} read) once the short-name registry is reconciled into
     * {@code BANK_INFM} &mdash; same window as the other surviving legacy
     * functions (see {@code wecms-api/CLAUDE.md}).
     */
    public String resolveBankNmShort(String bankCd) {
        if (bankCd == null || bankCd.isBlank()) {
            return null;
        }
        Query q = em.createNativeQuery(
                "SELECT COALESCE(" +
                        "NULLIF(wb_get_bank_nm(CAST(:cd AS varchar), 's'), ''), " +
                        "NULLIF(wb_get_bank_nm(CAST(:cd AS varchar)), ''))");
        q.setParameter("cd", bankCd);
        Object result = q.getSingleResult();
        return result != null ? result.toString() : null;
    }

    /**
     * Batch variant of {@link #resolveBankNmShort(String)} for cache warming:
     * one query resolves the short name (formal fallback) for every
     * {@code BANK_CD} in {@code BANK_INFM}, returning {@code bankCd → name}.
     * Avoids an N+1 of per-code function calls at startup.
     */
    @SuppressWarnings("unchecked")
    public java.util.Map<String, String> resolveBankNmShortAll() {
        Query q = em.createNativeQuery(
                "SELECT bank_cd, COALESCE(" +
                        "NULLIF(wb_get_bank_nm(bank_cd, 's'), ''), " +
                        "NULLIF(wb_get_bank_nm(bank_cd), '')) " +
                        "FROM bank_infm");
        java.util.Map<String, String> out = new java.util.HashMap<>();
        for (Object[] row : (java.util.List<Object[]>) q.getResultList()) {
            if (row[0] != null && row[1] != null) {
                out.put(row[0].toString(), row[1].toString());
            }
        }
        return out;
    }

    /**
     * Payment &gt; Payroll Payment Result (detail header) &mdash; resolve the
     * withdrawal-bank display name for an {@code OUT_BANK_CD} via the legacy
     * {@code wb_get_napas_bank_nm(bank_cd)} single-argument function. This is the
     * EXACT resolver the legacy detail-header IDO uses
     * ({@code IDO.SCDUL_PYMN_SUMR_R005.xml:50} —
     * {@code wb_get_napas_bank_nm(SPS.OUT_BANK_CD) AS OUT_BANK_NM}).
     *
     * <p>Distinct from {@link #resolveNapasOrCitadBankName} (the two-arg
     * NAPAS-or-CITAD variant the per-line Deposit Bank cell uses,
     * {@code R011.xml:54}). The two functions diverge for some codes &mdash;
     * e.g. {@code 970424 → "Shinhan VN"} (this fn) vs {@code "ShinhanBank"}
     * (the or-CITAD fn), {@code 970457 → "Woori VN"} vs {@code "WooriBank"} &mdash;
     * so the Payroll Result detail header MUST use this overload to match legacy
     * verbatim (the former {@code TransferLineDetailMapper.resolveBankNm} chain
     * resolved the header to the or-CITAD names, which mismatched legacy).
     *
     * <p>Returns {@code null} for an unknown / blank result so the caller falls
     * back to the raw {@code OUT_BANK_CD}.
     */
    public String resolveNapasBankNm(String bankCd) {
        if (bankCd == null || bankCd.isBlank()) {
            return null;
        }
        Query q = em.createNativeQuery(
                "SELECT wb_get_napas_bank_nm(CAST(?1 AS varchar))");
        q.setParameter(1, bankCd.trim());
        Object result = q.getSingleResult();
        return result != null && !result.toString().isBlank() ? result.toString() : null;
    }

    /**
     * Payment &gt; Payroll Payment Result (detail rows) &mdash; resolve the
     * Reporting Account display strings for a set of {@code CHART_ACCT_SEQ_NO}
     * values, mirroring the legacy detail-rows IDO
     * ({@code IDO.SCDUL_PYMN_SUMR_R011.xml:70-71}):
     * <ul>
     *   <li>{@code wb_get_chart_acct_nm(biz_key, seq_no, cd)} &rarr; the
     *       primary line, e.g. {@code "Code0011 - SALES TEST"}.</li>
     *   <li>{@code wb_get_chart_acct_lv_nm(biz_key, seq_no, cd)} &rarr; the
     *       grey level line, e.g. {@code "REVENUE > SALES"}.</li>
     * </ul>
     *
     * <p>The join + function arguments reproduce the legacy query exactly:
     * {@code CHART_ACCT_INFM CHAT} keyed by {@code (BIZ_KEY, CHART_ACCT_SEQ_NO)},
     * passing {@code CHAT.CHART_ACCT_CD} (the chart row's own code) into both
     * functions. Batched (one query for every line's seq-no) to avoid an N+1.
     *
     * <p>Returns {@code seqNo &rarr; [chartAcctNm, chartAcctLevelNm]}; either
     * element may be {@code null} when the function returns blank. Seq-nos with
     * no {@code CHART_ACCT_INFM} row are simply absent from the map (caller
     * renders blank). Both functions already exist in the refactor DB with
     * legacy-identical signatures &mdash; no new DB object.
     *
     * <p>Retire plan: drop this method once the Chart-of-Accounts name/level
     * resolution is reconciled into a pure-Java walker &mdash; same window as
     * the other surviving legacy functions (see {@code wecms-api/CLAUDE.md}).
     */
    @SuppressWarnings("unchecked")
    public java.util.Map<String, String[]> resolveChartAcctDisplay(
            String bizKey, java.util.Collection<String> chartAcctSeqNos) {
        java.util.List<String> ids = chartAcctSeqNos == null ? java.util.List.of()
                : chartAcctSeqNos.stream().filter(s -> s != null && !s.isBlank()).distinct().toList();
        if (bizKey == null || bizKey.isBlank() || ids.isEmpty()) {
            return java.util.Map.of();
        }
        Query q = em.createNativeQuery(
                "SELECT chat.chart_acct_seq_no, " +
                        "wb_get_chart_acct_nm(chat.biz_key, chat.chart_acct_seq_no, chat.chart_acct_cd), " +
                        "wb_get_chart_acct_lv_nm(chat.biz_key, chat.chart_acct_seq_no, chat.chart_acct_cd) " +
                        "FROM chart_acct_infm chat " +
                        "WHERE chat.biz_key = CAST(:bizKey AS varchar) " +
                        "  AND chat.chart_acct_seq_no IN (:seqs)");
        q.setParameter("bizKey", bizKey);
        q.setParameter("seqs", ids);
        java.util.Map<String, String[]> out = new java.util.HashMap<>();
        for (Object[] row : (java.util.List<Object[]>) q.getResultList()) {
            if (row[0] != null) {
                String nm = row[1] != null ? row[1].toString() : null;
                String lv = row[2] != null ? row[2].toString() : null;
                out.put(row[0].toString(), new String[] { nm, lv });
            }
        }
        return out;
    }

    /**
     * Banking &mdash; resolve the display bank name for ANY bank code
     * (internal / NAPAS / CITAD) via the legacy {@code wb_get_bank_name_any_code}
     * function. This is the resolver legacy
     * {@code IDO.SCDUL_PYMN_SUMR_R002.xml:97} uses for the Scheduled Payment
     * withdrawal-bank name ({@code OUT_BANK_NM}); the Vendor Payment detail page
     * routes the master withdrawal bank through here so its "Withdrawal Bank"
     * matches legacy verbatim (C-2 fix 2026-06-16). Returns {@code null} for an
     * unknown / blank code so the caller can fall back to the raw code.
     *
     * <p>Distinct from {@link #resolveBankNmShort} (Account Inquiry short name)
     * and {@link #resolveNapasOrCitadBankName} (per-line deposit-bank name) —
     * those resolvers are unchanged; only the withdrawal-bank master field on
     * the Vendor Scheduled-Payment detail uses this overload.
     */
    public String resolveBankNameAnyCode(String bankCd) {
        if (bankCd == null || bankCd.isBlank()) {
            return null;
        }
        Query q = em.createNativeQuery(
                "SELECT wb_get_bank_name_any_code(CAST(?1 AS varchar))");
        q.setParameter(1, bankCd);
        Object result = q.getSingleResult();
        return result != null ? result.toString() : null;
    }

    /**
     * Payment &gt; Scheduled Payment &mdash; derive the per-batch approval
     * status by delegating to the legacy {@code wb_get_appr_stts(...)} function
     * (the single source of truth for approval-status display; SCDUL_PYMN_SUMR
     * has no APPR_STTS column in the legacy JEX schema).
     *
     * <p>Argument binding mirrors {@code IDO.SCDUL_PYMN_SUMR_R002.xml:68}:
     * {@code wb_get_appr_stts(ERM.BIZ_KEY, ERM.REG_ID, ERM.REG_ID,
     * SPS.APPR_SEQ_NO, ERM.APPR_TYPE_CD)} &mdash; {@code _apprvr_id = _reg_id =
     * ERM.REG_ID}, so the function's admin/registrant branch always fires and it
     * returns the document-level status: {@code '00'→P}, {@code '01'→C},
     * {@code '02'→R}, {@code '03'→I} (the per-approver {@code ELSE} branch is
     * unreachable for this binding). The {@code R014/R018/R021} variants differ
     * only in arg&nbsp;3 ({@code SPS.REG_ID}), which only changes that dormant
     * branch &mdash; not the list/detail outcome.
     *
     * <p>Returns {@code null} when the batch has no approval master (draft).
     */
    public String getApprStts(String bizKey, String apprSeqNo) {
        if (apprSeqNo == null || apprSeqNo.isBlank()) {
            return null;
        }
        Query q = em.createNativeQuery(
                "SELECT wb_get_appr_stts(erm.biz_key, erm.reg_id, erm.reg_id, " +
                        "erm.appr_seq_no, erm.appr_type_cd) " +
                        "FROM expt_resol_mstr erm " +
                        "WHERE erm.biz_key = CAST(?1 AS varchar) " +
                        "  AND erm.appr_seq_no = CAST(?2 AS varchar)");
        q.setParameter(1, bizKey);
        q.setParameter(2, apprSeqNo);
        var rows = q.getResultList();
        if (rows.isEmpty() || rows.get(0) == null) {
            return null;
        }
        return rows.get(0).toString();
    }

    /**
     * Batch variant of {@link #getApprStts(String, String)} for list pages:
     * one server-side query computes {@code wb_get_appr_stts(...)} for every
     * supplied {@code APPR_SEQ_NO} (same R002 binding), returning
     * {@code apprSeqNo → status}. Mirrors the legacy IDO's inline per-row
     * function call without an N+1 Java loop. {@code APPR_SEQ_NO}s with no
     * approval master (or blank/null inputs) are simply absent from the map
     * (caller treats absent as {@code null} = draft).
     */
    @SuppressWarnings("unchecked")
    public java.util.Map<String, String> apprSttsByApprSeqNos(
            String bizKey, java.util.Collection<String> apprSeqNos) {
        java.util.List<String> ids = apprSeqNos == null ? java.util.List.of()
                : apprSeqNos.stream().filter(s -> s != null && !s.isBlank()).distinct().toList();
        if (ids.isEmpty()) {
            return java.util.Map.of();
        }
        Query q = em.createNativeQuery(
                "SELECT erm.appr_seq_no, " +
                        "wb_get_appr_stts(erm.biz_key, erm.reg_id, erm.reg_id, " +
                        "erm.appr_seq_no, erm.appr_type_cd) " +
                        "FROM expt_resol_mstr erm " +
                        "WHERE erm.biz_key = CAST(:bizKey AS varchar) " +
                        "  AND erm.appr_seq_no IN (:seqs)");
        q.setParameter("bizKey", bizKey);
        q.setParameter("seqs", ids);
        java.util.Map<String, String> out = new java.util.HashMap<>();
        for (Object[] row : (java.util.List<Object[]>) q.getResultList()) {
            if (row[0] != null && row[1] != null) {
                out.put(row[0].toString(), row[1].toString());
            }
        }
        return out;
    }
}
