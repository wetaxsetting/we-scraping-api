package com.kosign.wecms.common.enums;

import java.util.Locale;

/**
 * WeCMS language codes carried in the JWT {@code langCd} claim and the
 * {@code LANG_CD} field of API responses. Mirrors the legacy JEX set:
 * English, Korean, Khmer, Vietnamese.
 */
public enum LangCd {
    EN,
    KO,
    KM,
    VI;

    public static LangCd fromAcceptLanguage(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) return EN;
        String primary = acceptLanguage.split(",")[0].trim().split("-")[0].toLowerCase(Locale.ROOT);
        return switch (primary) {
            case "ko" -> KO;
            case "km" -> KM;
            case "vi" -> VI;
            default   -> EN;
        };
    }

    public static LangCd fromCodeOrDefault(String code) {
        if (code == null || code.isBlank()) return EN;
        try {
            return LangCd.valueOf(code.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return EN;
        }
    }

    /**
     * Map this enum to a JVM {@link Locale} suitable for
     * {@code MessageSource} lookups. Used by the shared
     * {@code AuditLogService} when fanning out one audit event into N
     * locale-specific rows. Constants from
     * {@link Locale#ENGLISH}/{@code KOREAN} are reused; {@code VI} and
     * {@code KM} have no JVM constants so a fresh
     * {@code Locale.of(...)} is built.
     */
    public Locale toLocale() {
        return switch (this) {
            case EN -> Locale.ENGLISH;
            case KO -> Locale.KOREAN;
            case VI -> Locale.of("vi");
            case KM -> Locale.of("km");
        };
    }
}
