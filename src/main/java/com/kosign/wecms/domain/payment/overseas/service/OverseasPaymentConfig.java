package com.kosign.wecms.domain.payment.overseas.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Overseas Payment feature wiring — provides the {@link Clock} the key
 * generator depends on. Uses {@code Clock.systemUTC()}; Vietnam-local
 * conversion happens inside {@link OverseasPaymentKeyGenerator}.
 */
@Configuration
public class OverseasPaymentConfig {

    @Bean
    public Clock overseasPaymentClock() {
        return Clock.systemUTC();
    }
}
