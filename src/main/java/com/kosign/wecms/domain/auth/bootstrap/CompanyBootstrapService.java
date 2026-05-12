package com.kosign.wecms.domain.auth.bootstrap;

import com.kosign.wecms.config.TenantProperties;
import com.kosign.wecms.domain.auth.entity.BusiInfm;
import com.kosign.wecms.domain.auth.entity.EmplInfm;
import com.kosign.wecms.domain.auth.repository.BusiInfmRepository;
import com.kosign.wecms.domain.auth.repository.EmplInfmRepository;
import com.kosign.wecms.domain.setting.environmentsettings.service.TenantEnvironmentSettingsBootstrapService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Single transactional orchestrator for the tenant bootstrap chain:
 *
 * <pre>
 *   BUSI_INFM
 *     -> BUSI_ENV_SET_INFM (seeded from SYS_ENV_SET_INFM, env-set leg
 *                           of legacy wb_init_busi_data)
 *     -> EMPL_INFM
 *       -> USER_INFM
 *         -> USER_BUSI_CONN_INFM
 *           -> BUSI_ADMN_INFM
 * </pre>
 *
 * <p><b>This service is NOT auto-invoked.</b> The previous auto-run trigger
 * ({@code BootstrapRunner}) was removed; production / UAT / local startup
 * never calls this orchestrator. Company + first-admin creation in
 * operational practice is <b>DBA-driven</b> via the legacy manual SQL setup
 * pattern (see {@code Claude Code (AI)/sql/setup_uat_account_*.sql}) run
 * directly against the target DB.
 *
 * <p>This class is kept as a callable skeleton for two purposes only:
 * <ol>
 *   <li>Unit tests that exercise the bootstrap chain in isolation
 *       ({@code CompanyBootstrapServiceTest}).</li>
 *   <li>A future Phase 2 admin API endpoint (not yet designed) that may
 *       expose company creation through HTTP rather than via direct SQL.</li>
 * </ol>
 *
 * <p>Do not invoke this service from any production code path (controller,
 * scheduler, listener) until that Phase 2 design lands and is reviewed.
 *
 * <p>Idempotent on {@code BIZ_KEY} — invocation against an existing tenant
 * short-circuits to {@link BootstrapResult#alreadyExists}. The full chain
 * runs under a single {@code @Transactional(rollbackFor = Exception.class)}
 * boundary so a partial failure leaves zero rows behind (seahorse plan §6).
 *
 * <p>Mirrors the legacy {@code WB_INIT_BUSI_DATA} IDO. The env-set copy is
 * delegated to {@link TenantEnvironmentSettingsBootstrapService}; the
 * usag-set + trco-grp legs of the legacy function are out of scope until
 * their matching frontend features arrive.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyBootstrapService {

    /** Audit user value for rows written before any real principal exists. */
    public static final String SYSTEM_BOOTSTRAP = "SYSTEM_BOOTSTRAP";

    private final BusiInfmRepository busiInfmRepository;
    private final EmplInfmRepository emplInfmRepository;
    private final UserBootstrapService userBootstrapService;
    private final BusinessKeyGeneratorService businessKeyGenerator;
    private final EmployeeNumberGeneratorService employeeNumberGenerator;
    private final TenantEnvironmentSettingsBootstrapService environmentSettingsBootstrap;
    private final TenantProperties tenantProperties;
    private final Clock clock;

    public boolean isBootstrapped(String bizKey) {
        return bizKey != null && busiInfmRepository.existsById(bizKey);
    }

    /**
     * Idempotent variant — if the {@code BIZ_KEY} on the command (or the
     * pinned tenant when none supplied) already exists, returns
     * {@link BootstrapResult#alreadyExists} without writes.
     */
    public BootstrapResult bootstrapIfAbsent(CompanyBootstrapCommand cmd) {
        String candidate = cmd.bizKey() != null ? cmd.bizKey() : tenantProperties.bizKey();
        if (isBootstrapped(candidate)) {
            log.info("Bootstrap skipped — BIZ_KEY {} already exists.", candidate);
            return BootstrapResult.alreadyExists(candidate);
        }
        return bootstrap(cmd);
    }

    /**
     * Creates the 5-table chain inside a single transaction. Throws
     * {@link IllegalStateException} if the resolved {@code BIZ_KEY} does
     * not match the pinned {@link TenantProperties#bizKey()} — single-tenant
     * R&D refuses to create a different tenant.
     */
    @Transactional(rollbackFor = Exception.class)
    public BootstrapResult bootstrap(CompanyBootstrapCommand cmd) {
        String bizKey = cmd.bizKey() != null
                ? cmd.bizKey()
                : businessKeyGenerator.nextBizKey(cmd.ntnlCd(), cmd.chnlId());
        if (bizKey == null) {
            throw new IllegalStateException(
                    "wb_get_next_val returned null for BIZ_KEY — check NTNL_CD/CHNL_ID inputs");
        }

        tenantProperties.assertConsistent(bizKey);

        if (busiInfmRepository.existsById(bizKey)) {
            return BootstrapResult.alreadyExists(bizKey);
        }

        OffsetDateTime now = OffsetDateTime.now(clock);

        BusiInfm busi = new BusiInfm();
        busi.setBizKey(bizKey);
        busi.setCmpnNm(cmd.cmpnNm());
        busi.setNtnlCd(cmd.ntnlCd());
        busi.setChnlId(cmd.chnlId());
        busi.setBusiStts("N");
        busi.setDelYn("N");
        // PRDT_TYPE='F' (WeCMS Default tier) matches the manual UAT setup SQL.
        busi.setPrdtType("F");
        busi.setWizRunYn("N");
        busi.setOverdueYn("N");
        busi.setRegId(SYSTEM_BOOTSTRAP);
        busi.setRegDtm(now);
        busi.setModId(SYSTEM_BOOTSTRAP);
        busi.setModDtm(now);
        busiInfmRepository.save(busi);

        // Mirrors legacy `wb_init_busi_data` env-set leg
        // (functions.sql:5910-5928) — copies every SYS_ENV_SET_INFM row
        // into BUSI_ENV_SET_INFM for the new tenant. Runs INSIDE the
        // outer @Transactional so a failure here rolls the whole
        // bootstrap chain back. Idempotent via WHERE NOT EXISTS.
        environmentSettingsBootstrap.seedDefaultsForTenant(bizKey, SYSTEM_BOOTSTRAP, now);

        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        String emplNo = employeeNumberGenerator.nextEmplNo(bizKey, today);

        EmplInfm empl = new EmplInfm();
        empl.setBizKey(bizKey);
        empl.setEmplNo(emplNo);
        empl.setEmplNm(cmd.firstAdmin().userNm());
        empl.setClphNo(cmd.firstAdmin().clphNo());
        empl.setEml(cmd.firstAdmin().eml());
        empl.setMappSeupYn("N");
        empl.setMappNotiYn("N");
        empl.setPayrollPageRegYn("N");
        empl.setWabooksEmpMngRegYn("Y");
        empl.setEmplNoAutoGenYn("Y");
        empl.setREmplNo(emplNo);
        empl.setRegId(SYSTEM_BOOTSTRAP);
        empl.setRegDtm(now);
        empl.setModId(SYSTEM_BOOTSTRAP);
        empl.setModDtm(now);
        emplInfmRepository.save(empl);

        String userId = userBootstrapService.createFirstAdmin(
                bizKey, emplNo, cmd.firstAdmin(), SYSTEM_BOOTSTRAP);

        log.info("Bootstrap created tenant BIZ_KEY={} EMPL_NO={} USER_ID={}",
                bizKey, emplNo, userId);
        return BootstrapResult.created(bizKey, userId, emplNo);
    }
}
