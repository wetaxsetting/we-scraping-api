package com.kosign.wecms.domain.setting.environmentsettings.service;

import com.kosign.wecms.domain.setting.environmentsettings.entity.BusiEnvSetInfm;
import com.kosign.wecms.domain.setting.environmentsettings.entity.SysEnvSetInfm;
import com.kosign.wecms.domain.setting.environmentsettings.repository.BusiEnvSetInfmRepository;
import com.kosign.wecms.domain.setting.environmentsettings.repository.SysEnvSetInfmRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Java implementation of the env-set leg of legacy
 * {@code wb_init_busi_data(p_biz_key, p_reg_id, p_reg_dtm)} - see
 * {@code WECMS DEV/WECMS_DOC/database/ddl/functions.sql:5899-5977}.
 *
 * <p>Mirrors the legacy {@code INSERT ... SELECT ... WHERE NOT EXISTS}
 * verbatim:
 *
 * <pre>
 *   for each row in SYS_ENV_SET_INFM:
 *       if BUSI_ENV_SET_INFM has no row for (bizKey, env_set_inf):
 *           insert (bizKey, env_set_inf, env_set_cont, modId, modDtm)
 * </pre>
 *
 * <p>The {@code WHERE NOT EXISTS} guard makes the operation idempotent
 * and preserves any tenant row that has already been customised (a
 * second invocation skips rows that exist - matches the
 * "Preserve existing BUSI_ENV_SET_INFM values if already customized
 * by tenant" requirement).
 *
 * <p>{@code modDtm} format is the legacy {@code yyyyMMddHHmmss}
 * VARCHAR(14) UTC string - matches {@code AuditLogService.REG_DTM_FORMAT}
 * and the rest of the legacy schema's audit footprint.
 *
 * <p>Out of scope: {@code BUSI_USAG_SET_INFM} and
 * {@code TRCO_GRP_BSC_CSTM} - the legacy function copies all three
 * tables; this service intentionally covers env-set only. Sibling
 * services land in their own MR if/when the matching frontend
 * features arrive.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantEnvironmentSettingsBootstrapService {

    /** Legacy {@code yyyyMMddHHmmss} pattern for VARCHAR(14) audit columns. */
    private static final DateTimeFormatter MOD_DTM_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final SysEnvSetInfmRepository sysRepo;
    private final BusiEnvSetInfmRepository busiRepo;

    /**
     * Copies every SYS_ENV_SET_INFM row into BUSI_ENV_SET_INFM for
     * the tenant, skipping rows that already exist. Safe to call
     * repeatedly.
     *
     * @param bizKey  tenant identifier (must be a valid BUSI_INFM.biz_key
     *                - the FK is enforced by the schema; calling for an
     *                unknown bizKey throws via the persistence layer)
     * @param modId   audit user id - {@code SYSTEM_BOOTSTRAP} during the
     *                tenant-bootstrap path, the acting user id during
     *                any future explicit re-init flow
     * @param nowUtc  reference timestamp for {@code mod_dtm}; format-
     *                converted to legacy yyyyMMddHHmmss in UTC.
     *                Caller passes the same instant it uses for the
     *                rest of the bootstrap chain so all rows share one
     *                consistent "now".
     * @return number of new BUSI rows inserted (0 if every code was
     *         already populated for this tenant)
     */
    @Transactional
    public int seedDefaultsForTenant(String bizKey, String modId, OffsetDateTime nowUtc) {
        if (bizKey == null || bizKey.isBlank()) {
            throw new IllegalArgumentException("bizKey must not be blank");
        }
        if (modId == null || modId.isBlank()) {
            throw new IllegalArgumentException("modId must not be blank");
        }
        if (nowUtc == null) {
            throw new IllegalArgumentException("nowUtc must not be null");
        }

        String modDtm = nowUtc.withOffsetSameInstant(ZoneOffset.UTC).format(MOD_DTM_FORMAT);

        List<SysEnvSetInfm> sysRows = sysRepo.findAll();
        if (sysRows.isEmpty()) {
            log.warn("SYS_ENV_SET_INFM is empty - skipping env-set bootstrap for bizKey={}", bizKey);
            return 0;
        }

        Set<String> existing = busiRepo.findEnvSetInfsByBizKey(bizKey);

        List<BusiEnvSetInfm> toInsert = new ArrayList<>();
        for (SysEnvSetInfm sys : sysRows) {
            if (existing.contains(sys.getEnvSetInf())) {
                continue;
            }
            BusiEnvSetInfm row = new BusiEnvSetInfm();
            row.setBizKey(bizKey);
            row.setEnvSetInf(sys.getEnvSetInf());
            row.setEnvSetCont(sys.getEnvSetCont());
            row.setModId(modId);
            row.setModDtm(modDtm);
            toInsert.add(row);
        }

        if (toInsert.isEmpty()) {
            log.debug("Env-set bootstrap: bizKey={} already has all {} SYS codes",
                    bizKey, sysRows.size());
            return 0;
        }

        busiRepo.saveAll(toInsert);
        log.info("Env-set bootstrap: bizKey={} inserted {} of {} SYS codes",
                bizKey, toInsert.size(), sysRows.size());
        return toInsert.size();
    }
}
