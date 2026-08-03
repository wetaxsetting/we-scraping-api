package com.kosign.wecms.domain.payment.dispatch.scheduler;

import com.kosign.wecms.domain.payment.dispatch.service.DispatchPacer;
import com.kosign.wecms.domain.payment.dispatch.service.ResultCheckService;
import com.kosign.wecms.domain.payment.dispatch.van.client.EfsProperties;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * C-11 — the {@code ResultCheckJob} ShedLock ceiling must cover the worst-case duration of one
 * <b>valid</b> pass.
 *
 * <h2>Why this is a test and not just a comment</h2>
 * {@code lockAtMostFor} and the values that bound a pass live in different files, and the pairing has
 * been wrong more than once. If the lock expires mid-pass, ShedLock lets a second instance start on
 * {@code PROC_STAT='6'} rows the first is still processing — concurrent finalization of real payments.
 *
 * <p>The failure mode is silent and load-dependent: it needs a slow gateway to surface, so it will not
 * show up in any functional test, in DEV, or in a healthy production. So the <b>relationship</b> is
 * asserted here, computed from the real constants rather than restated as a literal. Change the pass
 * deadline, the EFS timeouts, or the pacing, and this test tells you the ceiling needs recomputing.
 *
 * <h2>The relationship changed (2026-07-31) — deadline, not batch size</h2>
 * This used to assert {@code ceiling > batchSize × worstCaseRow}, which was correct while nothing
 * stopped a pass early. {@code ResultCheckService.passDeadline} breaks the loop at the top of each row
 * iteration, so the bound is now:
 *
 * <pre>  worst valid pass  =  passDeadline  +  ONE in-flight worst-case row</pre>
 *
 * <p>The {@code + one row} term is not slack: the deadline is checked <em>before</em> a row is touched,
 * never inside it, so a row that has already issued its 203 always runs to completion. A test that
 * omitted that term would pass while the real overrun exceeded the ceiling.
 *
 * <p>Note what is deliberately NOT asserted: the literal {@code "PT30M"}. Pinning the literal would
 * make this a change-detector; pinning the inequality makes it a safety property.
 */
class ResultCheckLockCeilingTest {

    /**
     * Worst-case gateway + pacing cost of one row, from the real defaults.
     *
     * <p>{@code PER_DELAY_WOORI}, {@code DEFAULT_BATCH_SIZE} and {@code DEFAULT_PASS_DEADLINE} are
     * package-private in {@code ...dispatch.service} and are read reflectively rather than widened to
     * {@code public} — a test's need for a constant is not a reason to enlarge a production API.
     */
    private static Duration worstCasePerRow() {
        EfsProperties efs = new EfsProperties();
        // A timed-out row takes the comms-fail branch, and THAT branch paces — so the worst case pays
        // connect + read + the slowest bank's delay, not connect + read alone.
        long woori = ((Number) ReflectionTestUtils.getField(DispatchPacer.class, "PER_DELAY_WOORI"))
                .longValue();
        return Duration.ofMillis(efs.getConnectTimeoutMs())
                .plusMillis(efs.getReadTimeoutMs())
                .plusMillis(woori);
    }

    /** The pass budget + the one row that may already be in flight when it expires. */
    private static Duration worstCasePass() throws Exception {
        return configuredDefaultPassDeadline().plus(worstCasePerRow());
    }

    private static int defaultBatchSizeConstant() {
        return ((Number) ReflectionTestUtils.getField(ResultCheckService.class, "DEFAULT_BATCH_SIZE"))
                .intValue();
    }

    private static Duration defaultPassDeadlineConstant() {
        return (Duration) ReflectionTestUtils.getField(ResultCheckService.class, "DEFAULT_PASS_DEADLINE");
    }

    private static Duration configuredDefaultPassDeadline() throws NoSuchFieldException {
        // Same reasoning as configuredDefaultBatchSize: the @Value default is what actually applies in
        // every environment whose WECMS_DISPATCH_RESULT_CHECK_PASS_DEADLINE is unset — all of them.
        Value value = ResultCheckService.class.getDeclaredField("passDeadline").getAnnotation(Value.class);
        assertThat(value).as("passDeadline must be @Value-bound").isNotNull();
        String expr = value.value();                       // ${wecms.dispatch.result-check.pass-deadline:PT10M}
        String fallback = expr.substring(expr.lastIndexOf(':') + 1, expr.length() - 1);
        return Duration.parse(fallback);
    }

    private static Duration lockAtMostFor() throws NoSuchMethodException {
        Method run = ResultCheckJob.class.getMethod("run");
        SchedulerLock lock = run.getAnnotation(SchedulerLock.class);
        assertThat(lock).as("ResultCheckJob.run must carry @SchedulerLock").isNotNull();
        return Duration.parse(lock.lockAtMostFor());
    }

