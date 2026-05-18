package com.kosign.wecms.domain.banking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body for
 * {@code PATCH /api/v1/banking/accounts/{acctSeqNo}/transactions/{trnsSeqNo}/contact}.
 *
 * <p>Carries the picker's selection — the global PK {@code TRCO_KEY}
 * from {@code TRCO_INFM}. Backend validates the row exists, is
 * non-trashed ({@code TRCO_STTS != 'C'}), and belongs to the current
 * tenant before writing the snapshot pair
 * ({@code RCV_PAY_TRCO_KEY} + {@code RCV_PAY_TRCO_NM}) on the
 * receipt/payment record. Clearing the contact uses the sibling
 * DELETE endpoint.
 */
public record TransactionContactRequest(
        @NotBlank @Size(max = 20) String trcoKey
) { }
