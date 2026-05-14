package com.kosign.wecms.domain.banking.nxisas;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Spring wiring for the NXISAS gateway client. Builds one shared
 * {@link RestClient} bean with the configured timeout and base URL.
 *
 * <p>Also enables Spring scheduling so the
 * {@link com.kosign.wecms.domain.banking.nxisas.session.NxisasSessionRegistry}
 * eviction task fires. First feature in the refactor to need
 * {@code @Scheduled}; enabling it here keeps the cross-cutting
 * annotation scoped to the NXISAS module.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(NxisasGatewayProperties.class)
public class NxisasConfig {

    @Bean("nxisasRestClient")
    public RestClient nxisasRestClient(NxisasGatewayProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofSeconds(props.timeoutSeconds());
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return RestClient.builder()
                .baseUrl(props.url())
                .requestFactory(factory)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
