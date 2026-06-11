package com.kosign.wecms.domain.payment.internaltransfer.dto;

import java.math.BigDecimal;
import java.util.List;

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
        String     wdrlCcyCd,
        /**
         * Legacy {@code APPR_FUND_TP} (Payroll Type) — joined from
         * {@code EXPT_RESOL_MSTR}. {@code "0"}=Salary, {@code "1"}=Bonus,
         * else blank. Only the Payroll Scheduled list populates it
         * (legacy {@code pymn_0005_001}); the Internal Transfer / Vendor /
         * Expense siblings pass {@code null} via the legacy constructor
         * below.
         */
        String     apprFundTp,
        /**
         * First approver's display name (legacy {@code APPRVR_NM}, line-1 of
         * the {@code EXPT_RESOL_APPRVR_INFM} chain). {@code null} when the
         * batch has no approval request yet. Only the Internal Transfer
         * Scheduled list populates it ({@code pymn_0009_001}); the Vendor /
         * Expense / Payroll siblings pass {@code null} via the
         * backward-compatible constructors below.
         */
        String     approver,
        /**
         * Total number of approvers in the chain ({@code EXPT_RESOL_APPRVR_INFM}
         * line count) — drives the legacy "{@code [N]}" count badge next to the
         * approver name. {@code null} when the batch has no approval request
         * yet. Only the Internal Transfer Scheduled list populates it; the
         * Vendor / Expense / Payroll siblings pass {@code null} via the
         * backward-compatible constructors below.
         */
        Integer    approverCount,
        /**
         * Full approval chain (name + status + datetime), in
         * {@code APPRVR_LINE_NO} order — feeds the legacy {@code pymn_0009_001}
         * approver-cell click popup (Phase 2). {@code null} when the batch has
         * no approval request yet. Only the Internal Transfer Scheduled list
         * populates it; the Vendor / Expense / Payroll siblings pass
         * {@code null} via the backward-compatible constructors below.
         */
        List<ScheduledTransferApprover> approvers,
        /**
         * Requestor display name — legacy {@code EXPT_RESOL_MSTR.DRAFT_USER_NM}
         * (발의자명), joined on {@code APPR_SEQ_NO}. This is the name the legacy
         * {@code pymn_000X_001} Scheduled list renders in its "Requestor"
         * column (legacy {@code IDO.SCDUL_PYMN_SUMR_R002} selects
         * {@code ERM.DRAFT_USER_NM}) — NOT the {@code SCDUL_PYMN_SUMR.REG_ID}
         * batch-creator login id. {@code null} when the batch has no approval
         * request yet; the frontend then falls back to {@link #regId}. Populated
         * by every Scheduled list (Internal Transfer / Vendor / Expense /
         * Payroll); Overseas carries its own {@code OverseasScheduledRow}.
         */
        String     draftUserNm) {

    /**
     * Payroll-shaped constructor (carries {@code apprFundTp}, no
     * {@code approver}/{@code approverCount}/{@code approvers}) — delegates
     * with those set to {@code null} so the Payroll {@code toListRow(...)}
     * call site compiles unchanged.
     */
    public ScheduledTransferRow(
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
            String     wdrlCcyCd,
            String     apprFundTp) {
        this(scdulPymnNo, scdulPymnTypeCd, ccyCd, titl, apprSeqNo,
             totalTranAmt, totTrnsCnt, regDtm, regId, apprStts,
             apprSttsKey, trnfStts, trnfYn, outBankCd, wdrlActNo,
             payDate, fileNm, docNo, wdrlCcyCd, apprFundTp, null, null, null, null);
    }

    /**
     * Backward-compatible constructor for the sibling modules (Vendor /
     * Expense) that carry neither a Payroll Type nor an Approver column —
     * delegates with {@code apprFundTp = null} and {@code approver = null}
     * so their {@code toListRow(...)} call sites compile unchanged.
     */
    public ScheduledTransferRow(
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
            String     wdrlCcyCd) {
        this(scdulPymnNo, scdulPymnTypeCd, ccyCd, titl, apprSeqNo,
             totalTranAmt, totTrnsCnt, regDtm, regId, apprStts,
             apprSttsKey, trnfStts, trnfYn, outBankCd, wdrlActNo,
             payDate, fileNm, docNo, wdrlCcyCd, null, null, null, null, null);
    }
}
