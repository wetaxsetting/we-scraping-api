package com.kosign.wecms.domain.payment.internaltransfer.service;

import com.kosign.wecms.common.web.SortableField;

import java.util.Optional;

/**
 * Per-screen sort whitelist for the Internal Transfer "Scheduled Transfer"
 * list endpoint (legacy {@code pymn_0009_001_r001}).
 *
 * <p>Constant names mirror the View Setting {@code FIELD_ID} values
 * seeded in V85 ({@code 'payment-internal-transfer-scheduled'}). A wire
 * {@code sort_field} that doesn't match any constant resolves to
 * {@link Optional#empty()} so the service falls back to the default
 * ORDER BY rather than echoing user input into a query.
 *
 * <p>{@code CHECKBOX} (DISP_ORD=0) and {@code ETC} (DISP_ORD=99) are
 * special FIELD_IDs and intentionally absent from the whitelist —
 * special rows are never sortable per
 * {@code wecms-api-main/CLAUDE.md} §"Common table sort".
 *
 * <p>{@code APPR_STTS} is seeded with {@code SORT_YN='N'} in V85 because
 * the column is computed from the EXPT_RESOL_MSTR join (not a direct
 * entity field) — sorting it would require either a synthetic column
 * or a sub-query, which Sprint 2 defers. The whitelist mirrors the
 * seed by omitting the constant.
 */
public enum InternalTransferSortField implements SortableField {

    REG_DTM       ("regDtm"),
    TITL          ("titl"),
    TOT_TRNS_CNT  ("totTrnsCnt"),
    TOTAL_TRAN_AMT("totalTranAmt"),
    REG_ID        ("regId"),
    APPR_SEQ_NO   ("apprSeqNo"),
    TRNF_YN       ("trnfYn"),
    DOC_NO        ("erpFileNum");

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
