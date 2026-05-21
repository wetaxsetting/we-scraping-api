package com.kosign.wecms.domain.payment.dispatch.entity;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite PK for {@link TbBatchScheduleDtl}:
 * (BATCH_ID, OUT_BANK_CD, ANT_DIV_CD). Mirrors DEV's
 * {@code pk_tb_batch_schedule_dtl}. No BIZ_KEY — same global-namespace
 * design as {@link TbBatchScheduleInfo}.
 */
public class TbBatchScheduleDtlId implements Serializable {

    private String batchId;
    private String outBankCd;
    private String antDivCd;

    public TbBatchScheduleDtlId() { }

    public TbBatchScheduleDtlId(String batchId, String outBankCd, String antDivCd) {
        this.batchId   = batchId;
        this.outBankCd = outBankCd;
        this.antDivCd  = antDivCd;
    }

    public String getBatchId()   { return batchId; }
    public String getOutBankCd() { return outBankCd; }
    public String getAntDivCd()  { return antDivCd; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TbBatchScheduleDtlId that)) return false;
        return Objects.equals(batchId, that.batchId)
            && Objects.equals(outBankCd, that.outBankCd)
            && Objects.equals(antDivCd, that.antDivCd);
    }

    @Override
    public int hashCode() {
        return Objects.hash(batchId, outBankCd, antDivCd);
    }
}
