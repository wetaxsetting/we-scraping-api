package com.kosign.wecms.common.enums;

import java.util.Optional;

/**
 * Active/Inactive status code — value set of {@code BUSI_VAS_ACCT_INFM.STTS}
 * and the wider legacy {@code STTS} column convention (any table where
 * Active/Inactive is the discriminator). Mirrors legacy
 * {@code wabooks.enums.AStts} at
 * {@code WECMS DEV/WECMS/src/wabooks/enums/AStts.java:8-12}.
 *
 * <p><b>Modernized refactor enum.</b> Carries {@code (code, i18nKey)} only —
 * no JEX {@code GenericEnum}, no {@code @JsonValue} / {@code @JsonCreator},
 * no {@code getLabel()}. Localised display strings live under the
 * {@code settings.vas_account.status.*} namespace; other consumers of this
 * enum may register their own keys when they land.
 *
 * <p>DB column stays {@code CHAR(1)}; entity maps as {@code String}.
 */
public enum AStts {

    /** {@code A} — Active. */
    ACTIVE("A", "settings.vas_account.status.active"),

    /** {@code I} — Inactive. */
    INACTIVE("I", "settings.vas_account.status.inactive");

    private final String code;
    private final String i18nKey;

    AStts(String code, String i18nKey) {
        this.code = code;
        this.i18nKey = i18nKey;
    }

    /** Raw 1-char code stored in {@code STTS}. */
    public String code() {
        return code;
    }

    /** Frontend i18n key — {@code settings.vas_account.status.{active,inactive}}. */
    public String i18nKey() {
        return i18nKey;
    }

    /** Resolve a raw {@code STTS} string to its enum constant.
     *  Returns {@link Optional#empty()} for unknown / blank input. */
    public static Optional<AStts> fromCode(String code) {
        if (code == null) return Optional.empty();
        String trimmed = code.trim();
        if (trimmed.isEmpty()) return Optional.empty();
        for (AStts t : values()) {
            if (t.code.equals(trimmed)) return Optional.of(t);
        }
        return Optional.empty();
    }
}
