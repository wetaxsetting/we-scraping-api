package com.kosign.wecms.domain.payment.dispatch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * <b>Q-1 numeric regression</b> — proves the corrected {@code countByBatch} classification produces
 * the same success / error counts as legacy {@code IDO.SCDUL_PYMN_SUMR_R007.xml}, and that the
 * pre-fix classification did not.
 *
 * <h2>What this is, and what it is not</h2>
 *
 * {@code countByBatch} is a native query over four tables, three of them not entity-mapped, and the
 * project ships no Testcontainers and no embedded database by policy ({@code CLAUDE.md}). So this
 * test does <b>not</b> execute SQL. Instead it encodes the three classifications as executable
 * models over the same fixture rows:
 *
 * <ul>
 *   <li>{@link Classifier#LEGACY} — legacy {@code R007} (`:58-144`), the reference;</li>
 *   <li>{@link Classifier#FIXED} — the corrected refactor query (master routing +
 *       {@code CAST(TPFD.SEQ AS VARCHAR) = SPMD.PAY_TRAN_SEQ});</li>
 *   <li>{@link Classifier#PRE_FIX} — the query as it was (detail routing + {@code TPFD.SEQ =
 *       SPMD.PAY_SEQ}), kept so the regression is demonstrated rather than asserted.</li>
 * </ul>
 *
 * Every case asserts {@code FIXED == LEGACY}. The file-route cases additionally assert
 * {@code PRE_FIX != LEGACY} — if a future edit reverts the routing, that assertion is what fails and
 * names the reason.
 *
 * <p>The SQL text itself is pinned separately by {@link DispatchCountQueryContractTest}; the two
 * together cover "the SQL says the legacy thing" (there) and "the legacy thing yields the legacy
 * numbers" (here). Executing the real query against PostgreSQL remains outstanding and is tracked
 * with the other DB-blocked tests in {@code docs/testing/payment-dispatch-integration-tests.md}. The
 * out-of-band verification that produced the batch-372 numbers below is recorded in
 * {@code docs/audits/dispatch-repository-query-reverification.md} §5.
 *
 * <h2>Fixture realism</h2>
 *
 * Every fixture reproduces the shape the dispatch executors actually write, which is also the shape
 * JEX DEV holds: {@code detail.payTranSeq} + {@code master.payFileSeqNo} populated,
 * {@code detail.payFileSeqNo} and {@code detail.paySeq} <b>null</b>. That is precisely why the
 * pre-fix routing collapsed.
 */
class DispatchCountLegacyParityRegressionTest {

    private static final String BIZ = "VN_VNM0004_000001";
    private static final String OTHER_BIZ = "VN_VNM0004_000014";
    private static final String TRAN_DT = "20260107";

    /* ══════════════════════════ row models ══════════════════════════ */

    /** {@code SCDUL_PYMN_SUMR} — only the columns the count expression reads. */
    private record Master(String bizKey, String scdulPymnNo, String payTranDt, String payFileSeqNo) {}

    /**
     * {@code SCDUL_PYMN_SUMR_DETL}. {@code payFileSeqNo} / {@code paySeq} are modelled because the
     * pre-fix query read them — they are null on every realistic row.
     */
    private record Detail(String bizKey, String scdulPymnNo, String detlNo,
                          String payTranDt, String payTranSeq,
                          String payFileSeqNo, Long paySeq) {
        static Detail dispatched(String detlNo, String payTranSeq) {
            return new Detail(BIZ, "372", detlNo, TRAN_DT, payTranSeq, null, null);
        }
        static Detail of(String bizKey, String batch, String detlNo, String payTranSeq) {
            return new Detail(bizKey, batch, detlNo, TRAN_DT, payTranSeq, null, null);
        }
    }

    private record PayFileMst(String bizKey, String tranDt, String fileSeqNo, String procStat) {}
    private record PayFileData(String bizKey, String tranDt, String fileSeqNo, long seq, String rsCd) {}
    private record PayTarget(String bizKey, String tranDt, String tranSeq, String procStat) {}

    /** The four-table fixture one classification runs against. */
    private record Db(List<PayFileMst> fileMst, List<PayFileData> fileData, List<PayTarget> targets) {
        static Db of(List<PayFileMst> m, List<PayFileData> d, List<PayTarget> t) { return new Db(m, d, t); }
    }

    /* ══════════════════════════ the three classifications ══════════════════════════ */

