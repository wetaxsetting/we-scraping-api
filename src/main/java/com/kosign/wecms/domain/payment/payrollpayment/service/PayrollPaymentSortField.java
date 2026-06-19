package com.kosign.wecms.domain.payment.payrollpayment.service;

import com.kosign.wecms.common.web.SortableField;

import java.util.Optional;

/**
 * Per-screen sort whitelist for the Payroll Payment "Scheduled Payment"
 * list endpoint (legacy {@code pymn_0005_001_r001}).
 *
 * <p>Mirrors {@link com.kosign.wecms.domain.payment.internaltransfer.service.InternalTransferSortField}
 * one-for-one. Payroll, Expense, Vendor, and Internal Transfer share the
 * same physical {@code SCDUL_PYMN_SUMR} table — only the type discriminator
 * ({@code SCDUL_PYMN_TYPE_CD = '00'}) differs — so the same entity
 * property names apply.
 *
 * <p>Aligned with the V232 legacy {@code pymn_0005_001} seed FIELD_IDs
 * (FILE_NM=File Name, DRAFT_USER_NM=Requestor, APPR_DOC_NO=Approval Ref,
 * TITL=Approval Title; the V144-era REG_ID / APPR_SEQ_NO / TRNF_YN / DOC_NO
 * constants are removed because the V232 seed no longer carries those
 * FIELD_IDs).
 *
 * <p>{@code APPRVR_NM} (Approver) and {@code APPR_FUND_TP} (Payroll Type)
 * are intentionally absent: both project from {@code EXPT_RESOL_MSTR} /
 * the approval-line join, which the single-table paged query can't sort
 * on. The seed carries {@code SORT_YN='Y'} for legacy parity but the
 * frontend omits them from {@code SORTABLE_COLUMNS} so no glyph renders —
 * same deferral as Internal Transfer's APPRVR_NM.
 */
public enum PayrollPaymentSortField implements SortableField {

    REG_DTM       ("regDtm"),
    FILE_NM       ("orgFileNm"),
    TOT_TRNS_CNT  ("totTrnsCnt"),
    TOTAL_TRAN_AMT("totalTranAmt"),
    // Schedule Date is displayed from EXPT_RESOL_MSTR.PAY_DATE (toListRow), so it
    // must sort by that same column — the {@code erm} join alias declared by
    // ScdulPymnSumrRepository.search's {@code LEFT JOIN ExptResolMstr erm} — NOT
    // SCDUL_PYMN_SUMR.PAY_TRAN_DT (dispatch-time, null for pending/approved
    // batches). Legacy parity: SortHelper("pymn_0001_001_r001") sorts PAY_DATE by
    // lower(pay_date) = the projected ERM.PAY_DATE (SortHelper.java:3148-3150).
    // Spring Data leaves an already-dot-qualified sort property un-prefixed and
    // Hibernate resolves it against the join. Mirrors VendorPaymentSortField.
    PAY_DATE      ("erm.payDate"),
    // APPR_STTS removed 2026-06-10: derived from EXPT_RESOL_MSTR (no
    // SCDUL_PYMN_SUMR.APPR_STTS column in legacy JEX) — not a single-table
    // sort key; falls back to default ORDER BY.
    DRAFT_USER_NM ("regId"),
    APPR_DOC_NO   ("erpFileNum"),
    TITL          ("titl");

    private final String entityProperty;

    PayrollPaymentSortField(String entityProperty) {
        this.entityProperty = entityProperty;
    }

    @Override
    public String entityProperty() { return entityProperty; }

    public static Optional<PayrollPaymentSortField> parse(String raw) {
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
