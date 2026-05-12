package com.kosign.wecms.domain.setting.environmentsettings.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.setting.environmentsettings.dto.EnvironmentSettingResponse;
import com.kosign.wecms.domain.setting.environmentsettings.entity.BusiEnvSetInfm;
import com.kosign.wecms.domain.setting.environmentsettings.entity.BusiEnvSetInfmId;
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

/**
 * Generic environment-settings read + update service. Mirrors the
 * legacy {@code comm_0600_001_r001} (read) and {@code comm_0215_001_u001}
 * (update) WSVCs verbatim, both of which any module may call with any
 * env_set_inf code.
 *
 * <p><b>Read</b> ({@link #get(String)}): mirrors legacy IDO
 * {@code BUSI_ENV_SET_INFM_R002} - a {@code COALESCE(BUSI override,
 * SYS default)}. The driving table is SYS; missing BUSI rows fall
 * back silently. Implemented as two PK lookups instead of a native
 * LEFT JOIN because both lookups are PK hits and the legacy IDO's
 * single-row contract makes a JOIN no faster in practice.
 *
 * <p><b>Update</b> ({@link #update(String, String)}): mirrors legacy
 * {@code comm_0215_001_u001_act.jsp}. Pure UPDATE - the legacy
 * action's {@code validateBusiEnvSetInfm} call assumes the BUSI row
 * already exists (seeded by {@code wb_init_busi_data} at tenant
 * bootstrap; {@link com.kosign.wecms.domain.setting.environmentsettings.service.TenantEnvironmentSettingsBootstrapService}
 * is the refactor's port of that copy). When the row is missing
 * (e.g. a SYS code added after the tenant was bootstrapped), the
 * service throws {@code WBE2031} - mirrors the legacy
 * row-not-found error path. Re-running tenant bootstrap fills the
 * gap.
 *
 * <p><b>Y/N validation</b>: legacy whitelists 31 codes for Y/N format
 * enforcement (see {@code validateInputEnvYesNo()}); codes outside
 * the whitelist (including the firm-banking automation codes
 * {@code 00078} / {@code 00079}) get no Y/N enforcement. The refactor
 * mirrors this - the wire field is free-text up to 1000 chars; the
 * frontend toggle UI constrains values to {@code "Y"}/{@code "N"} for
 * the codes it represents. Backends that need stricter validation
 * extend this service with a per-code policy; the default is the
 * legacy permissive behaviour.
 *
 * <p><b>No audit row</b>: legacy {@code comm_0215_001_u001} does not
 * write to {@code AUDIT_LOGS_INFM}. The refactor matches.
 *
 * <p><b>Tenant scope</b>: {@code BIZ_KEY} comes from {@link TenantContext}
 * (populated from the JWT in {@code JwtAuthenticationFilter}). The
 * service never accepts {@code bizKey} from request input.
 *
 * <p>{@code mod_dtm} format is the legacy {@code yyyyMMddHHmmss}
 * VARCHAR(14) UTC string - matches the rest of the legacy schema's
 * audit footprint and {@code TenantEnvironmentSettingsBootstrapService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnvironmentSettingsService {

    private static final DateTimeFormatter MOD_DTM_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final SysEnvSetInfmRepository sysRepo;
    private final BusiEnvSetInfmRepository busiRepo;

    /**
     * Reads one environment-setting value for the current tenant with
     * SYS-default fallback. Mirrors legacy {@code comm_0600_001_r001}.
     *
     * @param envSetInf 5-char code (e.g. {@code "00078"}); validated
     *                  by the controller's {@code @Pattern}
     * @return {@link EnvironmentSettingResponse} carrying the COALESCE
     *         result. {@code envSetCont} may be {@code null} if the
     *         SYS default is null.
     * @throws BusinessException {@code WBE2030} when {@code envSetInf}
     *         is not in {@code SYS_ENV_SET_INFM} (truly unknown code -
     *         legacy {@code validateSysEnvSetInfm} parity)
     */
    @Transactional(readOnly = true)
    public EnvironmentSettingResponse get(String envSetInf) {
        SysEnvSetInfm sys = sysRepo.findById(envSetInf)
                .orElseThrow(() -> new BusinessException("WBE2030", envSetInf));
        String bizKey = TenantContext.currentBizKey();
        String value = busiRepo.findById(new BusiEnvSetInfmId(bizKey, envSetInf))
                .map(BusiEnvSetInfm::getEnvSetCont)
                .orElse(sys.getEnvSetCont());
        return new EnvironmentSettingResponse(envSetInf, value);
    }

    /**
     * Updates one environment-setting value for the current tenant.
     * Mirrors legacy {@code comm_0215_001_u001}.
     *
     * <p>The BUSI row must already exist (legacy assumption - the
     * bootstrap path seeds every SYS code at tenant creation). Missing
     * rows surface as {@code WBE2031}; re-running the bootstrap fills
     * the gap.
     *
     * @return the saved row (legacy returns {@code ENV_JSON} - the
     *         full session config; the refactor returns just the
     *         updated row since there is no session-scoped config
     *         cache to refresh).
     * @throws BusinessException {@code WBE2030} when the code is
     *         unknown to SYS; {@code WBE2031} when the tenant has no
     *         BUSI row for this code yet
     */
    @Transactional
    public EnvironmentSettingResponse update(String envSetInf, String envSetCont) {
        if (!sysRepo.existsById(envSetInf)) {
            throw new BusinessException("WBE2030", envSetInf);
        }
        String bizKey = TenantContext.currentBizKey();
        BusiEnvSetInfmId pk = new BusiEnvSetInfmId(bizKey, envSetInf);
        BusiEnvSetInfm row = busiRepo.findById(pk)
                .orElseThrow(() -> new BusinessException("WBE2031", envSetInf));

        row.setEnvSetCont(envSetCont);
        row.setModId(TenantContext.currentUserId());
        row.setModDtm(OffsetDateTime.now(ZoneOffset.UTC).format(MOD_DTM_FORMAT));
        busiRepo.save(row);

        log.info("Env-set update: bizKey={} env_set_inf={} -> {}",
                bizKey, envSetInf, envSetCont);
        return new EnvironmentSettingResponse(envSetInf, envSetCont);
    }
}
