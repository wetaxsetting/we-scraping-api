package com.kosign.wecms.common.enums;

import java.util.Optional;

/**
 * Per-tenant product type for the refactor's recognised set —
 * {@code BUSI_INFM.PRDT_TYPE} restricted to the three letters the
 * refactor explicitly supports today.
 *
 * <p>Scope: only {@link #F} (WeCMS default), {@link #M} (WeCMS Lumens),
 * and {@link #H} (WeCMS HAENGSUNG Electronics) have enum constants.
 * Legacy DEV stores 11 additional letters ({@code W} WABOOKS Default,
 * {@code R} WABOOKS Pro, {@code B} WABOOKS Banking, {@code E} WABOOKS
 * Basic, {@code U} WABOOKS Plus, {@code L} WABOOKS Standard,
 * {@code S} WABOOKS Sales, {@code P} WABOOKS Payroll, {@code C}
 * WABOOKS Expense, {@code D} WABOOKS Expense+, {@code T} WABOOKS for
 * Tax Accountant); they are carried verbatim on the wire and JWT but
 * intentionally not modelled here — adding a constant happens when a
 * real consumer for that letter lands.
 *
 * <p>Behavioural gates for the three known values
 * (e-Invoice access for {@code F}, readonly overrides for {@code M},
 * HAENGSUNG payment-table-2 / bank-{@code 11704020} account masking /
 * hidden expense file column / daily-financial-HS report gating for
 * {@code H}) are deliberately deferred. This enum and its plumbing
 * surface only — the gates land in the MR that ports each matching
 * feature.
 *
 * <p>Modernised enum policy (see
 * {@code wecms-api-main/CLAUDE.md §Domain enums}): {@code code()}
 * exposes the verbatim DB letter; {@code i18nKey()} returns the
 * frontend i18n key under {@code auth.prdt_type.*}; no
 * {@code @JsonValue}, no {@code @Enumerated}, no localised label
 * inside the enum body.
 */
public enum PrdtType {

    /** WeCMS default — used by R&D tenant {@code VN_VNM0004_000001}.
     *  Enables e-Invoice in legacy (gate deferred). */
    F("F", "auth.prdt_type.f"),

    /** WeCMS Lumens — different readonly behaviour than {@code F} and
     *  {@code H} in legacy (gate deferred). No local R&D tenant yet. */
    M("M", "auth.prdt_type.m"),

    /** WeCMS HAENGSUNG Electronics — used by R&D tenant
     *  {@code VN_VNM0004_000014}. Different payment-table layout, account
     *  masking for bank {@code 11704020}, hidden expense file column,
     *  dedicated daily-financial-HS report in legacy (gates deferred). */
    H("H", "auth.prdt_type.h");

    private final String code;
    private final String i18nKey;

    PrdtType(String code, String i18nKey) {
        this.code = code;
        this.i18nKey = i18nKey;
    }

    /** Verbatim DB value — uppercase one-character code. */
    public String code() {
        return code;
    }

    /** Frontend i18n key for the user-facing product label. */
    public String i18nKey() {
        return i18nKey;
    }

    /**
     * Null-safe parse of a raw DB letter into the recognised set.
     *
     * <p>Returns {@link Optional#empty()} for {@code null}, blanks,
     * and any unrecognised letter — including the 11 deferred legacy
     * letters. Callers that need to gate behaviour on the recognised
     * set use {@link Optional#isPresent()}; callers that only need to
     * carry the value verbatim (JWT claim, wire field) use the raw
     * String directly without consulting this method.
     */
    public static Optional<PrdtType> fromCode(String raw) {
        if (raw == null) return Optional.empty();
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return Optional.empty();
        for (PrdtType t : values()) {
            if (t.code.equals(trimmed)) return Optional.of(t);
        }
        return Optional.empty();
    }
}
