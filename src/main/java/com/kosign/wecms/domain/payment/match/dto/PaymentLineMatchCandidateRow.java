package com.kosign.wecms.domain.payment.match.dto;

import java.math.BigDecimal;

/**
 * One candidate bank transaction in the domestic payment-line banking-match
 * popup (legacy {@code comm_0197_001_r001} {@code else} / domestic-detail
 * path). Shared by Vendor + Expense.
 */
public record PaymentLineMatchCandidateRow(
        String     acctSeqNo,
        String     bankTrnsShortId,
        String     bankTrnsFullId,
        String     trscDt,
        String     trscTm,
        BigDecimal trscAmt,
        String     ccyCd,
        String     rmrk,
        String     rmrk2) {
}
