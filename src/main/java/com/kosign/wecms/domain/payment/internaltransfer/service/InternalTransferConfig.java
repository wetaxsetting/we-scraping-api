package com.kosign.wecms.domain.payment.internaltransfer.service;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Internal Transfer feature wiring — provides the shared {@link Clock}
 * the key generator + cut-off validator depend on and enables the
 * feature's storage-root binding.
 *
 * <p>The {@link Clock} bean is registered only inside this feature
 * package so other modules can wire their own {@code Clock} if they
 * ever need different time semantics. The default uses
 * {@link Clock#systemUTC()} — Vietnam-local conversion happens at the
 * call sites that need it (key generator + cut-off validator).
 *
 * <p>Cut-off configuration is <b>DB-only</b> (2026-07-09): it is loaded from
 * {@code TB_CUTOFF_CONFIG} / {@code TB_CUTOFF_PARAM} by
 * {@link CutoffConfigProvider}, not from {@code application.yml}. The former
 * {@code CutoffProperties} {@code @ConfigurationProperties} binding was removed.
 */
@Configuration
@EnableConfigurationProperties({InternalTransferStorageProperties.class})
public class InternalTransferConfig {

    @Bean
    public Clock internalTransferClock() {
        return Clock.systemUTC();
    }
}
