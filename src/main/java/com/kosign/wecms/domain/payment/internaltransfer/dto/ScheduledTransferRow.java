package com.kosign.wecms.domain.payment.internaltransfer.dto;

import java.math.BigDecimal;

/**
 * One row in the {@code GET /api/v1/payment/internal-transfer} response.
 * Java fields stay camelCase; Jackson's global {@code SNAKE_CASE}
 * strategy emits the lowercase snake_case wire shape
 * (see {@code wecms-api/CLAUDE.md} §"API Naming &amp; Serialization
 * Standards").
 */
public record ScheduledTransferRow(
        String     scdulPymnNo,
        String     scdulPymnTypeCd,
        String     ccyCd,
        String     titl,
        String     apprSeqNo,
        BigDecimal totalTranAmt,
        BigDecimal totTrnsCnt,
        String     regDtm,
        String     regId,
        String     apprStts,
        String     apprSttsKey,
        String     trnfStts,
        String     trnfYn,
        String     outBankCd,
        String     wdrlActNo,
        String     payDate,
        String     fileNm,
        String     docNo,
        String     wdrlCcyCd) { }
