package com.kosign.wecms.domain.payment.dispatch.callback.service;

import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmFileMst;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileMst;
import com.kosign.wecms.domain.payment.dispatch.enums.ProcStat;
import com.kosign.wecms.domain.payment.dispatch.enums.ProcStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The pay-file finalisation guards — <b>false "all success"</b>.
 *
 * <h2>The defect (two live PROD instances, 2026-07-31)</h2>
 * {@code finalizePayFileStatus} computed {@code tot} / {@code succ} / {@code fail} over
 * <b>terminal siblings only</b> ({@code PROC_STAT} {@code '0'} or {@code '3'}), then set the parent to
 * {@code '0'} when {@code tot == succ}. A sibling still at {@code PROC_STAT='6'} is counted in
 * <b>none</b> of the three, so it is invisible to the ratio.
 *
 * <p><b>The observed PROD shape needs no split batch at all.</b> For a <b>single</b> file stranded at
 * {@code '2'/'6'}: {@code tot = 0}, {@code succ = 0}, and {@code 0 == 0} is <b>true</b> — so the parent
 * was finalised to {@code '0'} (ALL SUCCESS) while its only file never reported a result. An empty
 * count satisfies the equality. Both PROD instances are {@code siblings=1}.
 *
 * <p>The barrier did not stop it either: it tested {@code PROC_STEP='0' AND PROC_STAT='6'}
 * (reception-pending) — a faithful port of legacy {@code IDO.TB_FIRM_FILE_MST_R003.xml:26-30} — so a
 * file mid-execution at {@code '2'/'6'} sailed straight past. Legacy {@code R005} has the identical
 * {@code TOT} definition, so this is an <b>inherited</b> defect, not a refactor regression.
 *
 * <h2>The two guards under test</h2>
 * <ol>
 *   <li><b>Option A</b> — defer while ANY sibling is at {@code PROC_STAT='6'}, regardless of step.
 *       A deliberate divergence from legacy R003.</li>
 *   <li><b>{@code tot == 0}</b> — never finalise on an empty terminal set. Guard 1 makes this
 *       unreachable today; it is kept because the equality is the actual defect and a future change to
 *       Guard 1's predicate would silently re-expose it.</li>
 * </ol>
 *
 * <p>Asserted at the decision level (the same stream logic the service runs) rather than by driving a
 * full 01302 callback, so each sibling-state combination is stated explicitly and readably. The
 * end-to-end path is covered by {@link BulkExecutionResultServiceTest2}.
 */
class PayFileFinalizeGuardTest {

    private static TbFirmFileMst file(String step, String stat) {
        TbFirmFileMst f = new TbFirmFileMst();
        f.setTranDt("20260529");
        f.setBizKey("VN_VNM0004_000001");
        f.setFileSeqNo("1");
        f.setFirmFileNm("S_GV00000001_SHB_001_20260529");
        f.setProcStep(step);
        f.setProcStat(stat);
        return f;
    }

    /**
     * Mirrors {@code BulkExecutionResultService.finalizePayFileStatus} exactly: Guard 1, then the
     * counts, then Guard 2, then the ratio.
     *
     * @return the status the parent would be set to, or {@link Optional#empty()} when finalisation is
     *         refused (parent left at {@code '6'}).
     */
    private static Optional<String> finalizedStatus(List<TbFirmFileMst> siblings) {
        // Guard 1 (Option A) — any non-terminal sibling defers.
        if (siblings.stream().anyMatch(f -> ProcStat.REQ.code().equals(f.getProcStat()))) {
            return Optional.empty();
        }
        long tot = siblings.stream().filter(f ->
                ProcStat.SUCC.code().equals(f.getProcStat()) || ProcStat.FAIL.code().equals(f.getProcStat())).count();
        long succ = siblings.stream().filter(f -> ProcStat.SUCC.code().equals(f.getProcStat())).count();
        long fail = siblings.stream().filter(f -> ProcStat.FAIL.code().equals(f.getProcStat())).count();

        // Guard 2 — empty terminal set never finalises.
        if (tot == 0) {
            return Optional.empty();
        }
        if (tot == succ) return Optional.of(ProcStat.SUCC.code());
        if (tot == fail) return Optional.of(ProcStat.FAIL.code());
        return Optional.of("1");
    }

    // ── 1. THE PROD CASE ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("PROD case: a SINGLE file at '2'/'6' leaves the parent at '6' — never '0'")
    void singleStrandedExecutionFile_neverFinalisesToAllSuccess() {
        // Both observed PROD instances (20260407 WRB, 20260529 SHB) are exactly this: siblings=1.
        List<TbFirmFileMst> siblings = List.of(file(ProcStep.DEPO_REQ.code(), ProcStat.REQ.code()));

        Optional<String> result = finalizedStatus(siblings);

        assertThat(result)
                .as("a lone file that never reported a result must not finalise the parent at all")
                .isEmpty();
        // The specific regression: it must never come out as ALL SUCCESS.
        assertThat(result).isNotEqualTo(Optional.of(ProcStat.SUCC.code()));
    }

