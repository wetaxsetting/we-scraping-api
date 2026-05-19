package com.kosign.wecms.domain.payment.cardtransactions.dto;

import java.util.List;

/**
 * Update Cards response — summary of the synchronous scrape.
 *
 * <ul>
 *   <li>{@code cardsAttempted} — count of cards the orchestrator tried
 *       (all active cards in the tenant with a registered credential).</li>
 *   <li>{@code cardsSucceeded} — count whose scrape returned at least
 *       one row OR a clean empty result.</li>
 *   <li>{@code insertedCount} — total {@code CARD_TRNS_HSTR} rows
 *       added across all succeeded cards (post-dedup).</li>
 *   <li>{@code skippedCount} — total transactions Coocon returned that
 *       were already in {@code CARD_TRNS_HSTR} (dedupe hit).</li>
 *   <li>{@code errors} — per-card failure list. Each entry carries the
 *       card identity + a structured error code so the frontend can
 *       render "Re-authenticate Woori 5559" / "Captcha required for
 *       BIDV …" / "Scrape rate-limited" etc.</li>
 * </ul>
 */
public record SyncCardTransactionsResponse(
        int cardsAttempted,
        int cardsSucceeded,
        int insertedCount,
        int skippedCount,
        List<CardSyncError> errors) {

    /**
     * One entry in {@link SyncCardTransactionsResponse#errors}.
     *
     * <p>{@code code} carries a WBE-style error code so the frontend
     * can branch — {@code WBE_CARD_SYNC_NEEDS_RE_AUTH} (login
     * required, e.g. OTP / Captcha challenge surfaced); {@code
     * WBE_CARD_SYNC_GATEWAY_ERROR} (Coocon gateway returned a non-
     * success result code); {@code WBE_CARD_SYNC_NO_CREDENTIAL}
     * (card has no registered {@code BANK_CERT_INFO} row).
     */
    public record CardSyncError(
            String bankCd,
            String bankNm,
            String cardNo,
            String code,
            String message) {
    }
}
