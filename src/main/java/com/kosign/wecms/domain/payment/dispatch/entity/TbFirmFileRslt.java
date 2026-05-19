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
 * Gateway response row mirroring the corresponding {@link TbFirmFileData}
 * row after a dispatch batch is processed.
 *
 * <p>Mirrors {@code wecmsvndev.tb_firm_file_rslt}. PK is
 * (TRAN_DT, BIZ_KEY, FIRM_FILE_NM, SEQ) per DEV. Both {@code TRSC_CCY_CD}
 * and {@code PROC_RSLT_DIV} are NOT NULL with DEV defaults
 * ({@code 'VND'} / {@code 'N'}). {@code RS_CD} is NOT NULL — result rows
 * always carry a response code.
 */
@Entity
@Table(name = "TB_FIRM_FILE_RSLT")
@IdClass(TbFirmFileRsltId.class)
@Getter
@Setter
@NoArgsConstructor
public class TbFirmFileRslt {

    @Id
    @Column(name = "TRAN_DT", length = 8, nullable = false)
    private String tranDt;

    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    @Id
    @Column(name = "FIRM_FILE_NM", length = 50, nullable = false)
    private String firmFileNm;

    @Id
    @Column(name = "SEQ", precision = 7, scale = 0, nullable = false)
    private BigDecimal seq;

    @Column(name = "FILE_SEQ", length = 3, nullable = false)
    private String fileSeq;

    @Column(name = "TAR_BANK_CD", length = 15, nullable = false)
    private String tarBankCd;

    @Column(name = "FINC_MDA_DIV", length = 1, nullable = false)
    private String fincMdaDiv;

    @Column(name = "FINC_MDA_NO", length = 100, nullable = false)
    private String fincMdaNo;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "TRSC_CCY_CD", length = 3, nullable = false)
    private String trscCcyCd;

    @Column(name = "TRAN_AMT", precision = 23, scale = 2, nullable = false)
    private BigDecimal tranAmt;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "BANK_CHNL_DIV", length = 1)
    private String bankChnlDiv;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "ANT_DIV_CD", length = 1)
    private String antDivCd;

    @Column(name = "SENDER_NM", length = 150)
    private String senderNm;

    @Column(name = "FINC_MDA_OWNER_NM", length = 150)
    private String fincMdaOwnerNm;

    @Column(name = "TAR_ACCT_RMRK", length = 150)
    private String tarAcctRmrk;

    @Column(name = "WDRL_ACCT_RMRK", length = 150)
    private String wdrlAcctRmrk;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "PROC_RSLT_DIV", length = 1, nullable = false)
    private String procRsltDiv;

    @Column(name = "RS_CD", length = 10, nullable = false)
    private String rsCd;

    @Column(name = "RCPT_BANK_CNTRY_CD", length = 2)
    private String rcptBankCntryCd;

    @Column(name = "REMIT_CCY_CD", length = 3)
    private String remitCcyCd;

    @Column(name = "RCPT_USE_ADDR", length = 300)
    private String rcptUseAddr;

    @Column(name = "FEE_BRDN_CLS_CD", length = 3)
    private String feeBrdnClsCd;
}
