package com.kosign.wecms.domain.payment.dispatch.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * H-2 · Query contract for the dispatch <b>sweep</b> queries and the tenant scoping of every
 * dispatch <b>mutation</b>.
 *
 * <h2>The finding this encodes, and the correction to how H-2 was framed</h2>
 * H-2 was originally recorded as a "cross-tenant scan" defect. Tracing legacy showed that framing was
 * wrong on the tenant half. All three legacy batch pickup queries select across every tenant by
 * design, with {@code BIZ_KEY} as an <b>output column, never a predicate</b>:
 * <ul>
 *   <li>{@code IDO.TB_PAY_FILE_MST_R001.xml} — {@code WHERE PROC_STAT = :PROC_STAT AND TRAN_DT = :TRAN_DT}</li>
 *   <li>{@code IDO.TB_PAY_TARGET_R001.xml} — {@code WHERE TRAN_DT = TO_CHAR(now(),'yyyymmdd') AND PROC_STAT = '9'}</li>
 *   <li>{@code IDO.TB_FIRM_TRAN_R001.xml} — {@code WHERE TRAN_DT = … AND PROC_STAT = '6' AND TRAN_TM <= … AND TRAN_TP = '202'}</li>
 * </ul>
 * The legacy batch is a <b>system-scoped sweep</b>: it iterates the selected rows and then acts per
 * row using that row's own {@code BIZ_KEY} (e.g. {@code data.getString("BIZ_KEY")} throughout
 * {@code FILE_DEPOSIT_TRANSFER_BATCH1}). So tenant safety belongs to the <b>actions</b>, not the scan
 * — and adding {@code BIZ_KEY} to a sweep would require a tenant list the sweep does not have.
 *
 * <p>The <b>real</b> H-2 defect was therefore the other half: the scans were <b>unbounded and
 * unordered</b>, materialising every historical matching row on every tick and (on the result-check
 * rail) filtering in Java afterwards. This class pins both properties that matter:
 * <ol>
 *   <li>every sweep is bounded ({@link Pageable}) and deterministically ordered oldest-first;</li>
 *   <li>every mutation is tenant-scoped, so a system-wide sweep can never move another tenant's row.</li>
 * </ol>
 *
 * <p><b>Scope limit, stated honestly.</b> These are static query-contract assertions, the same idiom
 * the repo already uses ({@code ScdulPymnSumrSearchStatusFilterTest}). They prove the SQL says what it
 * must say. They do <b>not</b> execute PostgreSQL — the project ships no test database — so a
 * DB-backed test of actual row visibility remains outstanding; see
 * {@code docs/architecture/payment-dispatch-tenant-isolation.md}.
 */
class DispatchScanTenantScopeContractTest {

    /**
     * The bounded sweep finders that replaced the unbounded ones — <b>every scheduled reader on the
     * dispatch rails that selects rows by state + age</b>.
     *
     * <p>Completed 2026-07-29 (repository/query re-verification):
     * {@code TbFirmFileMstRepository.findStrandedFirmFiles} was a live scheduled sweep
     * ({@code StuckPayFileMonitorService:420}) that satisfied this whole contract but was not listed
     * here, so nothing would have caught it losing its {@code Pageable}, its ordering or its SQL age
     * cutoff. The result-check candidate scan has a different shape and is pinned separately below.
     */
    private static List<Method> boundedSweeps() throws NoSuchMethodException {
        List<Method> m = new ArrayList<>();
        m.add(TbPayTargetRepository.class.getMethod(
                "findStrandedPayTargets", String.class, String.class, Pageable.class));
        m.add(TbPayFileMstRepository.class.getMethod(
                "findStrandedPayFiles", String.class, String.class, Pageable.class));
        m.add(TbFirmFileMstRepository.class.getMethod(
                "findStrandedFirmFiles",
                String.class, String.class, String.class, String.class, Pageable.class));
        return m;
    }

