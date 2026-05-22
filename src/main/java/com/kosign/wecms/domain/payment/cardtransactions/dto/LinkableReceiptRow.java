package com.kosign.wecms.domain.payment.cardtransactions.dto;

import java.math.BigDecimal;

/**
 * Payment &gt; Card Transactions &mdash; one manually-registered
 * receipt eligible to be linked to a card transaction. Surfaced on the
 * "Link Card Payments with the Expenses" popup's <i>Receipt</i> tab
 * (legacy {@code card_0001_002_r002}).
 *
 * <p>Sourced from {@code BUSA_DOC_MSTR} ({@code DOC_TYPE='E'} +
 * {@code HAND_REG_YN='Y'} — hand-entered expense rows). Same exclusion
 * rules as the e-Invoice sibling: not Rejected / Voided, not already
 * linked.
 *
 * <p>Wire shape (Jackson SNAKE_CASE): {@code doc_id} / {@code use_dt} /
 * {@code merch_nm} / {@code totl_amt} / {@code ccy_cd} / {@code purpose} /
 * {@code description}.
 */
public record LinkableReceiptRow(
        String     docId,
        /** Receipt use date {@code YYYYMMDD} (NOT issue date — legacy
         *  r002 files manual receipts by USE_DT). */
        String     useDt,
        /** Merchant name — {@code TRCO_CMNM} else {@code TRCO_CMENM}. */
        String     merchNm,
        BigDecimal totlAmt,
        String     ccyCd,
        /** Raw {@code BUSA_USAG_CD} purpose code — Phase B JOIN to
         *  {@code USAG_INFM} for the resolved name. */
        String     purpose,
        String     description) {
}
