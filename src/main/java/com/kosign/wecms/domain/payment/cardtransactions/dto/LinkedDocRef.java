package com.kosign.wecms.domain.payment.cardtransactions.dto;

import java.math.BigDecimal;

/**
 * One element of {@code linked_docs[]} on a card-transaction row —
 * legacy {@code REC_1} sub-record from {@code IDO.CARD_MSTR_R002.xml}.
 *
 * <p>Phase B placeholder in this MR: the service emits an empty list on
 * every row until {@code BUSA_DOC_RCV_PAY_HIS} is ported as a JPA
 * entity. The wire contract is published now so the frontend can
 * implement the row renderer against the final shape (mirrors the
 * Internal Transfer Phase A {@code approvers[]} deferral pattern).
 */
public record LinkedDocRef(
        String     docId,
        String     dispDocId,
        String     handDocId,
        String     docType,
        String     einvYn,
        BigDecimal totlAmt) {
}
