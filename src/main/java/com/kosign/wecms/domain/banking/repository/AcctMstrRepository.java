package com.kosign.wecms.domain.banking.repository;

import com.kosign.wecms.domain.banking.entity.AcctMstr;
import com.kosign.wecms.domain.banking.entity.AcctMstrId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface AcctMstrRepository extends JpaRepository<AcctMstr, AcctMstrId> {

    /**
     * Clear the credential linkage owned by one {@code BANK_CERT_INFO} row.
     *
     * <p>Called when a bank credential is deleted (Settings &gt; Login
     * Credentials). The accounts themselves are KEPT — they live in Bank
     * Account Management — only their pointer is dropped, so
     * {@code BANK_CERT_SEQ IS NULL} honestly means "not registered against a
     * credential" instead of pointing at a row that no longer exists.
     *
     * <p>Scoped by {@code BIZ_KEY + BANK_CERT_SEQ}, which is exact:
     * {@code BANK_CERT_INFO}'s PK is {@code (BIZ_KEY, SEQ_NO)}, so one seq
     * identifies one credential inside a tenant regardless of bank.
     *
     * <p><b>Deliberate divergence from legacy</b> — legacy
     * ({@code comm_0218_001_d001_act.jsp}) deletes the credential row only and
     * leaves the pointer dangling. Approved 2026-08-19; rationale, coexistence
     * impact and the wrong-bank hazard it closes are in
     * {@code docs/bugs/bank-credential-delete-leaves-dangling-acct-linkage-2026-08-19.md}.
     *
     * <p>Bulk JPQL: bypasses entity callbacks, so {@code MOD_ID} / {@code MOD_DTM}
     * are stamped explicitly (same yyyyMMddHHmmss UTC convention as
     * {@code AccountRegistrationService}).
     *
     * @return number of accounts unlinked (0 when the credential owned none)
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
           update AcctMstr a
              set a.bankCertSeq = null,
                  a.modId       = :userId,
                  a.modDtm      = :nowDtm
            where a.bizKey      = :bizKey
              and a.bankCertSeq = :bankCertSeq
           """)
    int clearBankCertSeq(@Param("bizKey")      String bizKey,
                         @Param("bankCertSeq") Long   bankCertSeq,
                         @Param("userId")      String userId,
                         @Param("nowDtm")      String nowDtm);

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
     * Tenant-scoped lookup by account number only — mirrors legacy
     * {@code PymnHelper.validateBankAccountInAcctMstr} (lines 622-625)
     * which deliberately filters on {@code (biz_key, acct_no)} without
     * a {@code bank_cd} predicate. The Excel cell ships the NAPAS /
     * CITAD code (e.g. {@code 970424}) while {@code ACCT_MSTR.BANK_CD}
     * stores the bank's internal 8-char code (e.g. {@code 11704088});
     * the two formats are incompatible so the bank-code filter is
     * deliberately dropped on this path.
     *
     * <p>Returns the first match — within a tenant the same
     * {@code ACCT_NO} can technically exist under two {@code BANK_CD}s
     * but never does in practice on real data (the legacy code does
     * the same first-row pick via {@code findByDynamic}).
     */
    Optional<AcctMstr> findFirstByBizKeyAndAcctNo(String bizKey, String acctNo);

    /**
     * Bulk-delete every {@code ACCT_MSTR} row at one {@code (BIZ_KEY,
     * BANK_CD)} pair. Called by the Disconnect path in the Login
     * Credentials popup when the last remaining credential for that
     * bank is removed — the linked accounts have no surviving login
     * to scrape against, so they're cleaned up alongside.
     *
     * <p>Issued as a single {@code DELETE} statement to avoid an
     * N+1 entity load. Returns the number of rows deleted so the
     * caller can include it in audit logs / response payloads.
     */
    @Modifying
    @Query("DELETE FROM AcctMstr a WHERE a.bizKey = :bizKey AND a.bankCd = :bankCd")
    int deleteByBizKeyAndBankCd(@Param("bizKey") String bizKey,
                                @Param("bankCd") String bankCd);

    /**
     * Stamp a failed scrape outcome onto one account. HR-2 legacy parity:
     * {@code fnsh_0001_001_c001_act.jsp} records the bank/gateway error
     * code + message into the per-job status columns so the Account
     * Inquiry list shows "Failed" instead of the previous success.
     *
     * <p>Issued as a single UPDATE so it can run in a {@code REQUIRES_NEW}
     * transaction ({@link com.kosign.wecms.domain.banking.nxisas.AcctMstrStatusUpdater})
     * and survive the outer sync transaction's rollback when a
     * TRANSACTION / BALANCE job throws. {@code ACCT_BAL_INQ_STTS} is the
     * column the list badge reads ({@code AccountRow.acctBalInqStts} →
     * frontend {@code isInquiryError}); all three legacy status columns
     * are set to the same code so the row reads as failed regardless of
     * which job failed. Timestamps are intentionally NOT advanced on
     * failure (legacy only stamps {@code *_LST_INQ_DTM} on success).
     */
    @Modifying
    @Query("""
            UPDATE AcctMstr a
               SET a.lstLgnStts     = :code,
                   a.lstLgnSttsMsg  = :msg,
                   a.acctBalInqStts = :code,
                   a.acctBalInqMsg  = :msg,
                   a.acctHisInqStts = :code,
                   a.acctHisInqMsg  = :msg,
                   a.modId          = :userId,
                   a.modDtm         = :nowDb
             WHERE a.bizKey = :bizKey
               AND a.bankCd = :bankCd
               AND a.acctNo = :acctNo
            """)
    int markScrapeFailure(@Param("bizKey") String bizKey,
                          @Param("bankCd") String bankCd,
                          @Param("acctNo") String acctNo,
                          @Param("code")   String code,
                          @Param("msg")    String msg,
                          @Param("userId") String userId,
                          @Param("nowDb")  String nowDb);

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
     * Sort-accepting variant of
     * {@link #findByBizKeyOrderByPrnSeqAscBankCdAscAcctNoAsc(String)}.
     * Used by Settings &gt; Bank Account when the caller supplies a
     * common-sort {@code (sortField, sortDirection)} pair. The
     * {@code Sort} is built from validated entity properties via
     * {@code BankAccountSortField} — no raw wire strings reach JPA.
     */
    List<AcctMstr> findByBizKey(String bizKey, Sort sort);

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
     * <p>Always constrains on {@code BIZ_KEY} (tenant), {@code
     * ACTV_YN = 'Y'}, and {@code HAND_REG_YN = 'N'}. Sort + pagination
     * flow through {@link Pageable} — pass {@link Pageable#unpaged(Sort)}
     * for the export path so the full filtered set is returned in one
     * round trip.
     *
     * <p><b>A1-01 parity fixes (2026-07-24, approved refactor behavior).</b>
     * Two predicates were added to bring the Account-list query in line
     * with legacy {@code comm_0141_001_r001} (the Transaction History
     * bank-account filter, IDO {@code ACCT_MSTR_R014}):
     * <ul>
     *   <li><b>D1 — {@code HAND_REG_YN = 'N'}</b> (unconditional): legacy
     *       {@code getDynamic0}/{@code getDynamic1} force this on both the
     *       VND and FCY sidebar queries. Hand/manually-registered accounts
     *       are excluded. This shared query backs BOTH the Transaction
     *       History sidebar AND the Account Inquiry main table, so — per
     *       the PO decision on 2026-07-24 — Account Inquiry now also hides
     *       hand-registered rows. That is a deliberate refactor behavior
     *       change: legacy {@code fnsh_0001_001} (Account Inquiry) did NOT
     *       force {@code HAND_REG_YN}. Both refactor screens follow one
     *       consistent rule.</li>
     *   <li><b>D3 — PrdtType H maturity/balance guard</b>
     *       ({@code :excludeMaturedHSavings}): legacy sidebar applies this
     *       on the ROW list (redmine #42633). For an H tenant, savings /
     *       term-deposit types (05/06/08) are kept only when not-yet-matured
     *       ({@code EXPI_DT} null or {@code >= CURRENT_DATE}) AND
     *       {@code BAL > 0}. The SAME flag drives {@link #aggregateByCcy}
     *       so summary-card totals and rows stay in sync. Non-H tenants
     *       pass {@code false} and the guard is a no-op.</li>
     * </ul>
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
               AND a.handRegYn = 'N'
               AND (:ccyEq  IS NULL OR a.ccyCd = :ccyEq)
               AND (:ccyNot IS NULL OR a.ccyCd <> :ccyNot)
               AND (:acctTypeCdIn IS NULL OR a.acctTypeCd IN :acctTypeCdIn)
               AND (:bankCd IS NULL OR a.bankCd = :bankCd)
               AND (:authUserId IS NULL OR EXISTS (
                        SELECT 1 FROM AcctAuthInfo aa
                         WHERE aa.bizKey = a.bizKey
                           AND aa.bankCd = a.bankCd
                           AND aa.acctNo = a.acctNo
                           AND aa.userId = :authUserId
                           AND aa.useYn = 'Y'
                           AND aa.authType = 'V'))
               AND (:excludeMaturedHSavings = FALSE
                    OR a.acctTypeCd NOT IN ('05', '06', '08')
                    OR ((a.expiDt IS NULL
                         OR FUNCTION('to_date', a.expiDt, 'YYYYMMDD') >= CURRENT_DATE)
                        AND a.bal > 0))
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
            @Param("authUserId")      String authUserId,
            @Param("excludeMaturedHSavings") boolean excludeMaturedHSavings,
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
     *
     * <p>AI-06 / A1-01-D3: PrdtType H maturity/balance exclusion
     * (legacy {@code fnsh_0001_001_r001_tot_dynamic0} :505-509 +
     * {@code comm_0141_001_r001} getDynamic0/1, redmine #42633). For an
     * H tenant, savings/term-deposit accounts (05/06/08) are excluded
     * UNLESS they are not-yet-matured AND carry a positive balance.
     * <b>As of the A1-01 fix (2026-07-24) the SAME guard is applied to
     * the row list ({@link #findFiltered}) too</b> — the two queries now
     * share the {@code :excludeMaturedHSavings} flag so summary-card
     * totals and the visible rows stay in sync. (Previously this was
     * aggregate-only; the row-list H-guard is the D3 parity fix, mirroring
     * legacy {@code comm_0141_001} which applies the clause on its row
     * query.) Includes the {@code HAND_REG_YN = 'N'} predicate so the
     * totals match the hand-registered exclusion (D1) on the row list.
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
               AND a.handRegYn = 'N'
               AND (:ccyEq  IS NULL OR a.ccyCd = :ccyEq)
               AND (:ccyNot IS NULL OR a.ccyCd <> :ccyNot)
               AND (:acctTypeCdIn IS NULL OR a.acctTypeCd IN :acctTypeCdIn)
               AND (:bankCd IS NULL OR a.bankCd = :bankCd)
               AND (:authUserId IS NULL OR EXISTS (
                        SELECT 1 FROM AcctAuthInfo aa
                         WHERE aa.bizKey = a.bizKey
                           AND aa.bankCd = a.bankCd
                           AND aa.acctNo = a.acctNo
                           AND aa.userId = :authUserId
                           AND aa.useYn = 'Y'
                           AND aa.authType = 'V'))
               AND (:keywordPattern IS NULL
                    OR LOWER(COALESCE(
                            function('wb_format_acct_nick_nm',
                                     a.acctNickNm, a.bankCd, a.acctNo, a.handRegYn),
                            ''
                        )) LIKE :keywordPattern ESCAPE '!')
               AND (:excludeMaturedHSavings = FALSE
                    OR a.acctTypeCd NOT IN ('05', '06', '08')
                    OR ((a.expiDt IS NULL
                         OR FUNCTION('to_date', a.expiDt, 'YYYYMMDD') >= CURRENT_DATE)
                        AND a.bal > 0))
             GROUP BY a.ccyCd
             ORDER BY a.ccyCd
            """)
    List<CcyAggregate> aggregateByCcy(
            @Param("bizKey")          String bizKey,
            @Param("ccyEq")           String ccyEq,
            @Param("ccyNot")          String ccyNot,
            @Param("acctTypeCdIn")    List<String> acctTypeCdIn,
            @Param("bankCd")          String bankCd,
            @Param("keywordPattern")  String keywordPattern,
            @Param("authUserId")      String authUserId,
            @Param("excludeMaturedHSavings") boolean excludeMaturedHSavings);

    /**
     * AI-01 — as-of-date historical balance per account, a faithful port of
     * legacy IDO {@code ACCT_MSTR_R004} (the {@code fnsh_0001_001} read list).
     * Returns {@code (BANK_CD, ACCT_NO, asof_bal, asof_real_amt)} for every
     * account of the tenant.
     *
     * <p>Per {@code ACCT_MSTR_R004.xml:91-217}:
     * <ul>
     *   <li>When {@code :asOf} is blank / today / future → the current
     *       {@code ACCT_MSTR.BAL} / {@code REAL_AMT} (snapshot).</li>
     *   <li>Otherwise (historical) — Vietcombank {@code 11704004}:
     *       {@code ACCT_MSTR.BAL − Σ(signed txns after :asOf)} (deposit +,
     *       withdraw −); other banks: the last {@code ACCT_TRNS_HSTR.BAL}
     *       with {@code TRSC_DT <= :asOf} (ordered {@code TRSC_DT} desc, then
     *       numeric {@code ACCT_SEQ_NO} desc), else 0.</li>
     *   <li>Available balance ({@code REAL_AMT}) mirrors the BAL logic when the
     *       current {@code REAL_AMT = 0}; otherwise uses {@code REAL_AMT} as the
     *       VCB base / current value, with the same other-bank ledger fallback.</li>
     * </ul>
     *
     * <p>The date comparison runs in SQL against {@code CURRENT_DATE} exactly as
     * legacy, so callers may pass today's date and still receive current values.
     * The service invokes this ONLY when an {@code asOfDate} is supplied; the
     * default (no date) list path never calls it and is byte-for-byte unchanged.
     * This drives the per-ROW balance only — legacy summary-card totals
     * ({@code ACCT_MSTR_R004_CNT}) sum current balances with no date logic, so
     * the refactor's {@link #aggregateByCcy} is intentionally NOT date-scoped.
     */
    @Query(value = """
            SELECT amr.bank_cd, amr.acct_no,
              CASE
                WHEN :asOf IS NULL OR :asOf = '' OR :asOf >= to_char(CURRENT_DATE, 'YYYYMMDD')
                  THEN COALESCE(amr.bal, 0)
                ELSE
                  CASE WHEN amr.bank_cd = '11704004' THEN
                         COALESCE(amr.bal, 0) - COALESCE((
                             SELECT SUM(CASE WHEN ath.inout_dv = '1' THEN ath.trsc_amt ELSE -ath.trsc_amt END)
                               FROM acct_trns_hstr ath
                              WHERE ath.biz_key = amr.biz_key AND ath.bank_cd = amr.bank_cd
                                AND ath.acct_no = amr.acct_no AND ath.ccy_cd = amr.ccy_cd
                                AND ath.trsc_dt > :asOf), 0)
                       ELSE COALESCE((
                             SELECT ath.bal FROM acct_trns_hstr ath
                              WHERE ath.biz_key = amr.biz_key AND ath.bank_cd = amr.bank_cd
                                AND ath.acct_no = amr.acct_no AND ath.ccy_cd = amr.ccy_cd
                                AND ath.trsc_dt <= :asOf
                              ORDER BY ath.trsc_dt DESC, cast(ath.acct_seq_no AS NUMERIC) DESC
                              LIMIT 1), 0)
                  END
              END AS asof_bal,
              CASE
                WHEN COALESCE(amr.real_amt, 0) = 0 THEN
                  CASE
                    WHEN :asOf IS NULL OR :asOf = '' OR :asOf >= to_char(CURRENT_DATE, 'YYYYMMDD')
                      THEN COALESCE(amr.bal, 0)
                    ELSE
                      CASE WHEN amr.bank_cd = '11704004' THEN
                             COALESCE(amr.bal, 0) - COALESCE((
                                 SELECT SUM(CASE WHEN ath.inout_dv = '1' THEN ath.trsc_amt ELSE -ath.trsc_amt END)
                                   FROM acct_trns_hstr ath
                                  WHERE ath.biz_key = amr.biz_key AND ath.bank_cd = amr.bank_cd
                                    AND ath.acct_no = amr.acct_no AND ath.ccy_cd = amr.ccy_cd
                                    AND ath.trsc_dt > :asOf), 0)
                           ELSE COALESCE((
                                 SELECT ath.bal FROM acct_trns_hstr ath
                                  WHERE ath.biz_key = amr.biz_key AND ath.bank_cd = amr.bank_cd
                                    AND ath.acct_no = amr.acct_no AND ath.ccy_cd = amr.ccy_cd
                                    AND ath.trsc_dt <= :asOf
                                  ORDER BY ath.trsc_dt DESC, cast(ath.acct_seq_no AS NUMERIC) DESC
                                  LIMIT 1), 0)
                      END
                  END
                ELSE
                  CASE
                    WHEN :asOf IS NULL OR :asOf = '' OR :asOf >= to_char(CURRENT_DATE, 'YYYYMMDD')
                      THEN COALESCE(amr.real_amt, 0)
                    ELSE
                      CASE WHEN amr.bank_cd = '11704004' THEN
                             COALESCE(amr.real_amt, 0) - COALESCE((
                                 SELECT SUM(CASE WHEN ath.inout_dv = '1' THEN ath.trsc_amt ELSE -ath.trsc_amt END)
                                   FROM acct_trns_hstr ath
                                  WHERE ath.biz_key = amr.biz_key AND ath.bank_cd = amr.bank_cd
                                    AND ath.acct_no = amr.acct_no AND ath.ccy_cd = amr.ccy_cd
                                    AND ath.trsc_dt > :asOf), 0)
                           ELSE COALESCE((
                                 SELECT ath.bal FROM acct_trns_hstr ath
                                  WHERE ath.biz_key = amr.biz_key AND ath.bank_cd = amr.bank_cd
                                    AND ath.acct_no = amr.acct_no AND ath.ccy_cd = amr.ccy_cd
                                    AND ath.trsc_dt <= :asOf
                                  ORDER BY ath.trsc_dt DESC, cast(ath.acct_seq_no AS NUMERIC) DESC
                                  LIMIT 1), 0)
                      END
                  END
              END AS asof_real_amt
              FROM acct_mstr amr
             WHERE amr.biz_key = :bizKey
            """, nativeQuery = true)
    List<Object[]> findAsOfBalances(@Param("bizKey") String bizKey, @Param("asOf") String asOf);

    /**
     * Distinct {@code BANK_CD} list for the current tenant + currency
     * scope. Drives the frontend bank-filter dropdown. Intentionally
     * does NOT honour the {@code bankCd} or {@code keyword} filters —
     * the dropdown's option set must stay stable as the user narrows
     * by bank or types in the search box (selecting one bank should not
     * remove the others from the dropdown).
     *
     * <p>Active accounts only ({@code ACTV_YN = 'Y'}), and — per the
     * A1-01 fix (2026-07-24) — mirroring the same {@code HAND_REG_YN = 'N'}
     * (D1) and PrdtType H maturity/balance guard (D3) the row list
     * ({@link #findFiltered}) applies, so the dropdown never lists a bank
     * whose only accounts are hand-registered or matured-H-savings (which
     * would leave the user with an empty grid on selection). Ordered by
     * {@code BANK_CD} so the frontend renders a deterministic list.
     */
    @Query("""
            SELECT DISTINCT a.bankCd FROM AcctMstr a
             WHERE a.bizKey = :bizKey
               AND a.actvYn = 'Y'
               AND a.handRegYn = 'N'
               AND (:ccyEq  IS NULL OR a.ccyCd = :ccyEq)
               AND (:ccyNot IS NULL OR a.ccyCd <> :ccyNot)
               AND (:acctTypeCdIn IS NULL OR a.acctTypeCd IN :acctTypeCdIn)
               AND (:authUserId IS NULL OR EXISTS (
                        SELECT 1 FROM AcctAuthInfo aa
                         WHERE aa.bizKey = a.bizKey
                           AND aa.bankCd = a.bankCd
                           AND aa.acctNo = a.acctNo
                           AND aa.userId = :authUserId
                           AND aa.useYn = 'Y'
                           AND aa.authType = 'V'))
               AND (:excludeMaturedHSavings = FALSE
                    OR a.acctTypeCd NOT IN ('05', '06', '08')
                    OR ((a.expiDt IS NULL
                         OR FUNCTION('to_date', a.expiDt, 'YYYYMMDD') >= CURRENT_DATE)
                        AND a.bal > 0))
             ORDER BY a.bankCd
            """)
    List<String> findDistinctBankCds(
            @Param("bizKey")       String bizKey,
            @Param("ccyEq")        String ccyEq,
            @Param("ccyNot")       String ccyNot,
            @Param("acctTypeCdIn") List<String> acctTypeCdIn,
            @Param("authUserId")   String authUserId,
            @Param("excludeMaturedHSavings") boolean excludeMaturedHSavings);

    /**
     * Firm-banking withdrawal-account lookup for the payment Excel-Upload
     * dialog (legacy `comm_1001_001`). Returns active current/savings
     * accounts at the firm-banking banks that the current tenant has
     * enrolled in firm-banking (EXISTS in {@code TB_FIRM_CONF} with
     * {@code ORG_STAT='1'}). Module-agnostic — shared by all five payment
     * consumers via {@code WithdrawalAccountListService}.
     *
     * <p>Mirrors the legacy IDO {@code ACCT_MSTR_R011} via
     * {@code comm_1001_001_act.jsp:23-65}. The legacy filters applied
     * here are:
     * <ol>
     *   <li>tenant scope ({@code biz_key})</li>
     *   <li><b>G1</b> — firm-banking banks
     *       ({@code bank_cd IN :bankCds}; legacy hardcoded
     *       {@code ('11704088','11704020')} —
     *       {@code WithdrawalAccountConstants.WITHDRAWAL_BANK_CDS})</li>
     *   <li>current/savings only ({@code acct_type_cd IN ('03'..'08')})</li>
     *   <li>{@code ACTV_YN = 'Y'}</li>
     *   <li><b>G3</b> — EXISTS in {@code TB_FIRM_CONF} with
     *       {@code ORG_STAT='1'} joined via the {@code NAPAS_BANK_INFO}
     *       dictionary</li>
     * </ol>
     *
     * <p>The legacy per-user {@code ACCT_AUTH_INFO} {@code AUTH_TYPE IN
     * ('V','W')} authorization filter (<b>G2</b>) is applied in the
     * service layer ({@code WithdrawalAccountListService}), not here.
     *
     * <p>Optional {@code ccyCd} filter — legacy carries the parameter
     * but the SQL has it commented out. Honored here when the caller
     * supplies a non-null value so future per-upload-currency
     * narrowing is one-prop change away.
     *
     * <p><b>G3 fallback (2026-05-14):</b> when {@code
     * allowNoFirmConfigFallback} is {@code true}, the mandatory firm-
     * banking EXISTS is relaxed by an {@code OR NOT EXISTS (any
     * ORG_STAT='1' row for the tenant)} escape so an un-provisioned
     * tenant still surfaces active current/savings accounts (LOCAL R&D
     * only — the shared dev DB at 172.168.5.172/wecmsdevdb has no
     * firm-banking enrolment and we lack write access to seed it). The
     * flag defaults to {@code false} (strict legacy parity) in
     * SIT/UAT/PROD; it is turned on only in {@code application-local.yml}.
     * On a provisioned tenant the fallback branch is a no-op, so behavior
     * is identical regardless of the flag.
     */
    @Query("""
            SELECT a FROM AcctMstr a
             WHERE a.bizKey = :bizKey
               AND a.actvYn = 'Y'
               AND a.acctTypeCd IN ('03','04','05','06','07','08')
               AND a.bankCd IN :bankCds
               AND (:ccyCd IS NULL OR a.ccyCd = :ccyCd)
               AND (
                    EXISTS (
                         SELECT 1
                           FROM TbFirmConf tfc, NapasBankInfo nbi
                          WHERE tfc.bizKey      = a.bizKey
                            AND tfc.wdrlActNo   = a.acctNo
                            AND tfc.outBankCd   = nbi.napasBankCd
                            AND nbi.bankCd      = a.bankCd
                            AND tfc.orgStat     = '1'
                        )
                    OR (:allowNoFirmConfigFallback = TRUE AND NOT EXISTS (
                         SELECT 1
                           FROM TbFirmConf tfc2
                          WHERE tfc2.bizKey  = a.bizKey
                            AND tfc2.orgStat = '1'
                        ))
                   )
             ORDER BY a.bankCd, a.acctNo
            """)
    List<AcctMstr> findFirmBankingWithdrawalAccounts(
            @Param("bizKey")                   String bizKey,
            @Param("ccyCd")                    String ccyCd,
            @Param("bankCds")                  java.util.Collection<String> bankCds,
            @Param("allowNoFirmConfigFallback") boolean allowNoFirmConfigFallback);

    /* =================================================================
     *  Own Account Transfer (refactor slug: own-account-transfer)
     * ================================================================= */

    /**
     * Source-account list for {@code GET /api/v1/banking/own-account-transfer/source-accounts}.
     * Mirrors legacy {@code _r001_act.jsp:144-194} + {@code IDO.ACCT_MSTR_R015.xml}
     * minus the auth-pivot join (the service applies the USER-mode
     * filter in Java against {@link com.kosign.wecms.domain.setting.bankaccount.repository.AcctAuthInfoRepository} so the
     * tenant-slice query stays a clean entity load and the test
     * harness doesn't need to seed the pivot table for every fixture).
     *
     * <p>Filters (every predicate matches a legacy condition):
     * <ul>
     *   <li>{@code BIZ_KEY = :bizKey} — tenant scope.</li>
     *   <li>{@code CCY_CD = 'VND'} — feature is VND-only.</li>
     *   <li>{@code ACTV_YN = 'Y'} — inactive rows hidden.</li>
     *   <li>{@code ACCT_TYPE_CD IN ('03','05')} — current + saving
     *       only.</li>
     *   <li>{@code BANK_CD IN :bankCds} — the service supplies either
     *       the single requested bank or the 5-bank allow-list (1-bank
     *       VIETIN-only for HAENGSUNG tenants).</li>
     *   <li><b>HAENGSUNG saving-account gate</b> (only when
     *       {@code :prdtTypeH = true}, off otherwise): saving accounts
     *       ({@code 05}/{@code 06}/{@code 08}) are kept only when
     *       {@code (EXPI_DT IS NULL OR EXPI_DT &gt;= :todayYmd)} AND
     *       {@code BAL &gt; 0}. {@code EXPI_DT} is the legacy
     *       {@code VARCHAR(8)} {@code yyyyMMdd} column so the
     *       string-compare is parity-equivalent to legacy's
     *       {@code TO_DATE(EXPI_DT,'YYYYMMDD') &gt;= CURRENT_DATE}.</li>
     * </ul>
     *
     * <p>{@code :bankCds} must never be empty — the service constructs
     * it with at least one bank (Postgres would reject an empty
     * {@code IN ()} list).
     */
    @Query("""
            SELECT a FROM AcctMstr a
             WHERE a.bizKey      = :bizKey
               AND a.ccyCd       = 'VND'
               AND a.actvYn      = 'Y'
               AND a.acctTypeCd IN ('03','05')
               AND a.bankCd     IN :bankCds
               AND (
                    :prdtTypeH = false
                 OR a.acctTypeCd NOT IN ('05','06','08')
                 OR ((a.expiDt IS NULL OR a.expiDt >= :todayYmd) AND a.bal > 0)
                   )
            """)
    List<AcctMstr> findOwnTransferSourceRows(
            @Param("bizKey")    String bizKey,
            @Param("bankCds")   List<String> bankCds,
            @Param("prdtTypeH") boolean prdtTypeH,
            @Param("todayYmd")  String todayYmd,
            Sort sort);

    /**
     * Single-tenant balance-refresh patch invoked by
     * {@code POST /api/v1/banking/own-account-transfer/balance-refresh}.
     * Mirrors legacy {@code _u001_act.jsp:88-130} via a single
     * conditional UPDATE — {@code COALESCE(:newX, X)} keeps the existing
     * column value when the service passes {@code null} for that field,
     * so the same SQL statement handles every branch of the legacy
     * decision tree:
     * <ul>
     *   <li>Always patches {@code ACCT_BAL_INQ_STTS},
     *       {@code ACCT_BAL_INQ_MSG}, {@code ACCT_BAL_LST_INQ_DTM},
     *       {@code MOD_ID}, {@code MOD_DTM}.</li>
     *   <li>On engine success ({@code status='00000000'}) AND a balance
     *       delta: service passes non-null {@code :newBal} +
     *       {@code :newCorcBfBal} + {@code :newUpdDtm}; the columns
     *       update.</li>
     *   <li>On engine success AND {@code real_amt} reported: service
     *       passes non-null {@code :newRealAmt}; the column updates.</li>
     *   <li>On engine failure ({@code status &ne; '00000000'}): every
     *       optional param is null; only the status/msg/lst-inq
     *       columns move.</li>
     * </ul>
     *
     * <p>Returns the row count to the service for assertion (always 0
     * or 1 — the PK is the full {@code (BIZ_KEY, BANK_CD, ACCT_NO)}
     * tuple, so a hit is exactly one). The {@code @Modifying} annotation
     * needs {@code clearAutomatically = true} so a follow-up
     * {@code findById(...)} fetch sees the patched row instead of the
     * stale L1 cache copy — the response DTO uses the post-patch BAL.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE ACCT_MSTR
               SET ACCT_BAL_INQ_STTS    = :stts,
                   ACCT_BAL_INQ_MSG     = :msg,
                   ACCT_BAL_LST_INQ_DTM = :nowDtm,
                   MOD_ID               = :modId,
                   MOD_DTM              = :nowDtm,
                   BAL                  = COALESCE(:newBal, BAL),
                   REAL_AMT             = COALESCE(:newRealAmt, REAL_AMT),
                   CORC_BF_BAL          = COALESCE(:newCorcBfBal, CORC_BF_BAL),
                   ACCT_BAL_UPD_DTM     = COALESCE(:newUpdDtm, ACCT_BAL_UPD_DTM)
             WHERE BIZ_KEY = :bizKey AND BANK_CD = :bankCd AND ACCT_NO = :acctNo
            """, nativeQuery = true)
    int applyOwnTransferBalanceRefresh(
            @Param("bizKey")       String bizKey,
            @Param("bankCd")       String bankCd,
            @Param("acctNo")       String acctNo,
            @Param("stts")         String stts,
            @Param("msg")          String msg,
            @Param("nowDtm")       String nowDtm,
            @Param("modId")        String modId,
            @Param("newBal")       BigDecimal newBal,
            @Param("newRealAmt")   BigDecimal newRealAmt,
            @Param("newCorcBfBal") BigDecimal newCorcBfBal,
            @Param("newUpdDtm")    String newUpdDtm);

    /**
     * Persist the bank-account password(s) the user typed into the Own
     * Account Transfer composer, after the bank accepted them.
     *
     * <p><b>Both parameters are already ciphertext</b> — the service
     * encrypts with {@link com.kosign.wecms.domain.banking.bankcredentials.service.BankCertCryptoService}
     * before calling, which produces output byte-identical to the legacy
     * JEX DB function {@code public.encrypt(txt)}. The live JEX system
     * reads these same columns back with {@code decrypt()}
     * ({@code IDO.ACCT_MSTR_R015:115-116}), so coexistence is preserved.
     * Never pass plaintext here.
     *
     * <p><b>{@code COALESCE} is deliberate and mirrors legacy.</b>
     * {@code IDO.ACCT_MSTR_U001:55-56} is
     * {@code ACCT_PWD = coalesce(encrypt(:ACCT_PWD), ACCT_PWD)} — a null
     * parameter leaves the stored value untouched rather than wiping it.
     * That matters because the two passwords are independent: a bank that
     * needs only the account password must not blank an existing
     * {@code TRNS_PWD}.
     *
     * <p><b>Why a native UPDATE and not entity setters.</b> These two
     * columns are intentionally <i>not</i> mapped on {@link com.kosign.wecms.domain.banking.entity.AcctMstr}.
     * {@code AccountRegistrationService} calls {@code save(existing)} on
     * loaded rows, so a mapped field would let an unrelated write path
     * round-trip (or null) the ciphertext. Keeping them unmapped makes
     * this method the only writer in the refactor.
     *
     * <p>Returns the affected row count — always 0 or 1, since the PK is
     * the full {@code (BIZ_KEY, BANK_CD, ACCT_NO)} tuple.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE ACCT_MSTR
               SET ACCT_PWD = COALESCE(:acctPwdEnc, ACCT_PWD),
                   TRNS_PWD = COALESCE(:trnsPwdEnc, TRNS_PWD),
                   MOD_ID   = :modId,
                   MOD_DTM  = :nowDtm
             WHERE BIZ_KEY = :bizKey AND BANK_CD = :bankCd AND ACCT_NO = :acctNo
            """, nativeQuery = true)
    int applyTransferPasswords(
            @Param("bizKey")     String bizKey,
            @Param("bankCd")     String bankCd,
            @Param("acctNo")     String acctNo,
            @Param("acctPwdEnc") String acctPwdEnc,
            @Param("trnsPwdEnc") String trnsPwdEnc,
            @Param("modId")      String modId,
            @Param("nowDtm")     String nowDtm);
}