    private enum Classifier { LEGACY, FIXED, PRE_FIX }

    /** {@code TRIM(COALESCE(RS_CD,''))} — legacy's exact normalisation. */
    private static String trimCoalesce(String rsCd) {
        return rsCd == null ? "" : rsCd.trim();
    }

    private static PayFileMst findMst(Db db, String bizKey, String tranDt, String fileSeqNo) {
        if (tranDt == null || fileSeqNo == null) return null;   // SQL NULL never equals anything
        return db.fileMst().stream()
                .filter(m -> m.bizKey().equals(bizKey)
                        && m.tranDt().equals(tranDt)
                        && m.fileSeqNo().equals(fileSeqNo))
                .findFirst().orElse(null);
    }

    /** The join legacy writes as {@code CAST(TPFD.SEQ AS VARCHAR) = SPMD.PAY_TRAN_SEQ}. */
    private static PayFileData findDataByVarcharCast(Db db, String bizKey, String tranDt,
                                                    String fileSeqNo, String payTranSeq) {
        if (tranDt == null || fileSeqNo == null || payTranSeq == null) return null;
        return db.fileData().stream()
                .filter(d -> d.bizKey().equals(bizKey)
                        && d.tranDt().equals(tranDt)
                        && d.fileSeqNo().equals(fileSeqNo)
                        && String.valueOf(d.seq()).equals(payTranSeq))
                .findFirst().orElse(null);
    }

    /** The pre-fix join: {@code TPFD.SEQ = SPMD.PAY_SEQ} — numeric, against an always-null column. */
    private static PayFileData findDataByPaySeq(Db db, String bizKey, String tranDt,
                                                String fileSeqNo, Long paySeq) {
        if (tranDt == null || fileSeqNo == null || paySeq == null) return null;
        return db.fileData().stream()
                .filter(d -> d.bizKey().equals(bizKey)
                        && d.tranDt().equals(tranDt)
                        && d.fileSeqNo().equals(fileSeqNo)
                        && d.seq() == paySeq)
                .findFirst().orElse(null);
    }

    private static PayTarget findTarget(Db db, String bizKey, String tranDt, String tranSeq) {
        if (tranDt == null || tranSeq == null) return null;
        return db.targets().stream()
                .filter(t -> t.bizKey().equals(bizKey)
                        && t.tranDt().equals(tranDt)
                        && t.tranSeq().equals(tranSeq))
                .findFirst().orElse(null);
    }

    /** Legacy route probe — {@code EXISTS} over the MASTER's file key, no per-line predicate. */
    private static boolean masterHasFileData(Db db, Master m) {
        if (m.payTranDt() == null || m.payFileSeqNo() == null) return false;
        return db.fileData().stream().anyMatch(d -> d.bizKey().equals(m.bizKey())
                && d.tranDt().equals(m.payTranDt())
                && d.fileSeqNo().equals(m.payFileSeqNo()));
    }

    /**
     * Returns {@code [succ, err]} for one batch under the requested classification.
     *
     * <p>{@code LEGACY} and {@code FIXED} are written out separately rather than delegating, so a
     * future divergence between them shows up as a real difference here instead of being hidden
     * behind a shared helper.
     */
    private static long[] counts(Classifier which, Master master, List<Detail> allDetails, Db db) {
        List<Detail> lines = allDetails.stream()
                .filter(d -> d.bizKey().equals(master.bizKey())
                        && d.scdulPymnNo().equals(master.scdulPymnNo()))
                .toList();

        long succ = 0;
        long err = 0;

        for (Detail d : lines) {
            boolean fileRoute = switch (which) {
                // Legacy + fixed: EXISTS over the master's file key (R007:60-65 / :105-110).
                case LEGACY, FIXED -> masterHasFileData(db, master);
                // Pre-fix: per-line, on the detail's always-null PAY_FILE_SEQ_NO.
                case PRE_FIX -> d.payFileSeqNo() != null;
            };

            if (fileRoute) {
                PayFileMst mst = switch (which) {
                    case LEGACY, FIXED ->
                            findMst(db, d.bizKey(), master.payTranDt(), master.payFileSeqNo());
                    case PRE_FIX ->
                            findMst(db, d.bizKey(), d.payTranDt(), d.payFileSeqNo());
                };
                PayFileData fd = switch (which) {
                    case LEGACY, FIXED ->
                            findDataByVarcharCast(db, d.bizKey(), d.payTranDt(),
                                    master.payFileSeqNo(), d.payTranSeq());
                    case PRE_FIX ->
                            findDataByPaySeq(db, d.bizKey(), d.payTranDt(),
                                    d.payFileSeqNo(), d.paySeq());
                };
                String stat = mst == null ? null : mst.procStat();
                String rc = trimCoalesce(fd == null ? null : fd.rsCd());

                if ("0".equals(stat) && "0000".equals(rc)) {
                    succ++;
                } else if ("3".equals(stat) || ("0".equals(stat) && !"0000".equals(rc))) {
                    err++;
                }
                // else: in-progress ('6'/'9') or no row — contributes 0 to both (ELSE 0).
            } else {
                PayTarget t = findTarget(db, d.bizKey(), d.payTranDt(), d.payTranSeq());
                String stat = t == null ? null : t.procStat();
                if ("0".equals(stat)) {
                    succ++;
                } else if ("3".equals(stat)) {
                    err++;
                }
            }
        }
        return new long[] {succ, err};
    }

