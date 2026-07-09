package com.kosign.wecms.domain.payment.haengsung.cardaccount;

import java.math.BigDecimal;

/**
 * Read projection for the HAENGSUNG card-approval sync (Step 7 — legacy
 * {@code ERP_0100_003} via {@code CARD_MSTR_R005}). One row = one unsent
 * {@code CARD_TRNS_HSTR} approval joined to its {@code CARD_MSTR} (for
 * {@code CARD_HLDR_NM} + {@code LAST_INQ_DTTM}), with {@code BANK_CD} resolved
 * to the CITAD code and {@code AUTH_NO} computed per the legacy CASE
 * ({@code BANK_CD='11704020'} → {@code TRSC_DT||TRSC_TM}, else {@code AUTH_CODE}).
 *
 * <p>{@code cardSeqno} is the source {@code CARD_SEQ_NO} — the per-row send-flag
 * write-back key (the {@code CARD_TRNS_HSTR} PK is {@code (BIZ_KEY, CARD_SEQ_NO)}).
 * Note: the Oracle {@code CARD_NM} is mapped from {@code cardHldrNm} (legacy
 * override), not the source {@code CARD_TRNS_HSTR.CARD_NM}.
 */
public interface CardApprovalSyncRow {
    String getBizKey();
    String getBankCd();        // CITAD-mapped
    String getCardNo();
    String getApprDt();        // CARD_TRNS_HSTR.TRSC_DT
    String getApprTm();        // CARD_TRNS_HSTR.TRSC_TM
    String getAuthNo();        // legacy CASE on bank 11704020
    String getMerchNm();
    String getCardHldrNm();    // CARD_MSTR.CARD_HLDR_NM → Oracle CARD_NM
    String getCcyCd();
    BigDecimal getApprAmt();   // CARD_TRNS_HSTR.TRSC_AMT
    String getCity();
    String getLastInqDttm();   // CARD_MSTR.CARD_HIS_LST_INQ_DT
    String getCardSeqno();     // CARD_TRNS_HSTR.CARD_SEQ_NO (flag PK)
    String getPrchStts();      // → Oracle PURCHASE_ST
}
