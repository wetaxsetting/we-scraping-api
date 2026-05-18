package com.kosign.wecms.domain.payment.dto;

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
        List<OverseasPaymentDetailRowResponse> rows
) {}
