package com.kosign.wecms.domain.payment.cardtransactions.dto;

import jakarta.validation.constraints.Size;

/**
 * {@code field=description} variant of
 * {@link CardTransactionPatchRequest}. Maps to legacy
 * {@code card_0001_001_u001} with {@code FIELD_ID='CONT'}.
 *
 * <p>{@code value} is nullable so the user can clear the description.
 * An empty string trims to {@code null} at the service layer and writes
 * SQL NULL to the column.
 */
public record PatchDescriptionRequest(
        String                  field,
        @Size(max = 200) String value)
        implements CardTransactionPatchRequest {
}
