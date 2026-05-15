package com.kosign.wecms.domain.payment.dto;

import java.util.List;

/**
 * Full response for the overseas payment-result list endpoint.
 * Wire names auto-derived via global Jackson SNAKE_CASE strategy.
 */
public record OverseasResultListResponse(
        long totalCount,
        int page,
        int size,
        List<OverseasResultRow> rows,
        List<OverseasCurrencyTotal> totals
) {}
