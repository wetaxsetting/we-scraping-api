package com.kosign.wecms.common.enums;

import java.util.Optional;

/**
 * Account type code — value set of the legacy {@code ACCT_MSTR.ACCT_TYPE_CD}
 * column. Mirrors legacy enum {@code wabooks.enums.AcctTypeCd} at
 * {@code WECMS DEV/WECMS/src/wabooks/enums/AcctTypeCd.java:6-15}
 * (8 codes; legacy Korean labels are at lines 41-48).
 *
 * <p><b>Modernized refactor enum.</b> Carries {@code (code, i18nKey)} only —
 * no JEX {@code GenericEnum}, no {@code @JsonValue} / {@code @JsonCreator},
 * no {@code getLabel()}. Localised display strings live in the frontend
 * {@code locales/{en,ko,vi}.json} files under the {@code bank.acct_type.*}
 * namespace.
 *
 * <p>Replaces the static {@code Map<String,String>} in
 * {@link com.kosign.wecms.domain.banking.service.BankCdResolver}'s
 * legacy {@code ACCT_TYPES} table — the English labels there were
 * documented as a stopgap until a localisable enum surface landed.
 *
 * <p>Per the workspace task rule "Do not use {@code EnumType.ORDINAL}",
 * this enum is <b>not</b> persisted under any {@code @Enumerated}
 * mapping. The DB column stays {@code VARCHAR(2)} holding the legacy
 * 2-char code.
 *
 * <p><b>Coverage notes</b> (mirrors {@code BankCdResolver}'s class Javadoc):
 * <ul>
 *   <li>{@code 01} (LOAN) — intentionally unmapped on Account Inquiry;
 *       loans never appear there. The legacy
 *       {@code FnshHelper.fnsh_0001_001_r001} filter excludes them on
 *       both VND and FCY pages.</li>
 *   <li>{@code 02}–{@code 08} — mapped. Active tenant data
 *       ({@code VN_VNM0004_000001}, 2026-05-03 snapshot) exercises
 *       {@code 02, 03, 04, 05, 07}; {@code 06} / {@code 08} are
 *       forward-looking — defined by the legacy enum but not currently
 *       used by this tenant.</li>
 * </ul>
 *
 * <p><b>i18n key labels</b> — verified English labels match the
 * pre-refactor {@code BankCdResolver.ACCT_TYPES} map verbatim
 * (Fixed / Current / Foreign Current / Savings / Foreign Savings /
 * Current Accounts / Saving Accounts). The asymmetric prefix
 * ({@code Foreign Current} for {@code 04} vs. {@code Current Accounts}
 * for {@code 07}) is intentional and preserves the legacy distinction.
 * Korean / Vietnamese translations are NOT registered here — they live
 * in the frontend locale files (or stay unresolved per the workspace
 * "do not invent translations" rule).
 */
public enum AcctTypeCd {

    /** {@code 01} — 대출금 / Loan. Intentionally has no i18n key — Account
     *  Inquiry filters this code out at the SQL layer; no UI surface
     *  ever needs to render it. Keeps the legacy code set complete. */
    LOAN("01", null),

    /** {@code 02} — 정기예적금 / Time Deposit. Legacy English label was
     *  {@code "Fixed"} (matches the pre-refactor static map). */
    DEPOSIT("02", "bank.acct_type.02"),

    /** {@code 03} — 입출식예금 / KHR current. Legacy English label
     *  {@code "Current"}. */
    CURRENT_KHR("03", "bank.acct_type.03"),

    /** {@code 04} — 외화입출식예금 / USD current. Legacy English label
     *  {@code "Foreign Current"}. */
    CURRENT_USD("04", "bank.acct_type.04"),

    /** {@code 05} — 보통예금 / KHR savings. Legacy English label
     *  {@code "Savings"}. */
    SAVINGS_KHR("05", "bank.acct_type.05"),

    /** {@code 06} — 외화보통예금 / USD savings. Legacy English label
     *  {@code "Foreign Savings"}. */
    SAVINGS_USD("06", "bank.acct_type.06"),

    /** {@code 07} — KRW current. Legacy English label
     *  {@code "Current Accounts"} (Korean source already English). */
    CURRENT_KRW("07", "bank.acct_type.07"),

    /** {@code 08} — KRW savings. Legacy English label
     *  {@code "Saving Accounts"} (Korean source already English). */
    SAVINGS_KRW("08", "bank.acct_type.08");

    private final String code;
    private final String i18nKey;

    AcctTypeCd(String code, String i18nKey) {
        this.code = code;
        this.i18nKey = i18nKey;
    }

    /** Raw 2-char code stored in {@code ACCT_MSTR.ACCT_TYPE_CD}. */
    public String code() {
        return code;
    }

    /** Frontend i18n key — {@code bank.acct_type.<code>}. {@code null}
     *  for codes intentionally not surfaced by Sprint 1 ({@code 01}).
     *  Frontend treats {@code null} as "fall back to the raw
     *  {@code acct_type_nm} on the wire". */
    public String i18nKey() {
        return i18nKey;
    }

    /** Resolve a raw {@code ACCT_TYPE_CD} string to its enum constant.
     *  Returns {@link Optional#empty()} for unknown / blank input.
     *  Whitespace is trimmed; lookup is case-sensitive (legacy values
     *  are numeric only). */
    public static Optional<AcctTypeCd> fromCode(String code) {
        if (code == null) return Optional.empty();
        String trimmed = code.trim();
        if (trimmed.isEmpty()) return Optional.empty();
        for (AcctTypeCd t : values()) {
            if (t.code.equals(trimmed)) return Optional.of(t);
        }
        return Optional.empty();
    }
}
