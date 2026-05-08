package com.kosign.wecms.domain.auth.bootstrap;

import com.kosign.wecms.config.TenantProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * LOCAL-only entry point that ensures the configured tenant exists on
 * startup. Idempotent — short-circuits when the BIZ_KEY pinned by
 * {@link TenantProperties} is already present (V2 / V5 seeds, or a
 * previous run of this runner).
 *
 * <p>Order: Spring runs this AFTER Flyway and AFTER {@link DataSeeder} so
 * the legacy {@code wb_get_next_val} function (V11) is in place and any
 * V2 sentinel passwords have been replaced.
 *
 * <p>UAT/PROD will use the {@code POST /internal/bootstrap/company} API
 * (Phase 2) — this runner does not load on those profiles.
 */
@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
public class BootstrapRunner implements ApplicationRunner {

    /** R&D default password for the seeded admin — same value as DataSeeder. */
    static final String RND_PASSWORD = "Password!1";

    private final CompanyBootstrapService companyBootstrapService;
    private final TenantProperties tenantProperties;

    @Override
    public void run(ApplicationArguments args) {
        String bizKey = tenantProperties.bizKey();
        if (companyBootstrapService.isBootstrapped(bizKey)) {
            log.debug("BootstrapRunner: BIZ_KEY {} already present — no action.", bizKey);
            return;
        }

        // BIZ_KEY shape is {NTNL_CD}_{CHNL_ID}_{NNNNNN} — split it back so the
        // command carries those parts alongside the pinned key.
        String[] parts = bizKey.split("_");
        String ntnlCd = parts.length > 0 ? parts[0] : "VN";
        String chnlId = parts.length > 1 ? parts[1] : "VNM0004";

        FirstAdminCommand admin = new FirstAdminCommand(
                "rndadmin",
                "R&D Bootstrap Admin",
                RND_PASSWORD,
                "rndadmin@example.com",
                null);

        CompanyBootstrapCommand cmd = new CompanyBootstrapCommand(
                bizKey,
                "WeCMS R&D Tenant",
                ntnlCd,
                chnlId,
                admin);

        BootstrapResult result = companyBootstrapService.bootstrapIfAbsent(cmd);
        if (result.created()) {
            log.info("BootstrapRunner: created tenant BIZ_KEY={} USER_ID={} EMPL_NO={}",
                    result.bizKey(), result.userId(), result.emplNo());
        }
    }
}
