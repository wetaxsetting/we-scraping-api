package com.kosign.wecms.domain.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Individual beneficiary/transaction row within an overseas-payment batch.
 * Maps to the legacy JEX table {@code OVRS_PYMN_SUMR_DETL} (V76) column-for-column.
 *
 * <p>One parent {@link OvrsPymnSumr} row (BIZ_KEY + OVRS_PYMN_NO) has N child
 * detail rows identified by the natural key {@code OVRS_PYMN_DETL_NO} (a
 * per-tenant numeric sequence — NOT the refactor's old 1-based {@code TRN_SEQ}).
 *
 * <p>Re-pointed 2026-06-09 from the refactor-invented phantom table
 * {@code OVRS_PYMN_DTLS}. The class name is retained to limit churn; the
 * mapping is the legacy {@code OVRS_PYMN_SUMR_DETL}. This entity maps the
 * subset of columns the overseas-payment flow reads/writes; the remaining
 * JEX columns ({@code pay_file_seq_no}, {@code pay_seq}, {@code erp_*},
 * {@code acct_seq_no}, {@code bank_trns_*}, {@code link_match_yn}) are nullable /
 * defaulted in JEX and intentionally unmapped — Hibernate {@code validate}
 * ignores unmapped DB columns.
 */
@Entity
@Table(name = "OVRS_PYMN_SUMR_DETL")
@IdClass(OvrsPymnSumrDetlId.class)
@Getter
@Setter
@NoArgsConstructor
public class OvrsPymnSumrDetl {

    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    @Id
    @Column(name = "OVRS_PYMN_NO", length = 14, nullable = false)
    private String ovrsPymnNo;

    /** Natural detail key — per-tenant numeric sequence (legacy parity). */
    @Id
    @Column(name = "OVRS_PYMN_DETL_NO", length = 15, nullable = false)
    private String ovrsPymnDetlNo;

    @Column(name = "TRNF_AMT", precision = 20, scale = 6, nullable = false)
    private BigDecimal trnfAmt;

    @Column(name = "TRNF_CCY_CD", length = 3, nullable = false)
    private String ccyCd;

    @Column(name = "BNFC_NM", length = 100, nullable = false)
    private String bnfcNm;

    @Column(name = "BNFC_BANK_NAME", length = 200, nullable = false)
    private String bnfcBankNm;

    @Column(name = "BNFC_BANK_CD", length = 15, nullable = false)
    private String bnfcBankCd;

    @Column(name = "BNFC_ACCT_NO", length = 100, nullable = false)
    private String bnfcAcctNo;

    @Column(name = "BNFC_BANK_BIC", length = 15, nullable = false)
    private String bicCd;

    @Column(name = "BNFC_ADDR", length = 500, nullable = false)
    private String bnfcAddr;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "FEE_PAYER_CD", length = 1, nullable = false)
    private String feePayerCd;

    @Column(name = "TRCO_KEY", length = 20, nullable = false)
    private String trcoKey;

    @Column(name = "HAND_TRCO_KEY", length = 20, nullable = false)
    private String handTrcoKey;

    @Column(name = "MEMO", length = 500, nullable = false)
    private String memo;

    /** Reporting account — legacy {@code CHART_ACCT_CD}. Nullable. */
    @Column(name = "CHART_ACCT_CD", length = 20)
    private String rprtAcctCd;

    /**
     * Reporting account sequence — legacy {@code CHART_ACCT_SEQ_NO}. Nullable.
     *
     * <p>This is the {@code CHART_ACCT_INFM} primary-key identity (PK =
     * BIZ_KEY + CHART_ACCT_SEQ_NO); {@code CHART_ACCT_CD} alone is non-unique.
     * The Excel-upload path and the legacy inline picker both write
     * {@code (CHART_ACCT_SEQ_NO, CHART_ACCT_CD)} together, and the legacy
     * name lookups {@code wb_get_chart_acct_nm} / {@code wb_get_chart_acct_lv_nm}
     * resolve on the seq_no, so the reporting-account save must persist both
     * to keep the pair consistent for legacy/migration parity.
     */
    @Column(name = "CHART_ACCT_SEQ_NO", length = 10)
    private String chartAcctSeqNo;

    /** File-batch transfer date (yyyyMMdd) — shadow key stamped by the
     *  dispatch flow. Joins to {@code TB_PAY_FILE_DATA.TRAN_DT}. */
    @Column(name = "PAY_TRAN_DT", length = 8)
    private String payTranDt;

    /** Per-line bank-side sequence (varchar of the matching
     *  {@code TB_PAY_FILE_DATA.SEQ}). Joins the line to its dispatch row
     *  for the Result-detail PROC_STAT/RS_CD lookup. */
    @Column(name = "PAY_TRAN_SEQ", length = 10)
    private String payTranSeq;

    @Column(name = "REG_ID", length = 100, nullable = false)
    private String regId;

    @Column(name = "REG_DTM", length = 14, nullable = false)
    private String regDtm;

    @Column(name = "MOD_ID", length = 100, nullable = false)
    private String modId;

    @Column(name = "MOD_DTM", length = 14, nullable = false)
    private String modDtm;
}
