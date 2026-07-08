package com.kosign.wecms.domain.payment.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Candidate-list response for the Overseas payment-line banking-match popup
 * (legacy {@code comm_0197_001_r001} {@code isOversea} path). Mirrors the
 * domestic {@code PaymentLineMatchCandidateListResponse} but echoes the
 * overseas key + amount / date / currency (so the shared match dialog header
 * can show what is being matched). The frontend maps these overseas echo
 * fields onto the shared domestic shape (see {@code lib/api/payment.ts}).
 */
public record OverseasMatchCandidateListResponse(
        String                          ovrsPymnNo,
        String                          ovrsPymnDetlNo,
        String                          wdrlActNo,
        BigDecimal                      trnfAmt,
        String                          payTranDt,
        String                          ccyCd,
        long                            totCnt,
        List<OverseasMatchCandidateRow> candidates) {
}
