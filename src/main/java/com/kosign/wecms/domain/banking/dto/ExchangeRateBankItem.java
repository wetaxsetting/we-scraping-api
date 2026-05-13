package com.kosign.wecms.domain.banking.dto;

/**
 * One item in the bank selector list for Exchange Rate Inquiry.
 *
 * <p>Returned by {@code GET /api/v1/banking/exchange-rates/banks}.
 * Fields are camelCase in Java; the global Jackson SNAKE_CASE strategy
 * serialises them as {@code bank_cd} / {@code bank_nm} on the wire.</p>
 */
public record ExchangeRateBankItem(
        String bankCd,
        String bankNm
) {}
