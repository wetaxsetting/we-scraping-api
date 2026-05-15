package com.kosign.wecms.domain.setting.bankaccount.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Bulk-register payload sent by the Login Credentials / Bank Account
 * Add-chain after the user picks accounts from a successful Coocon
 * scrape. Mirrors the legacy
 * {@code comm_0218_003_c001_act.jsp + select_bank_accounts_popup} flow
 * where the picked accounts INSERT into {@code ACCT_MSTR} with
 * `ACTV_YN='Y'` + `REG_*` audit columns.
 */
public record BankAccountRegisterRequest(
        @NotBlank @Size(max = 20) String bankCd,
        @Valid @NotEmpty List<Item> items
) {
    /** One picked account row. The frontend ships the fields it
     *  collected from the Coocon scrape + the editable Account Type +
     *  Nickname inputs in {@code <SelectBankAccountsDialog>}. */
    public record Item(
            @NotBlank @Size(max = 100) String acctNo,
            /** ISO currency (VND / USD / KRW / …). Required because
             *  `ACCT_MSTR.CCY_CD` is NOT NULL. */
            @NotBlank @Size(max = 3)   String currency,
            /** Legacy 2-char `ACCT_TYPE_CD`. Frontend translates from
             *  the dropdown; defaults to "02" (Current) when the user
             *  doesn't pick. Required because the column is NOT NULL. */
            @NotBlank @Size(max = 2)   String accountTypeCd,
            @Size(max = 20)            String nickname,
            @Size(max = 200)           String description,
            /** Optional account-name display (Coocon-supplied). */
            @Size(max = 100)           String acctNm
    ) { }
}
