package com.kosign.wecms.domain.setting.environmentsettings.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Tenant copy of the system environment settings (legacy
 * {@code BUSI_ENV_SET_INFM}). One row per {@code (BIZ_KEY,
 * ENV_SET_INF)} - the per-tenant override of the
 * {@link SysEnvSetInfm} default.
 *
 * <p>Initial population mirrors legacy
 * {@code wb_init_busi_data(p_biz_key, p_reg_id, p_reg_dtm)} - see
 * {@code TenantEnvironmentSettingsBootstrapService} which runs the
 * same {@code INSERT ... SELECT ... WHERE NOT EXISTS} logic in Java.
 *
 * <p>Schema deliberately omits {@code BaseEntity} - legacy table
 * carries only {@code MOD_ID} / {@code MOD_DTM} (no REG_*). The
 * dtm is a legacy {@code yyyyMMddHHmmss} UTC string (VARCHAR(14)),
 * not a {@code TIMESTAMPTZ} - matches the rest of the legacy schema
 * surface (audit logs, firm-banking).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "BUSI_ENV_SET_INFM")
@IdClass(BusiEnvSetInfmId.class)
public class BusiEnvSetInfm {

    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    @Id
    @Column(name = "ENV_SET_INF", length = 5, nullable = false)
    private String envSetInf;

    @Column(name = "ENV_SET_CONT", length = 1000)
    private String envSetCont;

    @Column(name = "MOD_ID", length = 100)
    private String modId;

    /** Legacy {@code yyyyMMddHHmmss} UTC string (VARCHAR(14)). */
    @Column(name = "MOD_DTM", length = 14)
    private String modDtm;
}
