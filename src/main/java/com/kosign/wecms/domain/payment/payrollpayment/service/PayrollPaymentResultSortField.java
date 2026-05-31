package com.kosign.wecms.domain.payment.payrollpayment.service;

import com.kosign.wecms.common.web.SortableField;

import java.util.Optional;

/**
 * Sort whitelist for the Payroll Payment "Payment Result" list endpoint
 * (legacy {@code pymn_0006_001_r001}). Mirrors
 * {@link com.kosign.wecms.domain.payment.vendorpayment.service.VendorPaymentResultSortField}
 * + extends it with the Payroll-only {@code APPR_FUND_TP} column.
 *
 * <p>Constant names mirror the V230 legacy-aligned View Setting
 * {@code FIELD_ID} values for {@code 'payment-payroll-payment-result'}.
 *
 * <p>Coverage:
 * <ul>
 *   <li>{@code TRAN_DT} → {@code regDtm} — Date/Time</li>
 *   <li>{@code FILE_NM} → {@code orgFileNm} — File Name</li>
 *   <li>{@code SCDUL_PYMN_DETL_NO} → {@code totTrnsCnt} — No. of Transactions</li>
 *   <li>{@code TRAN_AMT} → {@code totalTranAmt} — Total Transfer Amount</li>
 *   <li>{@code APPR_FUND_TP} → {@code apprStts} — Payroll Type (closest
 *       single-column proxy on the master). The legacy column lives on
 *       {@code EXPT_RESOL_MSTR.APPR_FUND_TP} and reaches the wire only
 *       once the Payroll Result list query LEFT JOINs {@code ExptResolMstr}
 *       on {@code APPR_SEQ_NO}. Until that backend change lands, the
 *       refactor maps the sort key to the master's {@code APPR_STTS}
 *       proxy so a sort request doesn't fall back to default ORDER BY
 *       and the frontend's sort cycle keeps working — when the LEFT JOIN
 *       lands, swap to the joined {@code apprFundTp} property.</li>
 *   <li>{@code PROC_STAT} → {@code trnfYn} — Transfer Status (closest
 *       single-column proxy on the master)</li>
 *   <li>{@code APPR_DOC_NO} → {@code erpFileNum} — Approval Ref.</li>
 *   <li>{@code TITL} → {@code titl} — Approval Title</li>
 * </ul>
 *
 * <p>The V144-era constants ({@code REG_DTM}, {@code TOT_TRNS_CNT},
 * {@code TOTAL_TRAN_AMT}, {@code TRNF_YN}, {@code APPR_SEQ_NO},
 * {@code DOC_NO}) are removed because the V230 seed no longer carries
 * those FIELD_IDs.
 */
public enum PayrollPaymentResultSortField implements SortableField {

    TRAN_DT           ("regDtm"),
    FILE_NM           ("orgFileNm"),
    SCDUL_PYMN_DETL_NO("totTrnsCnt"),
    TRAN_AMT          ("totalTranAmt"),
    APPR_FUND_TP      ("apprStts"),
    PROC_STAT         ("trnfYn"),
    APPR_DOC_NO       ("erpFileNum"),
    TITL              ("titl");

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