    private static int configuredDefaultBatchSize() throws NoSuchFieldException {
        // The @Value default is the value that actually applies in every environment whose
        // WECMS_DISPATCH_RESULT_CHECK_BATCH_SIZE is unset, which is all of them today.
        Value value = ResultCheckService.class.getDeclaredField("batchSize").getAnnotation(Value.class);
        assertThat(value).as("batchSize must be @Value-bound").isNotNull();
        String expr = value.value();                       // ${wecms.dispatch.result-check.batch-size:100}
        String fallback = expr.substring(expr.lastIndexOf(':') + 1, expr.length() - 1);
        return Integer.parseInt(fallback);
    }

    @Test
    @DisplayName("lockAtMostFor covers the pass deadline plus one in-flight worst-case row")
    void lockCeilingCoversWorstCasePass() throws Exception {
        Duration worstPass = worstCasePass();
        Duration ceiling = lockAtMostFor();

        assertThat(ceiling)
                .as("lockAtMostFor (%s) must exceed the worst valid pass: pass-deadline %s + one "
                                + "in-flight row %s = %s. Raise lockAtMostFor on ResultCheckJob.run, or "
                                + "lower wecms.dispatch.result-check.pass-deadline.",
                        ceiling, configuredDefaultPassDeadline(), worstCasePerRow(), worstPass)
                .isGreaterThan(worstPass);
    }

    @Test
    @DisplayName("the ceiling keeps at least 20% headroom over the worst-case pass")
    void lockCeilingKeepsHeadroom() throws Exception {
        // The worst case counts the budget, the gateway timeouts and pacing. Per-row DB work (a
        // wb_get_next_val call, three TB_FIRM_TRAN saves, one REQUIRES_NEW finalize), JVM pauses and
        // clock skew are all real and unmodelled, so "just barely exceeds" is not good enough.
        Duration required = worstCasePass().multipliedBy(12).dividedBy(10);

        assertThat(lockAtMostFor())
                .as("lockAtMostFor must be >= 1.2x the worst-case pass (%s)", required)
                .isGreaterThanOrEqualTo(required);
    }

    @Test
    @DisplayName("the ceiling no longer scales with the batch size — the deadline is what bounds a pass")
    void theCeilingIsDecoupledFromTheBatchSize() throws Exception {
        // The property this pins: batchSize x worstCaseRow is ALLOWED to exceed the ceiling now. Before
        // passDeadline existed that was the defect; with it, a pass that would take that long is cut
        // short instead. If this ever stops holding, the pass-bounding mechanism has been removed and
        // the two assertions above have quietly stopped protecting anything.
        Duration unboundedModel = worstCasePerRow().multipliedBy(configuredDefaultBatchSize());

        assertThat(unboundedModel)
                .as("sanity: with a 100-row cap the OLD model (%s) is far larger than the deadline-based "
                        + "bound (%s) — which is exactly why the deadline is what must bound a pass",
                        unboundedModel, worstCasePass())
                .isGreaterThan(worstCasePass());

        assertThat(configuredDefaultPassDeadline())
                .as("the pass deadline must be positive, or nothing bounds the loop")
                .isPositive();
    }

    @Test
    @DisplayName("the fallback pass deadline matches the @Value default — the budget cannot be widened by misconfiguration")
    void fallbackPassDeadlineMatchesTheValueDefault() throws Exception {
        // effectivePassDeadline() falls back to DEFAULT_PASS_DEADLINE on a non-positive/unparseable
        // value. If that constant were LONGER than the @Value default, a mistyped property would
        // silently raise the per-pass budget above what this ceiling was derived from.
        assertThat(defaultPassDeadlineConstant())
                .as("DEFAULT_PASS_DEADLINE is the fallback target and must not exceed the @Value default")
                .isEqualTo(configuredDefaultPassDeadline());
    }

    @Test
    @DisplayName("the fallback batch size matches the @Value default — the cap cannot be widened by misconfiguration")
    void fallbackBatchSizeMatchesTheValueDefault() throws Exception {
        // effectiveBatchSize() clamps a non-positive configured value to DEFAULT_BATCH_SIZE. If that
        // constant were larger than the @Value default, a mistyped property would silently raise the
        // per-pass row count ABOVE what this ceiling was derived from.
        assertThat(defaultBatchSizeConstant())
                .as("DEFAULT_BATCH_SIZE is the clamp target and must not exceed the @Value default")
                .isEqualTo(configuredDefaultBatchSize());
    }

    @Test
    @DisplayName("lockAtLeastFor stays below the cron interval so a fast pass is not held past its next fire")
    void lockAtLeastForStaysShort() throws Exception {
        Method run = ResultCheckJob.class.getMethod("run");
        Duration atLeast = Duration.parse(run.getAnnotation(SchedulerLock.class).lockAtLeastFor());

        // The documented cadence is one minute. The floor only needs to outlast an empty pass; if it
        // ever grew past the interval it would start skipping fires rather than preventing re-runs.
        assertThat(atLeast).isLessThan(Duration.ofMinutes(1));
        assertThat(atLeast).isPositive();
    }
}
