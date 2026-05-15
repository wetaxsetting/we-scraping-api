package com.kosign.wecms.domain.setting.bankaccount.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;

/**
 * Replace-semantics payload for {@code PUT /api/v1/settings/bank-account/{bankCd}/{acctNo}/permissions}.
 *
 * <p>{@code userIds[]} is the complete desired selection for the given
 * {@code kind}; the service diffs against the current state and writes
 * grant + revoke side effects in one transaction.
 *
 * <p>{@code kind} is constrained to the two wire names the dialog
 * supports ({@code VIEW} / {@code WITHDRAWAL}). {@code FULL_ACCESS}
 * ('F') exists in the DB enum but is not exposed on this endpoint.
 */
public record BankAccountPermissionUpdateRequest(
        @NotBlank
        @Pattern(regexp = "VIEW|WITHDRAWAL")
        String       kind,
        @NotNull
        List<String> userIds
) { }
