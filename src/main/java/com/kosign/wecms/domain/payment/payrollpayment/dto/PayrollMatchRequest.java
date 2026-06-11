package com.kosign.wecms.domain.payment.payrollpayment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body of {@code POST /api/v1/payment/payroll-payment/{id}/match}.
 * The {@code {id}} path segment is the batch {@code SCDUL_PYMN_NO}; this body
 * carries the selected bank transaction's keys (straight off the candidate
 * row) + an optional memo (stored on {@code BUSI_MATCH_LOG.MEMO}).
 *
 * <p>Wire names are lowercase snake_case ({@code acct_seq_no},
 * {@code bank_trns_short_id}, {@code bank_trns_full_id}, {@code memo}).
 */
public record PayrollMatchRequest(
        @NotBlank @Size(max = 20)  String acctSeqNo,
        @NotBlank @Size(max = 20)  String bankTrnsShortId,
        @NotBlank @Size(max = 100) String bankTrnsFullId,
        @Size(max = 500)           String memo) {
}
