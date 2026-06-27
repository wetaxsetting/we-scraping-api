package com.kosign.wecms.common.exception;

/**
 * F-10 — thrown when a rate-limited endpoint (login / credential reveal) is hit
 * too often. Mapped to HTTP 429 by {@code GlobalExceptionHandler}. Carries a
 * {@code WBExxxx} code resolved to a localized, non-revealing message.
 */
public class RateLimitExceededException extends RuntimeException {

    private final String code;

    public RateLimitExceededException(String code) {
        super(code);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
