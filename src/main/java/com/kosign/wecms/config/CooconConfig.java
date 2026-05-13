package com.kosign.wecms.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registers {@link CooconProperties} as a Spring-managed bean so it can be
 * injected into batch services and scheduled jobs that call the Coocon API.
 *
 * <p>Values are bound from {@code wecms.coocon.*} in the active profile's
 * YAML — see {@code application-local.yml} and {@code application-dev.yml}
 * for the DEV credentials, and override via environment variables in PROD.
 */
@Configuration
@EnableConfigurationProperties(CooconProperties.class)
public class CooconConfig {
}
