package com.kosign.wecms.domain.banking.transferhistory.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Response envelope for
 * {@code GET /api/v1/banking/internal-transfer/transfer-history}.
 *
 * <p>Totals mirror the legacy {@code IDO.NAPAS_REMIT_TRNS_HSTR_R003_CNT}
 * projection (single row per request — TOT_CNT + per-CCY sums + per-CCY
 * counts) so the footer summary can render without a second roundtrip.
 *
 * <p>Pagination shape mirrors the legacy "explicit totals" pattern from
 * {@code WSVC.trns_0003_001_r001.xml:40-46} — {@code total_count} is the
 * untruncated tenant count for the date+status filter, while
 * {@link #rows} contains only the current page. The legacy footer only
 * renders the VND total (OQ-5 resolved 2026-05-20); USD totals are
 * carried on the wire for completeness so a future Figma frame can
 * surface a second footer row without a backend change.
 *
 * @param totalCount     total matching rows across all pages (tenant + filter)
 * @param totalVndAmount sum of {@code TRSC_AMT} where {@code CCY_CD = 'VND'}
 * @param totalUsdAmount sum of {@code TRSC_AMT} where {@code CCY_CD = 'USD'}
 * @param totalVndCount  count of rows where {@code CCY_CD = 'VND'}
 * @param totalUsdCount  count of rows where {@code CCY_CD = 'USD'}
 * @param rows           rows on the current page
 */
public record TransferHistoryListResponse(
        long totalCount,
        BigDecimal totalVndAmount,
        BigDecimal totalUsdAmount,
        long totalVndCount,
        long totalUsdCount,
        List<TransferHistoryRow> rows
) {
}
