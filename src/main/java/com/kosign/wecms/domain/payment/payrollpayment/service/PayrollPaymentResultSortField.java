package com.kosign.wecms.domain.payment.payrollpayment.service;

import com.kosign.wecms.common.web.SortableField;

import java.util.Optional;

/**
 * Sort whitelist for the Payroll Payment "Payment Result" list endpoint
 * (legacy {@code pymn_0006_001_r001}). Mirrors
 * {@link com.kosign.wecms.domain.payment.expensepayment.service.ExpensePaymentResultSortField}.
 */
public enum PayrollPaymentResultSortField implements SortableField {

    REG_DTM       ("regDtm"),
    TITL          ("titl"),
    TOT_TRNS_CNT  ("totTrnsCnt"),
    TOTAL_TRAN_AMT("totalTranAmt"),
    TRNF_YN       ("trnfYn"),
    APPR_SEQ_NO   ("apprSeqNo"),
    DOC_NO        ("erpFileNum");

    private final String entityProperty;

    PayrollPaymentResultSortField(String entityProperty) {
        this.entityProperty = entityProperty;
    }

    @Override
    public String entityProperty() { return entityProperty; }

    public static Optional<PayrollPaymentResultSortField> parse(String raw) {
        if (raw == null) return Optional.empty();
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return Optional.empty();
        try {
            return Optional.of(valueOf(trimmed.toUpperCase()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
