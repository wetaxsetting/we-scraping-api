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
}
