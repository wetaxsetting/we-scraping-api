package com.kosign.wecms.domain.payment.internaltransfer.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Response body for
 * {@code POST /api/v1/payment/internal-transfer/uploads/parse}.
 * Mirrors legacy {@code WSVC.pymn_0009_002_r001.xml:out}.
 *
 * <p>Phase A ships this endpoint as a local stub — it accepts the
 * upload, runs basic file-shape checks ({@code FILE_DATA} present,
 * file size &gt; 0, extension in the allow-list), and returns a
 * placeholder {@code totCnt=0 / errCnt=0 / sucCnt=0 / rows=[]}.
 * Real per-cell parsing (Apache POI workbook walk + per-line
 * validation that mirrors
 * {@code PymnHelper.importDataFromExcelForInternalTransfer}) lands
 * in a follow-up MR per backend doc §13.
 */
public record ParseExcelResponse(
        BigDecimal              totCnt,
        BigDecimal              sucCnt,
        BigDecimal              errCnt,
        List<ParsedTransferLine> rows) { }
