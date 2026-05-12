package com.kosign.wecms.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Dormant tenant config slot — retained for binding only.
 *
 * <p>Historical role (Sprint 0): this property pinned every login to a
 * single BIZ_KEY. That gate was removed when multi-company support
 * landed — login now resolves tenant per-user from
 * {@code USER_BUSI_CONN_INFM}, with a chooser step when the user has
 * multiple active links.
 *
 * <p>Subsequent role (briefly): identified the BIZ_KEY that the
 * since-removed {@code BootstrapRunner} would create on a fresh local
 * DB. That auto-invocation path is also gone — Spring no longer creates
 * company / admin / employee / link data at startup. Operational
 * practice is now to run the legacy DBA manual SQL setup pattern (see
 * {@code Claude Code (AI)/sql/setup_uat_account_*.sql}) directly
 * against the target database.
 *
 * <p>Current role: this property exists only so
 * {@link #assertConsistent(String)} has a comparison target if anyone
 * ever calls {@code CompanyBootstrapService.bootstrap(...)}
 * programmatically (unit test, future Phase 2 admin API). It does
 * <b>not</b> influence runtime login, JWT, or {@code TenantContext}.
 *
 * <p>The regex still rejects {@code %} so a stray value can't drift
 * into wildcard filtering on the {@code assertConsistent} guard.
 */
@ConfigurationProperties(prefix = "wecms.tenant")
public record TenantProperties(
        @NotBlank
        @Pattern(regexp = "^[A-Z0-9_]+$", message = "biz-key must be a literal BIZ_KEY (no '%' wildcards)")
        String bizKey
) {

    /**
     * Fail-fast guard for programmatic bootstrap calls. Login does not
     * route through this check — it queries USER_BUSI_CONN_INFM
     * directly. The only remaining consumer is
     * {@code CompanyBootstrapService.bootstrap(...)}, which has no
     * auto-invocation path today (see that class's Javadoc).
     */
    public void assertConsistent(String candidateBizKey) {
        if (candidateBizKey == null || !candidateBizKey.equals(bizKey)) {
            throw new IllegalStateException(
                    "Bootstrap BIZ_KEY '" + candidateBizKey
                            + "' does not match wecms.tenant.biz-key '" + bizKey + "'");
        }
    }
}
