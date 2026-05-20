package com.kosign.wecms.domain.operation.client;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registers {@link OsmcProperties} as a Spring-managed bean. */
@Configuration
@EnableConfigurationProperties(OsmcProperties.class)
public class OsmcConfig {
}
