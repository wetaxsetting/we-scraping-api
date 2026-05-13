package com.kosign.wecms.domain.setting.contacts.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * One bank-account element in a Contact {@code POST} / {@code PUT}
 * payload (Local contact only). Bean-Validation enforces shape;
 * {@code ContactsValidator} enforces business rules (per-upsert
 * required-bank, duplicate-(BANK_NM, ACCT_NO) checks).
 */
public record AccountOp(
        @NotBlank
        @Pattern(regexp = "^(upsert|delete)$",
                message = "account op must be 'upsert' or 'delete'")
        String op,

        @NotBlank
        @Size(max = 20)
        String trcoAcctKey,

        @Size(max = 200)
        String bankNm,

        @Size(max = 100)
        String acctNo,

        @Size(max = 10)
        String napasBankCd,

        @Size(max = 8)
        String citadBankCd,

        @Size(max = 3)
        String acctCcyCd,

        @Size(max = 150)
        String fincMdaOwnerNm,

        @Size(max = 500)
        String owacAddr,

        Integer dispOrd) { }
