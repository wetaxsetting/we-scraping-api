package com.kosign.wecms.domain.payment.dispatch.monitor;

import java.util.EnumMap;
import java.util.Map;

/**
 * Outcome of one {@code StuckPayFileMonitorService.monitorOnce()} sweep.
 *
 * <p>Returned rather than only logged so the sweep is assertable in tests and so a
 * future caller (an admin endpoint, a health indicator once actuator lands — readiness
 * blocker C-5) can consume the same numbers the log line reports.
 *
 * @param stuckCount         total {@code TB_PAY_FILE_MST} rows at {@code PROC_STAT='9'} older
 *                           than the threshold
 * @param byCause            per-cause counts across <b>both</b> sweeps; empty when nothing is stuck
 * @param alerted            whether this sweep dispatched an ops alert (false when suppressed by
 *                           de-duplication, when nothing is stuck, or when no VAN org code could
 *                           be resolved)
 * @param firmFileStuckCount {@code TB_FIRM_FILE_MST} rows stranded at
 *                           {@code PROC_STEP='1' / PROC_STAT='6'} past the threshold — the
 *                           post-{@code 00301}, pre-confirmed-{@code 00302} state. Counted
 *                           separately from {@code stuckCount} because the two describe different
 *                           tables at different points in the flow: a WAIT pay-file has not moved
 *                           any money, whereas these files are already at the bank.
 * @param claimedStuckCount  {@code TB_PAY_FILE_MST} rows stranded at {@code PROC_STAT='6'} past the
 *                           threshold — <b>claimed but never finished</b> (H-3 / N-3). Counted
 *                           separately again, because this population is neither "hasn't started"
 *                           ({@code stuckCount}) nor "definitely at the bank"
 *                           ({@code firmFileStuckCount}): whether anything left depends on how far
 *                           the claim got, which is what {@code classifyClaimedPayFile} determines.
 * @param payTargetStuckCount {@code TB_PAY_TARGET} rows stranded at {@code PROC_STAT='9'} past the
 *                           <b>pay-target</b> grace window — the real-time (202) rail's WAIT
 *                           population. A fourth count rather than a fold into {@code stuckCount}
 *                           because it is a different table, a different rail and a different grace
 *                           window: the real-time hand-off fires within milliseconds of commit, so a
 *                           row still at WAIT is stranded on a much shorter timescale than a bulk file
 *                           waiting on an operating-hours window.
 */
public record StuckPayFileReport(
        int stuckCount,
        Map<StuckPayFileCause, Integer> byCause,
        boolean alerted,
        int firmFileStuckCount,
        int claimedStuckCount,
        int payTargetStuckCount) {

    public static StuckPayFileReport empty() {
        return new StuckPayFileReport(0, new EnumMap<>(StuckPayFileCause.class), false, 0, 0, 0);
    }

    /** Total stranded rows across all four sweeps. */
    public int totalStuck() {
        return stuckCount + firmFileStuckCount + claimedStuckCount + payTargetStuckCount;
    }

    /** Count for one cause, 0 when absent. */
    public int count(StuckPayFileCause cause) {
        return byCause.getOrDefault(cause, 0);
    }
}
