package com.kosign.wecms.domain.payment.service;

/**
 * Sort whitelist for the Overseas Payment Result list.
 * Constant names mirror the frontend View Setting FIELD_IDs; {@link #sqlExpr}
 * is the SQL expression safe to inject into the ORDER BY clause — never pass
 * raw user input.
 */
public enum OverseasResultSortField {

    REG_DTM("ops.reg_dtm"),
    ORG_FILE_NM("ops.org_file_nm"),
    TOT_TRNS_CNT("ops.tot_trns_cnt"),
    TOTAL_TRAN_AMT("ops.total_tran_amt"),
    DRAFT_USER_NM("ops.draft_user_nm"),
    DOC_NO("erm.doc_no"),
    APPR_TITL("appr_titl"),
    PAY_DATE("pay_date"),
    APPR_STTS("appr_stts");

    private final String sqlExpr;

    OverseasResultSortField(String sqlExpr) {
        this.sqlExpr = sqlExpr;
    }

    /** The SQL expression safe to embed in an ORDER BY clause. */
    public String sqlExpr() {
        return sqlExpr;
    }

    /**
     * Lenient lookup. Returns {@code null} for unknown / blank values so the
     * service falls back to the default ORDER BY rather than rejecting the request.
     */
    public static OverseasResultSortField parse(String fieldId) {
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
