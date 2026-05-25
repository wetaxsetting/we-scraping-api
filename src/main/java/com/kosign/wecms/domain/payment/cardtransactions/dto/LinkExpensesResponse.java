package com.kosign.wecms.domain.payment.cardtransactions.dto;

/** Link Expenses response — one row was created on
 *  {@code CARD_TRNS_RCV_PAY_INFM} and N rows on
 *  {@code BUSA_DOC_RCV_PAY_HIS}. */
public record LinkExpensesResponse(
        int linkedCount) {
}
