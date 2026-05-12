package com.kosign.wecms.domain.setting.firmbankingsettings.repository;

import com.kosign.wecms.domain.setting.firmbankingsettings.entity.FirmBankingSetting;
import com.kosign.wecms.domain.setting.firmbankingsettings.entity.FirmBankingSettingId;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Owning-table repository for {@code TB_FIRM_CONF}. Used by the
 * firm-banking-settings Detail endpoint (PK lookup). The List endpoint
 * goes through the read-only join view —
 * {@link com.kosign.wecms.domain.setting.firmbankingsettings.repository.FirmBankingSettingViewRepository}.
 *
 * <p>Phase 2 read-only slice — Path A. Create / Update / Delete are
 * out of scope until OQ-8a + the shared audit-log writer service
 * (Phase 1 P-1) are resolved.
 *
 * <p>Tenant scoping: callers must construct {@link FirmBankingSettingId}
 * with {@code bizKey} from
 * {@link com.kosign.wecms.common.tenant.TenantContext#currentBizKey()}.
 * The PK shape guarantees a Detail lookup with someone else's
 * {@code bizKey} returns empty rather than leaking a row.
 */
public interface FirmBankingSettingRepository
        extends JpaRepository<FirmBankingSetting, FirmBankingSettingId> {
}
