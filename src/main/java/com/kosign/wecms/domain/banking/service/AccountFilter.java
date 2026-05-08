package com.kosign.wecms.domain.banking.service;

/**
 * Server-side filter chain for Account Inquiry list / export. Mirrors
 * the JEX {@code fnsh_0001_001_r001} input set (less the inputs the
 * refactor does not yet support: {@code HAND_REG_YN}, {@code
 * ACCT_CUST_TYPE}, date range — gated on PO confirmation).
 *
 * <p>{@code currency} accepts the wire vocabulary the controller already
 * speaks: {@code VND}, {@code FCY}, {@code ALL}, or any concrete CCY
 * code (e.g. {@code USD}, {@code KRW}). Resolution to a SQL predicate
 * happens in {@link AccountService}.
 *
 * <p>{@code bankCd} is an exact-match {@code BANK_CD} value sourced from
 * the on-screen bank dropdown (the dropdown's option key already carries
 * the legacy code, not the display name). {@code keyword} is the
 * free-text input matched case-insensitively against {@code ACCT_NO},
 * {@code ACCT_NICK_NM}, and {@code ACCT_NM}; the service handles
 * {@code %}/{@code _} escaping before binding to JPQL.
 *
 * <p>All three values are nullable; null = "no filter on this dimension".
 */
public record AccountFilter(String currency, String bankCd, String keyword) {

    /** Convenience for the legacy single-arg call sites that only need a
     *  currency filter (existing public {@code list(currency, …)}
     *  overloads). */
    public static AccountFilter currencyOnly(String currency) {
        return new AccountFilter(currency, null, null);
    }
}
