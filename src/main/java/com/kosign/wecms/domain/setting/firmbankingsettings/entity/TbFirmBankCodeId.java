package com.kosign.wecms.domain.setting.firmbankingsettings.entity;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite PK for {@link TbFirmBankCode}: {@code (ANT_DIV_CD, BANK_CD)}.
 * Mirrors DEV's {@code pk_tb_firm_bank_code}. A {@code BANK_CD} value can
 * recur across ancillary divisions (NAPAS vs CITAD), so the division code
 * is part of the key — single-column {@code BANK_CD} identity would alias
 * rows in the persistence context.
 */
public class TbFirmBankCodeId implements Serializable {

    private String antDivCd;
    private String bankCd;

    public TbFirmBankCodeId() { }

    public TbFirmBankCodeId(String antDivCd, String bankCd) {
        this.antDivCd = antDivCd;
        this.bankCd   = bankCd;
    }

    public String getAntDivCd() { return antDivCd; }
    public String getBankCd()   { return bankCd; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TbFirmBankCodeId that)) return false;
        return Objects.equals(antDivCd, that.antDivCd)
            && Objects.equals(bankCd, that.bankCd);
    }

    @Override
    public int hashCode() {
        return Objects.hash(antDivCd, bankCd);
    }
}
