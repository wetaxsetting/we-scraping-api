package com.kosign.wecms.domain.payment.internaltransfer.service;

import com.kosign.wecms.common.web.SortableField;

import java.util.Optional;

/**
 * Per-screen sort whitelist for the Internal Transfer "Scheduled Transfer"
 * list endpoint (legacy {@code pymn_0009_001_r001}).
 *
 * <p>Constant names mirror the View Setting {@code FIELD_ID} values
 * seeded in V217 ({@code 'payment-internal-transfer-scheduled'}). A wire
 * {@code sort_field} that doesn't match any constant resolves to
 * {@link Optional#empty()} so the service falls back to the default
 * ORDER BY rather than echoing user input into a query.
 *
 * <p>{@code CHECKBOX} (DISP_ORD=0) and {@code ETC} (DISP_ORD=99) are
 * special FIELD_IDs and intentionally absent from the whitelist —
 * special rows are never sortable per
 * {@code wecms-api/CLAUDE.md} §"Common table sort".
 *
 * <p>Coverage of the V217 (legacy-aligned) FIELD_IDs:
 * <ul>
 *   <li>{@code REG_DTM} → {@code regDtm} — Date/Time</li>
 *   <li>{@code FILE_NM} → {@code orgFileNm} — File Name (V131
 *       {@code SCDUL_PYMN_SUMR.ORG_FILE_NM})</li>
 *   <li>{@code TOT_TRNS_CNT} → {@code totTrnsCnt} — No. of Transactions</li>
 *   <li>{@code TOTAL_TRAN_AMT} → {@code totalTranAmt} — Total Transfer Amount</li>
 *   <li>{@code PAY_DATE} → {@code payTranDt} — Scheduled Date (the wire
 *       field {@code pay_date} is sourced from the entity's {@code
 *       PAY_TRAN_DT} column via {@code InternalTransferService.toListRow})</li>
 *   <li>{@code APPR_STTS} → {@code apprStts} — Approval Status (V120
 *       column on the master; the per-approver chain that produces
 *       intermediate {@code "P"} / {@code "I"} states is still a Phase B
 *       follow-up, but the master flag itself is sortable today)</li>
 *   <li>{@code DRAFT_USER_NM} → {@code regId} — Requestor (the wire
 *       column ships {@code reg_id} verbatim; the legacy semantic name
 *       on the View Setting is {@code DRAFT_USER_NM}, but the refactor
 *       resolves the display name on the frontend from the requestor's
 *       login id)</li>
 *   <li>{@code APPR_DOC_NO} → {@code erpFileNum} — Approval Ref. (the
 *       resolved {@code doc_no} on the wire comes from the {@code
 *       docNoByApprSeqNo} map populated from {@code ExptResolMstr.docNo};
 *       sorting by the entity's own {@code erpFileNum} is the closest
 *       available single-table sort key. When the EXPT_RESOL_MSTR join
 *       lands as a real projection, this mapping can flip to the
 *       projected property.)</li>
 *   <li>{@code TITL} → {@code titl} — Approval Title (the master's
 *       legacy "title" column — same column the refactor previously
 *       surfaced as File Name fallback)</li>
 * </ul>
 *
 * <p>Intentional gaps — left to silent default-ORDER-BY fallback:
 * <ul>
 *   <li>{@code APPRVR_NM} — requires a join to
 *       {@code EXPT_RESOL_APPRVR_INFM} and projecting the first
 *       approver's display name. Same blocker as
 *       {@link com.kosign.wecms.domain.payment.approvalbox.service.ApprovalBoxSortField}'s
 *       {@code APPRER_NM}. Deferred until the per-approver chain port.</li>
 * </ul>
 *
 * <p>The legacy {@code REG_ID}, {@code APPR_SEQ_NO}, {@code TRNF_YN},
 * {@code DOC_NO} constants from the V131-era seed are removed because
 * the V217 seed no longer carries those FIELD_IDs.
 */
public enum InternalTransferSortField implements SortableField {

    REG_DTM       ("regDtm"),
    FILE_NM       ("orgFileNm"),
    TOT_TRNS_CNT  ("totTrnsCnt"),
    TOTAL_TRAN_AMT("totalTranAmt"),
    PAY_DATE      ("payTranDt"),
    // APPR_STTS removed 2026-06-10: SCDUL_PYMN_SUMR has no APPR_STTS column in
    // legacy JEX — approval status is derived from EXPT_RESOL_MSTR on read and
    // is not a single-table sort key. Sorting by it falls back to default ORDER BY
    // (the same graceful-degradation contract used for APPRVR_NM below).
    DRAFT_USER_NM ("regId"),
    APPR_DOC_NO   ("erpFileNum"),
    TITL          ("titl");

    private final String entityProperty;

    InternalTransferSortField(String entityProperty) {
        this.entityProperty = entityProperty;
    }

    @Override
    public String entityProperty() { return entityProperty; }

    /** Lenient parse — null / blank / unknown returns
     *  {@link Optional#empty()} so the service degrades gracefully to
     *  the default ORDER BY. */
    public static Optional<InternalTransferSortField> parse(String raw) {
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