    /* ══════════════════════════ assertions ══════════════════════════ */

    /** {@code FIXED} must equal {@code LEGACY} for every shape. */
    private static void assertMatchesLegacy(String what, Master m, List<Detail> details, Db db,
                                            long expectedSucc, long expectedErr) {
        long[] legacy = counts(Classifier.LEGACY, m, details, db);
        long[] fixed = counts(Classifier.FIXED, m, details, db);
        assertArrayEquals(new long[] {expectedSucc, expectedErr}, legacy,
                what + " — legacy R007 reference counts");
        assertArrayEquals(legacy, fixed,
                what + " — the corrected query must equal legacy (got succ=" + fixed[0]
                        + " err=" + fixed[1] + ", legacy succ=" + legacy[0] + " err=" + legacy[1] + ")");
    }

    /** For file-route shapes, the pre-fix classification must be demonstrably wrong. */
    private static void assertPreFixWasBroken(String what, Master m, List<Detail> details, Db db) {
        long[] legacy = counts(Classifier.LEGACY, m, details, db);
        long[] preFix = counts(Classifier.PRE_FIX, m, details, db);
        assertFalse(legacy[0] == preFix[0] && legacy[1] == preFix[1],
                what + " — the pre-fix routing must NOT match legacy, otherwise this regression "
                        + "test proves nothing (both reported succ=" + preFix[0] + " err=" + preFix[1] + ")");
        assertArrayEquals(new long[] {0, 0}, preFix,
                what + " — the pre-fix query reported 0/0 for file-route batches");
    }

    /* ══════════════════════════ file route ══════════════════════════ */

    @Nested
    @DisplayName("file route (master carries PAY_FILE_SEQ_NO)")
    class FileRoute {

        private final Master master = new Master(BIZ, "372", TRAN_DT, "9001");

        @Test
        @DisplayName("batch 372: one settled success -> 1/0 (legacy), pre-fix said 0/0")
        void batch372_oneSuccess() {
            // The real DEV shape: legacy reported succ=1 err=0, the refactor reported 0/0.
            List<Detail> details = List.of(Detail.dispatched("1", "1"));
            Db db = Db.of(
                    List.of(new PayFileMst(BIZ, TRAN_DT, "9001", "0")),
                    List.of(new PayFileData(BIZ, TRAN_DT, "9001", 1L, "0000")),
                    List.of());

            assertMatchesLegacy("batch 372", master, details, db, 1, 0);
            assertPreFixWasBroken("batch 372", master, details, db);
        }

        @Test
        @DisplayName("all lines failed by a rejected file -> 0/N (the DEV 0/1, 0/2, 0/3, 0/4 shapes)")
        void allFailed_viaFileMstFail() {
            List<Detail> details = List.of(
                    Detail.dispatched("1", "1"), Detail.dispatched("2", "2"),
                    Detail.dispatched("3", "3"));
            Db db = Db.of(
                    List.of(new PayFileMst(BIZ, TRAN_DT, "9001", "3")),   // whole file rejected
                    List.of(new PayFileData(BIZ, TRAN_DT, "9001", 1L, ""),
                            new PayFileData(BIZ, TRAN_DT, "9001", 2L, ""),
                            new PayFileData(BIZ, TRAN_DT, "9001", 3L, "")),
                    List.of());

            assertMatchesLegacy("file FAIL", master, details, db, 0, 3);
            assertPreFixWasBroken("file FAIL", master, details, db);
        }