    /**
     * The result-check candidate scan — the fourth live scheduled reader, pinned separately because
     * its bounds are legitimately a different shape: it selects on {@code TRAN_DT IN} + {@code TRAN_TP}
     * + {@code PROC_STAT} and orders by {@code TRAN_DT}/{@code TRAN_TM}, and its <i>age</i> rule stays
     * in {@code ResultCheckService.selectAged} on purpose (the tiered {@code RQ_DTTM}-first rule; see
     * that method's Javadoc — folding it into SQL would reintroduce the UTC/ICT boundary bug).
     */
    private static Method resultCheckScan() throws NoSuchMethodException {
        return TbFirmTranRepository.class.getMethod(
                "findResultCheckCandidates",
                String.class, String.class, java.util.Collection.class, Pageable.class);
    }

    /** Every mutating query on the dispatch rails. */
    private static List<Method> mutations() throws NoSuchMethodException {
        List<Method> m = new ArrayList<>();
        m.add(TbPayTargetRepository.class.getMethod(
                "claimForDispatch", String.class, String.class, String.class, String.class));
        m.add(TbPayFileMstRepository.class.getMethod(
                "claimForReception", String.class, String.class, String.class, String.class));
        m.add(TbPayFileMstRepository.class.getMethod(
                "failClaimedPayFile", String.class, String.class, String.class, String.class, String.class));
        return m;
    }

    // ── bounded + ordered sweeps ─────────────────────────────────────────────

    @Test
    @DisplayName("every stranded-row sweep takes a Pageable — no sweep may be unbounded")
    void sweepsAreBounded() throws Exception {
        for (Method m : boundedSweeps()) {
            assertThat(m.getParameterTypes())
                    .as("%s must accept a Pageable so the sweep is capped", m.getName())
                    .contains(Pageable.class);
        }
    }

    @Test
    @DisplayName("every sweep is ordered oldest-first, so a capped sweep drains rather than starves")
    void sweepsAreOrderedOldestFirst() throws Exception {
        for (Method m : boundedSweeps()) {
            Query q = m.getAnnotation(Query.class);
            assertThat(q).as("%s must carry an explicit @Query", m.getName()).isNotNull();

            String jpql = q.value().replaceAll("\\s+", " ").trim();

            assertThat(jpql)
                    .as("%s must ORDER BY — without it the DB may return any page, so a capped "
                            + "sweep could revisit the same rows forever", m.getName())
                    .containsIgnoringCase("ORDER BY");
            assertThat(jpql)
                    .as("%s must order by its aging basis ASC first (oldest-first drains the backlog)",
                            m.getName())
                    .containsIgnoringWhitespaces(agingBasis(jpql) + " ASC");
            assertThat(jpql)
                    .as("%s must not order newest-first", m.getName())
                    .doesNotContainIgnoringCase("procDttm DESC");
        }
    }

    @Test
    @DisplayName("the age cutoff is a SQL predicate, applied before the limit (the N-1 lesson)")
    void ageCutoffIsASqlPredicate() throws Exception {
        for (Method m : boundedSweeps()) {
            String jpql = m.getAnnotation(Query.class).value().replaceAll("\\s+", " ");

            // If the age filter were applied in Java after the LIMIT, old rows would sort to the front
            // of every page, consume the whole window and then be discarded — the sweep would process
            // nothing, permanently. That is exactly what happened on the result-check rail (N-1).
            String basis = agingBasis(jpql);
            assertThat(jpql)
                    .as("%s must bound by its aging basis in SQL", m.getName())
                    .containsIgnoringWhitespaces(basis + " < :procDttmCutoff");
            assertThat(jpql)
                    .as("%s must exclude a null aging basis in SQL (never eligible)", m.getName())
                    .containsIgnoringWhitespaces(basis + " IS NOT NULL");
        }
    }

