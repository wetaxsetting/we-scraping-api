package com.kosign.wecms.domain.payment.dispatch.callback.event;

/**
 * Lifecycle signal — a firm-banking pay-file's dispatch result has been
 * <b>finalized</b>: every sibling file of the same {@code FILE_SEQ_NO} has
 * reported and {@code TB_PAY_FILE_MST.PROC_STAT} is now the closed value
 * ({@code 0} all-success / {@code 1} partial / {@code 3} all-fail).
 *
 * <p>Published once, at the single finalization point
 * ({@code BulkExecutionResultService.finalizePayFileStatus}), inside the
 * dispatch-result transaction. The only subscriber is the HAENGSUNG Oracle
 * dispatch-result write-back (Step 5,
 * {@code HaengsungDispatchResultWriteBackService}); it runs
 * {@code AFTER_COMMIT}, filters to HAENGSUNG / ERP batches, and is a no-op for
 * everything else — so emitting it for every tenant/module is safe (same
 * pattern as {@code PaymentApprovalStatusChangedEvent}).
 *
 * @param bizKey    the tenant the finalized file belongs to.
 * @param tranDt    the file batch transfer date ({@code yyyyMMdd}) —
 *                  {@code TB_PAY_FILE_MST.TRAN_DT} / the summary's
 *                  {@code PAY_TRAN_DT}.
 * @param fileSeqNo the pay-file sequence — {@code TB_PAY_FILE_MST.FILE_SEQ_NO}
 *                  / the summary's {@code PAY_FILE_SEQ_NO}.
 */
public record PaymentDispatchResultFinalizedEvent(String bizKey, String tranDt, String fileSeqNo) {
}
