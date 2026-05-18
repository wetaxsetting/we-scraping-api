package com.kosign.wecms.domain.banking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body for
 * {@code PATCH /api/v1/banking/accounts/{acctSeqNo}/transactions/{trnsSeqNo}/purpose}.
 *
 * <p>Carries the picker's selection — the 10-digit system PK
 * {@code USE_USAG_CD} from {@code BUSI_USAG_SET_INFM}. Backend
 * validates the row exists, is active ({@code USE_YN='Y'}), and
 * belongs to the current tenant before writing
 * {@code RCV_PAY_USAG_CD} on the receipt/payment record.
 *
 * <p>Clearing the purpose uses the sibling DELETE endpoint
 * (matches the Reporting Account precedent — DELETE clears, PATCH
 * assigns).
 */
public record TransactionPurposeRequest(
        @NotBlank @Size(max = 20) String useUsagCd
) { }
