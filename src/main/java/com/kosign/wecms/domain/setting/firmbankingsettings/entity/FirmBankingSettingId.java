package com.kosign.wecms.domain.setting.firmbankingsettings.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * Composite identity for {@link FirmBankingSetting}. Mirrors the
 * legacy JEX PK on {@code TB_FIRM_CONF}:
 * {@code (BIZ_KEY, OUT_BANK_CD, WDRL_ACT_NO)}.
 *
 * <p>Tenant scoping is part of the PK (the {@code BIZ_KEY} component)
 * — every repository lookup that builds an {@code Id} pulls
 * {@code bizKey} from {@link com.kosign.wecms.common.tenant.TenantContext}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class FirmBankingSettingId implements Serializable {
    private String bizKey;
    private String outBankCd;
    private String wdrlActNo;
}
