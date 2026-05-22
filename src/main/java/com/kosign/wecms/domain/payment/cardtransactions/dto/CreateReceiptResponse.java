package com.kosign.wecms.domain.payment.cardtransactions.dto;

/** Create Receipt response — returns the newly-allocated
 *  {@code BUSA_DOC_MSTR.DOC_ID} so the frontend can immediately
 *  select it on the Receipt tab. */
public record CreateReceiptResponse(String docId) {
}
