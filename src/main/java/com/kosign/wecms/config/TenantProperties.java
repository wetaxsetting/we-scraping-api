package com.kosign.wecms.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Active tenant for this local R&D deployment.
 *
 * <p>R&D scope is deliberately single-tenant: one exact BIZ_KEY, no wildcards.
 * The value is validated as an exact match against
 * {@code USER_BUSI_CONN_INFM.BIZ_KEY}. The regex rejects any '%' character so
 * configuration can't accidentally drift into wildcard filtering.
 */
@ConfigurationProperties(prefix = "wecms.tenant")
public record TenantProperties(
        @NotBlank
        @Pattern(regexp = "^[A-Z0-9_]+$", message = "biz-key must be a literal BIZ_KEY (no '%' wildcards)")
        String bizKey
) {

    /**
     * Fail-fast guard for bootstrap paths. When the runtime tenant is pinned
     * (always true in single-tenant R&D) any attempt to bootstrap a different
     * BIZ_KEY than the configured one is a configuration error.
     */
    public void assertConsistent(String candidateBizKey) {
        if (candidateBizKey == null || !candidateBizKey.equals(bizKey)) {
            throw new IllegalStateException(
                    "Bootstrap BIZ_KEY '" + candidateBizKey
                            + "' does not match wecms.tenant.biz-key '" + bizKey + "'");
        }
    }
}
