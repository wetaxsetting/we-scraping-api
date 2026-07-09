package com.kosign.wecms.domain.payment.dto;

import com.kosign.wecms.domain.payment.internaltransfer.dto.ApprovalAttachmentRow;

import java.math.BigDecimal;
import java.util.List;
// rows element was changed from OverseasPaymentExcelDetailRow to OverseasPaymentDetailRowResponse
// so that the read path can expose trnf_yn without touching the upload validation contract.

/**
 * Detail view for a single overseas-payment batch (OVRS_PYMN_SUMR + OVRS_PYMN_DTLS).
 * Returned by {@code GET /api/v1/payment/overseas-payment/scheduled/{id}/details}.
 * Mirrors legacy {@code pymn_0007_001} approval-request dialog data.
 */
public record OverseasPaymentDetailResponse(
        String ovrsPymnNo,
        String titl,
        String apprStts,
        BigDecimal totalTranAmt,
        String ccyCd,
        Integer totTrnsCnt,
        String regId,
        String regDtm,
        String modDtm,
        String outBankCd,
        String wdrlActNo,
        String wdrlCcyCd,
        String docNo,
        String apprSeqNo,
        String fileNm,
        String fileSize,
        String orgFileNm,
        List<OverseasPaymentDetailRowResponse> rows,
        /** GAP-OVS-ATT: approval attachments — UNION of EXPT_RESOL_FILE +
         *  EXPT_RESOL_PAY_EVID_FILE keyed by APPR_SEQ_NO (mirrors legacy
         *  IDO.EXPT_RESOL_FILE_R002). Empty when the batch has no approval
         *  request yet. Previously never loaded for overseas. */
        List<ApprovalAttachmentRow> attachments,
        /** GAP-OVS-MODE: {@code true} when this approval is a legacy
         *  "M-mode" record living only in {@code OVRS_PYMN_MSTR} (not
         *  {@code OVRS_PYMN_SUMR}). Detail line rendering for M-mode is not
         *  fully supported yet — the popup shows header + attachments and an
         *  empty {@code rows} list instead of throwing {@code WBE2045}. */
        boolean legacyMmode,
        /** Human-readable notice when {@link #legacyMmode} is true; null
         *  otherwise. Lets the popup explain why the line table is empty. */
        String legacyMmodeNotice,
        /** Approval Content note shown in the popup's "Approval Content"
         *  section. Legacy source of truth = {@code BLTB_POST_INFM.POST_CONT}
         *  joined by {@code APPR_SEQ_NO} — the legacy approval-detail reads
         *  {@code EXPT_RESOL_MSTR_R009 / R010} select {@code BPI.POST_CONT} and
         *  {@code comm_0195_001.ts:863} renders it into {@code #approval_input}.
         *  This is {@code POST_CONT}, NOT {@code EXPT_RESOL_MSTR.CONT}: the two
         *  columns can diverge (verified on the live JEX DB) and the legacy
         *  popup displays {@code POST_CONT}. null when the batch has no approval
         *  request yet or the note is blank. */
        String cont
) {
    /**
     * Backward-compatible constructor for the S-mode (OVRS_PYMN_SUMR) path
     * — supplies the attachments list and defaults the legacy-M-mode flags
     * to {@code (false, null)}.
     */
    public OverseasPaymentDetailResponse(
            String ovrsPymnNo, String titl, String apprStts, BigDecimal totalTranAmt,
            String ccyCd, Integer totTrnsCnt, String regId, String regDtm, String modDtm,
            String outBankCd, String wdrlActNo, String wdrlCcyCd, String docNo,
            String apprSeqNo, String fileNm, String fileSize, String orgFileNm,
            List<OverseasPaymentDetailRowResponse> rows,
            List<ApprovalAttachmentRow> attachments, String cont) {
        this(ovrsPymnNo, titl, apprStts, totalTranAmt, ccyCd, totTrnsCnt, regId, regDtm,
             modDtm, outBankCd, wdrlActNo, wdrlCcyCd, docNo, apprSeqNo, fileNm, fileSize,
             orgFileNm, rows, attachments, false, null, cont);
    }
}
