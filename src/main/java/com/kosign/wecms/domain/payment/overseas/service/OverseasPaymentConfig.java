package com.kosign.wecms.domain.payment.overseas.service;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Overseas Payment feature wiring — enables the
 * {@link OverseasPaymentStorageProperties} binding for the file upload /
 * download flow.
 *
 * <p>The former {@code overseasPaymentClock} bean was removed with the MR-2C
 * funnel migration (2026-06-10): {@code OverseasPaymentKeyGenerator} no longer
 * mints a date-prefixed key, so it no longer needs a dedicated {@link java.time.Clock}.
 */
@Configuration
@EnableConfigurationProperties(OverseasPaymentStorageProperties.class)
public class OverseasPaymentConfig {
}
