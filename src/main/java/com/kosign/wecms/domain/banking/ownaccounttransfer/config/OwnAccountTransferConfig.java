package com.kosign.wecms.domain.banking.ownaccounttransfer.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Spring wiring for the Own Account Transfer module. Currently scopes the
 * B1 SEED-CBC decrypt feature flag ({@link OatSeedDecryptProperties}) to
 * this module so the cross-cutting {@code @EnableConfigurationProperties}
 * annotation stays local to the feature that owns it.
 */
@Configuration
@EnableConfigurationProperties(OatSeedDecryptProperties.class)
public class OwnAccountTransferConfig {
}
