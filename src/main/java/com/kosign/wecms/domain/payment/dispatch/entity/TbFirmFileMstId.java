package com.kosign.wecms.domain.payment.dispatch.entity;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite PK for {@link TbFirmFileMst}:
 * (TRAN_DT, BIZ_KEY, FILE_SEQ_NO, FIRM_FILE_NM). Mirrors the legacy
 * {@code pk_tb_firm_file_mst} unique-index column order.
 */
public class TbFirmFileMstId implements Serializable {

    private String tranDt;
    private String bizKey;
    private String fileSeqNo;
    private String firmFileNm;

    public TbFirmFileMstId() { }

    public TbFirmFileMstId(String tranDt, String bizKey, String fileSeqNo, String firmFileNm) {
        this.tranDt     = tranDt;
        this.bizKey     = bizKey;
        this.fileSeqNo  = fileSeqNo;
        this.firmFileNm = firmFileNm;
    }

    public String getTranDt()     { return tranDt; }
    public String getBizKey()     { return bizKey; }
    public String getFileSeqNo()  { return fileSeqNo; }
    public String getFirmFileNm() { return firmFileNm; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TbFirmFileMstId that)) return false;
        return Objects.equals(tranDt, that.tranDt)
            && Objects.equals(bizKey, that.bizKey)
            && Objects.equals(fileSeqNo, that.fileSeqNo)
            && Objects.equals(firmFileNm, that.firmFileNm);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tranDt, bizKey, fileSeqNo, firmFileNm);
    }
}
