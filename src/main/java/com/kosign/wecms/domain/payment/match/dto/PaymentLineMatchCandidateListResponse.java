package com.kosign.wecms.domain.payment.match.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Candidate-list response for the domestic payment-line banking-match popup.
 * Echoes the payment line's own key + amount / date / currency (so the popup
 * header can show what is being matched) plus the candidate rows.
 */
public record PaymentLineMatchCandidateListResponse(
        String                              scdulPymnNo,
        String                              scdulPymnDetlNo,
        String                              wdrlActNo,
        BigDecimal                          tranAmt,
        String                              payTranDt,
        String                              trnfCcyCd,
        long                                totCnt,
        List<PaymentLineMatchCandidateRow>  candidates) {
}
