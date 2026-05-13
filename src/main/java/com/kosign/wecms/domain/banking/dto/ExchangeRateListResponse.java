package com.kosign.wecms.domain.banking.dto;

import java.util.List;

/**
 * Response body for {@code GET /api/v1/banking/exchange-rates}.
 *
 * <p>Mirrors the matrix shown in the Exchange Rate Inquiry page
 * ({@code ExchangeRateInquiryPage.tsx}): one row per counter currency,
 * columns for each rate type.</p>
 */
public record ExchangeRateListResponse(

        /** Reference date the rates apply to — YYYYMMDD. */
        String refDate,

        /** Bank code whose rates are returned, e.g. "11704088" (Shinhan). */
        String bankCd,

        /** Bank display name resolved from BANK_INFM. */
        String bankNm,

        /** Exchange rates, one entry per counter-currency, ordered by CCY_CD. */
        List<ExchangeRateRow> rates
) {}
