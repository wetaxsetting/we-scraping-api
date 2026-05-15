package com.kosign.wecms.domain.payment.dispatch.entity;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

/** Composite PK for {@link TbPayFileData}: (TRAN_DT, BIZ_KEY, FILE_SEQ_NO, SEQ). */
public class TbPayFileDataId implements Serializable {

    private String     tranDt;
    private String     bizKey;
    private String     fileSeqNo;
    private BigDecimal seq;

    public TbPayFileDataId() { }

    public TbPayFileDataId(String tranDt, String bizKey, String fileSeqNo, BigDecimal seq) {
        this.tranDt    = tranDt;
        this.bizKey    = bizKey;
        this.fileSeqNo = fileSeqNo;
        this.seq       = seq;
    }

    public String     getTranDt()    { return tranDt; }
    public String     getBizKey()    { return bizKey; }
    public String     getFileSeqNo() { return fileSeqNo; }
    public BigDecimal getSeq()       { return seq; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TbPayFileDataId that)) return false;
        return Objects.equals(tranDt, that.tranDt)
            && Objects.equals(bizKey, that.bizKey)
            && Objects.equals(fileSeqNo, that.fileSeqNo)
            && Objects.equals(seq, that.seq);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tranDt, bizKey, fileSeqNo, seq);
    }
}
