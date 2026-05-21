package com.kosign.wecms.domain.payment.dispatch.entity;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite PK for {@link TbFirmBatApiTran}:
 * (TRAN_DT, BIZ_KEY, TRAN_SEQ, API_TRAN_TP, OUT_BANK_CD, BANK_PRTN_ID).
 * Six-column composite — mirrors DEV's {@code pk_tb_firm_bat_api_tran}
 * unique-index column order verbatim.
 */
public class TbFirmBatApiTranId implements Serializable {

    private String tranDt;
    private String bizKey;
    private String tranSeq;
    private String apiTranTp;
    private String outBankCd;
    private String bankPrtnId;

    public TbFirmBatApiTranId() { }

    public TbFirmBatApiTranId(String tranDt, String bizKey, String tranSeq,
                              String apiTranTp, String outBankCd, String bankPrtnId) {
        this.tranDt     = tranDt;
        this.bizKey     = bizKey;
        this.tranSeq    = tranSeq;
        this.apiTranTp  = apiTranTp;
        this.outBankCd  = outBankCd;
        this.bankPrtnId = bankPrtnId;
    }

    public String getTranDt()     { return tranDt; }
    public String getBizKey()     { return bizKey; }
    public String getTranSeq()    { return tranSeq; }
    public String getApiTranTp()  { return apiTranTp; }
    public String getOutBankCd()  { return outBankCd; }
    public String getBankPrtnId() { return bankPrtnId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TbFirmBatApiTranId that)) return false;
        return Objects.equals(tranDt, that.tranDt)
            && Objects.equals(bizKey, that.bizKey)
            && Objects.equals(tranSeq, that.tranSeq)
            && Objects.equals(apiTranTp, that.apiTranTp)
            && Objects.equals(outBankCd, that.outBankCd)
            && Objects.equals(bankPrtnId, that.bankPrtnId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tranDt, bizKey, tranSeq, apiTranTp, outBankCd, bankPrtnId);
    }
}
