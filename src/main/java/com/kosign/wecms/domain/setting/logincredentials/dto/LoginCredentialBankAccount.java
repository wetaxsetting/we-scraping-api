package com.kosign.wecms.domain.setting.logincredentials.dto;

/**
 * One linked bank account surfaced under a {@link LoginCredentialGroup}.
 *
 * <p>Mirrors the legacy {@code IDO.BANK_CERT_INFO_R005.xml} {@code REC_1}
 * json_agg element (`BANK_CD` / `ACCT_NO` / `ACCT_NICK_NM`), which the
 * Registered Login Credentials popup (legacy {@code comm_0218_006}) renders
 * in its "Bank Account" column: the first account's
 * {@code ACCT_NICK_NM + ' ' + ACCT_NO} plus a {@code +N} badge for the
 * remaining accounts.
 *
 * <p>Like the legacy subquery — keyed only on {@code (BIZ_KEY, BANK_CD)} —
 * the list is per-bank, so every credential row under the same bank shows
 * the same set of linked accounts.
 */
public record LoginCredentialBankAccount(
        String bankCd,
        String acctNo,
        String acctNickNm
) { }
