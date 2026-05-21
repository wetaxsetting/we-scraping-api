package com.kosign.wecms.domain.payment.dispatch.entity;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite PK for {@link ScduPymnSumrDetlPayTargetConn}:
 * (BIZ_KEY, SCDUL_PYMN_NO, SCDUL_PYMN_DETL_NO). Matches DEV's
 * {@code pk_scdu_pymn_sumr_detl_pay_target_conn}. The bridge row is
 * identified by its payment-detail key; the dispatch-side
 * ({@code TRAN_DT, TRAN_SEQ}) columns are nullable on DEV because
 * the row exists before the API dispatch fires.
 */
public class ScduPymnSumrDetlPayTargetConnId implements Serializable {

    private String bizKey;
    private String scdulPymnNo;
    private String scdulPymnDetlNo;

    public ScduPymnSumrDetlPayTargetConnId() { }

    public ScduPymnSumrDetlPayTargetConnId(String bizKey, String scdulPymnNo, String scdulPymnDetlNo) {
        this.bizKey          = bizKey;
        this.scdulPymnNo     = scdulPymnNo;
        this.scdulPymnDetlNo = scdulPymnDetlNo;
    }

    public String getBizKey()          { return bizKey; }
    public String getScdulPymnNo()     { return scdulPymnNo; }
    public String getScdulPymnDetlNo() { return scdulPymnDetlNo; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ScduPymnSumrDetlPayTargetConnId that)) return false;
        return Objects.equals(bizKey, that.bizKey)
            && Objects.equals(scdulPymnNo, that.scdulPymnNo)
            && Objects.equals(scdulPymnDetlNo, that.scdulPymnDetlNo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bizKey, scdulPymnNo, scdulPymnDetlNo);
    }
}
