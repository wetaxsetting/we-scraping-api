package com.kosign.wecms.domain.payment.dispatch.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Shared hand-off from a committed non-VND (File-path) payment dispatch to the
 * 00301 reception flow ({@link BulkReceptionService#receive}).
 *
 * <p>Used by every File-path dispatch executor — the shared
 * {@link TransferDispatchExecutor} (Vendor / Expense) and the module-specific
 * {@code InternalTransferDispatchExecutor} / {@code PayrollDispatchExecutor} /
 * {@code OverseasDispatchExecutor} — so the registration pattern lives in exactly
 * one place.
 *
 * <p><b>Safety contract (identical for all callers):</b>
 * <ul>
 *   <li><b>After-commit only</b> — {@code receive()} runs in an
 *       {@code afterCommit} synchronization, never inline. The dispatch's
 *       {@code TB_PAY_FILE_MST} WAIT row is therefore durably committed before
 *       {@code receive()} re-reads it, and no SFTP/EFS network I/O ever runs
 *       inside the payment transaction.</li>
 *   <li><b>Failure isolation</b> — the payment is already committed when the hook
 *       fires, so a {@code receive()} failure (missing {@code TB_FIRM_CONF}, SFTP,
 *       gateway error) <b>cannot roll back the payment</b>. The exception is caught
 *       and logged; the pay-file row is left for monitoring / manual recovery
 *       (legacy parity: BATCH1 failures were per-target, never undoing dispatch).</li>
 *   <li><b>Gated</b> — no-op unless {@code wecms.dispatch.auto-receive=true}
 *       (default {@code false}), so existing behaviour (pay-file stays
 *       {@code PROC_STAT='9'}) is unchanged until SFTP (B4) + EFS (B1/B2) are
 *       provisioned.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DispatchAutoReceiveRegistrar {

    private final BulkReceptionService bulkReceptionService;

    @Value("${wecms.dispatch.auto-receive:false}")
    private boolean enabled;

    /**
     * Register the post-commit 00301 hand-off for one just-written File-path batch.
     * No-op when {@code auto-receive=false} or when there is no active transaction.
     */
    public void registerAfterCommit(String tranDt, String bizKey, String fileSeqNo) {
        if (!enabled) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            log.warn("auto-receive enabled but no active transaction — skipping 00301 hand-off "
                    + "bizKey={} fileSeqNo={}", bizKey, fileSeqNo);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    bulkReceptionService.receive(tranDt, bizKey, fileSeqNo);
                } catch (Exception e) {
                    // Isolated — the dispatch already committed; never rethrow.
                    log.error("auto-receive (00301) failed after dispatch bizKey={} fileSeqNo={} — "
                            + "pay-file left for recovery", bizKey, fileSeqNo, e);
                }
            }
        });
    }
}
