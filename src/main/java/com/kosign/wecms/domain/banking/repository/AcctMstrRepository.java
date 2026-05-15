package com.kosign.wecms.domain.banking.repository;

import com.kosign.wecms.domain.banking.entity.AcctMstr;
import com.kosign.wecms.domain.banking.entity.AcctMstrId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AcctMstrRepository extends JpaRepository<AcctMstr, AcctMstrId> {

    /** All active accounts for a tenant, sorted by bank then account number. */
    List<AcctMstr> findByBizKeyAndActvYnOrderByBankCdAscAcctNoAsc(String bizKey, String actvYn);

    /** Active accounts filtered to one currency (VND for the VND inquiry; USD/etc. for FCY). */
    List<AcctMstr> findByBizKeyAndCcyCdAndActvYnOrderByBankCdAscAcctNoAsc(String bizKey, String ccyCd, String actvYn);

    /** Active accounts excluding a single currency (FCY page = "everything except VND"). */
    List<AcctMstr> findByBizKeyAndCcyCdNotAndActvYnOrderByBankCdAscAcctNoAsc(String bizKey, String ccyCd, String actvYn);

    /**
     * Variant accepting a Spring Data {@link Sort} so the service can
     * apply a user-driven ORDER BY (built from the {@code AccountSortField}
     * whitelist). The {@code Sort} is constructed from validated entity
     * properties — no raw wire strings reach JPA.
     */
    List<AcctMstr> findByBizKeyAndActvYn(String bizKey, String actvYn, Sort sort);

    List<AcctMstr> findByBizKeyAndCcyCdAndActvYn(String bizKey, String ccyCd, String actvYn, Sort sort);

    List<AcctMstr> findByBizKeyAndCcyCdNotAndActvYn(String bizKey, String ccyCd, String actvYn, Sort sort);

    Optional<AcctMstr> findByBizKeyAndBankCdAndAcctNo(String bizKey, String bankCd, String acctNo);

    /**
     * All accounts for the tenant (active + inactive), ordered the way
     * Settings &gt; Bank Account renders them: print order first, then
     * bank then account no. Mirrors legacy {@code comm_0183_001_r001}
     * which does not filter by {@code ACTV_YN} so users can re-enable
     * disabled rows.
     *
     * <p>PostgreSQL's default ASC NULLS LAST means rows without a
     * {@code PRN_SEQ} fall through to the bank/account secondary order
     * — the same convention {@code AccountService.DEFAULT_SORT} uses
     * for Banking &gt; Account Inquiry.
     */
    List<AcctMstr> findByBizKeyOrderByPrnSeqAscBankCdAscAcctNoAsc(String bizKey);

    /**
     * All accounts for the tenant under one internal {@code BANK_CD},
     * ordered by account number. Mirrors the {@code IDODynamic} JEX
     * runs in {@code comm_1004_001_act.jsp:62-66} —
     * {@code biz_key = ? AND bank_cd = ?}, no {@code ACTV_YN}
     * filter, no currency filter — used by the firm-banking-settings
     * Withdrawal Account autocomplete.
     */
    List<AcctMstr> findByBizKeyAndBankCdOrderByAcctNoAsc(String bizKey, String bankCd);

    /**
     * Server-side filter + pagination for Account Inquiry list /
     * export. Optional predicates use the {@code (:param IS NULL OR …)}
     * pattern so a single JPQL query handles every filter combination
     * without a Specification graph.
     *
     * <ul>
     *   <li>{@code ccyEq} — concrete CCY filter (VND inquiry, USD/KRW/…
     *       narrow); {@code null} skips.</li>
     *   <li>{@code ccyNot} — exclude one CCY (FCY page = {@code
     *       ccy_cd <> 'VND'}); {@code null} skips.</li>
     *   <li>{@code acctTypeCdIn} — ACCT_TYPE_CD whitelist per legacy
     *       {@code FnshHelper.fnsh_0001_001_r001_dynamic0}; {@code null}
     *       skips. Excludes loans ({@code 01}) on every page.</li>
     *   <li>{@code bankCd} — exact-match BANK_CD; {@code null} skips.</li>
     *   <li>{@code keywordPattern} — pre-lowercased, pre-{@code %}-wrapped
     *       LIKE pattern matched against the <b>formatted</b> account
     *       nickname (output of {@code wb_format_acct_nick_nm}, the
     *       same string the user sees in the table cell). Mirrors JEX
     *       {@code FnshHelper#fnsh_0001_001_r001} at line 470:
     *       {@code AND (T.ACCT_NICK_NM ILIKE '%'||?||'%')} where
     *       {@code T.ACCT_NICK_NM} is the inner-subquery alias for the
     *       formatted value. The service escapes {@code %} / {@code _} /
     *       {@code !} in user input with a leading {@code !}, matching
     *       the {@code ESCAPE '!'} clause in the JPQL. {@code null}
     *       skips.</li>
     * </ul>
     *
     * <p>Always constrains on {@code BIZ_KEY} (tenant) and {@code
     * ACTV_YN = 'Y'}. Sort + pagination flow through {@link Pageable}
     * — pass {@link Pageable#unpaged(Sort)} for the export path so the
     * full filtered set is returned in one round trip.
     *
     * <p><b>Why not match raw columns:</b> the legacy seed leaves
     * {@code ACCT_NICK_NM} NULL for most accounts; the displayed string
     * (e.g. {@code "Shinhan VN 5280"}) is synthesized by
     * {@code wb_format_acct_nick_nm} at SELECT time. Searching the raw
     * column would miss every row that relies on the synthesized
     * fallback — visible mismatch with what the user sees on screen.
     * JEX matches the formatted output exactly for that reason.
     */
    @Query("""
            SELECT a FROM AcctMstr a
             WHERE a.bizKey = :bizKey
               AND a.actvYn = 'Y'
               AND (:ccyEq  IS NULL OR a.ccyCd = :ccyEq)
               AND (:ccyNot IS NULL OR a.ccyCd <> :ccyNot)
               AND (:acctTypeCdIn IS NULL OR a.acctTypeCd IN :acctTypeCdIn)
               AND (:bankCd IS NULL OR a.bankCd = :bankCd)
               AND (:keywordPattern IS NULL
                    OR LOWER(COALESCE(
                            function('wb_format_acct_nick_nm',
                                     a.acctNickNm, a.bankCd, a.acctNo, a.handRegYn),
                            ''
                        )) LIKE :keywordPattern ESCAPE '!')
            """)
    Page<AcctMstr> findFiltered(
            @Param("bizKey")          String bizKey,
            @Param("ccyEq")           String ccyEq,
            @Param("ccyNot")          String ccyNot,
            @Param("acctTypeCdIn")    List<String> acctTypeCdIn,
            @Param("bankCd")          String bankCd,
            @Param("keywordPattern")  String keywordPattern,
            Pageable pageable);

    /**
     * Per-currency aggregation for the summary card. Runs the SAME WHERE
     * chain as {@link #findFiltered} (minus pagination) so totals reflect
     * the full filtered result regardless of the requested page.
     *
     * <p>Mirrors the legacy {@code IDO.ACCT_MSTR_R001_CNT.xml} pattern at
     * a higher level: one row per distinct {@code CCY_CD}, ordered by
     * code so the frontend renders tiles deterministically.
     *
     * <p>The keyword predicate matches the same formatted nickname as
     * {@link #findFiltered} so the summary tile counts/totals stay in
     * sync with the table rows when a search is in effect.
     */
    @Query("""
            SELECT new com.kosign.wecms.domain.banking.repository.CcyAggregate(
                    a.ccyCd,
                    COUNT(a),
                    COALESCE(SUM(a.bal), 0),
                    COALESCE(SUM(a.realAmt), 0))
              FROM AcctMstr a
             WHERE a.bizKey = :bizKey
               AND a.actvYn = 'Y'
               AND (:ccyEq  IS NULL OR a.ccyCd = :ccyEq)
               AND (:ccyNot IS NULL OR a.ccyCd <> :ccyNot)
               AND (:acctTypeCdIn IS NULL OR a.acctTypeCd IN :acctTypeCdIn)
               AND (:bankCd IS NULL OR a.bankCd = :bankCd)
               AND (:keywordPattern IS NULL
                    OR LOWER(COALESCE(
                            function('wb_format_acct_nick_nm',
                                     a.acctNickNm, a.bankCd, a.acctNo, a.handRegYn),
                            ''
                        )) LIKE :keywordPattern ESCAPE '!')
             GROUP BY a.ccyCd
             ORDER BY a.ccyCd
            """)
    List<CcyAggregate> aggregateByCcy(
            @Param("bizKey")          String bizKey,
            @Param("ccyEq")           String ccyEq,
            @Param("ccyNot")          String ccyNot,
            @Param("acctTypeCdIn")    List<String> acctTypeCdIn,
            @Param("bankCd")          String bankCd,
            @Param("keywordPattern")  String keywordPattern);

    /**
     * Distinct {@code BANK_CD} list for the current tenant + currency
     * scope. Drives the frontend bank-filter dropdown. Intentionally
     * does NOT honour the {@code bankCd} or {@code keyword} filters —
     * the dropdown's option set must stay stable as the user narrows
     * by bank or types in the search box (selecting one bank should not
     * remove the others from the dropdown).
     *
     * <p>Active accounts only ({@code ACTV_YN = 'Y'}). Ordered by
     * {@code BANK_CD} so the frontend renders a deterministic list.
     */
    @Query("""
            SELECT DISTINCT a.bankCd FROM AcctMstr a
             WHERE a.bizKey = :bizKey
               AND a.actvYn = 'Y'
               AND (:ccyEq  IS NULL OR a.ccyCd = :ccyEq)
               AND (:ccyNot IS NULL OR a.ccyCd <> :ccyNot)
               AND (:acctTypeCdIn IS NULL OR a.acctTypeCd IN :acctTypeCdIn)
             ORDER BY a.bankCd
            """)
    List<String> findDistinctBankCds(
            @Param("bizKey")       String bizKey,
            @Param("ccyEq")        String ccyEq,
            @Param("ccyNot")       String ccyNot,
            @Param("acctTypeCdIn") List<String> acctTypeCdIn);

    /**
     * Withdrawal-account lookup for the Payment Internal Transfer
     * upload dialog (legacy `comm_1001_001`). Returns active
     * current/savings accounts the current tenant has enrolled in
     * firm-banking (EXISTS in {@code TB_FIRM_CONF} with {@code
     * ORG_STAT='1'}).
     *
     * <p>Mirrors the legacy IDO {@code ACCT_MSTR_R011} via
     * {@code comm_1001_001_act.jsp:23-65}. The five legacy filters
     * are:
     * <ol>
     *   <li>tenant scope ({@code biz_key})</li>
     *   <li>firm-banking-capable banks (legacy hardcoded
     *       {@code bank_cd IN ('11704088', '11704020')}; the
     *       refactor relaxes this — any bank with a matching
     *       {@code TB_FIRM_CONF} row qualifies, since the firm-banking
     *       enrolment table is itself the authoritative "capable"
     *       indicator)</li>
     *   <li>current/savings only ({@code acct_type_cd IN ('03'..'08')})</li>
     *   <li>{@code ACTV_YN = 'Y'}</li>
     *   <li>EXISTS in {@code TB_FIRM_CONF} with {@code ORG_STAT='1'}
     *       joined via the {@code NAPAS_BANK_INFO} dictionary</li>
     * </ol>
     *
     * <p><b>Deferred filter:</b> the legacy also requires EXISTS in
     * {@code ACCT_AUTH_INFO} with {@code AUTH_TYPE IN ('V','W')} so
     * only accounts the user has View/Withdraw authority on appear.
     * That table is not yet migrated in the refactor (workspace
     * `CLAUDE.md` PROD active list); the filter ports together with
     * the {@code ACCT_AUTH_INFO} entity in a follow-up. R&D safe:
     * with a single test tenant the per-user check is a no-op.
     *
     * <p>Optional {@code ccyCd} filter — legacy carries the parameter
     * but the SQL has it commented out. Honored here when the caller
     * supplies a non-null value so future per-upload-currency
     * narrowing is one-prop change away.
     *
     * <p><b>R&D fallback (2026-05-14):</b> the strict EXISTS clause is
     * gated by a "tenant has any firm-banking enrolment at all" check.
     * When {@code TB_FIRM_CONF} has at least one {@code ORG_STAT='1'}
     * row for the tenant, the legacy filter applies verbatim. When the
     * tenant has NO firm-banking enrolment rows yet (the case on the
     * shared dev DB at 172.168.5.172/wecmsdevdb where seeding requires
     * write access we don't have), the EXISTS clause is bypassed so the
     * dropdown still surfaces active current/savings accounts and the
     * Excel-upload demo can run end-to-end. Once firm-banking is
     * provisioned for the tenant, strict parity resumes automatically —
     * no code change needed. Production parity is preserved because any
     * real WeCMS tenant has firm-banking enrolled before reaching this
     * screen; the fallback only kicks in for un-provisioned R&D tenants.
     */
    @Query("""
            SELECT a FROM AcctMstr a
             WHERE a.bizKey = :bizKey
               AND a.actvYn = 'Y'
               AND a.acctTypeCd IN ('03','04','05','06','07','08')
               AND (:ccyCd IS NULL OR a.ccyCd = :ccyCd)
               AND (
                    EXISTS (
                         SELECT 1
                           FROM FirmBankingSetting tfc, NapasBankInfo nbi
                          WHERE tfc.bizKey      = a.bizKey
                            AND tfc.wdrlActNo   = a.acctNo
                            AND tfc.outBankCd   = nbi.napasBankCd
                            AND nbi.bankCd      = a.bankCd
                            AND tfc.orgStat     = '1'
                        )
                    OR NOT EXISTS (
                         SELECT 1
                           FROM FirmBankingSetting tfc2
                          WHERE tfc2.bizKey  = a.bizKey
                            AND tfc2.orgStat = '1'
                        )
                   )
             ORDER BY a.bankCd, a.acctNo
            """)
    List<AcctMstr> findWithdrawalAccounts(
            @Param("bizKey") String bizKey,
            @Param("ccyCd")  String ccyCd);
}
