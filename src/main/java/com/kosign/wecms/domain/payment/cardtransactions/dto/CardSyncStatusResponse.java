package com.kosign.wecms.domain.payment.cardtransactions.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.kosign.wecms.domain.banking.nxisas.dto.QnaQuestion;

import java.util.List;
import java.util.UUID;

/**
 * Unified async status snapshot for the Card Transactions Update flow
 * (Phase 2 — background worker + status polling). Returned by the start,
 * status-poll, challenge-resume, and cancel endpoints so the frontend reads
 * one stable shape and branches on {@code status}.
 *
 * <p>The card twin of
 * {@link com.kosign.wecms.domain.banking.dto.SyncStatusResponse} — same
 * discriminated shape and progress/counter fields, so the Update Card
 * Transactions popup can drive a determinate progress bar + a live
 * "In Progress ( x / N )" counter exactly the way Bank Transaction Sync does.
 * The card scrape has no per-account debit/credit tally, so the COMPLETED
 * payload carries {@code cardsSynced} / {@code insertedCount} /
 * {@code skippedCount} instead.
 *
 * <p><b>Immutable value snapshot</b> — safe to poll repeatedly, no side
 * effects. {@code @JsonInclude(NON_NULL)} keeps each state's payload tight.
 *
 * <p><b>No secrets.</b> This DTO never carries a password, OTP, CAPTCHA value,
 * QNA answer, SAS token, cookie, decrypted credential, or gateway
 * continuation. {@link Challenge} carries only display metadata (prompt, QNA
 * question labels + hidden tokens, CAPTCHA image, login id, bank name).
 *
 * <p>{@code status} values: {@code RUNNING}, {@code COMPLETED}, {@code FAILED},
 * {@code CANCELLED}, {@code OTP_REQUIRED}, {@code QNA_REQUIRED},
 * {@code CAPTCHA_REQUIRED}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CardSyncStatusResponse(

        UUID sessionId,
        String status,

        // ── progress + counters (every non-error snapshot) ──
        Integer progress,
        String currentPhase,
        Integer completedCards,
        Integer totalCards,
        Integer successCards,
        Integer failedCards,

        // ── challenge metadata (only when status is *_REQUIRED) ──
        Challenge challenge,

        // ── COMPLETED terminal extras ──
        Integer cardsSynced,
        Integer insertedCount,
        Integer skippedCount,

        // ── ERROR / FAILED terminal (sanitized) ──
        String errorCode,
        String errorMessage
) {

    /**
     * Immediate terminal COMPLETED snapshot with no in-flight session — used
     * when the tenant has zero eligible cards so there is nothing to poll.
     * Progress is pinned to 100 and every counter is zero.
     */
    public static CardSyncStatusResponse completed(
            UUID sessionId, int cardsSynced, int insertedCount, int skippedCount) {
        return new CardSyncStatusResponse(
                sessionId, "COMPLETED",
                100, null, 0, 0, cardsSynced, 0,
                null,
                cardsSynced, insertedCount, skippedCount,
                null, null);
    }

    /**
     * Display-only challenge metadata. Carries no submitted answer and no
     * gateway continuation — just what the frontend renders in the OTP / QNA /
     * CAPTCHA form. {@code bankName} is echoed so the Security Question popup
     * can show the bank whose LOGIN raised the challenge (the card Update flow
     * scrapes every active card, so the dialog can't derive it client-side).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Challenge(
            /** {@code OTP} / {@code QNA} / {@code CAPTCHA}. */
            String kind,
            String prompt,
            /** Populated only for QNA. */
            List<QnaQuestion> qnaQuestions,
            /** Base64 CAPTCHA image (CAPTCHA only). */
            String captchaImage,
            /** {@code png} / {@code svg+xml} (CAPTCHA only). */
            String captchaImageFormat,
            /** Registered bank login id, echoed for the QNA form header. */
            String loginId,
            /** Display name of the bank whose LOGIN raised the challenge. */
            String bankName) { }
}
