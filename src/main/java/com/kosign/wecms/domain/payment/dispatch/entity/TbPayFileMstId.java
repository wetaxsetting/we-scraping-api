package com.kosign.wecms.domain.payment.dispatch.entity;

import java.io.Serializable;
import java.util.Objects;

/** Composite PK for {@link TbPayFileMst}: (TRAN_DT, BIZ_KEY, FILE_SEQ_NO). */
public class TbPayFileMstId implements Serializable {

    private String tranDt;
    private String bizKey;
    private String fileSeqNo;

    public TbPayFileMstId() { }

    public TbPayFileMstId(String tranDt, String bizKey, String fileSeqNo) {
        this.tranDt    = tranDt;
        this.bizKey    = bizKey;
        this.fileSeqNo = fileSeqNo;
    }

    public String getTranDt()    { return tranDt; }
    public String getBizKey()    { return bizKey; }
    public String getFileSeqNo() { return fileSeqNo; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TbPayFileMstId that)) return false;
        return Objects.equals(tranDt, that.tranDt)
            && Objects.equals(bizKey, that.bizKey)
            && Objects.equals(fileSeqNo, that.fileSeqNo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tranDt, bizKey, fileSeqNo);
    }
}
