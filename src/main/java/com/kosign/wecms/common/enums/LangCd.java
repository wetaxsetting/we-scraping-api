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
}
