package com.kosign.wecms.domain.setting.contacts.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request body for {@code PUT /api/v1/settings/contacts/{trcoKey}}.
 * Mirrors {@link ContactCreateRequest} with two additions:
 *
 * <ul>
 *   <li>{@code trcoStts} &mdash; nullable; non-null only when the
 *       client wants to flip the contact to {@code 'C'} (trash) via
 *       the edit endpoint. {@code ContactsValidator} runs the
 *       {@code WBE1190} in-use guard on the
 *       {@code null &rarr; 'C'} transition (OQ-2).</li>
 *   <li>{@code accountsToDelete} / {@code overseasToDelete} &mdash;
 *       fold the legacy {@code comm_0184_005_d001} +
 *       {@code REC4} / {@code REC6} delete-sub-records into the same
 *       single-transaction edit body.</li>
 * </ul>
 *
 * <p>The legacy {@code REC3} file-delete sub-record is intentionally
 * NOT ported &mdash; {@code TRCO_INFO_FILE} is out of the PROD active
 * table list.
 */
public record ContactUpdateRequest(
        @NotBlank
        @Size(max = 20)
        @Pattern(regexp = "^[A-Za-z0-9_\\-.]+$",
                message = "hand_trco_key must be alphanumeric + _ - .")
        String handTrcoKey,

        @NotBlank
        @Size(max = 200)
        String trcoCmnm,

        @NotBlank
        @Size(max = 200)
        String trcoCmenm,

        @Size(max = 40)
        String trcoTaxId,

        @NotBlank
        @Pattern(regexp = "^[LO]$", message = "trco_type must be 'L' or 'O'")
        String trcoType,

        @Pattern(regexp = "^[C]?$",
                message = "trco_stts must be null or 'C'")
        String trcoStts,

        @Valid
        @Size(max = 50)
        List<AddressOp> addresses,

        @Valid
        @Size(max = 50)
        List<AccountOp> accounts,

        @Valid
        @Size(max = 50)
        List<AccountDeleteKey> accountsToDelete,

        @Valid
        @Size(max = 50)
        List<OverseasOp> overseasBeneficiaries,

        @Valid
        @Size(max = 50)
        List<OverseasDeleteKey> overseasToDelete) { }
