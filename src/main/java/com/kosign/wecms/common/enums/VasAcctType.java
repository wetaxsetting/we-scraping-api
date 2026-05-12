package com.kosign.wecms.common.enums;

import java.util.Optional;

/**
 * VAS account type code — value set of {@code BUSI_VAS_ACCT_INFM.VAS_ACCT_TYPE}.
 * Mirrors legacy {@code wabooks.enums.VasAcctType} at
 * {@code WECMS DEV/WECMS/src/wabooks/enums/VasAcctType.java:8-12}.
 *
 * <p><b>Modernized refactor enum.</b> Carries {@code (code, i18nKey)} only —
 * no JEX {@code GenericEnum}, no {@code @JsonValue} / {@code @JsonCreator},
 * no {@code getLabel()}. Localised display strings live in the frontend
 * locale files under the {@code settings.vas_account.type.*} namespace.
 *
 * <p>DB column stays {@code CHAR(1)} holding the legacy code; the entity
 * maps it as {@code String} (refactor enum policy — no {@code EnumType.STRING}
 * on a column whose value differs from the enum constant name).
 */
public enum VasAcctType {

    /** {@code 0} — Dual balance (both credit and debit). */
    DUAL("0", "settings.vas_account.type.dual"),

    /** {@code 1} — Credit balance only. */
    CREDIT("1", "settings.vas_account.type.credit"),

    /** {@code 2} — Debit balance only. */
    DEBIT("2", "settings.vas_account.type.debit");

    private final String code;
    private final String i18nKey;

    VasAcctType(String code, String i18nKey) {
        this.code = code;
        this.i18nKey = i18nKey;
    }

    /** Raw 1-char code stored in {@code VAS_ACCT_TYPE}. */
    public String code() {
        return code;
    }

    /** Frontend i18n key — {@code settings.vas_account.type.{dual,credit,debit}}. */
    public String i18nKey() {
        return i18nKey;
    }

    /** Resolve a raw {@code VAS_ACCT_TYPE} string to its enum constant.
     *  Returns {@link Optional#empty()} for unknown / blank input. */
    public static Optional<VasAcctType> fromCode(String code) {
        if (code == null) return Optional.empty();
        String trimmed = code.trim();
        if (trimmed.isEmpty()) return Optional.empty();
        for (VasAcctType t : values()) {
            if (t.code.equals(trimmed)) return Optional.of(t);
        }
        return Optional.empty();
    }
}
