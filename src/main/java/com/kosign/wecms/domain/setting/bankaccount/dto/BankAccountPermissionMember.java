package com.kosign.wecms.domain.setting.bankaccount.dto;

/**
 * One user row in the View / Withdrawal Permission dialog roster.
 *
 * <p>Members are sourced from {@code USER_INFM} (every active user in
 * the tenant) joined LEFT against {@code ACCT_AUTH_INFO} so the
 * {@code isSelected} flag is true when an active permission row exists
 * for {@code (acctId, authType, userId)}.
 *
 * <p>{@code isLocked} marks rows the dialog must render disabled:
 * admins (per {@code BUSI_ADMN_INFM}) always hold both permissions, and
 * a non-admin caller cannot revoke their own access. {@code lockReason}
 * carries the discriminator so the frontend tooltip can explain which
 * rule fired.
 */
public record BankAccountPermissionMember(
        String  userId,
        String  userNm,
        /** {@code ADMIN} when {@code BUSI_ADMN_INFM} carries a row for
         *  this user in the active company; {@code USER} otherwise. */
        String  userType,
        /** Masked phone for display ({@code +84••••1234}). May be null
         *  when {@code USER_INFM.PHONE_NO} is empty. */
        String  phoneNo,
        boolean isSelected,
        boolean isLocked,
        /** {@code ADMIN} | {@code SELF} | null. */
        String  lockReason
) { }
