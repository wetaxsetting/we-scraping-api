package com.kosign.wecms.domain.setting.contacts.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * One overseas-beneficiary element in a Contact {@code POST} /
 * {@code PUT} payload (Overseas contact only). Bean-Validation enforces
 * shape; {@code ContactsValidator} enforces business rules
 * (duplicate-(BENE_ACCT_NO, BIC_CODE) checks).
 */
public record OverseasOp(
        @NotBlank
        @Pattern(regexp = "^(upsert|delete)$",
                message = "overseas op must be 'upsert' or 'delete'")
        String op,

        @NotBlank
        @Size(max = 20)
        String trcoOvrsKey,

        @Size(max = 200)
        String beneName,

        @Size(max = 100)
        String beneAcctNo,

        @Size(max = 50)
        String bicCode,

        @Size(max = 200)
        String beneBankName,

        @Size(max = 20)
        String beneBankCode,

        @Size(max = 500)
        String beneAddr) { }
