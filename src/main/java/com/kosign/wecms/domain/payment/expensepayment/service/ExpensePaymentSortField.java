package com.kosign.wecms.domain.payment.expensepayment.service;

import com.kosign.wecms.common.web.SortableField;

import java.util.Optional;

/**
 * Per-screen sort whitelist for the Expense Payment "Scheduled Payment"
 * list endpoint (legacy {@code pymn_0003_001_r001}).
 *
 * <p>Constant names mirror the V225 legacy-aligned View Setting
 * {@code FIELD_ID} values for {@code 'payment-expense-payment-scheduled'}.
 * Expense, Vendor, and Internal Transfer share the same physical
 * {@code SCDUL_PYMN_SUMR} table — only the type discriminator
 * ({@code SCDUL_PYMN_TYPE_CD = '02'} for Expense) differs — so the same
 * entity property names apply (one-for-one with
 * {@link com.kosign.wecms.domain.payment.vendorpayment.service.VendorPaymentSortField}).
 *
 * <p>Coverage:
 * <ul>
 *   <li>{@code REG_DTM} → {@code regDtm} — Date/Time</li>
 *   <li>{@code FILE_NM} → {@code orgFileNm} — File Name</li>
 *   <li>{@code TOT_TRNS_CNT} → {@code totTrnsCnt} — No. of Transactions</li>
 *   <li>{@code TOTAL_TRAN_AMT} → {@code totalTranAmt} — Total Transfer Amount</li>
 *   <li>{@code PAY_DATE} → {@code payTranDt} — Scheduled Date</li>
 *   <li>{@code APPR_STTS} → {@code apprStts} — Approval Status</li>
 *   <li>{@code DRAFT_USER_NM} → {@code regId} — Requestor</li>
 *   <li>{@code APPR_DOC_NO} → {@code erpFileNum} — Approval Ref.</li>
 *   <li>{@code TITL} → {@code titl} — Approval Title</li>
 * </ul>
 *
 * <p>Intentional gap (same blocker as the Internal Transfer / Vendor
 * Scheduled lists): {@code APPRVR_NM} requires a join to
 * {@code EXPT_RESOL_APPRVR_INFM}; deferred until the per-approver chain
 * port. The frontend renders the header glyph but the backend degrades
 * to default ORDER BY.
 *
 * <p>The V143-era constants ({@code REG_ID}, {@code APPR_SEQ_NO},
 * {@code TRNF_YN}, {@code DOC_NO}) are removed because the V225 seed no
 * longer carries those FIELD_IDs.
 */
public enum ExpensePaymentSortField implements SortableField {

    REG_DTM       ("regDtm"),
    FILE_NM       ("orgFileNm"),
    TOT_TRNS_CNT  ("totTrnsCnt"),
    TOTAL_TRAN_AMT("totalTranAmt"),
    PAY_DATE      ("payTranDt"),
    APPR_STTS     ("apprStts"),
    DRAFT_USER_NM ("regId"),
    APPR_DOC_NO   ("erpFileNum"),
    TITL          ("titl");

    private final String entityProperty;

    ExpensePaymentSortField(String entityProperty) {
        this.entityProperty = entityProperty;
    }

    @Override
    public String entityProperty() { return entityProperty; }

    public static Optional<ExpensePaymentSortField> parse(String raw) {
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
