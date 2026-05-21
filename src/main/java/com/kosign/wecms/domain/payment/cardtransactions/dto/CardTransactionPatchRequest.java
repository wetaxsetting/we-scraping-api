package com.kosign.wecms.domain.payment.cardtransactions.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Discriminated patch request for
 * {@code PATCH /api/v1/payment/card-transactions/{cardSeqNo}}.
 *
 * <p>The legacy {@code card_0001_001_u001} contract accepts a generic
 * {@code FIELD_ID} + {@code FIELD_VAL} pair where {@code FIELD_ID} is
 * either {@code CONT} or {@code CARD_TRNS_USAGE_CODE}. The refactor
 * lifts that into a Jackson-polymorphic body where the {@code field}
 * property selects the concrete sub-record:
 *
 * <pre>{@code
 * { "field": "description", "value": "..." }
 * { "field": "purpose",     "value": "..." }
 * }</pre>
 *
 * <p>Unknown {@code field} values surface as
 * {@code HttpMessageNotReadableException} → {@code WBE_BAD_REQUEST} via
 * the shared {@code GlobalExceptionHandler} — no per-service "unknown
 * field" branch.
 */
@JsonTypeInfo(
        use      = JsonTypeInfo.Id.NAME,
        property = "field",
        include  = JsonTypeInfo.As.EXISTING_PROPERTY,
        visible  = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = PatchDescriptionRequest.class, name = "description"),
        @JsonSubTypes.Type(value = PatchPurposeRequest.class,     name = "purpose")
})
public sealed interface CardTransactionPatchRequest
        permits PatchDescriptionRequest, PatchPurposeRequest {

    /** Echoes the discriminator literal back so the service can route
     *  the call to the matching repository writer. */
    String field();
}
