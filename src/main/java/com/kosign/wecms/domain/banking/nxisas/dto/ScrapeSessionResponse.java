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
 */
public record ScrapeSessionResponse(
        UUID sessionId,
        Status status,
        String challengePrompt,
        List<ScrapedAccount> accounts,
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
}
