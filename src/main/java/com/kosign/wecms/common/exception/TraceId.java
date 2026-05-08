package com.kosign.wecms.common.exception;

import org.slf4j.MDC;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Generates and manages the per-request trace identifier.
 *
 * <p>Format: {@code yyyyMMddHHmmss + "PT" + 14 random digits + "DEVL"}.
 * Mirrors the legacy JEX correlation-id shape so an id surfaced in the
 * client-side error dialog can be grepped verbatim against server logs
 * + audit tables.
 *
 * <p>Lives in the SLF4J {@link MDC} under key {@value #MDC_KEY} for the
 * duration of one request — {@code TraceIdFilter} populates it on entry
 * and clears it on exit so the id appears in every log line emitted
 * during the request and never leaks across threads.
 */
public final class TraceId {

    public static final String MDC_KEY = "traceId";

    private static final DateTimeFormatter TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private static final SecureRandom RANDOM = new SecureRandom();

    private TraceId() {}

    public static String generate() {
        StringBuilder digits = new StringBuilder(14);
        for (int i = 0; i < 14; i++) {
            digits.append(RANDOM.nextInt(10));
        }
        return LocalDateTime.now().format(TIMESTAMP_FMT) + "PT" + digits + "DEVL";
    }

    /** Read the request-scoped traceId from MDC, generating one if the
     *  filter hasn't (e.g. inside a unit test or a non-HTTP entry point). */
    public static String current() {
        String existing = MDC.get(MDC_KEY);
        return existing != null ? existing : generate();
    }
}
