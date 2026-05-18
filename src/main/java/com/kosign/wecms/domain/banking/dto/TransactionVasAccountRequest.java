package com.kosign.wecms.domain.banking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body for
 * {@code PATCH /api/v1/banking/accounts/{acctSeqNo}/transactions/{trnsSeqNo}/vas-account}.
 *
 * <p>Carries the picker's selection — the user-visible
 * {@code VAS_ACCT_CD} from {@code BUSI_VAS_ACCT_INFM}. Backend
 * validates the row exists, is active ({@code STTS='A'}), and belongs
 * to the current tenant before writing
 * {@code VAS_ACCT_CD} on the receipt/payment record. Clearing the VAS
 * Account uses the sibling DELETE endpoint.
 */
public record TransactionVasAccountRequest(
        @NotBlank @Size(max = 50) String vasAcctCd
) { }
