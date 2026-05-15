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
 * API-path dispatch row. One row per detail line of a VND batch
 * dispatched through {@link
 * com.kosign.wecms.domain.payment.dispatch.service.TransferDispatchExecutor}.
 *
 * <p>Schema parity with {@code wecmsvndev.tb_pay_target}; not all columns
 * are written on insert — values default to NULL on the wire when the
 * executor does not populate them. Sprint 2+ batch + bank-side updaters
 * will fill {@code PROC_STAT} / {@code PROC_DTTM} / {@code APL_EXC_RATE}
 * etc.
 */
@Entity
@Table(name = "TB_PAY_TARGET")
@IdClass(TbPayTargetId.class)
@Getter
@Setter
@NoArgsConstructor
public class TbPayTarget {

    @Id
    @Column(name = "TRAN_DT", length = 8, nullable = false)
    private String tranDt;

    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    @Id
    @Column(name = "TRAN_SEQ", length = 10, nullable = false)
    private String tranSeq;

    @Column(name = "OUT_BANK_CD", length = 15, nullable = false)
    private String outBankCd;

    @Column(name = "WDRL_ACT_NO", length = 100, nullable = false)
    private String wdrlActNo;

    @Column(name = "REG_TM", length = 6, nullable = false)
    private String regTm;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "ANT_DIV_CD", length = 5, nullable = false)
    private String antDivCd;

    @Column(name = "TAR_BANK_CD", length = 15, nullable = false)
    private String tarBankCd;

    @Column(name = "FINC_MDA_NO", length = 100, nullable = false)
    private String fincMdaNo;

    @Column(name = "FINC_MDA_OWNER_NM", length = 150)
    private String fincMdaOwnerNm;

    @Column(name = "TRAN_AMT", precision = 23, scale = 2, nullable = false)
    private BigDecimal tranAmt;

    @Column(name = "TRAN_USAG", length = 200)
    private String tranUsag;

    @Column(name = "PROC_DTTM", length = 14)
    private String procDttm;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "PROC_STAT", length = 1)
    private String procStat;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "RERUN_DIV", length = 1)
    private String rerunDiv;

    @Column(name = "RERUN_REQ_DTTM", length = 14)
    private String rerunReqDttm;

    @Column(name = "USER_ID", length = 100)
    private String userId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "TRSC_CCY_CD", length = 3)
    private String trscCcyCd;

    @Column(name = "WITHD_CCY_CD", length = 3)
    private String withdCcyCd;

    @Column(name = "DEP_CCY_CD", length = 3)
    private String depCcyCd;

    @Column(name = "APL_EXC_RATE", precision = 23, scale = 6)
    private BigDecimal aplExcRate;

    @Column(name = "WITHD_AMT", precision = 23, scale = 2)
    private BigDecimal withdAmt;

    @Column(name = "DEP_AMT", precision = 23, scale = 2)
    private BigDecimal depAmt;
}
