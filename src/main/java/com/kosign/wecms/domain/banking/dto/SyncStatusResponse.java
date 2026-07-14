package com.kosign.wecms.domain.banking.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.kosign.wecms.domain.banking.nxisas.dto.QnaQuestion;

import java.util.List;
import java.util.UUID;

/**
 * Unified async status snapshot for Bank Transaction Sync (Phase 2). Returned
 * by the start, status-poll, challenge-resume, and cancel endpoints so the
 * frontend reads one stable shape and branches on {@code status}.
 *
 * <p>Immutable value snapshot — safe to poll repeatedly, no side effects.
 * {@code @JsonInclude(NON_NULL)} keeps each state's payload tight (a RUNNING
 * snapshot carries progress + counters; a COMPLETED snapshot adds the tally +
 * summaries; a {@code *_REQUIRED} snapshot carries {@link Challenge}).
 *
 * <p><b>No secrets.</b> This DTO never carries a password, OTP, CAPTCHA value,
 * QNA answer, SAS token, cookie, decrypted credential, or gateway continuation.
 * {@link Challenge} carries only the display metadata the frontend needs to
 * render the challenge form (prompt, QNA question labels + hidden tokens, the
 * CAPTCHA image, the registered login id).
 *
 * <p>{@code status} values: {@code RUNNING}, {@code COMPLETED}, {@code FAILED},
 * {@code CANCELLED}, {@code OTP_REQUIRED}, {@code QNA_REQUIRED},
 * {@code CAPTCHA_REQUIRED}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SyncStatusResponse(

        UUID sessionId,
        String status,

        // ── progress + counters (every non-error snapshot) ──
        Integer progress,
        String currentPhase,
        Integer completedAccounts,
        Integer totalAccounts,
        Integer successAccounts,
        Integer failedAccounts,

        // ── per-currency roll-up (accumulates during RUNNING; final on COMPLETED) ──
        List<CurrencySummary> currencySummaries,

        // ── per-account failures (accounts that failed WITHOUT aborting the
        //    batch — today only LOGIN_CAN challenge-cancels). Drives the popup's
        //    per-account failed tooltip "[bank acct] {message} (code)". ──
        List<FailedAccount> failedAccountDetails,

        // ── challenge metadata (only when status is *_REQUIRED) ──
        Challenge challenge,

        // ── COMPLETED terminal extras (what the popup's completion view needs) ──
        Integer accountsSynced,
        Integer debitCount,
        Integer creditCount,
        String debitTotal,
        String creditTotal,
        String lastUpdate,
        Integer autoMatchedCount,
        Boolean autoMatchEnabled,

        // ── ERROR / FAILED terminal (sanitized) ──
        String errorCode,
        String errorMessage
) {

    /**
     * Display-only challenge metadata. Carries no submitted answer and no
     * gateway continuation — just what the frontend renders in the OTP / QNA /
     * CAPTCHA form.
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
            String loginId) { }

    /**
     * One account that failed without aborting the batch (a {@code LOGIN_CAN}
     * challenge-cancel). Carries only non-secret locators + the sanitized error
     * so the frontend can render "[bank acct] {localized message} (code)"; the
     * localized message comes from the shared result-code map (falling back to
     * {@code errorMessage} / a safe generic), never raw {@code undefined}.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FailedAccount(
            String bankCd,
            String acctNo,
            String ccyCd,
            String errorCode,
            String errorMessage) { }
}
