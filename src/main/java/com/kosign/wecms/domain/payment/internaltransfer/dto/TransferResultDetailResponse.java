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
        List<TransferResultLine> rows,
        /** Banking-match link flag (legacy {@code SCDUL_PYMN_SUMR.LINK_MATCH_YN}):
         *  {@code "Y"} when the batch is matched to a bank transaction, else
         *  {@code "N"}/null. Drives the Payroll Result-detail header Match-vs-Unmatch
         *  visibility (Phase 1 read-only; the match/unmatch write path lands later).
         *  Header-level / batch-level — Payroll is 1:1 batch ↔ bank txn. */
        String                   linkMatchYn,
        /** Matched bank transaction's account sequence (legacy
         *  {@code SCDUL_PYMN_SUMR.ACCT_SEQ_NO}). null when unmatched. */
        String                   acctSeqNo,
        /** Matched bank transaction short id (legacy
         *  {@code SCDUL_PYMN_SUMR.BANK_TRNS_SHORT_ID}). Rendered as the matched-ref;
         *  null when unmatched. */
        String                   bankTrnsShortId,
        /** Matched bank transaction full id (legacy
         *  {@code SCDUL_PYMN_SUMR.BANK_TRNS_FULL_ID}). null when unmatched. */
        String                   bankTrnsFullId) {

    /**
     * Constructor matching the prior canonical 21-component shape (no header
     * banking-match fields) — delegates with {@code linkMatchYn} /
     * {@code acctSeqNo} / {@code bankTrnsShortId} / {@code bankTrnsFullId} all
     * {@code null}. Keeps the Vendor / Expense / Internal Transfer result-detail
     * call sites compiling unchanged; only the Payroll result detail populates the
     * banking-match fields (Phase 1).
     */
    public TransferResultDetailResponse(
            String                   scdulPymnNo,
            String                   outBankCd,
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
            String                   regId,
            String                   requestor,
            BigDecimal               sucCnt,
            BigDecimal               errCnt,
            BigDecimal               prcCnt,
            List<TransferResultLine> rows) {
        this(scdulPymnNo, outBankCd, outBankNm, wdrlActNo, titl, totalTranAmt,
             totTrnsCnt, apprSeqNo, docNo, ccyCd, wdrlCcyCd, trnfYn, trnfDtm,
             orgFileNm, fileSize, regId, requestor, sucCnt, errCnt, prcCnt, rows,
             null, null, null, null);
    }

    /**
     * Backward-compatible constructor for sibling result services that don't
     * resolve a withdrawal-bank name or requester name — delegates with
     * {@code outBankNm = null}, {@code requestor = null}, and the four
     * banking-match fields {@code null} so their {@code detail(...)} call sites
     * compile unchanged.
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
             orgFileNm, fileSize, regId, null, sucCnt, errCnt, prcCnt, rows,
             null, null, null, null);
    }
}
