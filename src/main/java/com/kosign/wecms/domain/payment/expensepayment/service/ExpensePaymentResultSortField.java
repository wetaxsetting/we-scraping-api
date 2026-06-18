package com.kosign.wecms.domain.payment.expensepayment.service;

import com.kosign.wecms.common.web.SortableField;

import java.util.Optional;

/**
 * Sort whitelist for the Expense Payment "Payment Result" list endpoint
 * (legacy {@code pymn_0004_001_r001}). Mirrors
 * {@link com.kosign.wecms.domain.payment.vendorpayment.service.VendorPaymentResultSortField}
 * one-for-one — Expense, Vendor, and Internal Transfer share the same
 * physical {@code SCDUL_PYMN_SUMR} table (type discriminator
 * {@code SCDUL_PYMN_TYPE_CD = '02'} for Expense).
 *
 * <p>Constant names mirror the V227 legacy-aligned View Setting
 * {@code FIELD_ID} values for {@code 'payment-expense-payment-result'}.
 *
 * <p>Coverage:
 * <ul>
 *   <li>{@code TRAN_DT} → {@code trnfDtm} — Date/Time (dispatch datetime;
 *       matches the displayed value + the date filter window — legacy R007)</li>
 *   <li>{@code FILE_NM} → {@code orgFileNm} — File Name</li>
 *   <li>{@code SCDUL_PYMN_DETL_NO} → {@code totTrnsCnt} — No. of Transactions</li>
 *   <li>{@code TRAN_AMT} → {@code totalTranAmt} — Total Transfer Amount</li>
 *   <li>{@code PROC_STAT} → {@code trnfYn} — Transfer Status (closest
 *       single-column proxy on the master)</li>
 *   <li>{@code APPR_DOC_NO} → {@code erpFileNum} — Approval Ref.</li>
 *   <li>{@code TITL} → {@code titl} — Approval Title</li>
 * </ul>
 *
 * <p>The V143-era constants ({@code REG_DTM}, {@code TOT_TRNS_CNT},
 * {@code TOTAL_TRAN_AMT}, {@code TRNF_YN}, {@code APPR_SEQ_NO},
 * {@code DOC_NO}) are removed because the V227 seed no longer carries
 * those FIELD_IDs.
 */
public enum ExpensePaymentResultSortField implements SortableField {

    TRAN_DT           ("trnfDtm"),
    FILE_NM           ("orgFileNm"),
    SCDUL_PYMN_DETL_NO("totTrnsCnt"),
    TRAN_AMT          ("totalTranAmt"),
    PROC_STAT         ("trnfYn"),
    APPR_DOC_NO       ("erpFileNum"),
    TITL              ("titl");

    private final String entityProperty;

    ExpensePaymentResultSortField(String entityProperty) {
        this.entityProperty = entityProperty;
    }

    @Override
    public String entityProperty() { return entityProperty; }

    public static Optional<ExpensePaymentResultSortField> parse(String raw) {
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
