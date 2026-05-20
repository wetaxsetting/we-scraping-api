package com.kosign.wecms.domain.payment.service;

/**
 * Sort whitelist for the Overseas Payment Scheduled list.
 * Constant names mirror the frontend View Setting FIELD_IDs; {@link #sqlAlias}
 * is the column alias emitted by {@code buildUnionSql} that is safe to inject
 * directly into the ORDER BY clause — never pass raw user input.
 */
public enum OverseasScheduledSortField {

    REG_DTM("reg_dtm"),
    TOT_TRNS_CNT("tot_trns_cnt"),
    TOTAL_TRAN_AMT("total_tran_amt"),
    REG_ID("reg_id"),
    APPR_STTS("appr_sts"),
    APPR_SEQ_NO("appr_seq_no"),
    TITL("titl"),
    PAY_DATE("pay_date");

    private final String sqlAlias;

    OverseasScheduledSortField(String sqlAlias) {
        this.sqlAlias = sqlAlias;
    }

    /** The SQL column alias safe to embed in an ORDER BY clause. */
    public String sqlAlias() {
        return sqlAlias;
    }

    /**
     * Lenient lookup. Returns {@code null} for unknown / blank values so the
     * service falls back to the default ORDER BY rather than rejecting the request.
     */
    public static OverseasScheduledSortField parse(String fieldId) {
        if (fieldId == null) return null;
        String trimmed = fieldId.trim();
        if (trimmed.isEmpty()) return null;
        try {
            return valueOf(trimmed.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
