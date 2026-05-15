package com.kosign.wecms.domain.payment.dto;

import java.math.BigDecimal;

/**
 * Single row in the overseas-payment result list.
 * Wire names auto-derived via global Jackson SNAKE_CASE strategy.
 *
 * <p>Mirrors the payment-result view (JEX {@code pymn_0007_003}) —
 * batch records from {@code OVRS_PYMN_SUMR} that have been submitted
 * for processing, joined with {@code EXPT_RESOL_MSTR} for approval info.
 *
 * <p>Note: {@code success_count} / {@code failed_count} are not yet tracked
 * at the summary level — returned as 0 until individual transaction-result
 * tables are ported in a future sprint.
 */
public record OverseasResultRow(
        String ovrsPymnNo,
        String ccyCd,
        String fileNm,
        BigDecimal totalTranAmt,
        Integer totTrnsCnt,
        String regDtm,
        String draftUserNm,
        String regId,
        String outBankNm,
        String outBankCd,
        String wdrlActNo,
        /** {@code "Y"} = completed / transferred; {@code "N"} = in progress. */
        String trnfYn,
        String docNo,
        String titl,
        Integer successCount,
        Integer failedCount
) {}
