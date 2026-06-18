package com.kosign.wecms.domain.setting.bankaccount.service;

import java.util.Set;

/**
 * Legacy withdrawal-permission bank whitelist for Settings &gt; Bank Account.
 *
 * <p>Withdraw permission ({@code AUTH_TYPE='W'}) may only be granted on the
 * five firm-banking banks that support outbound withdrawal. Mirrors the legacy
 * {@code comm_0183_002_u001_act.jsp:160-193} {@code grpBankCd} whitelist (throws
 * {@code WBE0452} otherwise), the list-grid column gate in
 * {@code comm_0183_001.ts:591-606}, and the REC_2 sub-query filter in
 * {@code IDO.ACCT_MSTR_R012.xml:123}.
 *
 * <p>Business decision (2026-06-16): keep the legacy 5-bank restriction.
 * Held here as the single source of truth so both the permission grant guard
 * ({@code BankAccountPermissionService.replace}) and the registration fan-out
 * ({@code AccountRegistrationService.fanOutAccountAuth}) agree.
 */
public final class WithdrawalBankPolicy {

    private WithdrawalBankPolicy() {
    }

    /** The five banks for which Withdraw permission is legal (verbatim legacy codes). */
    public static final Set<String> ALLOWED_BANK_CDS = Set.of(
            "11704004", // Vietcombank
            "11704003", // IBK
            "11704088", // Shinhan
            "11704020", // Woori
            "11704001"  // Vietin
    );

    /** {@code true} when {@code bankCd} is one of the withdrawal-capable banks. */
    public static boolean isWithdrawalAllowed(String bankCd) {
        return bankCd != null && ALLOWED_BANK_CDS.contains(bankCd.trim());
    }
}
