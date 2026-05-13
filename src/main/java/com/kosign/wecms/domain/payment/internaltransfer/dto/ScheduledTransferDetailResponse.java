package com.kosign.wecms.domain.payment.internaltransfer.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Envelope for {@code GET /api/v1/payment/internal-transfer/{id}}.
 * Master fields + per-line {@code rows[]} (sorted by the caller-supplied
 * sort or by {@code SCDUL_PYMN_DETL_NO} ascending).
 */
public record ScheduledTransferDetailResponse(
        String                    scdulPymnNo,
        String                    scdulPymnTypeCd,
        String                    outBankCd,
        String                    wdrlActNo,
        String                    titl,
        BigDecimal                totalTranAmt,
        BigDecimal                totTrnsCnt,
        String                    docNo,
        String                    regId,
        String                    apprSeqNo,
        String                    apprStts,
        String                    apprSttsKey,
        String                    trnfYn,
        String                    trnfDtm,
        String                    ccyCd,
        String                    wdrlCcyCd,
        String                    orgFileNm,
        BigDecimal                fileSize,
        List<TransferLineDetail>  rows) { }
