package com.kosign.wecms.domain.payment.dispatch.entity;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite PK for {@link TbPayTarget}: (TRAN_DT, BIZ_KEY, TRAN_SEQ).
 * Mirrors the legacy {@code TB_PAY_TARGET} primary key. Order matches
 * the DB declaration so PostgreSQL's PK btree is reused by lookups that
 * leading-anchor on the same columns.
 */
public class TbPayTargetId implements Serializable {

    private String tranDt;
    private String bizKey;
    private String tranSeq;

    public TbPayTargetId() { }

    public TbPayTargetId(String tranDt, String bizKey, String tranSeq) {
        this.tranDt  = tranDt;
        this.bizKey  = bizKey;
        this.tranSeq = tranSeq;
    }

    public String getTranDt()  { return tranDt; }
    public String getBizKey()  { return bizKey; }
    public String getTranSeq() { return tranSeq; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TbPayTargetId that)) return false;
        return Objects.equals(tranDt, that.tranDt)
            && Objects.equals(bizKey, that.bizKey)
            && Objects.equals(tranSeq, that.tranSeq);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tranDt, bizKey, tranSeq);
    }
}
