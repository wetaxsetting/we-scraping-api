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
        /** User id of the requester — wired so the detail page's
         *  General Information panel can render the real submitter
         *  rather than a hardcoded mock. Mirrors the Scheduled
         *  detail's {@code reg_id}. */
        String                   regId,
        BigDecimal               sucCnt,
        BigDecimal               errCnt,
        BigDecimal               prcCnt,
        List<TransferResultLine> rows) { }
