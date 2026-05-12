package com.kosign.wecms.domain.setting.erptransactionhistory.service;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Feature-local Spring configuration. Currently only wires
 * {@link ErpTrnsHistoryStorageProperties} into the application context;
 * additional feature-scoped beans (caches, executors, etc.) live here
 * if / when they appear.
 *
 * <p>Kept inside the feature package rather than promoted to
 * {@code com.kosign.wecms.config} so the property binding moves with
 * the feature — adding a new {@code domain/setting/erptransactionhistory/}
 * caller does not require touching unrelated config classes.
 */
@Configuration
@EnableConfigurationProperties(ErpTrnsHistoryStorageProperties.class)
public class ErpTrnsHistoryConfig {
}
