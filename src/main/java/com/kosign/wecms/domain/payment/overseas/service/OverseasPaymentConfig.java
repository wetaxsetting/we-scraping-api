package com.kosign.wecms.domain.payment.overseas.service;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Overseas Payment feature wiring — provides the {@link Clock} the key
 * generator depends on and enables the {@link OverseasPaymentStorageProperties}
 * binding for the file upload / download flow.
 */
@Configuration
@EnableConfigurationProperties(OverseasPaymentStorageProperties.class)
public class OverseasPaymentConfig {

    @Bean
    public Clock overseasPaymentClock() {
        return Clock.systemUTC();
    }
}
