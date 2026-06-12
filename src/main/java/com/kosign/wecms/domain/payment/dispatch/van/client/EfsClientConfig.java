package com.kosign.wecms.domain.payment.dispatch.van.client;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Builds the shared {@code efsRestClient} {@link RestClient} for the EFS gateway,
 * mirroring the {@code nxisasRestClient} convention
 * ({@link com.kosign.wecms.domain.banking.nxisas.NxisasConfig}):
 * {@link SimpleClientHttpRequestFactory} + explicit connect/read timeouts +
 * {@code base-url} from {@link EfsProperties}.
 *
 * <p>Active only when {@code wecms.dispatch.efs.enabled=true} (DEV/SIT). In the
 * default profile this config is inert and {@link MockEfsApiClient} is the active
 * {@link EfsApiClient}. No secret (the {@code API_KEY}) is involved here.
 */
@Configuration
@ConditionalOnProperty(prefix = "wecms.dispatch.efs", name = "enabled", havingValue = "true")
public class EfsClientConfig {

    @Bean("efsRestClient")
    public RestClient efsRestClient(EfsProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(props.getConnectTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(props.getReadTimeoutMs()));
        return RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .requestFactory(factory)
                .build();
    }
}
