package com.kosign.wecms.domain.payment.vendorpayment.service;

import com.kosign.wecms.common.web.SortableField;

import java.util.Optional;

/**
 * Per-screen sort whitelist for the Vendor Payment "Scheduled Payment"
 * list endpoint (legacy {@code pymn_0001_001_r001}).
 *
 * <p>Constant names mirror the V221 legacy-aligned View Setting
 * {@code FIELD_ID} values for {@code 'payment-vendor-payment-scheduled'}.
 * Vendor and Internal Transfer share the same physical
 * {@code SCDUL_PYMN_SUMR} table — only the type discriminator
 * ({@code SCDUL_PYMN_TYPE_CD = '01'} for Vendor vs {@code '03'} for
 * Internal Transfer) differs — so the same entity property names apply
 * (one-for-one with {@link com.kosign.wecms.domain.payment.internaltransfer.service.InternalTransferSortField}).
 *
 * <p>Coverage (entity-property targets resolved by the {@code search}
 * query — root {@code ScdulPymnSumr} unless prefixed with the
 * {@code erm} join alias):
 * <ul>
 *   <li>{@code REG_DTM} → {@code regDtm} — Date/Time</li>
 *   <li>{@code FILE_NM} → {@code orgFileNm} — File Name</li>
 *   <li>{@code TOT_TRNS_CNT} → {@code totTrnsCnt} — No. of Transactions</li>
 *   <li>{@code TOTAL_TRAN_AMT} → {@code totalTranAmt} — Total Transfer Amount</li>
 *   <li>{@code PAY_DATE} → {@code erm.payDate} ({@code EXPT_RESOL_MSTR.PAY_DATE}) — Scheduled Date</li>
 *   <li>{@code APPR_STTS} → {@code apprStts} — Approval Status</li>
 *   <li>{@code DRAFT_USER_NM} → {@code erm.draftUserNm} ({@code EXPT_RESOL_MSTR.DRAFT_USER_NM}) — Requestor</li>
 *   <li>{@code APPR_DOC_NO} → {@code erm.docNo} ({@code EXPT_RESOL_MSTR.DOC_NO}) — Approval Ref.</li>
 *   <li>{@code TITL} → {@code titl} — Approval Title</li>
 * </ul>
 *
 * <p><b>Sort/display alignment (2026-06-16).</b> Schedule Date, Requestor,
 * and Approval Ref are <em>displayed</em> from the LEFT-joined
 * {@code EXPT_RESOL_MSTR} (see {@code VendorPaymentService.toListRow} — C-3 /
 * C-4 + {@code DOC_NO}). Their sort keys therefore target the {@code erm}
 * join-alias properties ({@code erm.payDate} / {@code erm.draftUserNm} /
 * {@code erm.docNo}) — NOT the former {@code SCDUL_PYMN_SUMR} columns
 * ({@code payTranDt} / {@code regId} / {@code erpFileNum}) — so a header click
 * orders rows by the value the user sees. The {@code erm} alias is declared by
 * the {@code ScdulPymnSumrRepository.search} {@code LEFT JOIN ExptResolMstr erm}
 * query; Spring Data leaves an already-dot-qualified sort property un-prefixed
 * and Hibernate resolves it against that join. Root-entity columns stay
 * unqualified ({@code regDtm}, etc.) so Spring Data prefixes them with the
 * root alias as before.
 *
 * <p>Intentional gap (same blocker as Internal Transfer Scheduled):
 * {@code APPRVR_NM} requires a join to {@code EXPT_RESOL_APPRVR_INFM};
 * deferred until the per-approver chain port. The frontend renders the
 * header glyph but the backend degrades to default ORDER BY.
 *
 * <p>The V141-era constants ({@code REG_ID}, {@code APPR_SEQ_NO},
 * {@code TRNF_YN}, {@code DOC_NO}) are removed because the V221 seed no
 * longer carries those FIELD_IDs.
 */
public enum VendorPaymentSortField implements SortableField {

    REG_DTM       ("regDtm"),
    FILE_NM       ("orgFileNm"),
    TOT_TRNS_CNT  ("totTrnsCnt"),
    TOTAL_TRAN_AMT("totalTranAmt"),
    // Schedule Date is displayed from EXPT_RESOL_MSTR.PAY_DATE (C-4), so it must
    // sort by the same column — the erm join alias, not SCDUL_PYMN_SUMR.PAY_TRAN_DT.
    PAY_DATE      ("erm.payDate"),
    // APPR_STTS removed 2026-06-10: derived from EXPT_RESOL_MSTR (no
    // SCDUL_PYMN_SUMR.APPR_STTS column in legacy JEX) — not a single-table
    // sort key; falls back to default ORDER BY.
    // Requestor is displayed from EXPT_RESOL_MSTR.DRAFT_USER_NM (C-3) — sort by
    // the same column via the erm alias, not SCDUL_PYMN_SUMR.REG_ID (login id).
    DRAFT_USER_NM ("erm.draftUserNm"),
    // Approval Ref. is displayed from EXPT_RESOL_MSTR.DOC_NO — sort by the same
    // column via the erm alias, not SCDUL_PYMN_SUMR.ERP_FILE_NUM.
    APPR_DOC_NO   ("erm.docNo"),
    TITL          ("titl");

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
