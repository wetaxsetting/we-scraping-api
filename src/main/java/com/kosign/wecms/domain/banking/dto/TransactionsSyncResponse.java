package com.kosign.wecms.domain.banking.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.kosign.wecms.domain.banking.nxisas.dto.QnaQuestion;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Wire response for {@code POST /api/v1/banking/accounts/transactions-sync}
 * and the matching resume endpoints
 * ({@code .../{sessionId}/qna|otp|captcha}).
 *
 * <p>Discriminated by {@code status}:
 * <ul>
 *   <li>{@code COMPLETED} — every selected account synced; the
 *       {@code accountsSynced} / {@code debitCount} / {@code creditCount}
 *       / {@code lastUpdate} fields carry the result and the popup
 *       can dismiss with a success toast.</li>
 *   <li>{@code OTP_REQUIRED} / {@code CAPTCHA_REQUIRED} /
 *       {@code QNA_REQUIRED} — Coocon stopped the scrape at the
 *       current bank's LOGIN. Frontend opens the matching dialog
 *       (the same dialog components Login Credentials Setup uses),
 *       collects the answer, calls the matching resume endpoint
 *       with {@code sessionId} echoed back.</li>
 *   <li>{@code ERROR} — terminal bank-side or gateway-side rejection;
 *       {@code errorCode} + {@code errorMessage} carry the user-visible
 *       reason. Popup shows the error dialog and tears down.</li>
 * </ul>
 *
 * <p>{@code @JsonInclude(NON_NULL)} drops the unused fields per status
 * so the wire shape stays tight for each terminal state. A
 * {@code COMPLETED} response carries the four count fields and
 * nothing else; a {@code QNA_REQUIRED} response carries
 * {@code sessionId} + {@code qnaQuestions} + {@code challengePrompt}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TransactionsSyncResponse(

        /** Server-issued session id — non-null on every non-terminal
         *  response. Frontend echoes back on the matching resume
         *  endpoint. {@code null} only on synchronous COMPLETED. */
        UUID sessionId,

        /** Discriminator — see class-level Javadoc. */
        Status status,

        /** ──── COMPLETED-only ──── */
        Integer accountsSynced,
        Integer debitCount,
        Integer creditCount,
        /** Sum of TRSC_AMT for every Coocon-returned IN (deposit) row
         *  in the scrape window — includes rows skipped by dedup,
         *  not just newly-inserted ones. Mirrors what the Latest
         *  Transaction History dialog displays from the DB so the
         *  popup totals stay stable across re-syncs of the same
         *  window. Serialised as a string (matches the rest of the
         *  banking wire) to keep VND precision intact across JS
         *  Number cast. Single currency assumed; multi-currency
         *  batches are deferred. */
        String creditTotal,
        /** Sum of TRSC_AMT for every Coocon-returned OUT (withdraw)
         *  row. See {@link #creditTotal}. */
        String debitTotal,
        String lastUpdate,

        /** ──── *_REQUIRED-only ──── */
        String challengePrompt,
        List<QnaQuestion> qnaQuestions,
        /** Base64-encoded CAPTCHA image bytes; decode + render as
         *  {@code data:image/{format};base64,...}. CAPTCHA-only. */
        String captchaImage,
        /** {@code "png"} or {@code "svg+xml"} per the legacy parity
         *  in nxisas_v2.js:1539-1542. CAPTCHA-only. */
        String captchaImageFormat,
        /** Bank-credential username for the account whose LOGIN raised
         *  the challenge — echoed back so the Security Question popup
         *  can render the registered Login ID. Populated on
         *  {@code QNA_REQUIRED} today; {@code null} for every other
         *  status (dropped from the wire by {@code @JsonInclude(NON_NULL)}). */
        String loginId,

        /** ──── ERROR-only ──── */
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

    /** Terminal success — every selected account synced. */
    public static TransactionsSyncResponse completed(
            UUID sessionId, int accountsSynced, int debitCount, int creditCount,
            BigDecimal debitTotal, BigDecimal creditTotal, String lastUpdate) {
        return new TransactionsSyncResponse(
                sessionId, Status.COMPLETED,
                accountsSynced, debitCount, creditCount,
                creditTotal == null ? "0" : creditTotal.toPlainString(),
                debitTotal  == null ? "0" : debitTotal.toPlainString(),
                lastUpdate,
                null, null, null, null, null,
                null, null);
    }

    /** OTP challenge — frontend opens the OTP dialog. */
    public static TransactionsSyncResponse otpChallenge(UUID sessionId, String prompt) {
        return new TransactionsSyncResponse(
                sessionId, Status.OTP_REQUIRED,
                null, null, null, null, null, null,
                prompt, null, null, null, null,
                null, null);
    }

    /** QNA challenge — frontend opens the QNA question-picker dialog.
     *  {@code loginId} is the credential username the LOGIN call sent;
     *  surfaced so the Security Question popup can render it in the
     *  read-only Login ID slot (parity with the legacy popup). */
    public static TransactionsSyncResponse qnaChallenge(
            UUID sessionId, String prompt, List<QnaQuestion> questions, String loginId) {
        return new TransactionsSyncResponse(
                sessionId, Status.QNA_REQUIRED,
                null, null, null, null, null, null,
                prompt, questions, null, null, loginId,
                null, null);
    }

    /** CAPTCHA challenge — frontend opens the CAPTCHA dialog. */
    public static TransactionsSyncResponse captchaChallenge(
            UUID sessionId, String prompt, String image, String format) {
        return new TransactionsSyncResponse(
                sessionId, Status.CAPTCHA_REQUIRED,
                null, null, null, null, null, null,
                prompt, null, image, format, null,
                null, null);
    }

    /** Terminal error — frontend tears down with the standard error dialog. */
    public static TransactionsSyncResponse error(UUID sessionId, String code, String message) {
        return new TransactionsSyncResponse(
                sessionId, Status.ERROR,
                null, null, null, null, null, null,
                null, null, null, null, null,
                code, message);
    }
}
