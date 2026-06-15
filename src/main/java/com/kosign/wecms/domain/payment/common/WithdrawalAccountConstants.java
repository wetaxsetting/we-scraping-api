package com.kosign.wecms.domain.payment.common;

import java.util.List;

/**
 * Legacy-parity constants for the payment Excel-Upload "Withdrawal
 * Bank/Account No." picker (legacy {@code comm_1001_001}).
 *
 * <p>Shared by every withdrawal-account consumer (Internal Transfer,
 * Vendor, Expense, Payroll, Overseas) via
 * {@link WithdrawalAccountListService}. Legacy {@code comm_1001_001} is
 * module-agnostic — it does not branch by payment type — so one constant
 * set serves all five. Moved here from
 * {@code internaltransfer.InternalTransferConstants} as part of the
 * withdrawal-account-listing consolidation (Option A, see
 * {@code docs/audits/vendor-expense-payroll-withdrawal-account-remediation-plan-2026-06-10.md}).
 */
public final class WithdrawalAccountConstants {

    private WithdrawalAccountConstants() {
    }

    /**
     * G1 — banks whose accounts may appear in the withdrawal-account
     * dropdown. Legacy {@code comm_1001_001_act.jsp:31} hard-restricts to
     * these two firm-banking-integrated banks:
     * Shinhan VN ({@code 11704088}) + Woori VN ({@code 11704020}).
     */
    public static final List<String> WITHDRAWAL_BANK_CDS = List.of("11704088", "11704020");

    /**
     * G2 — {@code ACCT_AUTH_INFO} authority types that grant
     * withdrawal-account visibility. Legacy {@code comm_1001_001_act.jsp:51}
     * requires {@code AUTH_TYPE IN ('V','W')} (View or Withdraw).
     */
    public static final String AUTH_TYPE_VIEW = "V";
    public static final String AUTH_TYPE_WITHDRAW = "W";
}
