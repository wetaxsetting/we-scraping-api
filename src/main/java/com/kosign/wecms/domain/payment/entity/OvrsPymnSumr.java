package com.kosign.wecms.domain.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
    @Column(name = "OVRS_PYMN_NO", length = 14, nullable = false)
    private String ovrsPymnNo;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "OVRS_PYMN_TYPE_CD", length = 2)
    private String ovrsPymnTypeCd;

    /** Title / original filename of the Excel batch. */
    @Column(name = "TITL", length = 100)
    private String titl;

    @Column(name = "CCY_CD", length = 3)
    private String ccyCd;

    @Column(name = "TOTAL_TRAN_AMT", precision = 20, scale = 6)
    private BigDecimal totalTranAmt;

    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "TOT_TRNS_CNT", precision = 10, scale = 0)
    private Integer totTrnsCnt;

    @Column(name = "APPR_SEQ_NO", length = 15)
    private String apprSeqNo;

    @Column(name = "OUT_BANK_CD", length = 15)
    private String outBankCd;

    @Column(name = "WDRL_ACT_NO", length = 100)
    private String wdrlActNo;

    @JdbcTypeCode(SqlTypes.CHAR)
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

    /** HAENGSUNG ERP batch date (Oracle {@code TB_TRNS_MAST.FILE_DATE}) —
     *  half of the dedup key for the HAENGSUNG Oracle Refresh import
     *  ({@code haengsung-payment-refresh}). Null for ordinary Excel-upload
     *  batches. DB column already present on {@code OVRS_PYMN_SUMR}; mapped
     *  2026-06-28 for the Overseas Refresh leg (M-5). */
    @Column(name = "ERP_FILE_DATE", length = 8)
    private String erpFileDate;

    /** HAENGSUNG ERP batch number (Oracle {@code TB_TRNS_MAST.FILE_NUM}) —
     *  the other half of the Refresh-import dedup key. Null for Excel-upload
     *  batches. */
    @Column(name = "ERP_FILE_NUM", length = 8)
    private String erpFileNum;

    /**
     * HAENGSUNG ERP dispatch-result push flag (legacy
     * {@code OVRS_PYMN_SUMR.ERP_PROC_CMPL_YN}, CHAR(1) DEFAULT {@code 'N'}).
     * {@code 'N'} until the dispatch-result write-back (Step 5,
     * {@code HaengsungDispatchResultWriteBackService}) has pushed the final
     * result to the Oracle ERP; flipped to {@code 'Y'} only after that Oracle
     * write succeeds, so the batch is never re-pushed. Null on non-ERP /
     * Excel-upload batches (the DB default supplies {@code 'N'}); the import
     * (Step 2) stamps {@code 'N'} explicitly. Mirrors the SCDUL twin
     * {@code ScdulPymnSumr.erpProcCmplYn}. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "ERP_PROC_CMPL_YN", length = 1)
    private String erpProcCmplYn;

    @Column(name = "ORG_FILE_NM", length = 500)
    private String orgFileNm;

    @Column(name = "SAVE_FILE_NM", length = 500)
    private String saveFileNm;

    @Column(name = "FILE_SIZE", length = 100)
    private String fileSize;

    @Column(name = "REG_DTM", length = 14, nullable = false)
    private String regDtm;

    @Column(name = "REG_ID", length = 100)
    private String regId;

    @Column(name = "MOD_DTM", length = 14)
    private String modDtm;

    @Column(name = "MOD_ID", length = 100)
    private String modId;
}
