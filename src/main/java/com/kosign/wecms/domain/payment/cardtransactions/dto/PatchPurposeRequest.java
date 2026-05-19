package com.kosign.wecms.domain.payment.cardtransactions.dto;

import jakarta.validation.constraints.Pattern;

/**
 * {@code field=purpose} variant of {@link CardTransactionPatchRequest}.
 * Maps to legacy {@code card_0001_001_u001} with
 * {@code FIELD_ID='CARD_TRNS_USAGE_CODE'}.
 *
 * <p>{@code value} is the digit-only purpose code from the {@code
 * USAG_INFM} dictionary. Nullable (and {@code ""} permitted) so the
 * user can clear the assignment — an empty string trims to {@code null}
 * at the service layer.
 */
public record PatchPurposeRequest(
        String                                       field,
        @Pattern(regexp = "^[0-9]{0,20}$") String    value)
        implements CardTransactionPatchRequest {
}
