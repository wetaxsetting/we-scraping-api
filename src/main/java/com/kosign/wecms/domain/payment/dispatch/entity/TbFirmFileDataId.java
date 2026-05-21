package com.kosign.wecms.domain.payment.dispatch.entity;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite PK for {@link TbFirmFileData}:
 * (TRAN_DT, FILE_SEQ_NO, FIRM_FILE_NM, SEQ). Mirrors DEV's
 * {@code pk_tb_firm_file_data} unique-index column order verbatim — note
 * BIZ_KEY is intentionally NOT in the PK (DEV parity).
 */
public class TbFirmFileDataId implements Serializable {

    private String tranDt;
    private String fileSeqNo;
    private String firmFileNm;
    private java.math.BigDecimal seq;

    public TbFirmFileDataId() { }

    public TbFirmFileDataId(String tranDt, String fileSeqNo, String firmFileNm, java.math.BigDecimal seq) {
        this.tranDt     = tranDt;
        this.fileSeqNo  = fileSeqNo;
        this.firmFileNm = firmFileNm;
        this.seq        = seq;
    }

    public String getTranDt()                  { return tranDt; }
    public String getFileSeqNo()               { return fileSeqNo; }
    public String getFirmFileNm()              { return firmFileNm; }
    public java.math.BigDecimal getSeq()       { return seq; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TbFirmFileDataId that)) return false;
        return Objects.equals(tranDt, that.tranDt)
            && Objects.equals(fileSeqNo, that.fileSeqNo)
            && Objects.equals(firmFileNm, that.firmFileNm)
            && Objects.equals(seq, that.seq);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tranDt, fileSeqNo, firmFileNm, seq);
    }
}
