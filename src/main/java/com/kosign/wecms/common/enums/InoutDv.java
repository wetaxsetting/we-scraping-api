package com.kosign.wecms.common.enums;

import java.util.Optional;

/**
 * Deposit / withdraw direction code — value set of the legacy
 * {@code ACCT_TRNS_HSTR.INOUT_DV} column. Mirrors legacy enum
 * {@code wabooks.enums.InoutDv} at
 * {@code WECMS DEV/WECMS/src/wabooks/enums/InoutDv.java:6-10}.
 *
 * <p><b>Modernized refactor enum.</b> Carries {@code (code, i18nKey)} only —
 * no JEX {@code GenericEnum}, no {@code @JsonValue} / {@code @JsonCreator},
 * no {@code getLabel()} (the legacy enum returned hard-coded Korean
 * labels {@code 입금} / {@code 출금} / {@code 미정의}).
 *
 * <p><b>DB encoding verified against</b> the {@code wecmsvndev} seed on
 * 2026-04-29 — the column stores {@code '1'} for deposit and {@code '2'}
 * for withdraw. The earlier refactor code path used {@code 'I'} / {@code 'O'}
 * literals, which never matched the live encoding (silent zero totals).
 * That bug is fixed in {@link com.kosign.wecms.domain.banking.service.TransactionService}
 * by routing through this enum.
 *
 * <p>Per the workspace task rule "Do not use {@code EnumType.ORDINAL}",
 * this enum is <b>not</b> persisted under any {@code @Enumerated}
 * mapping. The DB column stays {@code VARCHAR(1)} holding the legacy
 * single-char code.
 *
 * <p>The legacy enum carried a third constant {@code ETC("0")}; it is
 * preserved here so a future caller resolving an unknown / pending row
 * can match the legacy code set without an out-of-band check.
 */
public enum InoutDv {

    /** {@code 1} — deposit (incoming). Legacy Korean label: {@code 입금}. */
    IN("1", "tx.inout.in"),

    /** {@code 2} — withdraw (outgoing). Legacy Korean label: {@code 출금}. */
    OUT("2", "tx.inout.out"),

    /** {@code 0} — undefined / unclassified. Legacy Korean label: {@code 미정의}.
     *  Rare in real data but defined by the legacy enum; preserved for
     *  parity. {@link #i18nKey()} is left {@code null} until a verified
     *  English/Korean/Vietnamese label is registered. */
    ETC("0", null);

    private final String code;
    private final String i18nKey;

    InoutDv(String code, String i18nKey) {
        this.code = code;
        this.i18nKey = i18nKey;
    }

    /** Raw 1-char code stored in {@code ACCT_TRNS_HSTR.INOUT_DV}. */
    public String code() {
        return code;
    }

    /** Frontend i18n key — {@code tx.inout.<name>}. {@code null} when no
     *  verified translation exists yet. Frontend treats {@code null} as
     *  "render raw {@code inout_dv}". */
    public String i18nKey() {
        return i18nKey;
    }

    /** Resolve a raw {@code INOUT_DV} string to its enum constant.
     *  Returns {@link Optional#empty()} for unknown / blank input.
     *  Whitespace is trimmed; lookup is case-sensitive (legacy values
     *  are numeric only). */
    public static Optional<InoutDv> fromCode(String code) {
        if (code == null) return Optional.empty();
        String trimmed = code.trim();
        if (trimmed.isEmpty()) return Optional.empty();
        for (InoutDv d : values()) {
            if (d.code.equals(trimmed)) return Optional.of(d);
        }
        return Optional.empty();
    }
}
