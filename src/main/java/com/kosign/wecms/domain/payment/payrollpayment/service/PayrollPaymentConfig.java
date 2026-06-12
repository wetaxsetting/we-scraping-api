package com.kosign.wecms.domain.payment.payrollpayment.service;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Payroll feature wiring — enables the
 * {@link PayrollPaymentStorageProperties} binding for the file upload /
 * download flow.
 */
@Configuration
@EnableConfigurationProperties(PayrollPaymentStorageProperties.class)
public class PayrollPaymentConfig {
}
