package com.kosign.wecms.domain.payment.internaltransfer.service;

import com.kosign.wecms.common.web.SortableField;

import java.util.Optional;

/**
 * Per-screen sort whitelist for the Internal Transfer "Transfer Result"
 * list endpoint (legacy {@code pymn_0010_001_r001}).
 *
 * <p>Constant names mirror the V219 legacy-aligned View Setting
 * {@code FIELD_ID} values for {@code 'payment-internal-transfer-result'}.
 * Same lenient {@link #parse(String)} contract as
 * {@link InternalTransferSortField}.
 *
 * <p>Coverage:
 * <ul>
 *   <li>{@code TRAN_DT} → {@code regDtm} — Date/Time</li>
 *   <li>{@code FILE_NM} → {@code orgFileNm} — File Name</li>
 *   <li>{@code SCDUL_PYMN_DETL_NO} → {@code totTrnsCnt} — No. of
 *       Transactions (the wire's `tot_trns_cnt` projection — counts the
 *       per-batch detail-line rows; legacy FIELD_ID name preserved
 *       verbatim from PROD `sys_scr_option_wecms`)</li>
 *   <li>{@code TRAN_AMT} → {@code totalTranAmt} — Total Transfer Amount</li>
 *   <li>{@code PROC_STAT} → {@code trnfYn} — Transfer Status (closest
 *       single-column proxy on the master; full process-status semantics
 *       require the dispatch-tables join from Phase B)</li>
 *   <li>{@code APPR_DOC_NO} → {@code erpFileNum} — Approval Ref. (same
 *       pattern as the Scheduled list: the resolved doc_no on the wire
 *       comes from a join map populated server-side; sorting against
 *       the entity-side property is the closest single-table path)</li>
 *   <li>{@code TITL} → {@code titl} — Approval Title</li>
 * </ul>
 *
 * <p>The legacy V131-era constants ({@code REG_DTM}, {@code TOT_TRNS_CNT},
 * {@code TOTAL_TRAN_AMT}, {@code TRNF_YN}, {@code APPR_SEQ_NO},
 * {@code DOC_NO}) are removed because the V219 seed no longer carries
 * those FIELD_IDs.
 */
public enum InternalTransferResultSortField implements SortableField {

    TRAN_DT           ("regDtm"),
    FILE_NM           ("orgFileNm"),
    SCDUL_PYMN_DETL_NO("totTrnsCnt"),
    TRAN_AMT          ("totalTranAmt"),
    PROC_STAT         ("trnfYn"),
    APPR_DOC_NO       ("erpFileNum"),
    TITL              ("titl");

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
