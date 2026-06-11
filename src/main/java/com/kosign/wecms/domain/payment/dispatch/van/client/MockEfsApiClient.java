package com.kosign.wecms.domain.payment.dispatch.van.client;

import com.kosign.wecms.domain.payment.dispatch.van.dto.EfsEnvelope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Default/offline {@link EfsApiClient}. Active when EFS is NOT enabled
 * ({@code wecms.dispatch.efs.enabled} false or missing) — i.e. every profile
 * except a DEV/SIT one that has wired a real base URL. Performs <b>no network
 * I/O</b>; any call throws so a live EFS request can never leave by accident.
 *
 * <p>Mutually exclusive with {@link RestClientEfsApiClient} via the inverse
 * {@code @ConditionalOnProperty} guard — no duplicate-bean conflict.
 *
 * <p><b>TODO(B1/B2):</b> the real path is {@link RestClientEfsApiClient}, which
 * activates once {@code enabled=true} + a base URL (B1) are set; the per-VAN
 * {@code API_KEY} (B2) is supplied separately at the service layer.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "wecms.dispatch.efs", name = "enabled", havingValue = "false", matchIfMissing = true)
public class MockEfsApiClient implements EfsApiClient {

    @Override
    public EfsEnvelope send(String endpointKey, EfsEnvelope request) {
        log.warn("MockEfsApiClient.send endpointKey={} — EFS disabled; no request sent.", endpointKey);
        throw new UnsupportedOperationException(
                "Live EFS disabled — set wecms.dispatch.efs.enabled=true with a configured base-url (B1) "
                        + "and provision the API key (B2). endpointKey=" + endpointKey);
    }
}
