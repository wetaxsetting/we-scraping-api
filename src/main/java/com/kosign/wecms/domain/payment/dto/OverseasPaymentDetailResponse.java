package com.kosign.wecms.domain.payment.dto;

import java.math.BigDecimal;
import java.util.List;
// rows element was changed from OverseasPaymentExcelDetailRow to OverseasPaymentDetailRowResponse
// so that the read path can expose trnf_yn without touching the upload validation contract.

/**
 * Detail view for a single overseas-payment batch (OVRS_PYMN_SUMR + OVRS_PYMN_DTLS).
 * Returned by {@code GET /api/v1/payment/overseas-payment/scheduled/{id}/details}.
 * Mirrors legacy {@code pymn_0007_001} approval-request dialog data.
 *
 * <p>The batch-level fields ({@code titl}, {@code regId}, {@code regDtm},
 * {@code modDtm}) describe the uploaded {@code OVRS_PYMN_SUMR} row — the
 * file's upload identity. The <b>approval-document</b> fields
 * ({@code apprTitl}, {@code apprCont}, {@code apprRegDtm}, {@code draftUserNm})
 * are sourced from {@code EXPT_RESOL_MSTR} when the batch has been submitted
 * for approval ({@code APPR_SEQ_NO} present) and are {@code null} otherwise.
 * The read-only Approval Details popup must bind its footer "Requested by …
 * on …" and "Approval Content" section to the <b>approval-document</b> fields,
 * never the batch upload datetime — legacy {@code comm_0195_001.ts:863,874,900}
 * reads {@code BLTB_POST_INFM.POST_CONT / REG_NM / REG_DTM}, surfaced in the
 * refactor through {@code EXPT_RESOL_MSTR.CONT / DRAFT_USER_NM / REG_DTM}.
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
        // Approval-document metadata from EXPT_RESOL_MSTR (null until submitted).
        String apprTitl,
        String apprCont,
        String apprRegDtm,
        String draftUserNm,
        List<OverseasPaymentDetailRowResponse> rows
) {}
