package com.kosign.wecms.domain.payment.internaltransfer.dto;

/**
 * One firm-banking-capable withdrawal account row returned from
 * {@code GET /api/v1/payment/internal-transfer/withdrawal-accounts}.
 *
 * <p>Mirrors the legacy {@code WSVC.comm_1001_001.xml:rule_Sub_0}
 * output shape (subset — fields the Internal Transfer upload dialog
 * actually consumes; balance + last-inquiry timestamps are dropped
 * here because the dialog doesn't render them). All values are
 * non-null per the {@code ACCT_MSTR} schema except {@code acctNickNm}
 * which is nullable.
 *
 * <p>Wire shape is lowercase snake_case via the global Jackson
 * {@code SNAKE_CASE} strategy. Frontend display format mirrors legacy
 * {@code pymn_0009_002.ts:573}:
 * {@code "${BANK_NM} ${ACCT_NO} (${ACCT_TYPE} ${CCY_CD})"}.
 */
public record WithdrawalAccountRow(
        String bankCd,
        String bankNm,
        String acctNo,
        String acctTypeCd,
        String ccyCd,
        String acctNickNm) {}
