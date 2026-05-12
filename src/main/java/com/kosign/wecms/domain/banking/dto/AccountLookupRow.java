package com.kosign.wecms.domain.banking.dto;

/**
 * Narrow projection of {@code ACCT_MSTR} for the firm-banking-settings
 * Create dialog's Withdrawal Account autocomplete. Mirrors the legacy
 * {@code comm_1004_001} {@code REC} shape (raw {@code ACCT_NO} +
 * {@code CCY_CD}); adds a server-masked variant and the optional
 * {@code ACCT_NICK_NM} / {@code ACCT_NM} columns so the frontend can
 * render the dropdown row + sub-line without a second round-trip.
 *
 * <p>Field semantics:
 * <ul>
 *   <li>{@code acctNo} — the raw {@code ACCT_MSTR.ACCT_NO}. The
 *       frontend submits this verbatim as
 *       {@code wdrl_act_no} when the user picks a row.</li>
 *   <li>{@code acctNoMasked} — the same value through the shared
 *       {@code LogMask.acct(...)} helper (legacy presentation
 *       convention — middle 4 digits replaced with asterisks).
 *       Convenience for the dropdown chip / list-item display so
 *       the frontend doesn't need to re-implement masking.</li>
 *   <li>{@code ccyCd} — currency code. Frontend renders the
 *       {@code (VND)} / {@code (USD)} suffix in the dropdown row;
 *       the wire ships the raw 3-letter code only.</li>
 *   <li>{@code acctNickNm} — optional nickname (e.g.
 *       {@code "Shinhan VN2658"}). Null for most rows.</li>
 *   <li>{@code acctNm} — optional account holder name (e.g.
 *       {@code "CONG TY TNHH WEBCASH VIET NAM"}). Null for some
 *       rows.</li>
 * </ul>
 *
 * <p>Wire format: lowercase snake_case via the global Jackson
 * SNAKE_CASE strategy. Java fields stay camelCase.
 */
public record AccountLookupRow(
        String acctNo,
        String acctNoMasked,
        String ccyCd,
        String acctNickNm,
        String acctNm
) { }
