package com.kosign.wecms.domain.banking.ownaccounttransfer.dto.response;

import java.math.BigDecimal;

/**
 * Response body for
 * {@code POST /api/v1/banking/own-account-transfer/scraping-result}.
 * Returned after the row is persisted to {@code NAPAS_REMIT_TRNS_HSTR}
 * (or — on retry — after the prior row is looked up by idempotency key).
 *
 * <p>{@code alreadyRecorded == true} signals the client that a prior
 * intake POST with the same {@code idempotency_key} already recorded
 * the row; the response carries the original outcome and no new DB
 * write happened. Clients should treat that as success and surface
 * the same success / failure UX they would for a fresh write.
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