        @Test
        @DisplayName("settled file, per-line RS_CD decides -> mixed 2/2")
        void mixed_perLineRsCd() {
            List<Detail> details = List.of(
                    Detail.dispatched("1", "1"), Detail.dispatched("2", "2"),
                    Detail.dispatched("3", "3"), Detail.dispatched("4", "4"));
            Db db = Db.of(
                    List.of(new PayFileMst(BIZ, TRAN_DT, "9001", "0")),   // file settled
                    List.of(new PayFileData(BIZ, TRAN_DT, "9001", 1L, "0000"),
                            new PayFileData(BIZ, TRAN_DT, "9001", 2L, "E015"),
                            new PayFileData(BIZ, TRAN_DT, "9001", 3L, "0000"),
                            new PayFileData(BIZ, TRAN_DT, "9001", 4L, "9999")),
                    List.of());

            assertMatchesLegacy("mixed", master, details, db, 2, 2);
            assertPreFixWasBroken("mixed", master, details, db);
        }

        @Test
        @DisplayName("settled file with a blank RS_CD counts as an error (TRIM(COALESCE(..,'')) <> '0000')")
        void settledFile_blankRsCd_isError() {
            List<Detail> details = List.of(Detail.dispatched("1", "1"), Detail.dispatched("2", "2"));
            Db db = Db.of(
                    List.of(new PayFileMst(BIZ, TRAN_DT, "9001", "0")),
                    List.of(new PayFileData(BIZ, TRAN_DT, "9001", 1L, "   "),  // trims to ""
                            new PayFileData(BIZ, TRAN_DT, "9001", 2L, null)),  // coalesces to ""
                    List.of());

            assertMatchesLegacy("blank RS_CD", master, details, db, 0, 2);
        }

        @Test
        @DisplayName("in-progress file -> 0/0 (zero result, and legitimately so)")
        void inProgressFile_isZeroZero() {
            List<Detail> details = List.of(Detail.dispatched("1", "1"), Detail.dispatched("2", "2"));
            Db db = Db.of(
                    List.of(new PayFileMst(BIZ, TRAN_DT, "9001", "6")),   // still in flight
                    List.of(new PayFileData(BIZ, TRAN_DT, "9001", 1L, ""),
                            new PayFileData(BIZ, TRAN_DT, "9001", 2L, "")),
                    List.of());

            // Both classifications say 0/0 here — which is exactly why the Q-1 bug was invisible:
            // a genuinely in-progress batch and a broken settled batch looked identical.
            assertMatchesLegacy("in progress", master, details, db, 0, 0);
            long[] preFix = counts(Classifier.PRE_FIX, master, details, db);
            assertArrayEquals(new long[] {0, 0}, preFix,
                    "the pre-fix query agreed here — the collision that hid the defect");
        }

        @Test
        @DisplayName("the varchar CAST is what matches: SEQ 1 joins PAY_TRAN_SEQ \"1\", not \"01\"")
        void varcharCastSemantics() {
            List<Detail> details = List.of(
                    Detail.dispatched("1", "1"),     // matches CAST(1 AS VARCHAR)
                    Detail.dispatched("2", "01"));   // does not
            Db db = Db.of(
                    List.of(new PayFileMst(BIZ, TRAN_DT, "9001", "0")),
                    List.of(new PayFileData(BIZ, TRAN_DT, "9001", 1L, "0000")),
                    List.of());

            // Line 2 finds no TPFD row -> RS_CD coalesces to "" -> settled file + non-0000 -> error.
            assertMatchesLegacy("varchar cast", master, details, db, 1, 1);
        }
    }

    /* ══════════════════════════ API route ══════════════════════════ */

    @Nested
    @DisplayName("API route (master has no PAY_FILE_SEQ_NO)")
    class ApiRoute {

        private final Master master = new Master(BIZ, "500", TRAN_DT, null);

        private List<Detail> threeLines() {
            return List.of(
                    Detail.of(BIZ, "500", "1", "7001"),
                    Detail.of(BIZ, "500", "2", "7002"),
                    Detail.of(BIZ, "500", "3", "7003"));
        }

