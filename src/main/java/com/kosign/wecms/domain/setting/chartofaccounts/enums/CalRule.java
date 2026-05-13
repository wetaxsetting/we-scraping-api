package com.kosign.wecms.domain.setting.chartofaccounts.enums;

import java.util.Optional;

/**
 * Sign-aggregation rule used by downstream financial reports
 * ({@code fnsh_0200_001} Income Statement / Trial Balance, deferred to
 * the Reports-module port). Mirrors legacy DEV column shape — CHAR(1)
 * holding either {@code 'P'} (plus, add) or {@code 'M'} (minus,
 * subtract). Nullable in the schema; service treats {@code null} as
 * "no rule".
 *
 * <p>Modernized refactor enum per
 * {@code wecms-api-main/CLAUDE.md §"Domain enums"}: carries
 * {@code code()} only — no JEX {@code GenericEnum}, no
 * {@code @JsonValue} / {@code @JsonCreator}. The DB / entity column
 * stays {@code String}; the enum is used for service-layer validation
 * via {@link #fromCode(String)} and for any future computed projection
 * that needs the sign.
 *
 * <p>No {@code i18nKey()} today — there is no localized rendering of
 * the sign on the wire (frontend renders the raw {@code '+'} / {@code '-'}
 * glyph). Add a key if/when a feature needs a localized label.
 */
public enum CalRule {

    /** {@code P} — plus / add. */
    PLUS("P"),

    /** {@code M} — minus / subtract. */
    MINUS("M");

    private final String code;

    CalRule(String code) {
        this.code = code;
    }

    /** Raw 1-char code stored in {@code CAL_RULE}. */
    public String code() {
        return code;
    }

    /** Resolve a raw {@code CAL_RULE} string to its enum constant.
     *  Returns {@link Optional#empty()} for unknown / blank / null. */
    public static Optional<CalRule> fromCode(String code) {
        if (code == null) return Optional.empty();
        String trimmed = code.trim();
        if (trimmed.isEmpty()) return Optional.empty();
        for (CalRule r : values()) {
            if (r.code.equals(trimmed)) return Optional.of(r);
        }
        return Optional.empty();
    }
}
