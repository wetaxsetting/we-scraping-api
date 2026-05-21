package com.kosign.wecms.domain.payment.cardtransactions.dto;

/**
 * One card in the All-Cards dropdown for the Card Transactions filter
 * bar. Mirrors the legacy {@code WSVC.card_0001_001_r002.xml}
 * {@code REC} sub-rule minus the audit columns (the dropdown doesn't
 * surface them).
 */
public record ActiveCard(
        String bankCd,
        String bankNm,
        String cardNo,
        String cardNm,
        String cardNickNm,
        String cardHldrNm) {
}
