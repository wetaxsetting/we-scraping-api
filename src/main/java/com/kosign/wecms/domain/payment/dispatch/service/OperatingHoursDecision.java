package com.kosign.wecms.domain.payment.dispatch.service;

/**
 * Outcome of the legacy operating-hours gate ({@code TB_BATCH_SCHEDULE_INFO} /
 * {@code TB_BATCH_SCHEDULE_DTL}, {@code EXE_DIV}) — see
 * {@code docs/features/wecms-batch/batch-schedule-operating-hours-audit.md}.
 *
 * <p>Maps the legacy per-file / per-target flag combination onto a single value:
 * <ul>
 *   <li>{@link #DISPATCH} — in the operating window ({@code EXE_DIV='Y'} at every
 *       applicable level); the dispatch proceeds.</li>
 *   <li>{@link #CUTOFF} — after the end time ({@code EXE_DIV='E'} → legacy
 *       {@code exeEndYN=true}); dispatch is failed and the {@code 거래 마감}
 *       alert is sent.</li>
 *   <li>{@link #NO_CRITERIA} — no schedule row for the key (legacy
 *       {@code BATCH_START_TM == null}); dispatch is failed and the
 *       {@code 수행 기준 없음} alert is sent.</li>
 *   <li>{@link #SKIP} — before the start time ({@code EXE_DIV='N'}), or (BATCH1)
 *       a channel not yet open; the row is left untouched for a later run and
 *       <b>no</b> alert is sent (legacy silent {@code continue}).</li>
 * </ul>
 */
public enum OperatingHoursDecision {
    DISPATCH,
    CUTOFF,
    NO_CRITERIA,
    SKIP
}
