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
        String acctNickNm,
        /**
         * {@code ACCT_MSTR.ACCT_NM} — the bank-registered account-holder name.
         *
         * <p>Added for C-14 / FIX B. This is <b>not</b> a display nicety: on the Overseas rail this
         * column becomes {@code TB_PAY_FILE_DATA.SENDER_NM} and then the fixed-length record's
         * 150-byte {@code senderName} slot ({@code OverseasDispatchExecutor.resolveSenderNm}). It is
         * the one wire field with no entry surface — nobody types it, so nothing validated it, and
         * until now the picker could not even show the user the value their payment would send.
         *
         * <p>{@code varchar(100)} <i>characters</i>, so 100 Hangul/CJK characters is 300 bytes against
         * a 150-byte slot. Nullable — the column is nullable and the scrape may not have returned a
         * holder name.
         */
        String acctNm) {}
