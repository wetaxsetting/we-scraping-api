package com.kosign.wecms.domain.weinvoice.dto;

/**
 * One sync run (UI Spec {@code SyncRun}, screen 10). Runs requested from WeInvoice have an id
 * {@code R<request id>}; runs the engine scheduled itself have {@code J<engine run id>} and
 * trigger {@code schedule}.
 *
 * @param status    {@code queued}, {@code running}, {@code success}, {@code partial}, {@code failed}, {@code cancelled}
 * @param direction {@code both}, {@code purchase}, {@code sales}
 */
public record SyncRunItem(
        String id,
        String requestId,
        String taxId,
        String companyName,
        String direction,
        String fromMonth,
        String toMonth,
        String startedAt,
        Long durationSec,
        Integer newCount,
        Integer updatedCount,
        Integer xmlCount,
        String status,
        String message,
        String trigger) {
}
