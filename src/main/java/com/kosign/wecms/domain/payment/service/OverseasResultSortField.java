package com.kosign.wecms.domain.payment.service;

/**
 * Sort whitelist for the Overseas Payment Result list endpoint
 * (legacy {@code pymn_0008_001_r001}).
 *
 * <p>Constant names mirror the V235 legacy-aligned View Setting
 * {@code FIELD_ID} values for {@code 'payment-overseas-payment-result'}.
 * Legacy {@code _2} suffix is stripped per the V235 area-collapse
 * convention (legacy {@code dym_area_id='table2'} → refactor
 * {@code 'default'}).
 *
 * <p>{@link #sqlExpr} is the SQL expression safe to inject into the
 * ORDER BY clause — never pass raw user input.
 *
 * <p>Coverage:
 * <ul>
 *   <li>{@code TRAN_DT}            → {@code ops.reg_dtm} — Date/Time</li>
 *   <li>{@code FILE_NM}            → {@code ops.org_file_nm} — File Name</li>
 *   <li>{@code SCDUL_PYMN_DETL_NO} → {@code ops.tot_trns_cnt} — No. of Transactions</li>
 *   <li>{@code TRAN_AMT}           → {@code ops.total_tran_amt} — Total Transfer Amount</li>
 *   <li>{@code PROC_STAT}          → {@code ops.trnf_yn} — Transfer Status (closest
 *       single-column proxy on the master)</li>
 *   <li>{@code APPR_DOC_NO}        → {@code erm.doc_no} — Approval Ref.</li>
 *   <li>{@code TITL}               → {@code appr_titl} — Approval Title</li>
 * </ul>
 *
 * <p>The pre-V235 constants ({@code REG_DTM}, {@code ORG_FILE_NM},
 * {@code TOT_TRNS_CNT}, {@code TOTAL_TRAN_AMT}, {@code DRAFT_USER_NM},
 * {@code DOC_NO}, {@code APPR_TITL}, {@code PAY_DATE},
 * {@code APPR_STTS}) are retained for backwards-compat with any saved
 * {@code USER_SCR_OPTION_WECMS} override that still carries the V177
 * names — they map to the same SQL expressions as the new constants
 * where applicable, so a stale user override sorts identically. Safe
 * to remove after one round of dev/QA confirms no stale overrides.
 */
public enum OverseasResultSortField {

    /* Legacy V235 constants ----------------------------------------- */
    // sqlExpr values reference the OUTER aliased result projection (see
    // OverseasPaymentRepository.buildResultSql) — the ORDER BY is appended to
    // the outermost SELECT whose columns are output aliases, so no table
    // prefix. F1 (2026-06-19): the Date/Time column is the transfer date
    // (TRNF_DT) aliased AS reg_dtm, matching legacy R005. PROC_STAT sorts on
    // the computed TRNF_STTS (F/P/C), not the always-'Y' trnf_yn.
    TRAN_DT           ("reg_dtm"),
    FILE_NM           ("file_nm"),
    SCDUL_PYMN_DETL_NO("tot_trns_cnt"),
    TRAN_AMT          ("total_tran_amt"),
    PROC_STAT         ("trnf_stts"),
    APPR_DOC_NO       ("doc_no"),
    TITL              ("appr_titl"),

    /* Pre-V235 backwards-compat constants --------------------------- */
    REG_DTM       ("reg_dtm"),
    ORG_FILE_NM   ("file_nm"),
    TOT_TRNS_CNT  ("tot_trns_cnt"),
    TOTAL_TRAN_AMT("total_tran_amt"),
    DRAFT_USER_NM ("draft_user_nm"),
    DOC_NO        ("doc_no"),
    APPR_TITL     ("appr_titl"),
    PAY_DATE      ("pay_date"),
    APPR_STTS     ("appr_stts");

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
