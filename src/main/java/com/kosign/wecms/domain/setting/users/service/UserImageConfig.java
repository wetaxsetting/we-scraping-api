package com.kosign.wecms.domain.setting.users.service;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Wires {@link UserImageStorageProperties} into the application context
 * so {@link UserImageResolver} can be constructor-injected. Mirrors the
 * Preferences / Contacts / ERP Transaction History config beans.
 */
@Configuration
@EnableConfigurationProperties(UserImageStorageProperties.class)
public class UserImageConfig {
}
