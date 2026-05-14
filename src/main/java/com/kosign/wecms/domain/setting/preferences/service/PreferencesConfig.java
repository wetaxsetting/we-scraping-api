package com.kosign.wecms.domain.setting.preferences.service;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Wires {@link PreferencesLogoStorageProperties} into the application
 * context so {@link PreferencesLogoResolver} can be constructor-injected.
 * Mirrors the Contacts / ERP Transaction History config beans.
 */
@Configuration
@EnableConfigurationProperties(PreferencesLogoStorageProperties.class)
public class PreferencesConfig {
}
