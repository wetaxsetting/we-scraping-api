package com.kosign.wecms.domain.payment.cardtransactions.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.kosign.wecms.domain.banking.nxisas.dto.QnaQuestion;

import java.util.List;
import java.util.UUID;

/**
 * Wire response for the session-driven Card Transactions Update flow:
 * {@code POST /api/v1/payment/card-transactions/sync-session} and the
 * matching resume endpoints ({@code .../{sessionId}/qna|otp|captcha}).
 *
 * <p>The card twin of
 * {@link com.kosign.wecms.domain.banking.dto.TransactionsSyncResponse} —
 * same discriminated shape, same challenge fields, so the frontend can
 * reuse the existing {@code QnaDialog} / {@code OtpDialog} /
 * {@code CaptchaDialog} chrome. Only the COMPLETED payload differs: card
 * sync reports {@code cardsSynced} / {@code insertedCount} /
 * {@code skippedCount} instead of per-account debit/credit tallies.
 *
 * <p>Discriminated by {@code status}:
 * <ul>
 *   <li>{@code COMPLETED} — every eligible card scraped + persisted.</li>
 *   <li>{@code QNA_REQUIRED} / {@code OTP_REQUIRED} /
 *       {@code CAPTCHA_REQUIRED} — Coocon stopped at the current
 *       credential's LOGIN; the frontend opens the matching dialog and
 *       calls the resume endpoint with {@code sessionId} echoed back.</li>
 *   <li>{@code ERROR} — terminal bank/gateway rejection.</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CardSyncSessionResponse(

        /** Server-issued session id — non-null on every challenge
         *  response so the frontend can resume. */
        UUID sessionId,

        Status status,

        /* ──── COMPLETED-only ──── */
        Integer cardsSynced,
        Integer insertedCount,
        Integer skippedCount,

        /* ──── *_REQUIRED-only ──── */
        String challengePrompt,
        List<QnaQuestion> qnaQuestions,
        String captchaImage,
        String captchaImageFormat,
        /** Bank-credential username for the card whose LOGIN raised the
         *  challenge — echoed back so the Security Question popup can show
         *  the registered Login ID (parity with the legacy popup +
         *  {@code TransactionsSyncResponse}). */
        String loginId,
        /** Display name of the bank whose LOGIN raised the challenge —
         *  echoed back so the Security Question popup can render the bank
         *  (the card Update flow scrapes every active card, so the dialog
         *  can't derive the bank client-side the way the per-account
         *  Transfer dialog does). */
        String bankName,

        /* ──── ERROR-only ──── */
        String errorCode,
        String errorMessage
) {

    public enum Status {
        OTP_REQUIRED,
        CAPTCHA_REQUIRED,
        QNA_REQUIRED,
        COMPLETED,
        ERROR
    }

    /** Terminal success — every eligible card scraped + persisted. */
    public static CardSyncSessionResponse completed(
            UUID sessionId, int cardsSynced, int insertedCount, int skippedCount) {
        return new CardSyncSessionResponse(
                sessionId, Status.COMPLETED,
                cardsSynced, insertedCount, skippedCount,
                null, null, null, null, null, null,
                null, null);
    }

    /** QNA challenge — frontend opens the security-question dialog.
     *  {@code bankName} renders next to the registered {@code loginId}. */
    public static CardSyncSessionResponse qnaChallenge(
            UUID sessionId, String prompt, List<QnaQuestion> questions,
            String loginId, String bankName) {
        return new CardSyncSessionResponse(
                sessionId, Status.QNA_REQUIRED,
                null, null, null,
                prompt, questions, null, null, loginId, bankName,
                null, null);
    }

    /** OTP challenge — frontend opens the OTP dialog. */
    public static CardSyncSessionResponse otpChallenge(UUID sessionId, String prompt, String bankName) {
        return new CardSyncSessionResponse(
                sessionId, Status.OTP_REQUIRED,
                null, null, null,
                prompt, null, null, null, null, bankName,
                null, null);
    }

    /** CAPTCHA challenge — frontend opens the CAPTCHA dialog. */
    public static CardSyncSessionResponse captchaChallenge(
            UUID sessionId, String prompt, String image, String format, String bankName) {
        return new CardSyncSessionResponse(
                sessionId, Status.CAPTCHA_REQUIRED,
                null, null, null,
                prompt, null, image, format, null, bankName,
                null, null);
    }

    /** Terminal error — frontend shows the standard error dialog. */
    public static CardSyncSessionResponse error(UUID sessionId, String code, String message) {
        return new CardSyncSessionResponse(
                sessionId, Status.ERROR,
                null, null, null,
                null, null, null, null, null, null,
                code, message);
    }
}
