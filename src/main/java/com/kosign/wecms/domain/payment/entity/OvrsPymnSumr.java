package com.kosign.wecms.domain.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Overseas Payment Summary — batch / Excel-upload payment records (type = 'E').
 * Source: wabooks.entity.OvrsPymnSumr (JEX DEV).
 * Table created by V85.
 *
 * <p>REG_DTM / MOD_DTM are VARCHAR(14) for legacy parity — this entity
 * does NOT extend {@code BaseEntity} (which uses OffsetDateTime).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "OVRS_PYMN_SUMR")
@IdClass(OvrsPymnSumrId.class)
public class OvrsPymnSumr {

    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    @Id
    @Column(name = "OVRS_PYMN_NO", length = 20, nullable = false)
    private String ovrsPymnNo;

    @Column(name = "OVRS_PYMN_TYPE_CD", length = 10)
    private String ovrsPymnTypeCd;

    /** Title / original filename of the Excel batch. */
    @Column(name = "TITL", length = 200)
    private String titl;

    @Column(name = "CCY_CD", length = 10)
    private String ccyCd;

    @Column(name = "TOTAL_TRAN_AMT", precision = 20, scale = 2)
    private BigDecimal totalTranAmt;

    @Column(name = "TOT_TRNS_CNT")
    private Integer totTrnsCnt;

    @Column(name = "APPR_SEQ_NO", length = 20)
    private String apprSeqNo;

    /** Phase-A bridge: approval status stamped by the approval-action endpoint.
     *  DEV stores this in EXPT_RESOL_MSTR; this local column fills the gap
     *  until EXPT_RESOL_MSTR writes are ported. C=approved, X=rejected, P=pending. */
    @Column(name = "APPR_STTS", length = 1)
    private String apprStts;

    @Column(name = "OUT_BANK_CD", length = 20)
    private String outBankCd;

    @Column(name = "WDRL_ACT_NO", length = 100)
    private String wdrlActNo;

    @Column(name = "TRNF_YN", length = 1, nullable = false)
    private String trnfYn;

    /** Transfer datetime (yyyyMMddHHmmss) — Overseas legacy uses
     *  {@code TRNF_DT} where SCDUL_PYMN uses {@code TRNF_DTM}. Stamped
     *  by the dispatch flow alongside {@code TRNF_YN='Y'}. */
    @Column(name = "TRNF_DT", length = 14)
    private String trnfDt;

    /** Withdrawal-account currency — distinct from {@link #ccyCd} which
     *  is the transfer currency. Populated by the Excel upload + read by
     *  the dispatch flow when deciding the TB_PAY_FILE_DATA currency. */
    @Column(name = "WDRL_CCY_CD", length = 3)
    private String wdrlCcyCd;

    /** File-batch transfer date (yyyyMMdd) — shadow key stamped by the
     *  dispatch flow. Joins to {@code TB_PAY_FILE_MST.TRAN_DT}. */
    @Column(name = "PAY_TRAN_DT", length = 8)
    private String payTranDt;

    /** File-batch sequence — shadow key stamped by the dispatch flow.
     *  Joins to {@code TB_PAY_FILE_MST.FILE_SEQ_NO}. */
    @Column(name = "PAY_FILE_SEQ_NO", length = 7)
    private String payFileSeqNo;

    @Column(name = "OUT_BANK_NM", length = 100)
    private String outBankNm;

    @Column(name = "ORG_FILE_NM", length = 300)
    private String orgFileNm;

    @Column(name = "SAVE_FILE_NM", length = 300)
    private String saveFileNm;

    @Column(name = "FILE_URL", length = 500)
    private String fileUrl;

    @Column(name = "FILE_SIZE", precision = 20, scale = 0)
    private BigDecimal fileSize;

    @Column(name = "REG_DTM", length = 14, nullable = false)
    private String regDtm;

    @Column(name = "REG_ID", length = 100)
    private String regId;

    @Column(name = "MOD_DTM", length = 14)
    private String modDtm;

    @Column(name = "MOD_ID", length = 100)
    private String modId;
}
