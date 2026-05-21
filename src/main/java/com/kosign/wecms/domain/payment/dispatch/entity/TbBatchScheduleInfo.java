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
 * Per-bank batch schedule header — drives the firm-banking dispatch
 * scheduler's run windows ({@code BATCH_START_TM} .. {@code BATCH_END_TM})
 * per outbound bank code.
 *
 * <p>Mirrors {@code wecmsvndev.tb_batch_schedule_info}. No BIZ_KEY
 * column — DEV stores schedules globally per (BATCH_ID, OUT_BANK_CD).
 */
@Entity
@Table(name = "TB_BATCH_SCHEDULE_INFO")
@IdClass(TbBatchScheduleInfoId.class)
@Getter
@Setter
@NoArgsConstructor
public class TbBatchScheduleInfo {

    @Id
    @Column(name = "BATCH_ID", length = 50, nullable = false)
    private String batchId;

    @Id
    @Column(name = "OUT_BANK_CD", length = 15, nullable = false)
    private String outBankCd;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "ANT_WHERE_USE_YN", length = 1)
    private String antWhereUseYn;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "BATCH_START_TM", length = 6, nullable = false)
    private String batchStartTm;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "BATCH_END_TM", length = 6, nullable = false)
    private String batchEndTm;

    @Column(name = "REG_DTTM", length = 14)
    private String regDttm;

    @Column(name = "MOD_DTTM", length = 14)
    private String modDttm;

    @Column(name = "LAST_EDTR", length = 20)
    private String lastEdtr;
}
