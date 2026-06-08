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
        /** Human-readable name of the withdrawal bank — resolved on the
         *  api side via the shared two-step lookup
         *  ({@code BankCdResolver} → V86 NAPAS / CITAD fallback) so the
         *  Approval Details / Scheduled Transfer Detail popups render
         *  "Woori VN" / "ShinhanBank" / etc. instead of the raw code
         *  (e.g. {@code "11704020"}). {@code null} when neither lookup
         *  matches — the frontend falls back to the code alone. */
        String                    outBankNm,
        String                    wdrlActNo,
        String                    titl,
        BigDecimal                totalTranAmt,
        BigDecimal                totTrnsCnt,
        String                    docNo,
        String                    regId,
        /** Requester display name (legacy {@code EXPT_RESOL_MSTR.DRAFT_USER_NM},
         *  filled into the legacy {@code #requestor_name} header cell). null
         *  when the batch has no approval request — the frontend falls back to
         *  {@code regId}. Only Internal Transfer populates this; the sibling
         *  result services (Vendor / Expense / Payroll) construct via the
         *  backward-compatible constructor below and emit it as null. */
        String                    requestor,
        String                    regDtm,
        String                    apprSeqNo,
        String                    apprStts,
        String                    apprSttsKey,
        /** Last-modification timestamp ({@code yyyyMMddHHmmss}). After
         *  the approval-action endpoint stamps {@code APPR_STTS}, this
         *  reflects the action time — the dialog surfaces it as the
         *  "has approved on …" / "has rejected on …" date on the
         *  approver chip. Phase B may add a dedicated {@code APPR_DTM}
         *  column when the per-approver chain lands. */
        String                    modDtm,
        String                    trnfYn,
        String                    trnfDtm,
        String                    ccyCd,
        String                    wdrlCcyCd,
        String                    orgFileNm,
        BigDecimal                fileSize,
        List<TransferLineDetail>  rows,
        /** Approval-request attachments registered against this
         *  batch's {@code APPR_SEQ_NO}. Empty when the batch has no
         *  approval submission yet, or when the user submitted with
         *  zero attachments. Driven by {@code EXPT_RESOL_FILE} via
         *  {@code ExptResolFileRepository}; the Approval Details popup
         *  renders one chip per row. */
        List<ApprovalAttachmentRow> attachments,
        /** Free-text Approval Content — {@code EXPT_RESOL_MSTR.CONT}
         *  (legacy {@code BLTB_POST_INFM.POST_CONT}). Null when the batch
         *  has no approval request yet or the requestor submitted an empty
         *  note. Only Internal Transfer populates this today; the sibling
         *  Scheduled-detail services construct via the backward-compatible
         *  constructor below and emit it as null. */
        String                    cont,
        /** Scheduled / pay date — {@code yyyyMMdd} from
         *  {@code EXPT_RESOL_MSTR.PAY_DATE}, the same source the Approval
         *  Box list endpoint surfaces. Populated by every scheduled-detail
         *  caller (Internal Transfer via the canonical constructor; Vendor /
         *  Expense / Payroll via the requestor constructor below). Null when
         *  the batch has no approval request yet or carries no pay date — the
         *  frontend hides the Scheduled Date row then. */
        String                    payDate,
        /** Single-character legacy {@code APPR_FUND_TP} (Payroll Type) from
         *  {@code EXPT_RESOL_MSTR} — {@code "0"}=Salary, {@code "1"}=Bonus,
         *  {@code "9"}=Other; null for non-payroll batches. The frontend
         *  resolves the display label (including the withdrawal-bank
         *  {@code 11704088} → "Salary/Allowance/Bonus" override) so the
         *  Approval Details popup renders Payroll Type identically from the
         *  Approval Box and Scheduled entries. Populated by every
         *  scheduled-detail caller from the already-loaded {@code EXPT_RESOL_MSTR}
         *  (same source as {@code requestor} / {@code cont} / {@code payDate}). */
        String                    apprFundTp) {

    /**
     * Backward-compatible constructor for the sibling Scheduled-detail callers
     * (Vendor / Expense / Payroll) that don't resolve a requester display name
     * — delegates with {@code requestor = null} so their {@code detail(...)}
     * call sites compile unchanged (they emit the additive field as null).
     * Only the Internal Transfer detail populates {@code requestor}.
     */
    public ScheduledTransferDetailResponse(
            String                    scdulPymnNo,
            String                    scdulPymnTypeCd,
            String                    outBankCd,
            String                    outBankNm,
            String                    wdrlActNo,
            String                    titl,
            BigDecimal                totalTranAmt,
            BigDecimal                totTrnsCnt,
            String                    docNo,
            String                    regId,
            String                    regDtm,
            String                    apprSeqNo,
            String                    apprStts,
            String                    apprSttsKey,
            String                    modDtm,
            String                    trnfYn,
            String                    trnfDtm,
            String                    ccyCd,
            String                    wdrlCcyCd,
            String                    orgFileNm,
            BigDecimal                fileSize,
            List<TransferLineDetail>  rows,
            List<ApprovalAttachmentRow> attachments) {
        this(scdulPymnNo, scdulPymnTypeCd, outBankCd, outBankNm, wdrlActNo, titl,
             totalTranAmt, totTrnsCnt, docNo, regId, null, regDtm, apprSeqNo,
             apprStts, apprSttsKey, modDtm, trnfYn, trnfDtm, ccyCd, wdrlCcyCd,
             orgFileNm, fileSize, rows, attachments, null, null, null);
    }

    /**
     * Constructor for the sibling Scheduled-detail callers (Vendor / Expense /
     * Payroll) — carries {@code requestor} + {@code payDate} + {@code apprFundTp}
     * but not {@code cont}; it delegates with {@code cont = null} so those
     * services (which don't surface Approval Content) compile unchanged. Only
     * the Internal Transfer detail populates {@code cont} (via the canonical
     * constructor). {@code payDate} flows from {@code EXPT_RESOL_MSTR.PAY_DATE}
     * and {@code apprFundTp} from {@code EXPT_RESOL_MSTR.APPR_FUND_TP} so the
     * Vendor / Expense / Payroll detail popups show Scheduled Date + Payroll
     * Type the same way the Approval Box and Internal Transfer entries do.
     */
    public ScheduledTransferDetailResponse(
            String                    scdulPymnNo,
            String                    scdulPymnTypeCd,
            String                    outBankCd,
            String                    outBankNm,
            String                    wdrlActNo,
            String                    titl,
            BigDecimal                totalTranAmt,
            BigDecimal                totTrnsCnt,
            String                    docNo,
            String                    regId,
            String                    requestor,
            String                    regDtm,
            String                    apprSeqNo,
            String                    apprStts,
            String                    apprSttsKey,
            String                    modDtm,
            String                    trnfYn,
            String                    trnfDtm,
            String                    ccyCd,
            String                    wdrlCcyCd,
            String                    orgFileNm,
            BigDecimal                fileSize,
            List<TransferLineDetail>  rows,
            List<ApprovalAttachmentRow> attachments,
            String                    payDate,
            String                    apprFundTp) {
        this(scdulPymnNo, scdulPymnTypeCd, outBankCd, outBankNm, wdrlActNo, titl,
             totalTranAmt, totTrnsCnt, docNo, regId, requestor, regDtm, apprSeqNo,
             apprStts, apprSttsKey, modDtm, trnfYn, trnfDtm, ccyCd, wdrlCcyCd,
             orgFileNm, fileSize, rows, attachments, null, payDate, apprFundTp);
    }
}
