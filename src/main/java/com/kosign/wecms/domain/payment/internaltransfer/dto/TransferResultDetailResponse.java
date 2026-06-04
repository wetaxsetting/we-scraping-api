package com.kosign.wecms.domain.payment.internaltransfer.dto;

import java.math.BigDecimal;
import java.util.List;

public record TransferResultDetailResponse(
        String                   scdulPymnNo,
        String                   outBankCd,
        /** Resolved withdrawal-bank display name (legacy {@code OUT_BANK_NM}).
         *  null when the code can't be resolved — the frontend falls back to
         *  {@code outBankCd}. */
        String                   outBankNm,
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
        /** Requester display name (legacy {@code EXPT_RESOL_MSTR.DRAFT_USER_NM}).
         *  null when the batch has no approval request — the frontend falls
         *  back to {@code regId}. */
        String                   requestor,
        BigDecimal               sucCnt,
        BigDecimal               errCnt,
        BigDecimal               prcCnt,
        List<TransferResultLine> rows) {

    /**
     * Backward-compatible constructor for the sibling result services
     * (Vendor / Expense / Payroll) that don't resolve a withdrawal-bank name
     * or requester name — delegates with {@code outBankNm = null} and
     * {@code requestor = null} so their {@code detail(...)} call sites compile
     * unchanged (they emit the additive fields as {@code null}). Only the
     * Internal Transfer result detail populates them.
     */
    public TransferResultDetailResponse(
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
            String                   regId,
            BigDecimal               sucCnt,
            BigDecimal               errCnt,
            BigDecimal               prcCnt,
            List<TransferResultLine> rows) {
        this(scdulPymnNo, outBankCd, null, wdrlActNo, titl, totalTranAmt,
             totTrnsCnt, apprSeqNo, docNo, ccyCd, wdrlCcyCd, trnfYn, trnfDtm,
             orgFileNm, fileSize, regId, null, sucCnt, errCnt, prcCnt, rows);
    }
}
