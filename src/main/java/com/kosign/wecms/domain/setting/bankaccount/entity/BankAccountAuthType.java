package com.kosign.wecms.domain.setting.bankaccount.entity;

import java.util.Optional;

/**
 * Modernized refactor enum for {@code ACCT_AUTH_INFO.AUTH_TYPE}.
 * Mirrors legacy {@code wabooks.enums.AuthType} (V/W/F codes).
 *
 * <p>Follows the standard enum shape from
 * {@code wecms-api/CLAUDE.md §"Domain enums"} — stable
 * {@link #code()} accessor, optional {@link #i18nKey()}, lenient
 * {@link #fromCode(String)} for service-side parsing. Frontend never
 * receives this enum directly — Settings &gt; Bank Account exposes
 * the {@code VIEW} / {@code WITHDRAWAL} wire names on the URL query
 * (`?type=VIEW`) and the service translates with {@link #fromWireName}.
 *
 * <p>{@code FULL_ACCESS} ('F') is included for legacy DEV data
 * compatibility but not exposed on the wire — the Permission dialog
 * is split into two kinds (View + Withdrawal); a future "Full
 * Access" tab would consume it.
 */
public enum BankAccountAuthType {

    VIEW       ("V", "VIEW",       "bank.auth_type.v"),
    WITHDRAWAL ("W", "WITHDRAWAL", "bank.auth_type.w"),
    FULL_ACCESS("F", "FULL_ACCESS","bank.auth_type.f");

    private final String code;
    private final String wireName;
    private final String i18nKey;

    BankAccountAuthType(String code, String wireName, String i18nKey) {
        this.code = code;
        this.wireName = wireName;
        this.i18nKey = i18nKey;
    }

    /** Legacy DB column value — {@code 'V'} / {@code 'W'} / {@code 'F'}. */
    public String code() { return code; }

    /** Frontend wire name — {@code VIEW} / {@code WITHDRAWAL} / {@code FULL_ACCESS}. */
    public String wireName() { return wireName; }

    public String i18nKey() { return i18nKey; }

    /** Service-side parser for the DB code. Returns empty for unknown values. */
    public static Optional<BankAccountAuthType> fromCode(String raw) {
        if (raw == null) return Optional.empty();
        for (BankAccountAuthType t : values()) {
            if (t.code.equals(raw)) return Optional.of(t);
        }
        return Optional.empty();
    }

    /** Service-side parser for the wire name (uppercase). */
    public static Optional<BankAccountAuthType> fromWireName(String raw) {
        if (raw == null) return Optional.empty();
        for (BankAccountAuthType t : values()) {
            if (t.wireName.equals(raw)) return Optional.of(t);
        }
        return Optional.empty();
    }
}
