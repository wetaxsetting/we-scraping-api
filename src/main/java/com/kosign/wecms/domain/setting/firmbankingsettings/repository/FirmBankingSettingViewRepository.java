package com.kosign.wecms.domain.setting.firmbankingsettings.repository;

import com.kosign.wecms.domain.setting.firmbankingsettings.entity.TbFirmConfId;
import com.kosign.wecms.domain.setting.firmbankingsettings.entity.FirmBankingSettingView;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Read-only repository over {@code V_FIRM_BANKING_SETTING} (V64).
 * Backs the firm-banking-settings list endpoint. Mirrors legacy
 * {@code IDO.TB_FIRM_CONF_R004}.
 *
 * <p>Legacy parity: the list returns every row for the current
 * tenant with no client-side filters (matches
 * {@code comm_0220_001_r001_act.jsp} which only constrains on
 * {@code BIZ_KEY}). Sort / pagination are delegated to the service —
 * the JEX list returns rows in insertion order, so no implicit
 * {@code ORDER BY} is added here.
 *
 * <p>Tenant scoping is the caller's responsibility — every read must
 * pass the {@code BIZ_KEY} from
 * {@link com.kosign.wecms.common.tenant.TenantContext#currentBizKey()}.
 */
public interface FirmBankingSettingViewRepository
        extends JpaRepository<FirmBankingSettingView, TbFirmConfId> {

    /**
     * All bindings for the given tenant, ordered for stable display.
     * The {@code OUT_BANK_CD ASC, WDRL_ACT_NO ASC} ordering matches
     * the natural iteration shape of the JEX list (the legacy IDO has
     * no explicit ORDER BY but PostgreSQL returned rows in PK order in
     * practice; making it explicit guarantees the same behaviour
     * across environments and avoids surprise re-orderings under
     * pagination).
     */
    List<FirmBankingSettingView> findAllByBizKeyOrderByOutBankCdAscWdrlActNoAsc(String bizKey);
}
