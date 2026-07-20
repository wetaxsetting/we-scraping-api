package com.kosign.wecms.domain.banking.entity;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Composite key for {@link BankInfmDetl} — mirrors the legacy
 * {@code bank_infm_detl} primary key
 * {@code (BANK_CD, BANK_DETL_INFO_CD, START_DATE, END_DATE, SEQ_NO)}.
 */
public class BankInfmDetlId implements Serializable {

    private String bankCd;
    private String bankDetlInfoCd;
    private String startDate;
    private String endDate;
    private BigDecimal seqNo;

    public BankInfmDetlId() { }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BankInfmDetlId that)) return false;
        return Objects.equals(bankCd, that.bankCd)
                && Objects.equals(bankDetlInfoCd, that.bankDetlInfoCd)
                && Objects.equals(startDate, that.startDate)
                && Objects.equals(endDate, that.endDate)
                && Objects.equals(seqNo, that.seqNo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bankCd, bankDetlInfoCd, startDate, endDate, seqNo);
    }
}
