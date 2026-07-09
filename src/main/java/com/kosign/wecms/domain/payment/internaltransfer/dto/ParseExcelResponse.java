package com.kosign.wecms.domain.payment.internaltransfer.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Response body for
 * {@code POST /api/v1/payment/internal-transfer/uploads/parse}.
 * Mirrors legacy {@code WSVC.pymn_0009_002_r001.xml:out}.
 *
 * <p>{@code totCnt} / {@code sucCnt} / {@code errCnt} are the row
 * counters produced by {@code InternalTransferExcelParser} (Apache POI
 * workbook walk + per-cell validation that mirrors
 * {@code PymnHelper.readExcelFromInternalTransfer}); {@code rows}
 * carries one {@link ParsedTransferLine} per data row with per-cell
 * error flags for the preview.
 */
public record ParseExcelResponse(
        BigDecimal              totCnt,
        BigDecimal              sucCnt,
        BigDecimal              errCnt,
        List<ParsedTransferLine> rows) { }
