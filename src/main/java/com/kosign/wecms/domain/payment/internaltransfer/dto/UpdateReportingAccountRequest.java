package com.kosign.wecms.domain.payment.internaltransfer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for
 * {@code PATCH /api/v1/payment/internal-transfer/{id}/lines/{lineId}/reporting-account}.
 * Mirrors legacy {@code WSVC.pymn_0009_002_u002.xml:in}.
 *
 * <p>Both fields are required and reference a row in
 * {@code CHART_ACCT_INFM} via the {@code (BIZ_KEY, CHART_ACCT_SEQ_NO,
 * DEL_YN='N')} predicate the legacy action JSP enforces.
 */
public record UpdateReportingAccountRequest(

        @NotBlank
        @Size(max = 10)
        @Pattern(regexp = "^[0-9]{1,10}$",
                 message = "chart_acct_seq_no: digits only, ≤ 10 chars")
        String chartAcctSeqNo,

        @NotBlank
        @Size(max = 20)
        String chartAcctCd) { }
