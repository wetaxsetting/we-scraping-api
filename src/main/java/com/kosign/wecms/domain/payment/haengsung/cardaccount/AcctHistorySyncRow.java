package com.kosign.wecms.domain.payment.haengsung.cardaccount;

import java.math.BigDecimal;

/**
 * Read projection for the HAENGSUNG account-history sync (Step 7 — legacy
 * {@code ERP_0100_003} via {@code ACCT_TRNS_HSTR_R029}). One row = one
 * unsent {@code ACCT_TRNS_HSTR} transaction joined to its {@code ACCT_MSTR}
 * (for {@code LAST_INQ_DTTM}), with {@code BANK_CD} already resolved to the
 * CITAD code via {@code CITAD_BANK_CD_LOOKUP}.
 *
 * <p>{@code trSeqno} is the source {@code ACCT_SEQ_NO} — it is both the Oracle
 * {@code TR_SEQNO} value and the per-row send-flag write-back key (the
 * {@code ACCT_TRNS_HSTR} PK is {@code (BIZ_KEY, ACCT_SEQ_NO)}).
 */
public interface AcctHistorySyncRow {
    String getBizKey();
    String getBankCd();        // CITAD-mapped
    String getAcctNo();
    String getTrDt();          // ACCT_TRNS_HSTR.TRSC_DT
    String getTrSeqno();       // ACCT_TRNS_HSTR.ACCT_SEQ_NO (also the flag PK)
    String getCcyCd();
    String getIoGb();          // ACCT_TRNS_HSTR.INOUT_DV
    BigDecimal getTrAmt();     // ACCT_TRNS_HSTR.TRSC_AMT
    BigDecimal getTrCurBal();  // ACCT_TRNS_HSTR.BAL
    String getLastInqDttm();   // ACCT_MSTR.ACCT_HIS_LST_INQ_DT
    String getAcctMemo();      // ACCT_TRNS_HSTR.RMRK
}
