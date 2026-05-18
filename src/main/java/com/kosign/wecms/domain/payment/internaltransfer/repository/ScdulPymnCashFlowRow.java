package com.kosign.wecms.domain.payment.internaltransfer.repository;

import java.math.BigDecimal;

/**
 * JPA interface projection for the Cash Flow report's scheduled-payment
 * source. Carries detail-row fields (chart classifier + amount) plus
 * the header fields used by the legacy {@code all_payment_data}
 * scheduled UNION branch (bank account, currency, registration time,
 * approval-seq).
 */
public interface ScdulPymnCashFlowRow {
    String getChartAcctSeqNo();
    String getChartAcctCd();
    BigDecimal getTranAmt();
    /** Header currency — defaults to {@code "VND"} via service-level COALESCE. */
    String getCcyCd();
    String getOutBankCd();
    String getWdrlActNo();
    /** Header {@code reg_dtm} in {@code yyyyMMddHHmmss} shape. */
    String getRegDtm();
    String getApprSeqNo();
}
