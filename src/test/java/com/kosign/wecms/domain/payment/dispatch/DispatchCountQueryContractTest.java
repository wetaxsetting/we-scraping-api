package com.kosign.wecms.domain.payment.dispatch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <b>Q-1 query contract</b> for {@link DispatchCountRepository#countByBatch} — the per-batch
 * success / error counts behind the "Completed N✓ M✗" chips on every Payment Result list
 * (Internal Transfer / Vendor / Expense / Payroll).
 *
 * <h2>The defect this pins shut</h2>
 *
 * The query used to route per line on the <b>detail</b> {@code SPMD.PAY_FILE_SEQ_NO} and join
 * {@code TPFD.SEQ = SPMD.PAY_SEQ}. Both columns are always {@code NULL} — the dispatch executors
 * stamp only {@code line.payTranSeq} plus {@code master.payFileSeqNo} and explicitly null the other
 * two ({@code InternalTransferDispatchExecutor:276-277,319},
 * {@code ExpenseDispatchExecutor:198-199,241}, {@code PayrollDispatchExecutor:184,284},
 * {@code OverseasDispatchExecutor:215,378}), which is exactly how legacy JEX data looks. The
 * file-route arm was therefore unreachable; every file-route line fell through to the API arm, where
 * it has no {@code TB_PAY_TARGET} row, so it counted as neither success nor error and the chips read
 * {@code 0 / 0}. Meanwhile the Result <i>detail</i> page — which goes through
 * {@code DispatchResultResolver} and uses the master key, i.e. legacy's rule — showed the real
 * statuses, so list and detail disagreed. Confirmed on JEX DEV against 20+ file-route batches
 * (e.g. batch {@code 372}: legacy {@code 1 / 0}, refactor {@code 0 / 0}).
 *
 * <h2>Legacy source of truth — {@code IDO.SCDUL_PYMN_SUMR_R007.xml}</h2>
 *
 * {@code WECMS DEV/WECMS_ETC/xml/service/IDO/IDO.SCDUL_PYMN_SUMR_R007.xml}, the
 * {@code TOT_TRNS_ERROR_CNT} (`:58-102`) and {@code TOT_TRNS_SUCC_CNT} (`:103-144`) expressions:
 *
 * <pre>
 * -- route: decided ONCE per batch, from the MASTER (no per-line predicate)   :60-65, :105-110
 * WHEN EXISTS (SELECT 1 FROM TB_PAY_FILE_DATA TPFD
 *               WHERE TPFD.BIZ_KEY     = SPS.BIZ_KEY
 *                 AND TPFD.TRAN_DT     = SPS.PAY_TRAN_DT
 *                 AND TPFD.FILE_SEQ_NO = SPS.PAY_FILE_SEQ_NO)
 * -- file arm: per-line result joined with a VARCHAR CAST on PAY_TRAN_SEQ      :78, :123
 *      AND CAST(TPFD.SEQ AS VARCHAR) = SPMD.PAY_TRAN_SEQ
 *      err: TPFM.PROC_STAT = '3'
 *           OR (TPFM.PROC_STAT = '0' AND TRIM(COALESCE(TPFD.RS_CD,'')) NOT IN ('0000'))   :82-83
 *      suc: TPFM.PROC_STAT = '0' AND TRIM(COALESCE(TPFD.RS_CD,'')) = '0000'               :126-127
 * -- api arm                                                                   :90, :132
 *      err: SUM(CASE WHEN SPT.PROC_STAT = '3' THEN 1 ELSE 0 END)
 *      suc: SUM(CASE WHEN SPT.PROC_STAT = '0' THEN 1 ELSE 0 END)
 * </pre>
 *
 * The same route rule and the same {@code CAST(... AS VARCHAR) = PAY_TRAN_SEQ} join appear in
 * {@code IDO.SCDUL_PYMN_SUMR_R006.xml` (`:66-78`, `:150-153`) — the detail-page query. Aligning
 * {@code countByBatch} with them is what makes list and detail agree.
 *
 * <h2>Why a query-text contract rather than an executed query</h2>
 *
 * {@code countByBatch} is a <b>native</b> query over four tables, three of which
 * ({@code TB_PAY_TARGET} / {@code TB_PAY_FILE_MST} / {@code TB_PAY_FILE_DATA}) are not
 * entity-mapped. The project ships no Testcontainers and no embedded database by policy
 * ({@code CLAUDE.md}: <i>"Not currently in build.gradle … Testcontainers"</i>, <i>"No Testcontainers
 * … H2/in-memory deferred"</i>), so there is no way to execute it here. This is the same idiom the
 * repo already uses for its other repository contracts ({@code DispatchClaimQueryContractTest},
 * {@code DispatchScanTenantScopeContractTest}, {@code ScdulPymnSumrSearchStatusFilterTest}).
 *
 * <p><b>Honest limitation.</b> These tests prove the SQL <i>says</i> the legacy thing — the route
 * source, the join columns, the predicates, the tenant scope. They do not prove PostgreSQL evaluates
 * it to legacy's numbers; that was verified out-of-band by running both expressions side by side on
 * JEX DEV (recorded in
 * {@code docs/audits/dispatch-repository-query-reverification.md} §5). The truth table each arm must
 * implement is asserted below case-by-case so the numeric intent is version-controlled rather than
 * living only in the audit note. The DB-backed execution test remains outstanding and is tracked with
 * the other blocked integration tests in {@code docs/testing/payment-dispatch-integration-tests.md}.
 */
class DispatchCountQueryContractTest {

    private static String jpql() throws NoSuchMethodException {
        Method m = DispatchCountRepository.class.getMethod(
                "countByBatch", String.class, Collection.class);
        Query q = m.getAnnotation(Query.class);
        assertThat(q).as("countByBatch must carry an explicit @Query").isNotNull();
        assertThat(q.nativeQuery()).as("countByBatch is a native query").isTrue();
        return q.value().replaceAll("\\s+", " ").trim();
    }

    /** Whitespace-insensitive containment. */
    private static void assertContains(String sql, String needle, String because) {
        assertThat(sql.replace(" ", ""))
                .as(because)
                .contains(needle.replace(" ", ""));
    }

    private static void assertAbsent(String sql, String needle, String because) {
        assertThat(sql.replace(" ", ""))
                .as(because)
                .doesNotContain(needle.replace(" ", ""));
    }

    /* ─────────────── route selection comes from the master ─────────────── */

    @Test
    @DisplayName("the master SCDUL_PYMN_SUMR is joined — the route source must be available")
    void masterIsJoined() throws Exception {
        String sql = jpql();
        assertContains(sql, "JOIN SCDUL_PYMN_SUMR SPS",
                "the route + file-join keys come from the master; without this join the query can "
                        + "only see the detail's always-null shadow columns");
        assertContains(sql, "SPS.BIZ_KEY = SPMD.BIZ_KEY", "master join is tenant-qualified");
        assertContains(sql, "SPS.SCDUL_PYMN_NO = SPMD.SCDUL_PYMN_NO", "master join is key-qualified");
    }

    @Test
    @DisplayName("route is an EXISTS over the MASTER's file key (legacy R007:60-65 / :105-110)")
    void routeIsAnExistsOnTheMasterFileKey() throws Exception {
        String sql = jpql();
        assertContains(sql, "RTE.BIZ_KEY = SPS.BIZ_KEY", "route EXISTS keys on the master");
        assertContains(sql, "RTE.TRAN_DT = SPS.PAY_TRAN_DT", "route EXISTS keys on the master");
        assertContains(sql, "RTE.FILE_SEQ_NO = SPS.PAY_FILE_SEQ_NO", "route EXISTS keys on the master");
        // Legacy repeats the EXISTS once per aggregate; both counts must route identically.
        assertThat(Pattern.compile("EXISTS\\s*\\(").matcher(sql).results().count())
                .as("both succ_cnt and err_cnt must make the same route decision")
                .isEqualTo(2);
    }

    /**
     * The regression guard proper: the two always-null detail columns must not appear anywhere. If
     * either comes back, file-route batches silently return to reporting {@code 0 / 0}.
     */
    @Test
    @DisplayName("Q-1 guard: never route on detail PAY_FILE_SEQ_NO, never join on PAY_SEQ")
    void neverUsesTheAlwaysNullDetailColumns() throws Exception {
        String sql = jpql();
        assertAbsent(sql, "SPMD.PAY_FILE_SEQ_NO",
                "the detail's PAY_FILE_SEQ_NO is always NULL (executors null it deliberately, "
                        + "matching legacy data) — routing on it makes the file arm unreachable");
        assertAbsent(sql, "SPMD.PAY_SEQ",
                "the detail's PAY_SEQ is always NULL — joining on it never matches a "
                        + "TB_PAY_FILE_DATA row, so RS_CD is always absent");
    }

    /* ─────────────── join keys ─────────────── */

    @Test
    @DisplayName("file-route joins use the MASTER's file key, not the detail's")
    void fileJoinsUseTheMasterFileKey() throws Exception {
        String sql = jpql();
        assertContains(sql, "TPFM.TRAN_DT = SPS.PAY_TRAN_DT", "legacy R007:72 / :117");
        assertContains(sql, "TPFM.FILE_SEQ_NO = SPS.PAY_FILE_SEQ_NO", "legacy R007:73 / :118");
        assertContains(sql, "TPFD.FILE_SEQ_NO = SPS.PAY_FILE_SEQ_NO", "legacy R007:77 / :122");
        // The TPFD date, however, is the LINE's — legacy R007:76 / :121.
        assertContains(sql, "TPFD.TRAN_DT = SPMD.PAY_TRAN_DT", "legacy R007:76 / :121");
    }

    @Test
    @DisplayName("per-line RS_CD joins via CAST(TPFD.SEQ AS VARCHAR) = SPMD.PAY_TRAN_SEQ")
    void perLineRsCdJoinsOnTheVarcharCast() throws Exception {
        String sql = jpql();
        assertContains(sql, "CAST(TPFD.SEQ AS VARCHAR) = SPMD.PAY_TRAN_SEQ",
                "legacy R007:78 / :123 bridges numeric SEQ to the varchar PAY_TRAN_SEQ with a CAST; "
                        + "the same join appears in R006:153 for the detail page");
    }

    @Test
    @DisplayName("API-route join is on the line's own (PAY_TRAN_DT, PAY_TRAN_SEQ)")
    void apiJoinUsesTheLineShadowKeys() throws Exception {
        String sql = jpql();
        assertContains(sql, "TPT.TRAN_DT = SPMD.PAY_TRAN_DT", "legacy R007:95");
        assertContains(sql, "TPT.TRAN_SEQ = SPMD.PAY_TRAN_SEQ", "legacy R007:96");
    }

    /* ─────────────── the success / error truth table ─────────────── */

    /**
     * Each arm of the legacy truth table must be present verbatim. Expressed as data so the numeric
     * intent behind "success", "error", "mixed" and "zero-result" is recorded here and not only in
     * the audit note — see the class Javadoc's limitation paragraph.
     *
     * <table>
     *   <caption>Legacy classification</caption>
     *   <tr><th>Route</th><th>Outcome</th><th>Condition</th></tr>
     *   <tr><td>File</td><td>success</td><td>{@code TPFM='0'} AND {@code RS_CD='0000'}</td></tr>
     *   <tr><td>File</td><td>error</td><td>{@code TPFM='3'} OR ({@code TPFM='0'} AND {@code RS_CD<>'0000'})</td></tr>
     *   <tr><td>File</td><td>neither (in progress)</td><td>{@code TPFM} ∈ {'6','9'} — matches no arm</td></tr>
     *   <tr><td>API</td><td>success</td><td>{@code TPT='0'}</td></tr>
     *   <tr><td>API</td><td>error</td><td>{@code TPT='3'}</td></tr>
     *   <tr><td>API</td><td>neither</td><td>{@code TPT} ∈ {'6','9'} or no row</td></tr>
     * </table>
     */
    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "file success      | TPFM.PROC_STAT = '0' AND TRIM(COALESCE(TPFD.RS_CD, '')) = '0000'",
            "file error (fail) | TPFM.PROC_STAT = '3'",
            "file error (rc)   | TPFM.PROC_STAT = '0' AND TRIM(COALESCE(TPFD.RS_CD, '')) NOT IN ('0000')",
            "api success       | TPT.PROC_STAT = '0'",
            "api error         | TPT.PROC_STAT = '3'",
    })
    @DisplayName("every legacy classification arm is present verbatim")
    void everyClassificationArmIsPresent(String label, String condition) throws Exception {
        assertContains(jpql(), condition, label + " — legacy R007 arm must be reproduced verbatim");
    }

    /**
     * "Mixed" is not a separate arm — it is the natural consequence of summing the per-line arms.
     * Both aggregates must therefore be per-line {@code SUM(CASE …)} over the joined rows (so one
     * batch can report both a success and an error), never a whole-batch flag.
     */
    @Test
    @DisplayName("mixed batches work because both counts are per-line SUM(CASE ...) aggregates")
    void bothCountsArePerLineSums() throws Exception {
        String sql = jpql();
        assertThat(Pattern.compile("CAST\\s*\\(\\s*SUM\\s*\\(").matcher(sql).results().count())
                .as("succ_cnt and err_cnt are both CAST(SUM(...) AS BIGINT)")
                .isEqualTo(2);
        assertContains(sql, "AS succ_cnt", "projection alias bound by DispatchCountRow.getSuccCnt");
        assertContains(sql, "AS err_cnt", "projection alias bound by DispatchCountRow.getErrCnt");
        assertContains(sql, "GROUP BY SPMD.SCDUL_PYMN_NO",
                "one row per batch — the aggregation grain the caller maps by SCDUL_PYMN_NO");
    }

    /**
     * Zero-result behaviour: every {@code CASE} ends in an {@code ELSE 0}, so an in-progress line
     * (or one with no dispatch row at all) contributes 0 to both counts rather than making the whole
     * aggregate null. A batch with nothing settled therefore reports {@code 0 / 0} — which is correct
     * for in-progress, and is exactly the value the Q-1 bug produced for <i>settled</i> file batches.
     */
    @Test
    @DisplayName("zero-result: unmatched lines contribute 0, never NULL")
    void unmatchedLinesContributeZero() throws Exception {
        String sql = jpql();
        assertThat(Pattern.compile("ELSE\\s*0\\s*END").matcher(sql).results().count())
                .as("each inner arm and each outer route branch terminates in ELSE 0")
                .isGreaterThanOrEqualTo(4);
    }

    /* ─────────────── tenant scope ─────────────── */

    @Test
    @DisplayName("tenant filtering is preserved — bound parameter, every join BIZ_KEY-qualified")
    void tenantScopeIsPreserved() throws Exception {
        String sql = jpql();
        assertContains(sql, "WHERE SPMD.BIZ_KEY = :bizKey",
                "the batch set is tenant-scoped by a bound parameter, never interpolated");
        for (String join : new String[] {
                "SPS.BIZ_KEY = SPMD.BIZ_KEY",
                "TPFM.BIZ_KEY = SPMD.BIZ_KEY",
                "TPFD.BIZ_KEY = SPMD.BIZ_KEY",
                "TPT.BIZ_KEY = SPMD.BIZ_KEY",
                "RTE.BIZ_KEY = SPS.BIZ_KEY" }) {
            assertContains(sql, join, "every join must be BIZ_KEY-qualified: " + join);
        }
        assertThat(sql).as("no wildcard tenant matching").doesNotContain("LIKE");
        assertThat(sql).as("no wildcard tenant matching").doesNotContain("%");
    }

    /**
     * Multiple tenants may legitimately hold the same {@code FILE_SEQ_NO} (it comes from the
     * <b>global</b> {@code firm_file_seq_no} sequence) and the same {@code TRAN_SEQ} (global
     * {@code firm_seq_no}). Without {@code BIZ_KEY} on the dispatch joins, one tenant's batch would
     * pick up another tenant's dispatch rows. This is the assertion that makes the counts
     * tenant-safe, and it is separate from the {@code WHERE}-clause scope above.
     */
    @Test
    @DisplayName("multi-tenant: global sequences make BIZ_KEY on the dispatch joins load-bearing")
    void dispatchJoinsCannotCrossTenants() throws Exception {
        String sql = jpql();
        // Each dispatch join must carry BIZ_KEY *in addition to* its date/sequence keys.
        assertContains(sql, "TPFM.BIZ_KEY = SPMD.BIZ_KEY", "TB_PAY_FILE_MST");
        assertContains(sql, "TPFD.BIZ_KEY = SPMD.BIZ_KEY", "TB_PAY_FILE_DATA");
        assertContains(sql, "TPT.BIZ_KEY = SPMD.BIZ_KEY", "TB_PAY_TARGET");
        // ...and so must the route probe, or the route could be decided off another tenant's rows.
        assertContains(sql, "RTE.BIZ_KEY = SPS.BIZ_KEY", "route EXISTS probe");
    }

    /* ─────────────── shape / safety ─────────────── */

    @Test
    @DisplayName("read-only: a count query must never mutate")
    void isReadOnly() throws Exception {
        String sql = jpql().toUpperCase();
        assertThat(sql).startsWith("SELECT");
        for (String verb : new String[] {"INSERT ", "UPDATE ", "DELETE ", "MERGE ", "TRUNCATE "}) {
            assertThat(sql).as("must not contain %s", verb.trim()).doesNotContain(verb);
        }
    }

    /**
     * No row multiplication: each {@code LEFT JOIN} must be on the full primary key of its table, and
     * the master join on the master's PK. A join that matched more than one row would double-count
     * a line and silently inflate both chips.
     */
    @Test
    @DisplayName("no fan-out: every join is fully key-qualified")
    void joinsCannotFanOut() throws Exception {
        String sql = jpql();
        // TB_PAY_FILE_MST PK = (TRAN_DT, BIZ_KEY, FILE_SEQ_NO)
        assertContains(sql, "TPFM.BIZ_KEY", "TPFM full PK");
        assertContains(sql, "TPFM.TRAN_DT", "TPFM full PK");
        assertContains(sql, "TPFM.FILE_SEQ_NO", "TPFM full PK");
        // TB_PAY_FILE_DATA PK = (TRAN_DT, BIZ_KEY, FILE_SEQ_NO, SEQ)
        assertContains(sql, "TPFD.SEQ", "TPFD full PK includes SEQ");
        // TB_PAY_TARGET PK = (TRAN_DT, BIZ_KEY, TRAN_SEQ)
        assertContains(sql, "TPT.TRAN_SEQ", "TPT full PK includes TRAN_SEQ");
        // SCDUL_PYMN_SUMR PK = (BIZ_KEY, SCDUL_PYMN_NO)
        assertContains(sql, "SPS.SCDUL_PYMN_NO = SPMD.SCDUL_PYMN_NO", "master full PK");
    }

    /**
     * The master join must be an INNER join. A {@code LEFT JOIN} would let a detail row whose master
     * is missing fall through the route {@code EXISTS} as "not file route" and be counted on the API
     * arm — legacy's {@code FROM SCDUL_PYMN_SUMR SPS} makes the master mandatory.
     */
    @Test
    @DisplayName("the master join is INNER, not LEFT (legacy selects FROM the master)")
    void masterJoinIsInner() throws Exception {
        String sql = jpql();
        assertAbsent(sql, "LEFT JOIN SCDUL_PYMN_SUMR SPS",
                "legacy drives the whole expression from the master; a LEFT JOIN would let an "
                        + "orphaned detail row be classified on the wrong route");
    }
}
