package com.kosign.wecms.domain.payment.dispatch.entity;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite PK for {@link TbBatchScheduleInfo}: (BATCH_ID, OUT_BANK_CD).
 * Mirrors DEV's {@code pk_tb_batch_schedule_info}. No BIZ_KEY — DEV
 * stores batch schedules globally per (BATCH_ID, OUT_BANK_CD).
 */
public class TbBatchScheduleInfoId implements Serializable {

    private String batchId;
    private String outBankCd;

    public TbBatchScheduleInfoId() { }

    public TbBatchScheduleInfoId(String batchId, String outBankCd) {
        this.batchId   = batchId;
        this.outBankCd = outBankCd;
    }

    public String getBatchId()   { return batchId; }
    public String getOutBankCd() { return outBankCd; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TbBatchScheduleInfoId that)) return false;
        return Objects.equals(batchId, that.batchId)
            && Objects.equals(outBankCd, that.outBankCd);
    }

    @Override
    public int hashCode() {
        return Objects.hash(batchId, outBankCd);
    }
}
