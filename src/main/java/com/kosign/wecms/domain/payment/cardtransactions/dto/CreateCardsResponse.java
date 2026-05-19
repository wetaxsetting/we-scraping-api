package com.kosign.wecms.domain.payment.cardtransactions.dto;

/**
 * Response shape for {@code POST /api/v1/payment/card-transactions/cards}.
 *
 * <p>{@code inserted_count} = rows actually created in {@code CARD_MSTR}.
 * {@code skipped_count}  = rows the user submitted but were already
 * present in the tenant (PK collision on
 * {@code (BIZ_KEY, BANK_CD, CARD_NO)}); the service silently dedupes
 * rather than throwing, matching the legacy
 * {@code comm_0218_007_c001} behaviour where re-running the registration
 * for an already-registered card is a no-op.
 */
public record CreateCardsResponse(
        int insertedCount,
        int skippedCount) {
}
