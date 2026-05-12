package com.kosign.wecms.domain.setting.environmentsettings.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * System default environment settings (legacy {@code SYS_ENV_SET_INFM}).
 * Read-only reference data. Each row is the system-wide default value
 * for one {@code ENV_SET_INF} code; tenant copies live in
 * {@code BUSI_ENV_SET_INFM} and start out as a verbatim copy of the
 * SYS rows (see legacy {@code wb_init_busi_data} - reproduced in Java
 * by {@code TenantEnvironmentSettingsBootstrapService}).
 *
 * <p>Schema is column-for-column with DEV (V69 migration). Two
 * columns total - the table is intentionally minimal because the
 * legacy DDL pre-dated the reg_id / mod_id audit conventions.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "SYS_ENV_SET_INFM")
public class SysEnvSetInfm {

    @Id
    @Column(name = "ENV_SET_INF", length = 5, nullable = false)
    private String envSetInf;

    @Column(name = "ENV_SET_CONT", length = 1000)
    private String envSetCont;
}
