package com.kosign.wecms.domain.payment.internaltransfer.service;

import com.kosign.wecms.common.web.SortableField;

import java.util.Optional;

/**
 * Per-screen sort whitelist for the Internal Transfer "Transfer Result"
 * list endpoint (legacy {@code pymn_0010_001_r001}).
 *
 * <p>Constant names mirror the View Setting {@code FIELD_ID} values
 * seeded in V85 ({@code 'payment-internal-transfer-result'}). Same
 * lenient {@link #parse(String)} contract as
 * {@link InternalTransferSortField}.
 */
public enum InternalTransferResultSortField implements SortableField {

    REG_DTM       ("regDtm"),
    TITL          ("titl"),
    TOT_TRNS_CNT  ("totTrnsCnt"),
    TOTAL_TRAN_AMT("totalTranAmt"),
    TRNF_YN       ("trnfYn"),
    APPR_SEQ_NO   ("apprSeqNo"),
    DOC_NO        ("erpFileNum");

    private final String entityProperty;

    InternalTransferResultSortField(String entityProperty) {
        this.entityProperty = entityProperty;
    }

    @Override
    public String entityProperty() { return entityProperty; }

    public static Optional<InternalTransferResultSortField> parse(String raw) {
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