    /**
     * The expression a sweep ages rows on — {@code f.procDttm} for most, or
     * {@code COALESCE(NULLIF(f.procDttm, ''), f.regDttm)} for {@code findStrandedFirmFiles} since the
     * 2026-08-01 unification (neither legacy nor the refactor stamps {@code PROC_DTTM} when a firm-file
     * row is inserted, so that rail must fall back to {@code REG_DTTM} or it selects nothing).
     *
     * <p>Derived from the JPQL rather than hardcoded per method, so the assertions above keep their
     * teeth: they check that <b>the same</b> expression is used for the ordering, the cutoff and the null
     * guard. A finder that ordered by one basis and bounded by another would still fail — which is the
     * defect worth catching, and the reason this is not simply an {@code anyOf(...)} over two literals.
     */
    private static String agingBasis(String jpql) {
        String normalised = jpql.replaceAll("\\s+", " ");
        Matcher coalesced = Pattern
                .compile("COALESCE\\(NULLIF\\((\\w+)\\.procDttm, ''\\), \\1\\.regDttm\\)")
                .matcher(normalised);
        if (coalesced.find()) {
            return coalesced.group();
        }
        // Otherwise the basis is the plain column, under whichever alias this finder selects with —
        // 't' for TB_PAY_TARGET, 'p' for TB_PAY_FILE_MST, 'f' for TB_FIRM_FILE_MST. Hardcoding one
        // alias here would silently pass the other two rails (a `contains` of an alias that never
        // appears fails, so the test would break rather than pass — but for the wrong reason).
        Matcher alias = Pattern.compile("SELECT (\\w+) FROM").matcher(normalised);
        return (alias.find() ? alias.group(1) : "f") + ".procDttm";
    }

    @Test
    @DisplayName("sweeps filter on an exact status, never a wildcard or an IN over all states")
    void sweepsFilterOnAnExactStatus() throws Exception {
        for (Method m : boundedSweeps()) {
            String jpql = m.getAnnotation(Query.class).value().replaceAll("\\s+", " ");
            assertThat(jpql).containsIgnoringWhitespaces("procStat = :procStat");
            assertThat(jpql).doesNotContain("LIKE");
            assertThat(jpql).doesNotContain("%");
        }
    }

    // ── the result-check candidate scan (different bound shape, same guarantees) ──────────

    @Test
    @DisplayName("result-check scan is bounded and ordered oldest-first (its own key shape)")
    void resultCheckScanIsBoundedAndOrderedOldestFirst() throws Exception {
        Method m = resultCheckScan();
        assertThat(m.getParameterTypes())
                .as("findResultCheckCandidates must accept a Pageable — the unbounded "
                        + "findByTranTpAndProcStat is what it replaced")
                .contains(Pageable.class);

        String jpql = m.getAnnotation(Query.class).value().replaceAll("\\s+", " ").trim();

        assertThat(jpql).containsIgnoringCase("ORDER BY");
        // Oldest-first on this rail means TRAN_DT then TRAN_TM: a young row must never displace an
        // aged one, or a truncated sweep stops making forward progress (the N-1 starvation).
        assertThat(jpql)
                .as("must order by tranDt ASC first")
                .containsIgnoringWhitespaces("t.tranDt ASC");
        assertThat(jpql)
                .as("then tranTm ASC — the within-day oldest-first key")
                .containsIgnoringWhitespaces("t.tranTm ASC");
        assertThat(jpql)
                .as("tranSeq is the final tiebreaker so the order is total and two nodes agree")
                .containsIgnoringWhitespaces("t.tranSeq ASC");
        assertThat(jpql)
                .as("must never order newest-first")
                .doesNotContainIgnoringCase("DESC");
    }

    @Test
    @DisplayName("result-check scan bounds the DATE in SQL, before the limit (the N-1 regression)")
    void resultCheckScanBoundsTheDateInSql() throws Exception {
        String jpql = resultCheckScan().getAnnotation(Query.class).value().replaceAll("\\s+", " ");

        // This is the exact regression N-1 records: with the date filter applied in Java AFTER the
        // limit, any row parked at 202/'6' from an older date sorts to the front of every page,
        // consumes the whole batch window and is then discarded — the sweep processes nothing,
        // permanently. Legacy's own IDO.TB_FIRM_TRAN_R001 has TRAN_DT in the WHERE clause too.
        assertThat(jpql)
                .as("tranDt must be a SQL predicate, not a post-limit Java filter")
                .containsIgnoringWhitespaces("t.tranDt IN :tranDts");
    }

    @Test
    @DisplayName("result-check scan filters on exact TRAN_TP + PROC_STAT, never a wildcard")
    void resultCheckScanFiltersOnExactStateAndType() throws Exception {
        String jpql = resultCheckScan().getAnnotation(Query.class).value().replaceAll("\\s+", " ");

        assertThat(jpql).containsIgnoringWhitespaces("t.tranTp = :tranTp");
        assertThat(jpql).containsIgnoringWhitespaces("t.procStat = :procStat");
        assertThat(jpql).doesNotContain("LIKE");
        assertThat(jpql).doesNotContain("%");
    }

