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

        @Size(max = 255)
        String beneName,

        @Size(max = 255)
        String beneAcctNo,

        @Size(max = 255)
        String bicCode,

        @Size(max = 255)
        String beneBankName,

        @Size(max = 20)
        String beneBankCode,

        @Size(max = 255)
        String beneAddr,

        /**
         * Beneficiary town / city — auto-filled read-only on the client
         * from the selected BIC's SWIFT directory entry
         * ({@code SWIFT_BIC_MST.PSTL_ADR_TWN_NM}). Persisted to
         * {@code TRCO_OVRS_INFM.BENE_TOWN_NM} (len 150). The client
         * enforces "required for Overseas + read-only"; the server stays
         * lenient (refactor parity stance — see {@code ContactsValidator}).
         */
        @Size(max = 150)
        String beneTownNm) { }
