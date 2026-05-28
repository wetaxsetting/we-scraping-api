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
    private String bnfcActNo;

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
}
