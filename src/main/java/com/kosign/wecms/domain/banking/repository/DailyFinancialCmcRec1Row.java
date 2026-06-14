package com.kosign.wecms.domain.banking.repository;

import java.math.BigDecimal;

/**
 * Native-query projection for one per-account row returned by the legacy DB
 * function {@code rept_0015_001_r001_rec1(p_biz_key, p_req_date, p_req_date2)}
 * (legacy WSVC {@code rept_0015_001_r001} — Daily Financial CMC). This is the
 * <b>source of truth</b> for CMC: the legacy {@code DAILY_FINCL_REPT_DAILY_SUMR_DETL}
 * summary table is merely a materialised cache of this function
 * ({@code WB_INSERT_DAILY_FINCL_REPT_DAILY_SUMR_DETL_DATA} = {@code INSERT … SELECT …
 * FROM rept_0015_001_r001_rec1(...)}), so the refactor reads the function directly —
 * no write-on-read, no summary-table staleness. Same architecture as Daily Cash
 * ({@code rept_0900_001_rec1}).
 *
 * <p>The function returns <b>every</b> account of the tenant (all
 * {@code ACCT_TYPE_CD}, active and inactive); only the same-day movement
 * sub-join is restricted to {@code 03/04/05/06}, so {@code 07/08} (KRW
 * current/savings) rows come back with their balance and zero same-day counts.
 * {@code SND/RCV} are non-internal; {@code INTL_SND/INTL_RCV} are internal
 * (disjoint — legacy parity). Bank name is resolved inside the function
 * ({@code wb_get_bank_nm}); amounts are scaled by {@code wb_get_amt}.
 *
 * <p>{@code actvYn} is not produced by the function — it is joined in from
 * {@code ACCT_MSTR} by {@link DailyFinancialCmcFunctionRepository#rec1} so the
 * {@code activeAccountsOnly} toggle (DF-3) can filter and the wire can echo it.
 * Column → getter binding is by the quoted camelCase aliases in that repository.
 */
public interface DailyFinancialCmcRec1Row {
    String getBizKey();
    String getRefDate();
    String getCcyCd();
    String getAcctTypeCd();
    String getBankCd();
    String getBankNm();
    String getAcctNo();
    String getAcctNickNm();
    BigDecimal getBal();
    String getLoanNm();
    String getExpiDt();
    BigDecimal getAgmtAmt();
    BigDecimal getSndCnt();
    BigDecimal getSndAmt();
    BigDecimal getRcvCnt();
    BigDecimal getRcvAmt();
    BigDecimal getIntlRcvAmt();
    BigDecimal getIntlSndAmt();
    BigDecimal getIntlRcvCnt();
    BigDecimal getIntlSndCnt();
    /** Joined from {@code ACCT_MSTR.ACTV_YN} (not a function column). */
    String getActvYn();
}
