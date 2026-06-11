package com.kosign.wecms.domain.payment.payrollpayment.dto;

import java.math.BigDecimal;

/**
 * One candidate bank transaction in the Payroll banking-match popup
 * (legacy {@code comm_0197_001_r001} → {@code IDO.ACCT_TRNS_HSTR_R001}
 * row). Carries exactly the keys the match action consumes
 * ({@code ACCT_SEQ_NO} + {@code BANK_TRNS_SHORT_ID} + {@code BANK_TRNS_FULL_ID})
 * plus the date/time/amount/remarks the popup grid renders.
 *
 * <p>Java fields camelCase; the global Jackson SNAKE_CASE strategy emits
 * lowercase snake_case on the wire.
 */
public record PayrollMatchCandidateRow(
        String     acctSeqNo,
        String     bankTrnsShortId,
        String     bankTrnsFullId,
        String     trscDt,
        String     trscTm,
        BigDecimal trscAmt,
        String     rmrk,
        String     rmrk2) {
}
