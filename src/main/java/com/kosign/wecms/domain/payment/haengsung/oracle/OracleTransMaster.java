package com.kosign.wecms.domain.payment.haengsung.oracle;

import java.math.BigDecimal;

/**
 * One {@code TB_TRNS_MAST} row read from the HAENGSUNG Oracle ERP DB
 * (the "left" / physical {@code FILE_*} column block — see
 * {@code haengsung-payment-refresh} §14.1, M-1). Read-only carrier; the
 * import maps these onto {@code SCDUL_PYMN_SUMR} / {@code OVRS_PYMN_SUMR}.
 *
 * <p>{@code erpRcvFlag} is carried so the orchestrator can decide whether the
 * MAST receive write-back is needed (only when it is not already {@code 'Y'} —
 * §7).
 */
public record OracleTransMaster(
        String     fileDate,    // PK part — batch date YYYYMMDD
        String     fileNum,     // PK part — batch sequence
        String     fileGb,      // AF1=overseas, AC2=payroll, else scheduled
        String     fileNm,      // batch/file name (Vendor vs Expense split — M-2)
        String     bizNo,       // withdrawing company tax id
        String     outBankCd,   // withdrawal bank (string — M-6, not length 3)
        String     outAcctNo,   // withdrawal account
        BigDecimal regiCnt,     // total count → TOT_TRNS_CNT
        BigDecimal regiAmt,     // total amount → TOTAL_TRAN_AMT
        String     erpRcvFlag   // N/S/Y/C — write-back sets Y when != 'Y'
) { }
