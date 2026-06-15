package com.kosign.wecms.domain.payment.expensepayment.service;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Expense Payment feature wiring — enables the
 * {@link ExpensePaymentStorageProperties} binding for the file upload /
 * download flow.
 */
@Configuration
@EnableConfigurationProperties(ExpensePaymentStorageProperties.class)
public class ExpensePaymentConfig {
}
