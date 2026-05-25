package com.kosign.wecms.domain.setting.contacts.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request body for {@code POST /api/v1/settings/contacts}. Validation
 * here is shape-only; {@code ContactsValidator} enforces business
 * rules (duplicate {@code hand_trco_key}, duplicate {@code trco_tax_id},
 * local-needs-bank, per-upsert disp_ord, etc.).
 *
 * <p>{@code hand_trco_key} pattern matches the legacy convention from
 * the seeded values (alphanumeric + {@code _} / {@code -} / {@code .}).
 */
public record ContactCreateRequest(
        @NotBlank
        @Size(max = 100)
        @Pattern(regexp = "^\\S+$",
                message = "hand_trco_key must not contain whitespace")
        String handTrcoKey,

        @NotBlank
        @Size(max = 200)
        String trcoCmnm,

        @NotBlank
        @Size(max = 200)
        String trcoCmenm,

        @Size(max = 100)
        String trcoTaxId,

        @NotBlank
        @Pattern(regexp = "^[LO]$", message = "trco_type must be 'L' or 'O'")
        String trcoType,

        @Valid
        @Size(max = 50)
        List<AddressOp> addresses,

        @Valid
        @Size(max = 50)
        List<AccountOp> accounts,

        @Valid
        @Size(max = 50)
        List<OverseasOp> overseasBeneficiaries) { }
