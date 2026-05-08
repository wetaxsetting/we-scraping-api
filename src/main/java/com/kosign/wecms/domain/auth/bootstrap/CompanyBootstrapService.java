package com.kosign.wecms.domain.auth.bootstrap;

import com.kosign.wecms.config.TenantProperties;
import com.kosign.wecms.domain.auth.entity.BusiInfm;
import com.kosign.wecms.domain.auth.entity.EmplInfm;
import com.kosign.wecms.domain.auth.repository.BusiInfmRepository;
import com.kosign.wecms.domain.auth.repository.EmplInfmRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Single transactional orchestrator for the 5-table tenant bootstrap:
 *
 * <pre>
 *   BUSI_INFM
 *     -> EMPL_INFM
 *       -> USER_INFM
 *         -> USER_BUSI_CONN_INFM
 *           -> BUSI_ADMN_INFM
 * </pre>
 *
 * <p>Idempotent on {@code BIZ_KEY} — second invocation with an existing
 * tenant short-circuits to {@link BootstrapResult#alreadyExists}. The full
 * chain runs under a single {@code @Transactional(rollbackFor = Exception.class)}
 * boundary so a partial failure leaves zero rows behind (seahorse plan §6).
 *
 * <p>Replaces the legacy {@code WB_INIT_BUSI_DATA} IDO + the manual SQL the
 * JEX system relies on for first-time tenant setup.
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
