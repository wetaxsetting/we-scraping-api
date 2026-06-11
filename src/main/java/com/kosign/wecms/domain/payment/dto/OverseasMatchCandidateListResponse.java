package com.kosign.wecms.domain.payment.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Candidate-list response for the Overseas payment-line banking-match popup.
 * Echoes the payment line's own key + amount / date / currency (so the popup
 * header can show what is being matched) plus the candidate rows. Mirrors the
 * domestic {@code PaymentLineMatchCandidateListResponse}, keyed per overseas
 * line by {@code OVRS_PYMN_DETL_NO}.
 */
public record OverseasMatchCandidateListResponse(
        String                            ovrsPymnNo,
        String                            ovrsPymnDetlNo,
        String                            wdrlActNo,
        BigDecimal                        trnfAmt,
        String                            payTranDt,
        String                            ccyCd,
        long                              totCnt,
        List<OverseasMatchCandidateRow>   candidates) {
}
