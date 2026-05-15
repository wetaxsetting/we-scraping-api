package com.kosign.wecms.domain.banking.dto;

import jakarta.validation.constraints.Size;

/**
 * Body for {@code PUT /api/v1/banking/accounts/{acctSeqNo}/transactions/{trnsSeqNo}/confirm}.
 *
 * <p>All fields optional — a "bare" confirm just flips CNFM_YN to 'Y' on the
 * existing row. Setting any of the classification fields stamps them into
 * ACCT_TRNS_RCV_PAY_INFM so the row can be exported / matched downstream.
 */
public record TransactionConfirmRequest(
        @Size(max = 20)  String rcvPayTrcoKey,
        @Size(max = 200) String rcvPayTrcoNm,
        @Size(max = 20)  String rcvPayUsagCd,
        @Size(max = 50)  String vasAcctCd
) { }
