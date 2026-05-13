package com.kosign.wecms.domain.setting.contacts.service;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Wires {@link ContactsFileStorageProperties} into the application
 * context so {@link ContactsFileResolver} can be constructor-injected.
 * Mirrors the ERP Transaction History pattern
 * ({@code ErpTrnsHistoryConfig}).
 */
@Configuration
@EnableConfigurationProperties(ContactsFileStorageProperties.class)
public class ContactsConfig {
}
