package com.kosign.wecms.domain.payment.vendorpayment.service;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Vendor Payment feature wiring — enables the
 * {@link VendorPaymentStorageProperties} binding for the file upload /
 * download flow.
 */
@Configuration
@EnableConfigurationProperties(VendorPaymentStorageProperties.class)
public class VendorPaymentConfig {
}
