package com.kosign.wecms.domain.payment.cardtransactions.dto;

import java.util.List;

/**
 * Update Cards response &mdash; summary of the synchronous scrape.
 *
 * <ul>
 *   <li>{@code cardsAttempted} &mdash; count of cards the orchestrator
 *       tried (all active cards in the tenant with a registered
 *       credential).</li>
 *   <li>{@code cardsSucceeded} &mdash; count whose scrape returned at
 *       least one row OR a clean empty result.</li>
 *   <li>{@code insertedCount} &mdash; total {@code CARD_TRNS_HSTR}
 *       rows added across all succeeded cards (post-dedup).</li>
 *   <li>{@code skippedCount} &mdash; total transactions Coocon
 *       returned that were already in {@code CARD_TRNS_HSTR} (dedupe
 *       hit).</li>
 *   <li>{@code errors} &mdash; per-card failure list. Each entry
 *       carries the card identity + a structured error code so the
 *       frontend can render &quot;Re-authenticate Woori 5559&quot; /
 *       &quot;Captcha required for BIDV &hellip;&quot; / &quot;Scrape
 *       rate-limited&quot; etc.</li>
 *   <li>{@code groups} &mdash; per-credential breakdown of the run.
 *       Mirrors the legacy {@code nxisas_cardTranList.ts:175-218}
 *       {@code _nxisas_dataArray} shape so the frontend can emit a
 *       cert-grouped {@code ---- orgObj ----} /
 *       {@code ---- tranList Result(Unit) ----} console block per
 *       cert, matching legacy debug output byte-for-byte.</li>
 * </ul>
 */
public record SyncCardTransactionsResponse(
        int cardsAttempted,
        int cardsSucceeded,
        int insertedCount,
        int skippedCount,
        List<CardSyncError> errors,
        List<SyncGroup> groups) {

    /**
     * One entry in {@link SyncCardTransactionsResponse#errors}.
     *
     * <p>{@code code} carries a WBE-style error code so the frontend
     * can branch &mdash; {@code WBE_CARD_SYNC_NEEDS_RE_AUTH} (login
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

    /**
     * Per-credential breakdown &mdash; one entry per {@code
     * BANK_CERT_SEQ} bucket. Mirrors the legacy {@code _nxisas_dataObj}
     * shape ({@code bankCd}, {@code bankNm}, {@code bankCertSeq},
     * {@code job[]}) plus per-card outcome so the frontend can render
     * the legacy {@code orgObj} / {@code tranList Result(Unit)}
     * console blocks identically.
     *
     * <p>{@code loginOk}: {@code true} when Coocon LOGIN returned a
     * success envelope AND no challenge. {@code false} for any failure
     * path; {@code loginErrorCode} / {@code loginErrorMessage} carry
     * the per-leg detail in that case ({@code WBE_CARD_SYNC_LOGIN_FAILED}
     * / {@code WBE_CARD_SYNC_NEEDS_RE_AUTH} /
     * {@code WBE_CARD_SYNC_UNSUPPORTED_BANK} /
     * {@code WBE_CARD_SYNC_NO_CREDENTIAL}).
     */
    public record SyncGroup(
            String  bankCd,
            String  bankNm,
            String  bankCertSeq,
            boolean loginOk,
            String  loginErrorCode,
            String  loginErrorMessage,
            List<SyncGroupCard> cards) {
    }

    /**
     * Per-card outcome inside a {@link SyncGroup}. Mirrors the legacy
     * per-job result shape so the frontend can fan out the {@code
     * tranList Result(Unit)} console block per card.
     *
     * <p>{@code scrapedCount} is the raw transaction count Coocon
     * returned; {@code inserted} + {@code skipped} are the
     * dedup-adjusted counts (skipped == already in {@code
     * CARD_TRNS_HSTR}). When LOGIN fails for the cert, every card
     * carries {@code scrapedCount=0, inserted=0, skipped=0} and a
     * non-null {@code errorCode}.
     */
    public record SyncGroupCard(
            String cardNo,
            int    scrapedCount,
            int    inserted,
            int    skipped,
            String errorCode,
            String errorMessage) {
    }
}
