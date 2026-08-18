package com.kosign.wecms.domain.setting.zalooa.client;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Builds the shared {@code zaloRestClient}, following the
 * {@code EfsClientConfig} / {@code NxisasConfig} convention:
 * {@link SimpleClientHttpRequestFactory} plus explicit connect/read timeouts.
 *
 * <p><b>No {@code baseUrl}.</b> Zalo splits OAuth
 * ({@code oauth.zaloapp.com}) from messaging
 * ({@code business.openapi.zalo.me}), so there is no single origin to pin and
 * {@link RestClientZaloApiClient} passes absolute URIs from
 * {@link ZaloProperties}. Setting a base URL here would silently work for one
 * surface and break the other.
 *
 * <p>Active only when {@code wecms.zalo.enabled=true}. In every other profile this
 * configuration is inert and {@link MockZaloApiClient} is the active
 * {@link ZaloApiClient}, so no billable send and no token exchange is reachable.
 *
 * <p>Timeouts are explicit rather than left to the JDK default of "wait forever":
 * a hung Zalo response would otherwise pin a scheduler thread during the token
 * refresh sweep, or a request thread during a send.
 */
@Configuration
@ConditionalOnProperty(prefix = "wecms.zalo", name = "enabled", havingValue = "true")
public class ZaloClientConfig {

    @Bean("zaloRestClient")
    public RestClient zaloRestClient(ZaloProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(props.getConnectTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(props.getReadTimeoutMs()));
        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }
}
