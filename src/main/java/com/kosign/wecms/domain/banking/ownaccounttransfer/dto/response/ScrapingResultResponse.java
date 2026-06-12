package com.kosign.wecms.domain.banking.ownaccounttransfer.dto.response;

import java.math.BigDecimal;

/**
 * Response body for
 * {@code POST /api/v1/banking/own-account-transfer/scraping-result}.
 * Returned after the row is persisted to {@code NAPAS_REMIT_TRNS_HSTR}.
 *
 * <p>{@code idempotencyKey} is echoed back from the request for
 * client-side correlation; it is not persisted. {@code alreadyRecorded}
 * is retained for frontend contract stability but is always
 * {@code false} — the legacy JEX table has no idempotency column, so
 * the server-side replay short-circuit was removed and every POST
 * records a fresh row.
 *
 * <p>{@code status} collapses {@code trns_stts} into the legacy
 * {@code "success"} / {@code "failed"} bucket so the frontend doesn't
 * need to know the engine's success-code constant.
 */
public record ScrapingResultResponse(
        String     seqNo,
        String     idempotencyKey,
        String     bankTxId,
        BigDecimal recordedAmt,
        String     trnsStts,
        String     status,
        boolean    alreadyRecorded
) {
    /** {@code status} value when the engine reported success. */
    public static final String STATUS_SUCCESS = "success";
    /** {@code status} value when the engine reported any non-success
     *  code (or no tx id). */
    public static final String STATUS_FAILED  = "failed";
}
