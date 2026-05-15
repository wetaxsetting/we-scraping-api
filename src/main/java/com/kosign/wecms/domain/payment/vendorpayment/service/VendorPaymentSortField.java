package com.kosign.wecms.domain.payment.vendorpayment.service;

import com.kosign.wecms.common.web.SortableField;

import java.util.Optional;

/**
 * Per-screen sort whitelist for the Vendor Payment "Scheduled Payment"
 * list endpoint (legacy {@code pymn_0001_001_r001}).
 *
 * <p>Mirrors {@link com.kosign.wecms.domain.payment.internaltransfer.service.InternalTransferSortField}
 * one-for-one. Vendor and Internal Transfer share the same physical
 * {@code SCDUL_PYMN_SUMR} table — only the type discriminator
 * ({@code SCDUL_PYMN_TYPE_CD = '01'}) differs — so the same entity
 * property names apply.
 */
public enum VendorPaymentSortField implements SortableField {

    REG_DTM       ("regDtm"),
    TITL          ("titl"),
    TOT_TRNS_CNT  ("totTrnsCnt"),
    TOTAL_TRAN_AMT("totalTranAmt"),
    REG_ID        ("regId"),
    APPR_SEQ_NO   ("apprSeqNo"),
    TRNF_YN       ("trnfYn"),
    DOC_NO        ("erpFileNum");

    private final String entityProperty;

    VendorPaymentSortField(String entityProperty) {
        this.entityProperty = entityProperty;
    }

    @Override
    public String entityProperty() { return entityProperty; }

    public static Optional<VendorPaymentSortField> parse(String raw) {
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
