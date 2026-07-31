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
 * File-path dispatch header. One row per non-VND batch dispatched
 * through the per-module dispatch executors
 * ({@code InternalTransferDispatchExecutor} / {@code VendorDispatchExecutor} /
 * {@code ExpenseDispatchExecutor} / {@code PayrollDispatchExecutor} /
 * {@code OverseasDispatchExecutor}).
 * (The formerly-shared {@code TransferDispatchExecutor} was deleted 2026-07-28 —
 * it had no production consumer; see {@code docs/reports/dead-code-architecture-review.md} §5.1.)
 *
 * <p>Composite PK {@code (TRAN_DT, BIZ_KEY, FILE_SEQ_NO)}. Detail rows
 * in {@link TbPayFileData} hang off the same key set.
 */
@Entity
@Table(name = "TB_PAY_FILE_MST")
@IdClass(TbPayFileMstId.class)
@Getter
@Setter
@NoArgsConstructor
public class TbPayFileMst {

    @Id
    @Column(name = "TRAN_DT", length = 8, nullable = false)
    private String tranDt;

    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    @Id
    @Column(name = "FILE_SEQ_NO", length = 7, nullable = false)
    private String fileSeqNo;

    @Column(name = "OUT_BANK_CD", length = 15, nullable = false)
    private String outBankCd;

    @Column(name = "WDRL_ACT_NO", length = 100, nullable = false)
    private String wdrlActNo;

    @Column(name = "BANK_PRTN_ID", length = 20, nullable = false)
    private String bankPrtnId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "FUND_TP", length = 1, nullable = false)
    private String fundTp;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "TRAN_TP", length = 1, nullable = false)
    private String tranTp;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "PROC_STAT", length = 1, nullable = false)
    private String procStat;

    @Column(name = "REQ_CNT", precision = 7, scale = 0)
    private BigDecimal reqCnt;

    @Column(name = "REQ_AMT", precision = 23, scale = 2)
    private BigDecimal reqAmt;

    @Column(name = "REG_DTTM", length = 14)
    private String regDttm;

    @Column(name = "PROC_DTTM", length = 14)
    private String procDttm;

    @Column(name = "RS_MSG", length = 200)
    private String rsMsg;
}
