package com.kosign.wecms.domain.weinvoice.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/v1/auth/signup} body. Wire: {@code email, user_nm, user_pw, clph_no, cmpn_nm}.
 * Format rules (email shape, password strength) are checked in {@link WeInvoiceAccountService} so they answer with
 * their own message codes.
 */
public record SignupRequest(
        @NotBlank @Size(max = 100) String email,
        @NotBlank @Size(max = 100) String userNm,
        @NotBlank @Size(max = 100) String userPw,
        @Size(max = 30) String clphNo,
        @Size(max = 200) String cmpnNm) {

    @Override
    public String toString() {
        // Never print the password.
        return "SignupRequest[email=" + email + ", userNm=" + userNm + "]";
    }
}
