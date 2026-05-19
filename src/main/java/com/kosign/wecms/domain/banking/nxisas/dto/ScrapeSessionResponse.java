package com.kosign.wecms.domain.banking.nxisas.dto;

import java.util.List;
import java.util.UUID;

/**
 * Unified response for both the start-scrape and submit-challenge
 * endpoints. {@code status} drives the frontend state machine:
 * {@code OTP_REQUIRED} / {@code CAPTCHA_REQUIRED} / {@code QNA_REQUIRED}
 * → render the matching dialog; {@code COMPLETED} → show
 * {@code accounts}; {@code ERROR} → show the standard envelope's
 * error using {@code errorCode} + {@code errorMessage}.
 *
 * <p>{@code qnaQuestions} is populated only when {@code status ==
 * QNA_REQUIRED}: the parsed security-question list (one entry per
 * {@code (HiddenValue, Question)} pair from the bank adapter). The
 * frontend renders the list as a picker; the user chooses ONE and
 * types its answer. The frontend cannot rely on parsing
 * {@code challengePrompt} as JSON for QNA — server-side parsing keeps
 * the wire contract typed.
 */
public record ScrapeSessionResponse(
        UUID sessionId,
        Status status,
        String challengePrompt,
        List<QnaQuestion> qnaQuestions,
        /** Base64-encoded image bytes for the CAPTCHA challenge —
         *  decode + render as {@code data:image/{format};base64,…}
         *  in the frontend. Null for non-CAPTCHA. */
        String captchaImage,
        /** Image MIME format ("png" or "svg+xml"). Null for non-CAPTCHA;
         *  defaults to "png" for any bank other than VIETNAM_GDT per
         *  the legacy parity in nxisas_v2.js:1539-1542. */
        String captchaImageFormat,
        List<ScrapedAccount> accounts,
        /** Scraped card list — populated when the session was started
         *  with {@code scrape_kind = "CARD_INFO"} and the post-login
         *  CARD_INFO job returned a result. Null on every ACCT_LIST
         *  session (back-compat with existing callers). */
        List<ScrapedCard> cards,
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

    /** OTP challenge — challengePrompt only. */
    public static ScrapeSessionResponse challenge(UUID sessionId, Status status, String prompt) {
        return new ScrapeSessionResponse(sessionId, status, prompt, null, null, null, null, null, null, null);
    }

    /** QNA challenge — challengePrompt + structured question list. */
    public static ScrapeSessionResponse qnaChallenge(UUID sessionId, String prompt, List<QnaQuestion> questions) {
        return new ScrapeSessionResponse(sessionId, Status.QNA_REQUIRED, prompt, questions, null, null, null, null, null, null);
    }

    /** CAPTCHA challenge — base64 image + format. */
    public static ScrapeSessionResponse captchaChallenge(UUID sessionId, String prompt, String image, String format) {
        return new ScrapeSessionResponse(sessionId, Status.CAPTCHA_REQUIRED, prompt, null, image, format, null, null, null, null);
    }

    /** Terminal error response (bank-side or gateway-side rejection). */
    public static ScrapeSessionResponse error(UUID sessionId, String errorCode, String errorMessage) {
        return new ScrapeSessionResponse(sessionId, Status.ERROR, null, null, null, null, null, null, errorCode, errorMessage);
    }

    /** Terminal success — scraped accounts payload (ACCT_LIST flow). */
    public static ScrapeSessionResponse completed(UUID sessionId, List<ScrapedAccount> accounts) {
        return new ScrapeSessionResponse(sessionId, Status.COMPLETED, null, null, null, null, accounts, null, null, null);
    }

    /** Terminal success — scraped cards payload (CARD_INFO flow). */
    public static ScrapeSessionResponse completedCards(UUID sessionId, List<ScrapedCard> cards) {
        return new ScrapeSessionResponse(sessionId, Status.COMPLETED, null, null, null, null, null, cards, null, null);
    }
}
