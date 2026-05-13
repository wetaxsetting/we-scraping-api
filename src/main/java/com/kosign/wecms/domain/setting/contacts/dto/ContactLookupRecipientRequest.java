package com.kosign.wecms.domain.setting.contacts.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/v1/settings/contacts/lookup-recipient}.
 * Mirrors the input fields legacy {@code comm_0184_002.ts:533} sends
 * to {@code comm_1003_001}.
 *
 * <p>{@code napasBankCd} OR {@code citadBankCd} is supplied (the
 * frontend ships whichever the bank picker selected); {@code acctNo}
 * is the user-entered account number. Both bank fields are optional
 * because the dialog allows either kind of bank.
 */
public record ContactLookupRecipientRequest(
        @Size(max = 15)
        String napasBankCd,

        @Size(max = 15)
        String citadBankCd,

        @NotBlank
        @Size(max = 100)
        String acctNo) { }
