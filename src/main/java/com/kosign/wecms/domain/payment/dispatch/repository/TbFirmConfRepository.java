package com.kosign.wecms.domain.payment.dispatch.repository;

import com.kosign.wecms.domain.setting.firmbankingsettings.entity.TbFirmConf;
import com.kosign.wecms.domain.setting.firmbankingsettings.entity.TbFirmConfId;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Read-only access to {@code TB_FIRM_CONF} for the firm-banking bulk dispatch
 * (00301 reception flow). Reuses the canonical {@link TbFirmConf} entity owned by
 * {@code domain.setting.firmbankingsettings} — there is exactly one JPA mapping of
 * the {@code TB_FIRM_CONF} table (a second mapping caused a Hibernate
 * {@code DuplicateMappingException} at boot). Mirrors legacy
 * {@code IDO.TB_FIRM_CONF_R001} — looked up by the PK
 * {@code (BIZ_KEY, OUT_BANK_CD, WDRL_ACT_NO)} via {@code findById}.
 */
public interface TbFirmConfRepository extends JpaRepository<TbFirmConf, TbFirmConfId> {
}
