package com.kosign.wecms.domain.payment.payrollpayment.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Response of {@code GET /api/v1/payment/payroll-payment/{id}/match/candidates}.
 * Mirrors the legacy {@code comm_0197_001_r001} batch-path result: the batch
 * summary echo (so the popup header can render withdrawal account / amount /
 * pay date) plus the candidate rows + total count.
 *
 * <p>Header-level / batch-level — Payroll matches one whole
 * {@code SCDUL_PYMN_SUMR} batch to one bank transaction.
 */
public record PayrollMatchCandidateListResponse(
        String                          scdulPymnNo,
        String                          wdrlActNo,
        BigDecimal                      totalTranAmt,
        String                          payTranDt,
        long                            totCnt,
        List<PayrollMatchCandidateRow>  candidates) {
}
