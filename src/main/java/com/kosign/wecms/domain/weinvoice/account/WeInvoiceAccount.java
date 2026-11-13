package com.kosign.wecms.domain.weinvoice.account;

/**
 * One row of {@code winv_user_infm}: an account that signs in to WeInvoice without a WeCMS {@code USER_INFM} row
 * (docs/features/addon-service/weinvoice/weinvoice-self-signup-2026-11-13.md). {@code userPw} is the stored hash.
 */
public record WeInvoiceAccount(
        String userId,
        String userNm,
        String eml,
        String clphNo,
        String cmpnNm,
        String userPw,
        String langCd,
        boolean active,
        boolean locked,
        boolean deleted,
        int failCnt) {

    @Override
    public String toString() {
        // Never print the hash.
        return "WeInvoiceAccount[userId=" + userId + ", active=" + active + ", locked=" + locked + ", deleted=" + deleted + "]";
    }
}
