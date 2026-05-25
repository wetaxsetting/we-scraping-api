package com.kosign.wecms.domain.setting.firmbankingsettings.repository;

import com.kosign.wecms.domain.setting.firmbankingsettings.entity.FirmBankingSetting;
import com.kosign.wecms.domain.setting.firmbankingsettings.entity.FirmBankingSettingId;
import java.util.Optional;
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

    /**
     * Resolve the active firm-banking config row for the tenant.
     * Mirrors the legacy {@code TB_FIRM_CONF_R003} query in
     * {@code comm_1003_001_act.jsp}:
     * <pre>
     * SELECT VAN_ORG_CD, OUT_BANK_CD, BANK_PRTN_ID, WDRL_ACT_NO
     *   FROM TB_FIRM_CONF
     *  WHERE BIZ_KEY = ? AND ORG_STAT = '1'
     *  ORDER BY REG_DTTM ASC
     *  LIMIT 1
     * </pre>
     *
     * <p>Used by {@link com.kosign.wecms.common.firmbanking.FirmBankingInquiryService}
     * to gate the {@code FIRM_PROC.inquiryAccount} call: when the
     * tenant has no active firm-banking config the inquiry is skipped
     * silently (legacy {@code if (contactInfo != null)} branch). The
     * returned row carries the four fields the legacy inquiry packs
     * into the WB_00000100 message: {@code VAN_ORG_CD},
     * {@code OUT_BANK_CD}, {@code BANK_PRTN_ID}, {@code WDRL_ACT_NO}.
     */
    Optional<FirmBankingSetting> findFirstByBizKeyAndOrgStatOrderByRegDttmAsc(
            String bizKey, String orgStat);

    /**
     * Fast {@code exists}-only variant of
     * {@link #findFirstByBizKeyAndOrgStatOrderByRegDttmAsc} &mdash; used
     * by the Excel-upload bulk-resolve path as a pre-check before
     * iterating rows, so a tenant with no active firm-banking config
     * skips every per-row inquiry instead of querying TB_FIRM_CONF once
     * per row.
     */
    boolean existsByBizKeyAndOrgStat(String bizKey, String orgStat);
}
