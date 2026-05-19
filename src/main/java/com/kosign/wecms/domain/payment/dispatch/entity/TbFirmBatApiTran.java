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
 * Batch-level API transaction audit — one row per batch dispatch attempt
 * (registration request, aggregate counts/amounts, response code +
 * message).
 *
 * <p>Mirrors {@code wecmsvndev.tb_firm_bat_api_tran}. PK is six-column
 * composite per DEV: (TRAN_DT, BIZ_KEY, TRAN_SEQ, API_TRAN_TP,
 * OUT_BANK_CD, BANK_PRTN_ID).
 */
@Entity
@Table(name = "TB_FIRM_BAT_API_TRAN")
@IdClass(TbFirmBatApiTranId.class)
@Getter
@Setter
@NoArgsConstructor
public class TbFirmBatApiTran {

    @Id
    @Column(name = "TRAN_DT", length = 8, nullable = false)
    private String tranDt;

    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    @Id
    @Column(name = "TRAN_SEQ", length = 10, nullable = false)
    private String tranSeq;

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "API_TRAN_TP", length = 5, nullable = false)
    private String apiTranTp;

    @Id
    @Column(name = "OUT_BANK_CD", length = 15, nullable = false)
    private String outBankCd;

    @Id
    @Column(name = "BANK_PRTN_ID", length = 20, nullable = false)
    private String bankPrtnId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "TRAN_TM", length = 6, nullable = false)
    private String tranTm;

    @Column(name = "FIRM_FILE_NM", length = 50, nullable = false)
    private String firmFileNm;

    @Column(name = "BAT_CNT", precision = 7, scale = 0, nullable = false)
    private BigDecimal batCnt;

    @Column(name = "BAT_AMT", precision = 23, scale = 2, nullable = false)
    private BigDecimal batAmt;

    @Column(name = "REG_RSLT_CD", length = 4)
    private String regRsltCd;

    @Column(name = "REG_RSLT_MSG", length = 150)
    private String regRsltMsg;

    @Column(name = "RS_CD", length = 4)
    private String rsCd;

    @Column(name = "RS_MSG", length = 200)
    private String rsMsg;

    @Column(name = "RS_DTTM", length = 14)
    private String rsDttm;
}
