package com.kosign.wecms.domain.banking.repository;

import java.math.BigDecimal;

/**
 * Native-query projection for one account-detail row returned by the legacy
 * DB function {@code rept_0900_001_rec1(p_biz_key, p_req_date, p_req_date2)}
 * (legacy WSVC {@code rept_0009_001_r001} REC1). This is the
 * <b>source-of-truth</b> path for Daily Cash account detail — it replaces the
 * Java live-recompute ({@code DailyCashRecomputeRepository}), aligning with
 * Cash Flow and Daily Financial HS which also read their legacy DB functions
 * directly (see {@link DailyFinancialHsRow}).
 *
 * <p>Column → getter binding is by the quoted camelCase aliases in
 * {@link DailyCashReportFunctionRepository#rec1}. Bank name is projected
 * straight from the function ({@code wb_get_bank_nm(bank_cd,'s')} inside the
 * function body) — the architecture decision keeps the function as the single
 * source of truth, so the refactor no longer re-resolves it via
 * {@code BankCdResolver} on this path. Currency scaling ({@code wb_get_amt})
 * is applied inside the function.
 */
public interface DailyCashRec1Row {
    String getBizKey();
    String getRefDate();
    String getCcyCd();
    String getAcctTypeCd();
    String getBankCd();
    String getBankNm();
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