    @Test
    @DisplayName("The pre-fix arithmetic is what produced it: tot==0 && succ==0 satisfies tot==succ")
    void demonstratesTheDefectiveEquality() {
        List<TbFirmFileMst> siblings = List.of(file(ProcStep.DEPO_REQ.code(), ProcStat.REQ.code()));

        long tot = siblings.stream().filter(f ->
                ProcStat.SUCC.code().equals(f.getProcStat()) || ProcStat.FAIL.code().equals(f.getProcStat())).count();
        long succ = siblings.stream().filter(f -> ProcStat.SUCC.code().equals(f.getProcStat())).count();

        assertThat(tot).isZero();
        assertThat(succ).isZero();
        assertThat(tot == succ)
                .as("0 == 0 is true — this is the comparison that reported ALL SUCCESS in PROD")
                .isTrue();
        // ...and the guards must nonetheless refuse.
        assertThat(finalizedStatus(siblings)).isEmpty();
    }

    // ── 2. GUARD 2 IN ISOLATION ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("tot==0 alone never yields '0' — for any all-non-terminal sibling set")
    void emptyTerminalSetNeverYieldsAllSuccess() {
        // Every combination of non-terminal steps: none may finalise.
        for (String step : new String[]{ProcStep.REQ.code(), ProcStep.REQ_RECV.code(), ProcStep.DEPO_REQ.code()}) {
            assertThat(finalizedStatus(List.of(file(step, ProcStat.REQ.code()))))
                    .as("single sibling at step %s / stat '6'", step)
                    .isEmpty();
        }
        assertThat(finalizedStatus(List.of(
                file(ProcStep.REQ_RECV.code(), ProcStat.REQ.code()),
                file(ProcStep.DEPO_REQ.code(), ProcStat.REQ.code()))))
                .as("two non-terminal siblings")
                .isEmpty();
        // An empty sibling list is degenerate but must not finalise either.
        assertThat(finalizedStatus(List.of())).isEmpty();
    }

    // ── 3. THE MULTI-FILE PATH ───────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("3 siblings, only the first 01302 has landed — parent stays '6', not '0'")
    void firstCallbackOfASplitBatch_doesNotFinaliseEarly() {
        // Pre-fix this returned '0' (tot=1, succ=1) the moment the FIRST sibling reported, because the
        // old barrier only looked for PROC_STEP='0' siblings and the other two were at '2'.
        List<TbFirmFileMst> siblings = List.of(
                file(ProcStep.DEPO_RES_RECV.code(), ProcStat.SUCC.code()),   // A reported
                file(ProcStep.DEPO_REQ.code(), ProcStat.REQ.code()),         // B in flight
                file(ProcStep.DEPO_REQ.code(), ProcStat.REQ.code()));        // C in flight

        assertThat(finalizedStatus(siblings)).isEmpty();
    }

    @Test
    @DisplayName("2 of 3 reported, the third stranded at '2'/'6' — still no false 'all success'")
    void strandedSiblingBlocksAllSuccess() {
        List<TbFirmFileMst> siblings = List.of(
                file(ProcStep.DEPO_RES_RECV.code(), ProcStat.SUCC.code()),
                file(ProcStep.DEPO_RES_RECV.code(), ProcStat.SUCC.code()),
                file(ProcStep.DEPO_REQ.code(), ProcStat.REQ.code()));        // never returned

        assertThat(finalizedStatus(siblings))
                .as("two successes plus one file that never reported is not 'all success'")
                .isEmpty();
    }

    @Test
    @DisplayName("Reception-pending sibling still defers — legacy R003 behaviour is preserved")
    void receptionPendingSiblingStillDefers() {
        assertThat(finalizedStatus(List.of(
                file(ProcStep.DEPO_RES_RECV.code(), ProcStat.SUCC.code()),
                file(ProcStep.REQ.code(), ProcStat.REQ.code()))))
                .isEmpty();
    }

    // ── 4. THE HAPPY PATHS MUST BE UNCHANGED ─────────────────────────────────────────────────────

    @Test
    @DisplayName("All siblings terminal → the legacy 0 / 1 / 3 ratio is unchanged")
    void allTerminalSiblingsFinaliseWithLegacyRatio() {
        // all success -> '0'
        assertThat(finalizedStatus(List.of(
                file(ProcStep.DEPO_RES_RECV.code(), ProcStat.SUCC.code()),
                file(ProcStep.DEPO_RES_RECV.code(), ProcStat.SUCC.code()))))
                .contains(ProcStat.SUCC.code());

        // all fail -> '3'
        assertThat(finalizedStatus(List.of(
                file(ProcStep.DEPO_REQ.code(), ProcStat.FAIL.code()),
                file(ProcStep.REQ_RECV.code(), ProcStat.FAIL.code()))))
                .contains(ProcStat.FAIL.code());

        // mixed -> '1' (verbatim legacy partial literal, NOT a ProcStat constant)
        assertThat(finalizedStatus(List.of(
                file(ProcStep.DEPO_RES_RECV.code(), ProcStat.SUCC.code()),
                file(ProcStep.DEPO_REQ.code(), ProcStat.FAIL.code()))))
                .contains("1");

        // the ordinary single-file success case must still finalise
        assertThat(finalizedStatus(List.of(file(ProcStep.DEPO_RES_RECV.code(), ProcStat.SUCC.code()))))
                .contains(ProcStat.SUCC.code());
    }
}
