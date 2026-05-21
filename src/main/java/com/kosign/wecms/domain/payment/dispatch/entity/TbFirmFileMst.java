package com.kosign.wecms.domain.payment.dispatch.entity;

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
 * Firm-banking dispatch file header. One row per dispatched file batch
 * tracks aggregate request / register / success / unprocessed counts and
 * amounts plus the per-step processing status.
 *
 * <p>Mirrors {@code wecmsvndev.tb_firm_file_mst}. DEV's
 * {@code pk_tb_firm_file_mst} unique index is preserved here as a formal
 * composite PRIMARY KEY constraint on
 * (TRAN_DT, BIZ_KEY, FILE_SEQ_NO, FIRM_FILE_NM) (same precedent as
 * {@link TbPayTarget}, {@link TbPayFileMst}).
 *
 * <p>{@code CHAR(N)} columns from DEV are mapped with
 * {@link JdbcTypeCode} = {@link SqlTypes#CHAR} so Hibernate's
 * {@code validate} accepts the {@code bpchar} columns without a
 * {@code @Type} hack (same precedent as
 * {@code SysScrOptionWecms.sortYn}).
 */
@Entity
@Table(name = "TB_FIRM_FILE_MST")
@IdClass(TbFirmFileMstId.class)
@Getter
@Setter
@NoArgsConstructor
public class TbFirmFileMst {

    @Id
    @Column(name = "TRAN_DT", length = 8, nullable = false)
    private String tranDt;

    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    @Id
    @Column(name = "FILE_SEQ_NO", length = 7, nullable = false)
    private String fileSeqNo;

    @Id
    @Column(name = "FIRM_FILE_NM", length = 50, nullable = false)
    private String firmFileNm;

    @Column(name = "OUT_BANK_CD", length = 15)
    private String outBankCd;

    @Column(name = "BANK_PRTN_ID", length = 20)
    private String bankPrtnId;

    @Column(name = "VAN_ORG_CD", length = 20)
    private String vanOrgCd;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "FILE_TYPE", length = 3)
    private String fileType;

    @Column(name = "WDRL_ACT_NO", length = 100)
    private String wdrlActNo;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "FUND_TP", length = 1)
    private String fundTp;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "TRAN_TP", length = 1)
    private String tranTp;

    @Column(name = "FILE_SEQ", length = 3)
    private String fileSeq;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "PROC_STEP", length = 1)
    private String procStep;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "PROC_STAT", length = 1, nullable = false)
    private String procStat;

    @Column(name = "REQ_CNT", precision = 7, scale = 0)
    private BigDecimal reqCnt;

    @Column(name = "REQ_AMT", precision = 23, scale = 2)
    private BigDecimal reqAmt;

    @Column(name = "REG_CNT", precision = 7, scale = 0)
    private BigDecimal regCnt;

    @Column(name = "REG_AMT", precision = 23, scale = 2)
    private BigDecimal regAmt;

    @Column(name = "REG_RSLT_CD", length = 4)
    private String regRsltCd;

    @Column(name = "REG_RSLT_MSG", length = 150)
    private String regRsltMsg;

    @Column(name = "SUCC_CNT", precision = 7, scale = 0)
    private BigDecimal succCnt;

    @Column(name = "SUCC_AMT", precision = 23, scale = 2)
    private BigDecimal succAmt;

    @Column(name = "UNPROC_CNT", precision = 7, scale = 0)
    private BigDecimal unprocCnt;

    @Column(name = "UNPROC_AMT", precision = 23, scale = 2)
    private BigDecimal unprocAmt;

    @Column(name = "REG_DTTM", length = 14)
    private String regDttm;

    @Column(name = "PROC_DTTM", length = 14)
    private String procDttm;
}
