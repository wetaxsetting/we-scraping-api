package com.kosign.wecms.domain.payment.match.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * P3 manual-candidate parity helper — converts a payment's UTC-stored
 * {@code TRNF_DTM} ({@code yyyyMMddHHmmss}) to the local (Vietnam, UTC+7)
 * {@code HHmmss} used by the manual candidate transfer-time filter.
 *
 * <p>Legacy parity: the manual "payment → banking" candidate queries
 * ({@code comm_0197_001_r001} — domestic {@code putBankingCandidateIdoIn},
 * overseas {@code …Ovrs}, payroll {@code …Sumr}) compute the filter time as
 * {@code DateTimeUtils.normalizeResult(sumr.getTrnfDtm(), loginUser)} then take
 * {@code substring(8,14)}, and apply {@code (TRSC_TM IS NULL OR TRSC_TM = ''
 * OR TRSC_TM >= <thatTime>)}. The refactor stores {@code TRNF_DTM} in UTC
 * (see the dispatch services' {@code OffsetDateTime.now(...).withOffsetSameInstant(UTC)})
 * while bank {@code ACCT_TRNS_HSTR.TRSC_TM} is the raw scraped Vietnam-local
 * time, so the payment time must be shifted to the display zone before the
 * comparison — exactly what {@code normalizeResult} does.
 *
 * <p>Uses the same {@code Asia/Ho_Chi_Minh} display zone + UTC→zone idiom as
 * {@code TransferHistoryQueryService.toDisplayZone14}. Returns {@code null}
 * when the input is absent / malformed so the caller passes {@code null} and
 * the query skips the filter entirely (mirrors the legacy
 * {@code if (sumr.getTrnfDtm() != null)} guard).
 */
public final class MatchCandidateTime {

    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DTM14 = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter HHMMSS = DateTimeFormatter.ofPattern("HHmmss");

    private MatchCandidateTime() { }

    /**
     * @param utcDtm14 the payment parent-summary {@code TRNF_DTM} (UTC,
     *                 {@code yyyyMMddHHmmss})
     * @return the local {@code HHmmss} floor for the candidate time filter, or
     *         {@code null} when {@code utcDtm14} is null / shorter than 14 /
     *         unparseable (⇒ filter skipped)
     */
    public static String localTransferTime(String utcDtm14) {
        if (utcDtm14 == null) return null;
        String trimmed = utcDtm14.trim();
        if (trimmed.length() < 14) return null;
        try {
            return LocalDateTime.parse(trimmed.substring(0, 14), DTM14)
                    .atZone(ZoneOffset.UTC)
                    .withZoneSameInstant(DISPLAY_ZONE)
                    .format(HHMMSS);
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
