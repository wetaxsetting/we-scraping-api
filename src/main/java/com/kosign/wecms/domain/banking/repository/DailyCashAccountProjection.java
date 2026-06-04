package com.kosign.wecms.domain.banking.repository;

import java.math.BigDecimal;

/**
 * Per-account row produced by the Daily Cash <b>live recompute</b> query
 * (refactor equivalent of legacy {@code rept_0900_001_rec1}). Read from
 * {@code ACCT_MSTR} + {@code ACCT_TRNS_HSTR} at request time, NOT from the
 * batch snapshot {@code DAILY_CASH_REPT_DAILY_SUMR_DETL} — so a selected
 * date still returns accounts (with balances) even when no snapshot row
 * exists for it, matching legacy behaviour.
 *
 * <p>Bank name is intentionally NOT projected here — it is resolved in Java
 * via {@code BankCdResolver} (the canonical refactor source), replacing the
 * legacy {@code wb_get_bank_nm(bank_cd,'s')} call inside {@code rec1}.
 * Currency scaling that legacy applied via {@code wb_get_amt(amt, ccy)} is
 * inlined in the query as {@code ROUND(amt, ccy='VND' ? 0 : 2)}.
 */
public interface DailyCashAccountProjection {
    String getCcyCd();
    String getAcctTypeCd();
    String getBankCd();
    String getAcctNo();
    BigDecimal getBal();
    String getLoanNm();
    String getExpiDt();
    BigDecimal getAgmtAmt();
    BigDecimal getSndCnt();
    BigDecimal getSndAmt();
    BigDecimal getRcvCnt();
    BigDecimal getRcvAmt();
}
