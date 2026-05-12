package com.kosign.wecms.domain.setting.environmentsettings.repository;

import com.kosign.wecms.domain.setting.environmentsettings.entity.BusiEnvSetInfm;
import com.kosign.wecms.domain.setting.environmentsettings.entity.BusiEnvSetInfmId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Set;

public interface BusiEnvSetInfmRepository extends JpaRepository<BusiEnvSetInfm, BusiEnvSetInfmId> {

    /**
     * Returns the {@code env_set_inf} values already present for one
     * tenant. Used by the bootstrap copy step to skip rows that are
     * already populated (mirrors legacy {@code wb_init_busi_data}'s
     * {@code WHERE NOT EXISTS} guard).
     */
    @org.springframework.data.jpa.repository.Query(
            "select b.envSetInf from BusiEnvSetInfm b where b.bizKey = :bizKey")
    Set<String> findEnvSetInfsByBizKey(@org.springframework.data.repository.query.Param("bizKey") String bizKey);
}
