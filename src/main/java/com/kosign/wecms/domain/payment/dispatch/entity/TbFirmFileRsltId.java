package com.kosign.wecms.domain.payment.dispatch.entity;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Composite PK for {@link TbFirmFileRslt}:
 * (TRAN_DT, BIZ_KEY, FIRM_FILE_NM, SEQ). Mirrors DEV's
 * {@code pk_tb_firm_file_rslt} unique-index column order.
 */
public class TbFirmFileRsltId implements Serializable {

    private String tranDt;
    private String bizKey;
    private String firmFileNm;
    private BigDecimal seq;

    public TbFirmFileRsltId() { }

    public TbFirmFileRsltId(String tranDt, String bizKey, String firmFileNm, BigDecimal seq) {
        this.tranDt     = tranDt;
        this.bizKey     = bizKey;
        this.firmFileNm = firmFileNm;
        this.seq        = seq;
    }

    public String getTranDt()     { return tranDt; }
    public String getBizKey()     { return bizKey; }
    public String getFirmFileNm() { return firmFileNm; }
    public BigDecimal getSeq()    { return seq; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TbFirmFileRsltId that)) return false;
        return Objects.equals(tranDt, that.tranDt)
            && Objects.equals(bizKey, that.bizKey)
            && Objects.equals(firmFileNm, that.firmFileNm)
            && Objects.equals(seq, that.seq);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tranDt, bizKey, firmFileNm, seq);
    }
}
