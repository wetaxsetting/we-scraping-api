package com.kosign.wecms.domain.weinvoice.dto;

/**
 * {@code GET /sync-runs/{run_id}} — UI Spec {@code SyncProgress} plus the request status.
 *
 * @param step {@code login | captcha | list | xml | done} as reported by the engine
 */
public record SyncRunProgress(
        String runId,
        String taxId,
        String status,
        String step,
        String month,
        Integer listed,
        Integer xmlDone,
        Integer xmlTotal,
        String error,
        SyncRunItem run) {
}
