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
        List<ApprovalAttachmentRow> attachments) { }
