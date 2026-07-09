package com.kosign.wecms.domain.setting.logincredentials.dto;

import com.kosign.wecms.domain.banking.dto.RegisterAccountsRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Atomic Save payload for {@code POST /api/v1/login-credentials/bank/register}
 * — the Select Bank Accounts "Save" action.
 *
 * <p>Carries the bank credential (verified inline, NOT yet persisted)
 * plus the accounts the user picked. The service persists
 * {@code BANK_CERT_INFO} + {@code ACCT_MSTR} + {@code ACCT_AUTH_INFO}
 * (+ audit) in ONE transaction, so the legacy {@code fnsh_0101_001_c001}
 * "all-or-nothing on Save" guarantee holds: if no accounts are saved,
 * no {@code BANK_CERT_INFO} row remains. The credential fields mirror
 * {@link BankCredentialCreateRequest}; {@code accounts} mirrors
 * {@link RegisterAccountsRequest#accounts()} (≥ 1 required — an empty
 * list rejects before any row is written).
 */
public record BankCredentialRegisterRequest(
        @NotBlank @Size(max = 20)   String bankCd,
        @NotBlank @Size(max = 2000) String loginId,
        @NotBlank @Size(max = 2000) String password,
        BigDecimal                  nxisasGbSeqNo,
        @Size(max = 2000)           String extra,
        @Size(max = 2000)           String loginType,

        @NotEmpty
        @Valid
        List<RegisterAccountsRequest.AccountEntry> accounts) {

    /** Project the credential fields onto the existing create DTO so the
     *  one-row insert logic is reused verbatim (single source of truth). */
    public BankCredentialCreateRequest toCreateRequest() {
        return new BankCredentialCreateRequest(
                bankCd, loginId, password, nxisasGbSeqNo, extra, loginType);
    }
}
