package com.kosign.wecms.domain.payment.service;

/**
 * Sort whitelist for the Overseas Payment Scheduled list endpoint
 * (legacy {@code pymn_0007_001_r001}).
 *
 * <p>Constant names mirror the V232 legacy-aligned View Setting
 * {@code FIELD_ID} values. The legacy page is per-transaction; the
 * refactor renders a batch-list view, so most per-transaction sort
 * keys (DBT_ACCT_NO, DBT_CCY_CD, BNFC_BANK_NM, BNFC_ACCT_NO,
 * BNFC_ADDR, CHART_ACCT_CD, TRNF_CCY_CD, FEE_PAYER_CD, MEMO) map to
 * the closest single-column proxy on the master / approval-line
 * tables until the page is refactored to per-transaction rendering.
 * The proxy mappings keep the frontend's sort cycle live (no
 * silent fallback to default ORDER BY) and document the gap inline
 * so a follow-up MR can swap in the joined property when the page
 * architecture catches up.
 *
 * <p>{@link #sqlAlias} is the column alias emitted by
 * {@code buildUnionSql} that is safe to inject directly into the
 * ORDER BY clause — never pass raw user input.
 */
public enum OverseasScheduledSortField {

    /* Canonical legacy pymn_0007_001 sort keys (V232) ------------------ */
    TRNF_DT       ("reg_dtm"),         // Transfer Date — proxy: batch REG_DTM
    TRNF_AMT      ("total_tran_amt"),  // Transfer Amount — proxy: batch TOTAL_TRAN_AMT
    APPR_STTS     ("appr_sts"),        // Approval Status
    USER_NM       ("reg_id"),          // Requestor — proxy: REG_ID (drafter)
    DBT_ACCT_NO   ("reg_dtm"),         // Debit Acct No. — proxy: batch REG_DTM (per-transaction; see Javadoc)
    DBT_CCY_CD    ("reg_dtm"),         // Debit Currency — proxy: batch REG_DTM
    BNFC_BANK_NM  ("reg_dtm"),         // Beneficiary Bank Name — proxy
    BNFC_ACCT_NO  ("reg_dtm"),         // Beneficiary Acct No. — proxy
    BNFC_ADDR     ("reg_dtm"),         // Beneficiary Address — proxy
    CHART_ACCT_CD ("reg_dtm"),         // Reporting Account — proxy
    TRNF_CCY_CD   ("reg_dtm"),         // Transfer Currency — proxy
    FEE_PAYER_CD  ("reg_dtm"),         // Fee Payer — proxy
    MEMO          ("reg_dtm"),         // Memo/Description — proxy

    /* Pre-V232 legacy aliases retained for backwards-compat — the page
       was previously seeded with these FIELD_IDs (V178 / V179) and any
       saved user override in `USER_SCR_OPTION_WECMS` carrying them
       would otherwise fall back to default ORDER BY. Safe to remove
       after one round of dev/QA confirms no stale overrides remain. */
    REG_DTM       ("reg_dtm"),
    TOT_TRNS_CNT  ("tot_trns_cnt"),
    TOTAL_TRAN_AMT("total_tran_amt"),
    REG_ID        ("reg_id"),
    APPR_SEQ_NO   ("appr_seq_no"),
    TITL          ("titl"),
    PAY_DATE      ("pay_date");

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
