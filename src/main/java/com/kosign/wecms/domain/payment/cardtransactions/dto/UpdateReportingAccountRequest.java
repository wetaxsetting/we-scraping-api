package com.kosign.wecms.domain.payment.cardtransactions.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for
 * {@code PUT /api/v1/payment/card-transactions/{cardSeqNo}/reporting-account}
 * — mirrors legacy {@code card_0001_001_u002}.
 *
 * <p>Always-paired: the two fields move together so a stale frontend
 * cannot send only one half and silently leave the row in an
 * inconsistent state. Service validates that the {@code (seqNo, cd)}
 * pair resolves to a single active row in {@code CHART_ACCT_INFM} for
 * the current tenant — same shape as
 * {@code TransactionService.updateReportingAccount}.
 */
public record UpdateReportingAccountRequest(
        @NotBlank @Size(max = 10) @Pattern(regexp = "^[0-9]{1,10}$",
                message = "chart_acct_seq_no must be a 1..10 digit numeric string")
        String chartAcctSeqNo,

        @NotBlank @Size(max = 20) @Pattern(regexp = "^[A-Za-z0-9-]{1,20}$",
                message = "chart_acct_cd must be 1..20 alphanumeric/dash characters")
        String chartAcctCd) {
}