        @Test
        @DisplayName("all succeeded -> 3/0")
        void allSuccess() {
            Db db = Db.of(List.of(), List.of(), List.of(
                    new PayTarget(BIZ, TRAN_DT, "7001", "0"),
                    new PayTarget(BIZ, TRAN_DT, "7002", "0"),
                    new PayTarget(BIZ, TRAN_DT, "7003", "0")));
            assertMatchesLegacy("api all success", master, threeLines(), db, 3, 0);
        }

        @Test
        @DisplayName("all failed -> 0/3")
        void allError() {
            Db db = Db.of(List.of(), List.of(), List.of(
                    new PayTarget(BIZ, TRAN_DT, "7001", "3"),
                    new PayTarget(BIZ, TRAN_DT, "7002", "3"),
                    new PayTarget(BIZ, TRAN_DT, "7003", "3")));
            assertMatchesLegacy("api all error", master, threeLines(), db, 0, 3);
        }

        @Test
        @DisplayName("mixed success / error / in-progress -> 1/1")
        void mixed() {
            Db db = Db.of(List.of(), List.of(), List.of(
                    new PayTarget(BIZ, TRAN_DT, "7001", "0"),
                    new PayTarget(BIZ, TRAN_DT, "7002", "3"),
                    new PayTarget(BIZ, TRAN_DT, "7003", "6")));   // in progress -> neither
            assertMatchesLegacy("api mixed", master, threeLines(), db, 1, 1);
        }

        @Test
        @DisplayName("no dispatch rows at all -> 0/0")
        void zeroResult() {
            assertMatchesLegacy("api zero", master, threeLines(), Db.of(List.of(), List.of(), List.of()), 0, 0);
        }

        /** The API route was already correct — the fix must not have disturbed it. */
        @Test
        @DisplayName("the API route is unaffected by the fix (pre-fix agreed here)")
        void apiRouteUnchangedByTheFix() {
            Db db = Db.of(List.of(), List.of(), List.of(
                    new PayTarget(BIZ, TRAN_DT, "7001", "0"),
                    new PayTarget(BIZ, TRAN_DT, "7002", "3"),
                    new PayTarget(BIZ, TRAN_DT, "7003", "0")));
            long[] legacy = counts(Classifier.LEGACY, master, threeLines(), db);
            long[] fixed = counts(Classifier.FIXED, master, threeLines(), db);
            long[] preFix = counts(Classifier.PRE_FIX, master, threeLines(), db);
            assertArrayEquals(legacy, fixed);
            assertArrayEquals(legacy, preFix, "the API arm was never broken — the fix is file-route only");
        }
    }

    /* ══════════════════════════ multiple tenants ══════════════════════════ */

    @Nested
    @DisplayName("multiple tenants")
    class MultiTenant {

        /**
         * {@code FILE_SEQ_NO} comes from the <b>global</b> {@code firm_file_seq_no} sequence, so two
         * tenants can hold the same value on the same date. Without {@code BIZ_KEY} on the dispatch
         * joins, tenant A's batch would read tenant B's {@code TB_PAY_FILE_MST} / {@code _DATA} rows.
         */
        @Test
        @DisplayName("same FILE_SEQ_NO in two tenants: each batch reads only its own rows")
        void sameFileSeqNoAcrossTenants_doesNotCross() {
            Master a = new Master(BIZ, "372", TRAN_DT, "9001");
            Master b = new Master(OTHER_BIZ, "372", TRAN_DT, "9001");   // same batch no AND file seq
            List<Detail> details = List.of(
                    Detail.of(BIZ, "372", "1", "1"),
                    Detail.of(OTHER_BIZ, "372", "1", "1"));
            Db db = Db.of(
                    List.of(new PayFileMst(BIZ, TRAN_DT, "9001", "0"),          // A settled ok
                            new PayFileMst(OTHER_BIZ, TRAN_DT, "9001", "3")),   // B rejected
                    List.of(new PayFileData(BIZ, TRAN_DT, "9001", 1L, "0000"),
                            new PayFileData(OTHER_BIZ, TRAN_DT, "9001", 1L, "E015")),
                    List.of());

            assertMatchesLegacy("tenant A", a, details, db, 1, 0);
            assertMatchesLegacy("tenant B", b, details, db, 0, 1);
        }