    // ── tenant scoping lives in the mutations ────────────────────────────────

    @Test
    @DisplayName("every dispatch mutation is tenant-scoped — a sweep cannot move another tenant's row")
    void everyMutationIsTenantScoped() throws Exception {
        for (Method m : mutations()) {
            assertThat(m.getAnnotation(Modifying.class))
                    .as("%s must be @Modifying", m.getName()).isNotNull();

            String jpql = m.getAnnotation(Query.class).value().replaceAll("\\s+", " ");

            // This is the assertion that makes system-scoped sweeping safe. The sweep may SEE every
            // tenant's rows (legacy does too); it must never be able to WRITE outside the row's own
            // tenant. FILE_SEQ_NO / TRAN_SEQ come from global sequences and repeat across tenants, so
            // without BIZ_KEY these updates could match the wrong row entirely.
            assertThat(jpql)
                    .as("%s must constrain on bizKey", m.getName())
                    .containsIgnoringWhitespaces("bizKey = :bizKey");
            assertThat(jpql)
                    .as("%s must never wildcard the tenant", m.getName())
                    .doesNotContain("LIKE");
            assertThat(jpql).doesNotContain("%");
        }
    }

    @Test
    @DisplayName("every dispatch mutation is fully key-qualified — it can only ever move one row")
    void everyMutationIsFullyKeyQualified() throws Exception {
        for (Method m : mutations()) {
            String jpql = m.getAnnotation(Query.class).value().replaceAll("\\s+", " ");
            assertThat(jpql).as("%s: tranDt", m.getName()).containsIgnoringWhitespaces("tranDt = :tranDt");

            boolean payTargetRail = jpql.contains("TbPayTarget");
            if (payTargetRail) {
                assertThat(jpql).as("%s: tranSeq", m.getName()).containsIgnoringWhitespaces("tranSeq = :tranSeq");
            } else {
                assertThat(jpql).as("%s: fileSeqNo", m.getName()).containsIgnoringWhitespaces("fileSeqNo = :fileSeqNo");
            }
        }
    }

    @Test
    @DisplayName("every dispatch mutation is state-guarded, so it cannot overwrite a terminal status")
    void everyMutationIsStateGuarded() throws Exception {
        for (Method m : mutations()) {
            String jpql = m.getAnnotation(Query.class).value().replaceAll("\\s+", " ");

            // The guard must be in the WHERE clause, so the assertion is made against the WHERE
            // portion ONLY.
            //
            // FIXED 2026-07-29 (independent verification). This previously matched
            // `procStat\s*=\s*'[0369]'` against the WHOLE statement, which every one of these
            // mutations satisfies from its SET clause alone — `SET p.procStat = '3'` for
            // failClaimedPayFile, `SET ... procStat = '6'` for both claims. Deleting the WHERE guard
            // therefore left this test GREEN, which was verified by mutation: removing
            // `AND p.procStat = '6'` from failClaimedPayFile failed nothing in the entire suite.
            //
            // That matters because the WHERE guard is the whole mechanism: for the two claims it is
            // what prevents concurrent double-dispatch, and for failClaimedPayFile it is what stops a
            // late terminal write clobbering a status a callback already advanced.
            int whereAt = jpql.toUpperCase().indexOf(" WHERE ");
            assertThat(whereAt)
                    .as("%s must have a WHERE clause — an unconditional UPDATE is never a guarded one",
                            m.getName())
                    .isGreaterThan(0);
            String whereClause = jpql.substring(whereAt);

            assertThat(whereClause)
                    .as("%s must guard on the expected CURRENT procStat inside WHERE — not merely "
                            + "assign one in SET. WHERE clause was: %s", m.getName(), whereClause)
                    .containsPattern("procStat\\s*=\\s*'[0369]'");
            assertThat(m.getReturnType())
                    .as("%s must return int so the caller can act on 0 rows", m.getName())
                    .isEqualTo(int.class);
        }
    }
}
