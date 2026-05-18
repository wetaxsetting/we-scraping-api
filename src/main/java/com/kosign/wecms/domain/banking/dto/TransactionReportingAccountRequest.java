package com.kosign.wecms.domain.banking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body for
 * {@code PATCH /api/v1/banking/accounts/{acctSeqNo}/transactions/{trnsSeqNo}/reporting-account}.
 *
 * <p>Both fields are required — clearing a reporting account is a
 * separate concern not exposed by the Transaction History picker (the
 * legacy {@code fnsh_0200_001} cell only writes a non-null
 * {@code CHART_ACCT_CD}). Backend validates that the
 * {@code (chart_acct_seq_no, chart_acct_cd)} pair refers to an active
 * row in {@code CHART_ACCT_INFM} for the current tenant before the
 * write commits.
 */
public record TransactionReportingAccountRequest(
        @NotBlank @Size(max = 10) String chartAcctSeqNo,
        @NotBlank @Size(max = 20) String chartAcctCd
) { }
