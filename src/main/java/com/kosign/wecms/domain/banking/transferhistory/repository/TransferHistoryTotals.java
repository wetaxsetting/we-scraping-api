package com.kosign.wecms.domain.banking.transferhistory.repository;

import java.math.BigDecimal;

/**
 * JPQL projection target for the Transfer History totals query — see
 * {@link TransferHistoryRepository#aggregateTotals}. The five columns
 * mirror the legacy IDO {@code NAPAS_REMIT_TRNS_HSTR_R003_CNT} surface
 * one-for-one. Not exposed on the wire directly; the service flattens
 * it into the response DTO.
 *
 * @param totalCount    {@code COUNT(*)} across the filtered set
 * @param totalVndAmount sum of {@code TRSC_AMT} where {@code CCY_CD = 'VND'}
 * @param totalUsdAmount sum of {@code TRSC_AMT} where {@code CCY_CD = 'USD'}
 * @param totalVndCount  count of rows where {@code CCY_CD = 'VND'}
 * @param totalUsdCount  count of rows where {@code CCY_CD = 'USD'}
 */
public record TransferHistoryTotals(
        long totalCount,
        BigDecimal totalVndAmount,
        BigDecimal totalUsdAmount,
        long totalVndCount,
        long totalUsdCount
) {
}
