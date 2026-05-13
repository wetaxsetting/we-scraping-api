package com.kosign.wecms.domain.payment.internaltransfer.dto;

import java.util.List;

/**
 * Envelope for the Scheduled Transfer list endpoint. Mirrors the legacy
 * {@code pymn_0009_001_r001} output (single {@code TOT_CNT} + {@code
 * TOT_REC} array + {@code REC} array) using camelCase fields that
 * Jackson serialises to snake_case on the wire.
 */
public record ScheduledTransferListResponse(
        long                       totalCount,
        int                        page,
        int                        size,
        List<CurrencyTotal>        currencyTotals,
        List<ScheduledTransferRow> rows) { }
