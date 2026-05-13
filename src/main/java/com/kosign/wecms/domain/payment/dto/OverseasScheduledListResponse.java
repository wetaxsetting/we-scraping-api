package com.kosign.wecms.domain.payment.dto;

import java.util.List;

/**
 * Full response for the overseas scheduled-payment list endpoint.
 * Wire names auto-derived via global Jackson SNAKE_CASE strategy.
 *
 * <p>Mirrors WSVC.pymn_0007_001_r001.xml output:
 * {@code TOT_CNT}, {@code REC_OUT} (paged rows), {@code TOT_REC} (currency totals).
 */
public record OverseasScheduledListResponse(
        long totalCount,
        int page,
        int size,
        List<OverseasScheduledRow> rows,
        List<OverseasCurrencyTotal> totals
) {}
