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
 * File-path dispatch line. Sibling of {@link TbPayFileMst} — one row
 * per detail line within a non-VND batch.
 *
 * <p>Composite PK {@code (TRAN_DT, BIZ_KEY, FILE_SEQ_NO, SEQ)}. The first
 * three columns join back to the parent {@code TB_PAY_FILE_MST};
 * {@code SEQ} starts at 1 and increments per-line.
 */
@Entity
@Table(name = "TB_PAY_FILE_DATA")
@IdClass(TbPayFileDataId.class)
@Getter
@Setter
@NoArgsConstructor
public class TbPayFileData {

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
    @Column(name = "SEQ", precision = 4, scale = 0, nullable = false)
    private BigDecimal seq;

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
    @Column(name = "BANK_CHNL_DIV", length = 1, nullable = false)
    private String bankChnlDiv;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "ANT_DIV_CD", length = 1)
    private String antDivCd;

    @Column(name = "SENDER_NM", length = 150)
    private String senderNm;

    @Column(name = "FINC_MDA_OWNER_NM", length = 150, nullable = false)
    private String fincMdaOwnerNm;

    @Column(name = "TAR_ACCT_RMRK", length = 150)
    private String tarAcctRmrk;

    @Column(name = "WDRL_ACCT_RMRK", length = 150)
    private String wdrlActRmrk;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "PROC_RSLT_DIV", length = 1)
    private String procRsltDiv;

    @Column(name = "RS_CD", length = 10)
    private String rsCd;

    @Column(name = "RCPT_BANK_CNTRY_CD", length = 2)
    private String rcptBankCntryCd;

    @Column(name = "REMIT_CCY_CD", length = 3)
    private String remitCcyCd;

    @Column(name = "RCPT_USE_ADDR", length = 300)
    private String rcptUseAddr;

    @Column(name = "FEE_BRDN_CLS_CD", length = 3)
    private String feeBrdnClsCd;

    /**
     * Beneficiary town name (legacy {@code BNFC_TOWN_NM}) — a structured recipient-address
     * component written by the overseas payment save (legacy {@code pymn_0007_001_c001_act.jsp:377})
     * alongside {@link #rcptUseAddr} (full address). Audit M-11 / E1: this column exists on the
     * legacy table ({@code varchar(35)}, nullable) but was previously unmapped, so refactor-written
     * overseas rows silently lost it. <b>DB-only</b> — NOT part of the 1000-byte fixed-length file.
     * Do not confuse with {@link #rcptUseAddr} ({@code RCPT_USE_ADDR}, the full address).
     */
    @Column(name = "RCPT_USE_ADDR_SI", length = 35)
    private String rcptUseAddrSi;
}
