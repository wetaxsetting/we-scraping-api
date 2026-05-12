package com.kosign.wecms.domain.setting.environmentsettings.repository;

import com.kosign.wecms.domain.setting.environmentsettings.entity.SysEnvSetInfm;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Read-only access to {@code SYS_ENV_SET_INFM}. The bootstrap service
 * iterates {@code findAll()} when copying defaults to a new tenant -
 * the table holds at most ~80 rows so paging adds no value.
 */
public interface SysEnvSetInfmRepository extends JpaRepository<SysEnvSetInfm, String> {
}
