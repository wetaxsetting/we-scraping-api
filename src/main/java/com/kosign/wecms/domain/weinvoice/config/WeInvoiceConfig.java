package com.kosign.wecms.domain.weinvoice.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registers {@link WeInvoiceProperties}. The database handle itself is {@link WeInvoiceDb}. */
@Configuration
@EnableConfigurationProperties(WeInvoiceProperties.class)
public class WeInvoiceConfig {
}
