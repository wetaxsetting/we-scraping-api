package com.kosign.wecms.domain.banking.dto;

/**
 * Single-transaction detail — extends {@link TransactionRow} with VAS account,
 * counterparty, and confirmation metadata. Wire shape is lowercase snake_case
 * (global Jackson SNAKE_CASE strategy), matching the sibling list endpoint.
 */
public record TransactionDetailResponse(
        String acctSeqNo,
        String trscDt,
        String trscTm,
        String inoutDv,
        String depositAmt,
        String withdrawAmt,
        String bal,
        String rmrk,
        String rmrk2,
        String bankRefNo,
        String ccyCd,
        String confYn,
        String rcvPayTrcoKey,
        String rcvPayTrcoNm,
        String rcvPayUsagCd,
        String vasAcctCd,
        String confirmedById,
        String confirmedByNm,
        String confirmedDtm
) { }