        /**
         * {@code TRAN_SEQ} likewise comes from the global {@code firm_seq_no} sequence, so the API
         * arm needs the same protection.
         */
        @Test
        @DisplayName("same TRAN_SEQ in two tenants: API counts stay separate")
        void sameTranSeqAcrossTenants_doesNotCross() {
            Master a = new Master(BIZ, "500", TRAN_DT, null);
            Master b = new Master(OTHER_BIZ, "500", TRAN_DT, null);
            List<Detail> details = List.of(
                    Detail.of(BIZ, "500", "1", "7001"),
                    Detail.of(OTHER_BIZ, "500", "1", "7001"));
            Db db = Db.of(List.of(), List.of(), List.of(
                    new PayTarget(BIZ, TRAN_DT, "7001", "0"),
                    new PayTarget(OTHER_BIZ, TRAN_DT, "7001", "3")));

            assertMatchesLegacy("tenant A api", a, details, db, 1, 0);
            assertMatchesLegacy("tenant B api", b, details, db, 0, 1);
        }

        /**
         * A tenant whose own batch has no dispatch rows must report {@code 0/0} even when another
         * tenant holds settled rows under the identical keys — the tenant-crossing failure mode would
         * show up here as a borrowed non-zero count.
         */
        @Test
        @DisplayName("a tenant with no rows of its own reports 0/0, never a neighbour's count")
        void emptyTenant_doesNotBorrowCounts() {
            Master empty = new Master(OTHER_BIZ, "372", TRAN_DT, "9001");
            List<Detail> details = List.of(
                    Detail.of(BIZ, "372", "1", "1"),
                    Detail.of(OTHER_BIZ, "372", "1", "1"));
            Db db = Db.of(
                    List.of(new PayFileMst(BIZ, TRAN_DT, "9001", "0")),
                    List.of(new PayFileData(BIZ, TRAN_DT, "9001", 1L, "0000")),
                    List.of());

            // The route probe is also tenant-scoped, so OTHER_BIZ does not even see a file route.
            assertMatchesLegacy("empty tenant", empty, details, db, 0, 0);
        }
    }

    /* ══════════════════════════ aggregation grain ══════════════════════════ */

    /**
     * The query groups by {@code SCDUL_PYMN_NO}, and the caller maps the result by that key. Two
     * batches in one tenant must be counted independently — a leak would show up as one batch's lines
     * inflating the other's chips.
     */
    @Test
    @DisplayName("two batches in one tenant are counted independently")
    void twoBatchesInOneTenant_areIndependent() {
        Master fileBatch = new Master(BIZ, "372", TRAN_DT, "9001");
        Master apiBatch = new Master(BIZ, "500", TRAN_DT, null);
        List<Detail> details = new ArrayList<>();
        details.add(Detail.of(BIZ, "372", "1", "1"));
        details.add(Detail.of(BIZ, "372", "2", "2"));
        details.add(Detail.of(BIZ, "500", "1", "7001"));
        Db db = Db.of(
                List.of(new PayFileMst(BIZ, TRAN_DT, "9001", "0")),
                List.of(new PayFileData(BIZ, TRAN_DT, "9001", 1L, "0000"),
                        new PayFileData(BIZ, TRAN_DT, "9001", 2L, "E015")),
                List.of(new PayTarget(BIZ, TRAN_DT, "7001", "0")));

        assertMatchesLegacy("file batch", fileBatch, details, db, 1, 1);
        assertMatchesLegacy("api batch", apiBatch, details, db, 1, 0);
    }

    /**
     * Sanity check on the models themselves: {@code LEGACY} and {@code PRE_FIX} must genuinely differ
     * in construction, otherwise every {@code assertPreFixWasBroken} above would be vacuous.
     */
    @Test
    @DisplayName("the three classifications are genuinely distinct models")
    void modelsAreDistinct() {
        Master m = new Master(BIZ, "372", TRAN_DT, "9001");
        List<Detail> details = List.of(Detail.dispatched("1", "1"));
        Db db = Db.of(
                List.of(new PayFileMst(BIZ, TRAN_DT, "9001", "0")),
                List.of(new PayFileData(BIZ, TRAN_DT, "9001", 1L, "0000")),
                List.of());

        assertEquals(1L, counts(Classifier.LEGACY, m, details, db)[0]);
        assertEquals(1L, counts(Classifier.FIXED, m, details, db)[0]);
        assertEquals(0L, counts(Classifier.PRE_FIX, m, details, db)[0]);
    }
}
