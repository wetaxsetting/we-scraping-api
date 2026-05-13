package com.kosign.wecms.domain.payment.internaltransfer.dto;

import java.math.BigDecimal;
import java.util.List;

public record TransferResultDetailResponse(
        String                   scdulPymnNo,
        String                   outBankCd,
        String                   wdrlActNo,
        String                   titl,
        BigDecimal               totalTranAmt,
        BigDecimal               totTrnsCnt,
        String                   apprSeqNo,
        String                   docNo,
        String                   ccyCd,
        String                   wdrlCcyCd,
        String                   trnfYn,
        String                   trnfDtm,
        String                   orgFileNm,
        BigDecimal               fileSize,
        BigDecimal               sucCnt,
        BigDecimal               errCnt,
        BigDecimal               prcCnt,
        List<TransferResultLine> rows) { }
