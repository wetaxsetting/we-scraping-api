package com.kosign.wecms.domain.payment.cardtransactions.repository;

import com.kosign.wecms.domain.payment.cardtransactions.entity.CardTrnsHstr;
import com.kosign.wecms.domain.payment.cardtransactions.entity.CardTrnsHstrId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link CardTrnsHstr}. Every method is
 * tenant-scoped via an explicit {@code bizKey} parameter — callers read
 * the tenant from {@link com.kosign.wecms.common.tenant.TenantContext#currentBizKey()},
 * never from the request body.
 *
 * <p>Driver query: {@link #findLedgerPage} — mirrors legacy
 * {@code IDO.CARD_MSTR_R002.xml} but rebuilt as JPQL against the entity
 * properties. The legacy IDO is a native SELECT with sub-selects for
 * linked docs + approver chain + reporting-account label — the refactor
 * defers those joins (Phase B; OQ-1 in the feature doc) and returns the
 * raw entity rows. The service composes {@code linked_docs[]} /
 * {@code approvers[]} as empty arrays for Sprint 0, matching the
 * Internal Transfer Phase-A precedent.
 *
 * <p>Bank-code / card-number list filters are expressed as {@code IS
 * EMPTY} short-circuits in the JPQL so a null / empty collection
 * skips the predicate cleanly (no JPQL parsing of array literals).
 *
 * <p>Search keyword is pre-lowercased by the service so the JPQL stays
 * predicate-free of {@code LOWER(...)} on a possibly-null parameter —
 * mirrors the {@code AcctMstrRepository#findFiltered} precedent that
 * dodges PostgreSQL's {@code function lower(bytea) does not exist}
 * footgun when a {@code CONCAT(...)} arg has unknown type.
 */
public interface CardTrnsHstrRepository extends JpaRepository<CardTrnsHstr, CardTrnsHstrId> {

    /**
     * Paged + sorted + filtered ledger read. Mirrors legacy
     * {@code IDO.CARD_MSTR_R002.xml} + {@code card_0001_001_r001_act.jsp}
     * dynamic predicates.
     *
     * <p><b>Linked / Unlinked ({@code linkYn}).</b> Legacy keys "linked"
     * on the presence of a linked document in {@code BUSA_DOC_RCV_PAY_HIS}
     * — {@code REC_1 IS [NOT] NULL} where {@code REC_1} aggregates
     * {@code BUSA_DOC_RCV_PAY_HIS BDRPH WHERE BDRPH.REF_NO = CTH.CARD_SEQ_NO
     * AND BDRPH.RCV_PAY_WAY_CD = 'R'} ({@code card_0001_001_r001_act.jsp:140-148},
     * {@code IDO.CARD_MSTR_R002.xml:131-138}). {@code 'R'} is
     * {@code RcvPayWayCd.CARD} (the legacy enum maps {@code 'C'} to CASH);
     * the link write / linked-doc read paths use the same {@code 'R'}.
     * {@code Y} → linked-doc EXISTS, {@code N} → NOT EXISTS,
     * blank/null → no-op. (Was incorrectly keyed on {@code APPR_SEQ_NO};
     * fixed 2026-06-19, audit D1.)
     *
     * <p><b>Search ({@code srchWdPattern}).</b> Legacy ILIKEs
     * {@code CM.CARD_HLDR_NM | CM.CARD_NM | CM.CARD_NICK_NM | CTH.MERCH_NM}
     * ({@code card_0001_001_r001_act.jsp:107-110}). The cardholder /
     * nickname / card-name terms live on {@code CARD_MSTR}, reached here
     * via a correlated {@code EXISTS} sub-select on the
     * {@code (BIZ_KEY, BANK_CD, CARD_NO)} natural key; merchant matches on
     * {@code CARD_TRNS_HSTR} directly. (Was merchant + CTH.card_nm only;
     * fixed 2026-06-19, audit D3.)
     *
     * <p><b>Bank-code / card-no multi-select ({@code filterBank} /
     * {@code filterCard}).</b> Legacy applies {@code CTH.bank_cd IN (...)}
     * / {@code CTH.card_no IN (...)} ({@code card_0001_001_r001_act.jsp:72-102}).
     * Expressed here as a boolean-flag guard so the {@code IN} list is only
     * evaluated when a filter is present — the service passes a non-empty
     * sentinel list when the flag is {@code false} so the bound {@code IN}
     * clause stays valid SQL (JPA's {@code IS EMPTY} only accepts
     * collection-valued entity paths, never bound parameters). (Was
     * accepted-but-ignored; fixed 2026-06-19, audit D2.)
     *
     * @param bizKey          tenant scope; required
     * @param fromDt          inclusive {@code yyyyMMdd} lower bound on {@code TRSC_DT}
     * @param toDt            inclusive {@code yyyyMMdd} upper bound on {@code TRSC_DT}
     * @param filterBank      apply the {@code bankCds} IN filter when {@code true}
     * @param bankCds         bank-code IN list (non-empty sentinel when {@code filterBank} is false)
     * @param filterCard      apply the {@code cardNos} IN filter when {@code true}
     * @param cardNos         card-no IN list (non-empty sentinel when {@code filterCard} is false)
     * @param srchWdPattern   pre-lowercased {@code %foo%} pattern; null → no-op
     * @param linkYn          {@code Y} / {@code N} / blank/null → no-op
     */
    @Query("""
            SELECT c FROM CardTrnsHstr c
             WHERE c.bizKey = :bizKey
               AND c.trscDt BETWEEN :fromDt AND :toDt
               AND (:filterBank = FALSE OR c.bankCd IN :bankCds)
               AND (:filterCard = FALSE OR c.cardNo IN :cardNos)
               AND (:srchWdPattern IS NULL
                    OR LOWER(COALESCE(c.merchNm, '')) LIKE :srchWdPattern
                    OR EXISTS (SELECT 1 FROM CardMstr m
                                WHERE m.bizKey = c.bizKey
                                  AND m.bankCd = c.bankCd
                                  AND m.cardNo = c.cardNo
                                  AND (LOWER(COALESCE(m.cardHldrNm, '')) LIKE :srchWdPattern
                                       OR LOWER(COALESCE(m.cardNm,     '')) LIKE :srchWdPattern
                                       OR LOWER(COALESCE(m.cardNickNm, '')) LIKE :srchWdPattern)))
               AND (:linkYn IS NULL OR :linkYn = ''
                    OR (:linkYn = 'Y' AND EXISTS (SELECT 1 FROM BusaDocRcvPayHis h
                                                   WHERE h.bizKey = c.bizKey
                                                     AND h.refNo = c.cardSeqNo
                                                     AND h.rcvPayWayCd = 'R'))
                    OR (:linkYn = 'N' AND NOT EXISTS (SELECT 1 FROM BusaDocRcvPayHis h
                                                       WHERE h.bizKey = c.bizKey
                                                         AND h.refNo = c.cardSeqNo
                                                         AND h.rcvPayWayCd = 'R')))
            """)
    Page<CardTrnsHstr> findLedgerPage(
            @Param("bizKey")        String       bizKey,
            @Param("fromDt")        String       fromDt,
            @Param("toDt")          String       toDt,
            @Param("filterBank")    boolean      filterBank,
            @Param("bankCds")       List<String> bankCds,
            @Param("filterCard")    boolean      filterCard,
            @Param("cardNos")       List<String> cardNos,
            @Param("srchWdPattern") String       srchWdPattern,
            @Param("linkYn")        String       linkYn,
            Pageable pageable);

    /**
     * Aggregate totals over the same filter set as
     * {@link #findLedgerPage}. Returns one row carrying:
     * <ol>
     *   <li>total row count (long)</li>
     *   <li>total amount (BigDecimal — {@code COALESCE}'d sum)</li>
     *   <li>approval-requested count (long; APPR_SEQ_NO IS NOT NULL)</li>
     *   <li>approval-requested amount sum</li>
     *   <li>approval-not-requested count</li>
     *   <li>approval-not-requested amount sum</li>
     * </ol>
     *
     * <p>JPQL doesn't ship a built-in {@code FILTER (WHERE ...)} clause
     * that all dialects honour, so the conditional sums use {@code CASE
     * WHEN ... THEN ... ELSE 0 END}. The Hibernate Postgres dialect
     * emits {@code CASE} natively.
     */
    @Query("""
            SELECT
                COUNT(c)                                                                     AS totalCount,
                COALESCE(SUM(c.trscAmt), 0)                                                  AS totlAmt,
                COALESCE(SUM(CASE WHEN c.apprSeqNo IS NOT NULL AND c.apprSeqNo <> '' THEN 1 ELSE 0 END), 0) AS totlApprReqCnt,
                COALESCE(SUM(CASE WHEN c.apprSeqNo IS NOT NULL AND c.apprSeqNo <> '' THEN c.trscAmt ELSE 0 END), 0) AS totlApprReqAmt,
                COALESCE(SUM(CASE WHEN c.apprSeqNo IS NULL OR c.apprSeqNo = '' THEN 1 ELSE 0 END), 0)     AS totlApprNotReqCnt,
                COALESCE(SUM(CASE WHEN c.apprSeqNo IS NULL OR c.apprSeqNo = '' THEN c.trscAmt ELSE 0 END), 0) AS totlApprNotReqAmt
              FROM CardTrnsHstr c
             WHERE c.bizKey = :bizKey
               AND c.trscDt BETWEEN :fromDt AND :toDt
               AND (:filterBank = FALSE OR c.bankCd IN :bankCds)
               AND (:filterCard = FALSE OR c.cardNo IN :cardNos)
               AND (:srchWdPattern IS NULL
                    OR LOWER(COALESCE(c.merchNm, '')) LIKE :srchWdPattern
                    OR EXISTS (SELECT 1 FROM CardMstr m
                                WHERE m.bizKey = c.bizKey
                                  AND m.bankCd = c.bankCd
                                  AND m.cardNo = c.cardNo
                                  AND (LOWER(COALESCE(m.cardHldrNm, '')) LIKE :srchWdPattern
                                       OR LOWER(COALESCE(m.cardNm,     '')) LIKE :srchWdPattern
                                       OR LOWER(COALESCE(m.cardNickNm, '')) LIKE :srchWdPattern)))
               AND (:linkYn IS NULL OR :linkYn = ''
                    OR (:linkYn = 'Y' AND EXISTS (SELECT 1 FROM BusaDocRcvPayHis h
                                                   WHERE h.bizKey = c.bizKey
                                                     AND h.refNo = c.cardSeqNo
                                                     AND h.rcvPayWayCd = 'R'))
                    OR (:linkYn = 'N' AND NOT EXISTS (SELECT 1 FROM BusaDocRcvPayHis h
                                                       WHERE h.bizKey = c.bizKey
                                                         AND h.refNo = c.cardSeqNo
                                                         AND h.rcvPayWayCd = 'R')))
            """)
    LedgerTotalsProjection findLedgerTotals(
            @Param("bizKey")        String       bizKey,
            @Param("fromDt")        String       fromDt,
            @Param("toDt")          String       toDt,
            @Param("filterBank")    boolean      filterBank,
            @Param("bankCds")       List<String> bankCds,
            @Param("filterCard")    boolean      filterCard,
            @Param("cardNos")       List<String> cardNos,
            @Param("srchWdPattern") String       srchWdPattern,
            @Param("linkYn")        String       linkYn);

    /**
     * Per-link-status tab counts over the same date / bank / card / search
     * filter set as {@link #findLedgerPage}, but <b>without</b> the
     * {@code linkYn} predicate — so the All / Linked / Unlinked tabs can all
     * show a count regardless of which tab is currently active. Returns one
     * row:
     * <ol>
     *   <li>{@code allCount} — total rows in the filter</li>
     *   <li>{@code linkedCount} — rows with a linked doc
     *       ({@code BUSA_DOC_RCV_PAY_HIS} EXISTS, {@code RCV_PAY_WAY_CD='R'})</li>
     *   <li>{@code unlinkedCount} — rows without a linked doc</li>
     * </ol>
     * The linked predicate is identical to the {@code linkYn='Y'} branch in
     * {@link #findLedgerPage}, so a tab's count equals the row count that tab
     * would list. {@code allCount == linkedCount + unlinkedCount}.
     */
    @Query("""
            SELECT
                COUNT(c) AS allCount,
                COALESCE(SUM(CASE WHEN EXISTS (SELECT 1 FROM BusaDocRcvPayHis h
                                                WHERE h.bizKey = c.bizKey
                                                  AND h.refNo = c.cardSeqNo
                                                  AND h.rcvPayWayCd = 'R')
                                  THEN 1 ELSE 0 END), 0) AS linkedCount,
                COALESCE(SUM(CASE WHEN NOT EXISTS (SELECT 1 FROM BusaDocRcvPayHis h
                                                    WHERE h.bizKey = c.bizKey
                                                      AND h.refNo = c.cardSeqNo
                                                      AND h.rcvPayWayCd = 'R')
                                  THEN 1 ELSE 0 END), 0) AS unlinkedCount
              FROM CardTrnsHstr c
             WHERE c.bizKey = :bizKey
               AND c.trscDt BETWEEN :fromDt AND :toDt
               AND (:filterBank = FALSE OR c.bankCd IN :bankCds)
               AND (:filterCard = FALSE OR c.cardNo IN :cardNos)
               AND (:srchWdPattern IS NULL
                    OR LOWER(COALESCE(c.merchNm, '')) LIKE :srchWdPattern
                    OR EXISTS (SELECT 1 FROM CardMstr m
                                WHERE m.bizKey = c.bizKey
                                  AND m.bankCd = c.bankCd
                                  AND m.cardNo = c.cardNo
                                  AND (LOWER(COALESCE(m.cardHldrNm, '')) LIKE :srchWdPattern
                                       OR LOWER(COALESCE(m.cardNm,     '')) LIKE :srchWdPattern
                                       OR LOWER(COALESCE(m.cardNickNm, '')) LIKE :srchWdPattern)))
            """)
    LinkCountsProjection findLinkCounts(
            @Param("bizKey")        String       bizKey,
            @Param("fromDt")        String       fromDt,
            @Param("toDt")          String       toDt,
            @Param("filterBank")    boolean      filterBank,
            @Param("bankCds")       List<String> bankCds,
            @Param("filterCard")    boolean      filterCard,
            @Param("cardNos")       List<String> cardNos,
            @Param("srchWdPattern") String       srchWdPattern);

    /**
     * Interface projection carrying the three tab counts from
     * {@link #findLinkCounts}. Boxed types — the {@code COALESCE}'d
     * {@code SUM(CASE ...)} still returns SQL {@code NULL} over an empty
     * rowset in some dialects; boxing lets Spring's projection adapter map
     * it without an unbox-of-null failure.
     */
    interface LinkCountsProjection {
        Long getAllCount();
        Long getLinkedCount();
        Long getUnlinkedCount();
    }

    /**
     * Narrow load-by-PK for the patch / put endpoints. Returns
     * {@link Optional#empty()} when the row is missing OR the row's
     * tenant differs from the supplied {@code bizKey} — the service
     * collapses both into a single {@code WBE_CARD_TRANSACTIONS_NOT_FOUND}
     * envelope (same precedent as
     * {@code WBE_APPROVAL_LINE_NOT_FOUND} / {@code WBE_CHART_OF_ACCOUNTS_NOT_FOUND}).
     */
    Optional<CardTrnsHstr> findByBizKeyAndCardSeqNo(String bizKey, String cardSeqNo);

    /**
     * In-use guard for {@code DELETE
     * /api/v1/payment/card-transactions/cards/{bankCd}/{cardNo}}.
     * Mirrors legacy {@code card_0001_003_d001_act.jsp:84-97} which
     * runs {@code IDO.CARD_TRNS_HSTR_R001_CNT} before the DAO delete
     * and throws {@code WBE1153} when the count is positive.
     *
     * <p>Spring Data derives the query as
     * {@code SELECT COUNT(*) FROM CARD_TRNS_HSTR
     *  WHERE biz_key = ? AND bank_cd = ? AND card_no = ?}.
     */
    long countByBizKeyAndBankCdAndCardNo(String bizKey, String bankCd, String cardNo);

    /**
     * Dedup probe for the Update Cards scrape — matches the legacy
     * composite natural key <b>exactly</b>
     * ({@code fnsh_0001_001_c001_act.jsp:1335-1357}):
     * {@code biz_key, card_no, trsc_dt, trsc_tm, ccy_cd, trsc_amt,
     * merch_nm} plus {@code coalesce(trim(auth_code),'') =
     * coalesce(trim(?),'')}. Legacy does NOT key on {@code bank_cd}
     * (card_no is bank-unique); we drop it too for byte-for-byte parity.
     *
     * <p>Replaces the earlier auth-code-first / wider-fallback pair,
     * which could skip genuinely-distinct transactions sharing an
     * auth code, or admit duplicates legacy would reject — a
     * migration-safety hazard on the first re-scrape after a JEX →
     * refactor data move (audit D5, fixed 2026-06-19).
     *
     * <p>Probe values must be the same normalised/truncated values the
     * insert path stores (merch_nm truncated to 50, auth_code to 15),
     * so a re-scrape of an already-persisted row resolves to a match.
     */
    @Query("""
            SELECT CASE WHEN COUNT(c) > 0 THEN TRUE ELSE FALSE END
              FROM CardTrnsHstr c
             WHERE c.bizKey  = :bizKey
               AND c.cardNo  = :cardNo
               AND c.trscDt  = :trscDt
               AND c.trscTm  = :trscTm
               AND c.ccyCd   = :ccyCd
               AND c.trscAmt = :trscAmt
               AND c.merchNm = :merchNm
               AND COALESCE(TRIM(c.authCode), '') = COALESCE(TRIM(:authCode), '')
            """)
    boolean existsLegacyScrapeDuplicate(
            @Param("bizKey")  String bizKey,
            @Param("cardNo")  String cardNo,
            @Param("trscDt")  String trscDt,
            @Param("trscTm")  String trscTm,
            @Param("ccyCd")   String ccyCd,
            @Param("trscAmt") java.math.BigDecimal trscAmt,
            @Param("merchNm") String merchNm,
            @Param("authCode") String authCode);

    /**
     * Cash Flow report aggregator read &mdash; all classified card
     * transactions for the tenant in a calendar year. Mirrors the
     * legacy {@code get_cashflow_report()} function body's per-source
     * predicate:
     * {@code WHERE biz_key = ? AND chart_acct_cd <> '' AND
     * EXTRACT(YEAR FROM TO_DATE(trsc_dt,'YYYYMMDD')) = ?}.
     *
     * <p>The year filter is applied as a string-prefix match
     * ({@code SUBSTRING(trsc_dt, 1, 4) = :year}) so the index on
     * {@code (biz_key, chart_acct_cd, trsc_dt)} is usable. The
     * approval-status gate runs in Java, not in this query.
     *
     * <p>Previously lived on a parallel {@code banking.repository.CardTrnsHstrRepository};
     * consolidated here on 2026-05-20 to remove the bean-name +
     * Hibernate-entity-name collision between the two repository /
     * entity pairs.
     */
    @Query("""
            SELECT c FROM CardTrnsHstr c
             WHERE c.bizKey = :bizKey
               AND c.chartAcctCd IS NOT NULL
               AND c.chartAcctCd <> ''
               AND SUBSTRING(c.trscDt, 1, 4) = :year
            """)
    List<CardTrnsHstr> findClassifiedByBizKeyAndYear(
            @Param("bizKey") String bizKey,
            @Param("year")   String year);

    /**
     * Narrow {@code CONT} writer. Returns the number of rows affected so
     * the service can throw {@code WBE_CARD_TRANSACTIONS_NOT_FOUND}
     * when the PK doesn't resolve in the tenant.
     */
    @Modifying
    @Query("""
            UPDATE CardTrnsHstr c
               SET c.cont   = :cont,
                   c.modId  = :modId,
                   c.modDtm = :modDtm
             WHERE c.bizKey     = :bizKey
               AND c.cardSeqNo  = :cardSeqNo
            """)
    int patchDescription(@Param("bizKey")    String bizKey,
                         @Param("cardSeqNo") String cardSeqNo,
                         @Param("cont")      String cont,
                         @Param("modId")     String modId,
                         @Param("modDtm")    String modDtm);

    /** Narrow {@code CARD_TRNS_USAGE_CODE} writer. */
    @Modifying
    @Query("""
            UPDATE CardTrnsHstr c
               SET c.cardTrnsUsageCode = :code,
                   c.modId             = :modId,
                   c.modDtm            = :modDtm
             WHERE c.bizKey     = :bizKey
               AND c.cardSeqNo  = :cardSeqNo
            """)
    int patchPurpose(@Param("bizKey")    String bizKey,
                     @Param("cardSeqNo") String cardSeqNo,
                     @Param("code")      String code,
                     @Param("modId")     String modId,
                     @Param("modDtm")    String modDtm);

    /** Always-paired reporting-account writer
     *  ({@code CHART_ACCT_SEQ_NO} + {@code CHART_ACCT_CD}). */
    @Modifying
    @Query("""
            UPDATE CardTrnsHstr c
               SET c.chartAcctSeqNo = :seqNo,
                   c.chartAcctCd    = :cd,
                   c.modId          = :modId,
                   c.modDtm         = :modDtm
             WHERE c.bizKey     = :bizKey
               AND c.cardSeqNo  = :cardSeqNo
            """)
    int patchReportingAccount(@Param("bizKey")    String bizKey,
                              @Param("cardSeqNo") String cardSeqNo,
                              @Param("seqNo")     String seqNo,
                              @Param("cd")        String cd,
                              @Param("modId")     String modId,
                              @Param("modDtm")    String modDtm);

    /**
     * Interface projection carrying the six aggregate columns from
     * {@link #findLedgerTotals}. Java-records-as-projections aren't
     * stable on Spring Data 3.x for JPQL aggregates, so interface
     * projection is the supported shape.
     */
    interface LedgerTotalsProjection {
        // Boxed types throughout — JPQL `SUM(CASE WHEN ... ELSE 0 END)`
        // returns SQL NULL over an empty rowset and Spring's interface-
        // projection adapter cannot unbox null into a primitive. The
        // COALESCE wrappers above already guard the SQL, but boxing
        // here is a belt-and-suspenders against future query edits
        // that forget the COALESCE.
        Long       getTotalCount();
        BigDecimal getTotlAmt();
        Long       getTotlApprReqCnt();
        BigDecimal getTotlApprReqAmt();
        Long       getTotlApprNotReqCnt();
        BigDecimal getTotlApprNotReqAmt();
    }

    /**
     * Every card transaction stamped with the given {@code APPR_SEQ_NO}
     * for the tenant. Ordered by transaction date / time desc so the
     * Approval Box detail popup shows the most recent purchase first.
     *
     * <p>Consumed by the Approval Box's per-card detail loader to
     * populate the Transaction Details grid when the user opens the
     * read-only Approval Details popup for an {@code APPR_DV='33'} row.
     */
    @Query("""
            SELECT c FROM CardTrnsHstr c
             WHERE c.bizKey    = :bizKey
               AND c.apprSeqNo = :apprSeqNo
             ORDER BY c.trscDt DESC, c.trscTm DESC, c.cardSeqNo DESC
            """)
    java.util.List<CardTrnsHstr> findByBizKeyAndApprSeqNo(
            @Param("bizKey")    String bizKey,
            @Param("apprSeqNo") String apprSeqNo);

    /**
     * Batch (APPR_SEQ_NO &rarr; CCY_CD) lookup for the Approval Box list
     * currency enrichment. One representative currency per approval —
     * legacy {@code EXPT_RESOL_MSTR_R008} reads the first card row's
     * {@code CCY_CD} ({@code LIMIT 1}); we use {@code MIN(CCY_CD)} so the
     * result is deterministic. A mixed-currency batch (rare) resolves to
     * the alphabetically-first code; the header amount column then follows
     * the same currency (see {@code ApprovalBoxService.toRow}). The
     * {@code "VND"} fallback in {@code toRow} still covers an approval with
     * no card rows.
     */
    @Query("""
            SELECT c.apprSeqNo, MIN(c.ccyCd)
              FROM CardTrnsHstr c
             WHERE c.bizKey = :bizKey
               AND c.apprSeqNo IN :apprSeqNos
             GROUP BY c.apprSeqNo
            """)
    java.util.List<Object[]> findCcyCdByBizKeyAndApprSeqNos(
            @Param("bizKey")     String bizKey,
            @Param("apprSeqNos") java.util.List<String> apprSeqNos);

    /**
     * Batch (APPR_SEQ_NO &rarr; SUM(TRSC_AMT)) lookup for the Approval Box
     * list / footer amount of Corporate Card approvals (APPR_DV='33').
     *
     * <p>Legacy parity: {@code IDO.EXPT_RESOL_MSTR_R003.xml:62-65} and
     * {@code IDO.EXPT_RESOL_MSTR_R003_CNT.xml:49-52} render a card row's
     * amount as {@code (SELECT sum(TRSC_AMT) FROM CARD_TRNS_HSTR WHERE
     * biz_key = ? AND appr_seq_no = ?)} — the {@code EXPT_RESOL_MSTR}
     * amount columns are not reliably populated for card approvals (the
     * newest DEV card rows carry {@code TOT_PAY_KHR_AMT = 0}), so the
     * card transaction sum is the source of truth. One sum per approval;
     * batched by {@code apprSeqNo IN} so the list / totals avoid an N+1.
     */
    @Query("""
            SELECT c.apprSeqNo, COALESCE(SUM(c.trscAmt), 0)
              FROM CardTrnsHstr c
             WHERE c.bizKey = :bizKey
               AND c.apprSeqNo IN :apprSeqNos
             GROUP BY c.apprSeqNo
            """)
    java.util.List<Object[]> sumTrscAmtByBizKeyAndApprSeqNos(
            @Param("bizKey")     String bizKey,
            @Param("apprSeqNos") java.util.List<String> apprSeqNos);

    /**
     * HAENGSUNG Oracle card-approval sync (Step 7 — legacy {@code ERP_0100_003}
     * via {@code CARD_MSTR_R005}). Reads a tenant's UNSENT card transactions
     * ({@code coalesce(cth.erp_snd_yn,'N')='N'}) joined to {@code CARD_MSTR} for
     * {@code CARD_HLDR_NM} + {@code CARD_HIS_LST_INQ_DT}, with {@code BANK_CD}
     * resolved to the CITAD code and {@code AUTH_NO} computed per the legacy CASE
     * ({@code BANK_CD='11704020'} → {@code TRSC_DT||TRSC_TM}, else {@code AUTH_CODE}).
     * Columns aliased to the {@link com.kosign.wecms.domain.payment.haengsung.cardaccount.CardApprovalSyncRow}
     * getter names. ({@code CARD_MSTR} has no {@code ERP_SND_YN} column, so the
     * legacy unqualified flag filter resolves to {@code CTH.ERP_SND_YN} — qualified
     * here for clarity.)
     */
    @Query(nativeQuery = true, value = """
            SELECT
                cth.biz_key                                                                   AS "bizKey",
                (SELECT l.citad_bank_cd FROM CITAD_BANK_CD_LOOKUP l
                  WHERE l.bank_cd = cth.bank_cd AND l.use_yn = 'Y')                           AS "bankCd",
                cth.card_no                                                                   AS "cardNo",
                cth.trsc_dt                                                                   AS "apprDt",
                cth.trsc_tm                                                                   AS "apprTm",
                (CASE WHEN cth.bank_cd = '11704020' THEN cth.trsc_dt || cth.trsc_tm
                      ELSE cth.auth_code END)                                                 AS "authNo",
                cth.merch_nm                                                                  AS "merchNm",
                mstr.card_hldr_nm                                                             AS "cardHldrNm",
                cth.ccy_cd                                                                    AS "ccyCd",
                cth.trsc_amt                                                                  AS "apprAmt",
                cth.city                                                                      AS "city",
                mstr.card_his_lst_inq_dt                                                      AS "lastInqDttm",
                cth.card_seq_no                                                               AS "cardSeqno",
                cth.prch_stts                                                                 AS "prchStts"
            FROM CARD_MSTR mstr
            JOIN CARD_TRNS_HSTR cth
              ON mstr.biz_key = cth.biz_key
             AND mstr.bank_cd = cth.bank_cd
             AND mstr.card_no = cth.card_no
            WHERE cth.biz_key = :bizKey
              AND COALESCE(cth.erp_snd_yn, 'N') = 'N'
            """)
    java.util.List<com.kosign.wecms.domain.payment.haengsung.cardaccount.CardApprovalSyncRow>
        findHaengsungCardApprovalForSync(@Param("bizKey") String bizKey);

    /**
     * HAENGSUNG Oracle card-approval sync (Step 7) — mark ONE card transaction
     * as sent ({@code ERP_SND_YN='Y'}, legacy {@code CARD_TRNS_HSTR_U003},
     * narrowed to a single row). Keyed on the PK {@code (BIZ_KEY, CARD_SEQ_NO)};
     * idempotent guard. Called ONLY after the Oracle insert for the row succeeds.
     * Returns rows updated.
     */
    @Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query(nativeQuery = true, value = """
            UPDATE CARD_TRNS_HSTR SET ERP_SND_YN = 'Y'
             WHERE BIZ_KEY = :bizKey
               AND CARD_SEQ_NO = :cardSeqNo
               AND COALESCE(ERP_SND_YN, 'N') = 'N'
            """)
    int markErpSent(@Param("bizKey") String bizKey, @Param("cardSeqNo") String cardSeqNo);
}
