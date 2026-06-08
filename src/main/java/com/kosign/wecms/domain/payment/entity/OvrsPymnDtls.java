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

/**
 * Individual transaction row within an overseas-payment Excel-upload batch.
 * Maps to {@code OVRS_PYMN_DTLS} created by V138.
 *
 * <p>One parent {@link OvrsPymnSumr} row (keyed by BIZ_KEY + OVRS_PYMN_NO)
 * has N child detail rows identified by TRN_SEQ (1-based).
 */
@Entity
@Table(name = "OVRS_PYMN_DTLS")
@IdClass(OvrsPymnDtlsId.class)
@Getter
@Setter
@NoArgsConstructor
public class OvrsPymnDtls {

    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    @Id
    @Column(name = "OVRS_PYMN_NO", length = 20, nullable = false)
    private String ovrsPymnNo;

    /** 1-based sequence number within the batch. */
    @Id
    @Column(name = "TRN_SEQ", nullable = false)
    private Integer trnSeq;

    @Column(name = "BNFC_CODE", length = 100)
    private String bnfcCode;

    @Column(name = "CCY_CD", length = 10)
    private String ccyCd;

    @Column(name = "TRNF_AMT", precision = 20, scale = 2)
    private BigDecimal trnfAmt;

    @Column(name = "FEE_PAYER_CD", length = 10)
    private String feePayerCd;

    @Column(name = "MEMO", length = 500)
    private String memo;

    @Column(name = "RPRT_ACCT_CD", length = 100)
    private String rprtAcctCd;

    @Column(name = "BNFC_NM", length = 200)
    private String bnfcNm;

    @Column(name = "BNFC_BANK_NM", length = 200)
    private String bnfcBankNm;

    @Column(name = "BIC_CD", length = 20)
    private String bicCd;

    @Column(name = "BNFC_ACCT_NO", length = 100)
    private String bnfcAcctNo;

    @Column(name = "BNFC_ADDR", length = 500)
    private String bnfcAddr;

    @Column(name = "TRNF_YN", length = 1, nullable = false)
    private String trnfYn = "N";

    /** File-batch transfer date (yyyyMMdd) — shadow key stamped by the
     *  dispatch flow. Joins to {@code TB_PAY_FILE_DATA.TRAN_DT}. */
    @Column(name = "PAY_TRAN_DT", length = 8)
    private String payTranDt;

    /** Per-line bank-side sequence (varchar of the matching
     *  {@code TB_PAY_FILE_DATA.SEQ}). Joins the line to its dispatch row
     *  for the Result-detail PROC_STAT/RS_CD lookup. Legacy parity per
     *  the same column on {@code SCDUL_PYMN_SUMR_DETL}. */
    @Column(name = "PAY_TRAN_SEQ", length = 10)
    private String payTranSeq;

    @Column(name = "REG_DTM", length = 14, nullable = false)
    private String regDtm;

    @Column(name = "REG_ID", length = 100)
    private String regId;

    // ── Banking-match identity + state (OVR-G1 Phase A, V248) ────────────────
    // Legacy parity with OVRS_PYMN_SUMR_DETL (comm_0197_* isOversea branch).
    // Additive only — the PK stays (BIZ_KEY, OVRS_PYMN_NO, TRN_SEQ).

    /** Per-tenant numeric match identity (legacy {@code OVRS_PYMN_DETL_NO}).
     *  Allocated at upload via {@code OverseasPaymentKeyGenerator}; unique per
     *  BIZ_KEY (index {@code ux_ovrs_pymn_dtls_biz_detl_no}). Becomes the
     *  {@code PAY_TRNS_ID} and the {@code ACCT_TRNS_HSTR} reverse stamp once
     *  banking match (Phase B) lands. VARCHAR(15) mirrors JEX DEV. */
    @Column(name = "OVRS_PYMN_DETL_NO", length = 15)
    private String ovrsPymnDetlNo;

    /** Match flag — {@code 'Y'} when this line is matched to a bank
     *  transaction, else {@code 'N'}. Candidate guard + row-visibility driver. */
    @Column(name = "LINK_MATCH_YN", length = 1, nullable = false)
    private String linkMatchYn = "N";

    /** Matched bank transaction's {@code ACCT_SEQ_NO} (reverse lookup on unmatch). */
    @Column(name = "ACCT_SEQ_NO", length = 20)
    private String acctSeqNo;

    /** Matched bank transaction short id. */
    @Column(name = "BANK_TRNS_SHORT_ID", length = 20)
    private String bankTrnsShortId;

    /** Matched bank transaction full id. */
    @Column(name = "BANK_TRNS_FULL_ID", length = 100)
    private String bankTrnsFullId;

    /** Audit — set by the match/unmatch patch (Phase B). */
    @Column(name = "MOD_ID", length = 100)
    private String modId;

    /** Audit — set by the match/unmatch patch (Phase B). yyyyMMddHHmmss. */
    @Column(name = "MOD_DTM", length = 14)
    private String modDtm;
}
