package com.kosign.wecms.domain.payment.dto;

import java.math.BigDecimal;

/**
 * One candidate bank transaction in the Overseas payment-line banking-match
 * popup (legacy {@code comm_0197_001_r001} {@code isOversea} path). Mirrors
 * the domestic {@code PaymentLineMatchCandidateRow}.
 */
public record OverseasMatchCandidateRow(
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
