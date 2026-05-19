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

/**
 * Per-bank, per-account-class detail of a {@link TbBatchScheduleInfo}
 * parent — {@code ANT_DIV_CD} splits a parent schedule into individual /
 * corporate / etc. windows.
 *
 * <p>Mirrors {@code wecmsvndev.tb_batch_schedule_dtl}. PK is
 * (BATCH_ID, OUT_BANK_CD, ANT_DIV_CD). No BIZ_KEY — same global-namespace
 * design as {@link TbBatchScheduleInfo}.
 */
@Entity
@Table(name = "TB_BATCH_SCHEDULE_DTL")
@IdClass(TbBatchScheduleDtlId.class)
@Getter
@Setter
@NoArgsConstructor
public class TbBatchScheduleDtl {

    @Id
    @Column(name = "BATCH_ID", length = 50, nullable = false)
    private String batchId;

    @Id
    @Column(name = "OUT_BANK_CD", length = 15, nullable = false)
    private String outBankCd;

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "ANT_DIV_CD", length = 5, nullable = false)
    private String antDivCd;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "BATCH_START_TM", length = 6, nullable = false)
    private String batchStartTm;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "BATCH_END_TM", length = 6, nullable = false)
    private String batchEndTm;

    @Column(name = "REG_DTTM", length = 14)
    private String regDttm;

    @Column(name = "LAST_EDTR", length = 20)
    private String lastEdtr;
}
