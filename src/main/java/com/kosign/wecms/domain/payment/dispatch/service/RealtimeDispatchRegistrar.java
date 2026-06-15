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
 *   <li><b>After-commit only</b> — {@code dispatch()} runs in an {@code afterCommit}
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
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RealtimeDispatchRegistrar {

    private final RealtimeDispatchService realtimeDispatchService;

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
                for (String tranSeq : seqs) {
                    try {
                        realtimeDispatchService.dispatch(bizKey, tranDt, tranSeq);
                    } catch (Exception e) {
                        // Isolated — the dispatch already committed; never rethrow, never block siblings.
                        log.error("realtime-dispatch (202) failed after dispatch bizKey={} tranDt={} "
                                + "tranSeq={} — pay-target left for recovery", bizKey, tranDt, tranSeq, e);
                    }
                }
            }
        });
    }
}
