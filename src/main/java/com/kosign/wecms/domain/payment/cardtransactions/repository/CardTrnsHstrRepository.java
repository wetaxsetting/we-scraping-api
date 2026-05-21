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
     * Paged + sorted + filtered ledger read.
     *
     * <p>{@code linkYn} {@code Y} / {@code N} / blank maps to
     * {@code apprSeqNo IS NOT NULL} / {@code IS NULL} / no-op
     * respectively. Until {@code BUSA_DOC_RCV_PAY_HIS} is ported the
     * presence of an {@code APPR_SEQ_NO} stamp on the row is the only
     * Sprint 0 indicator of "linked" — same approximation legacy uses
     * when the linked-doc sub-select is empty.
     *
     * <p><b>Sprint 0 scope:</b> the multi-select bank-code / card-no
     * filters are not yet exercised by the frontend (the popovers are
     * Phase B per the frontend design doc). The service signature
     * still accepts both lists for forward compatibility; the
     * repository ignores them today. When the popovers ship, the IN
     * clauses get added back via the canonical Spring Data pattern —
     * NOT via {@code :param IS NULL OR :param IS EMPTY}: JPA's
     * {@code IS EMPTY} operator only accepts collection-valued entity
     * paths (e.g. {@code parent.children IS EMPTY}), never bound
     * parameters. The clean idiom is to branch in the service: when
     * the filter list is non-empty, call a sibling method that
     * declares {@code AND c.bankCd IN :bankCds}; when empty, call the
     * filter-less method.
     *
     * @param bizKey          tenant scope; required
     * @param fromDt          inclusive {@code yyyyMMdd} lower bound on {@code TRSC_DT}
     * @param toDt            inclusive {@code yyyyMMdd} upper bound on {@code TRSC_DT}
     * @param srchWdPattern   pre-lowercased {@code %foo%} pattern; null → no-op
     * @param linkYn          {@code Y} / {@code N} / blank/null → no-op
     */
    @Query("""
            SELECT c FROM CardTrnsHstr c
             WHERE c.bizKey = :bizKey
               AND c.trscDt BETWEEN :fromDt AND :toDt
               AND (:srchWdPattern IS NULL
                    OR LOWER(COALESCE(c.merchNm, '')) LIKE :srchWdPattern
                    OR LOWER(COALESCE(c.cardNm,  '')) LIKE :srchWdPattern)
               AND (:linkYn IS NULL OR :linkYn = ''
                    OR (:linkYn = 'Y' AND c.apprSeqNo IS NOT NULL AND c.apprSeqNo <> '')
                    OR (:linkYn = 'N' AND (c.apprSeqNo IS NULL OR c.apprSeqNo = '')))
            """)
    Page<CardTrnsHstr> findLedgerPage(
            @Param("bizKey")        String       bizKey,
            @Param("fromDt")        String       fromDt,
            @Param("toDt")          String       toDt,
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
               AND (:srchWdPattern IS NULL
                    OR LOWER(COALESCE(c.merchNm, '')) LIKE :srchWdPattern
                    OR LOWER(COALESCE(c.cardNm,  '')) LIKE :srchWdPattern)
               AND (:linkYn IS NULL OR :linkYn = ''
                    OR (:linkYn = 'Y' AND c.apprSeqNo IS NOT NULL AND c.apprSeqNo <> '')
                    OR (:linkYn = 'N' AND (c.apprSeqNo IS NULL OR c.apprSeqNo = '')))
            """)
    LedgerTotalsProjection findLedgerTotals(
            @Param("bizKey")        String       bizKey,
            @Param("fromDt")        String       fromDt,
            @Param("toDt")          String       toDt,
            @Param("srchWdPattern") String       srchWdPattern,
            @Param("linkYn")        String       linkYn);

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
     * Dedup probe for the Update Cards sync. Returns {@code true} when
     * a row with the same {@code (BIZ_KEY, BANK_CD, CARD_NO, TRSC_DT,
     * AUTH_CODE)} already exists. Mirrors the legacy
     * {@code IDO.CARD_TRNS_HSTR_CHK} idempotency probe — Coocon returns
     * the same {@code AUTH_CODE} for every re-scrape of the same
     * transaction, so the auth code is the de-facto natural key once
     * the (bank, card, date) scope is fixed.
     *
     * <p>When {@code AUTH_CODE} is null / blank (some adapters omit it
     * for cash advances etc.) the caller falls back to a wider check
     * via {@link #existsByBizKeyAndBankCdAndCardNoAndTrscDtAndTrscTmAndTrscAmt}.
     */
    boolean existsByBizKeyAndBankCdAndCardNoAndTrscDtAndAuthCode(
            String bizKey, String bankCd, String cardNo, String trscDt, String authCode);

    /** Wider dedup fallback when the bank adapter omits {@code AUTH_CODE}.
     *  Same composite-natural-key shape as legacy
     *  {@code IDO.CARD_TRNS_HSTR_CHK} when {@code AUTH_CODE} is empty. */
    boolean existsByBizKeyAndBankCdAndCardNoAndTrscDtAndTrscTmAndTrscAmt(
            String bizKey, String bankCd, String cardNo, String trscDt,
            String trscTm, java.math.BigDecimal trscAmt);

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
}
