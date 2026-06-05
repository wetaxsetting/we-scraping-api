package com.kosign.wecms.domain.payment.payrollpayment.dto;

/**
 * Result of a Payroll banking match / unmatch write. Echoes the refreshed
 * header match state so the frontend can update the Result-detail header
 * without a full re-fetch (Phase 3). {@code linkMatchYn = "Y"} after match,
 * {@code "N"} after unmatch; the bank-ref fields are null after unmatch.
 */
public record PayrollMatchResultResponse(
        String scdulPymnNo,
        String linkMatchYn,
        String acctSeqNo,
        String bankTrnsShortId,
        String bankTrnsFullId,
        String matchLogSeq) {
}
