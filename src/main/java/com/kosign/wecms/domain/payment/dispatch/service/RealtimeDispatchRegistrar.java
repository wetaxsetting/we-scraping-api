package com.kosign.wecms.domain.payment.dispatch.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

/**
 * Shared hand-off from a committed VND (API-path) payment dispatch to the real-time
 * 202 transfer ({@link RealtimeDispatchService#dispatch}). The API-path twin of
 * {@link DispatchAutoReceiveRegistrar} (which serves the non-VND File path).
 *
 * <p><b>Safety contract (mirrors the File path):</b>
 * <ul>
 *   <li><b>After-commit only</b> — the hand-off runs in an {@code afterCommit}
 *       synchronization, never inline. The {@code TB_PAY_TARGET} {@code PROC_STAT='9'}
 *       rows are durably committed before they are re-read, and no EFS network I/O
 *       runs inside the payment transaction.</li>
 *   <li><b>Per-target failure isolation</b> — each {@code tranSeq} is dispatched
 *       independently; a failure on one (and the payment is already committed) is
 *       caught and logged, never rethrown, and never blocks the remaining targets
 *       (legacy {@code NAPAS_REAL_TIME_BATCH} per-target {@code continue}).</li>
 *   <li><b>Gated</b> — no-op unless {@code wecms.dispatch.realtime-dispatch=true}
 *       (default {@code false}), so the existing behaviour (pay-target stays
 *       {@code PROC_STAT='9'}) is unchanged until VAN keys (B2) + EFS (B1) are
 *       provisioned, and the refactor never co-processes live rows on a shared DB.</li>
 * </ul>
 *
 * <h2>C-6 (2026-07-31) — this hook enqueues; it no longer dispatches</h2>
 * {@code afterCommit} used to loop the {@code TRAN_SEQ}s and call
 * {@link RealtimeDispatchService#dispatch} for each, sequentially, <b>on the Tomcat request thread</b> —
 * N unpaced 202s per approval, each able to take up to 90 s against a slow gateway, with the user's
 * request held for the whole run. It now hands each row to {@link RealtimeDispatchQueue}, a single
 * worker draining FIFO with {@link DispatchPacer} between rows, and returns in O(N) enqueues.
 *
 * <p>Two consequences worth being explicit about:
 * <ul>
 *   <li><b>Failure isolation is now the drain's job.</b> Nothing can fail here except an enqueue
 *       rejection, which is logged by the queue and leaves the row at {@code '9'}.</li>
 *   <li><b>The dispatch outcome is no longer known when the response returns.</b> It never usefully was
 *       — the errors were caught and logged — but now it is not even attempted yet. The surface for a
 *       dispatch that never happens is {@code StuckPayFileMonitorService}'s {@code TB_PAY_TARGET}
 *       {@code '9'} sweep; see {@link RealtimeDispatchQueue}'s dependency note before enabling this rail
 *       in an environment where that monitor is off.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RealtimeDispatchRegistrar {

    private final RealtimeDispatchQueue realtimeDispatchQueue;

    @Value("${wecms.dispatch.realtime-dispatch:false}")
    private boolean enabled;

    /**
     * Register the post-commit 202 hand-off for the just-written VND dispatch rows.
     * No-op when {@code realtime-dispatch=false}, when there are no rows, or when
     * there is no active transaction.
     */
    public void registerAfterCommit(String bizKey, String tranDt, List<String> tranSeqs) {
        if (!enabled) {
            return;
        }
        if (tranSeqs == null || tranSeqs.isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            log.warn("realtime-dispatch enabled but no active transaction — skipping 202 hand-off "
                    + "bizKey={} tranDt={} count={}", bizKey, tranDt, tranSeqs.size());
            return;
        }
        List<String> seqs = List.copyOf(tranSeqs);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // C-6: enqueue only. The gateway calls happen on the drain's single worker thread with
                // per-bank pacing between them — never here, and never on the request thread.
                int accepted = 0;
                for (String tranSeq : seqs) {
                    if (realtimeDispatchQueue.enqueue(bizKey, tranDt, tranSeq)) {
                        accepted++;
                    }
                    // A rejected ticket is logged at ERROR by the queue and leaves the row at
                    // PROC_STAT='9'; keep going so one drop cannot cost the rest of the payment.
                }
                if (accepted == seqs.size()) {
                    log.info("realtime-dispatch queued {} target(s) bizKey={} tranDt={}",
                            accepted, bizKey, tranDt);
                } else {
                    log.error("realtime-dispatch queued only {} of {} target(s) bizKey={} tranDt={} — the "
                                    + "remainder stay PROC_STAT='9' for the stuck-monitor pay-target sweep",
                            accepted, seqs.size(), bizKey, tranDt);
                }
            }
        });
    }
}
