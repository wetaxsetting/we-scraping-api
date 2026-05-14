package com.kosign.wecms.domain.banking.nxisas;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Properties for the NXISAS gateway integration. Defaults mirror the
 * legacy JEX values from
 * {@code WECMS DEV/WECMS/src/kr/co/coocon/ibasews/NxisasRestClient.java}
 * lines 40-50 so the spike is runnable out of the box; production
 * deployments override via environment / Spring config.
 *
 * <p>NB: gateway connectivity (real key + IP allowlist + VCB test login)
 * comes from Sonny — the default {@code apiKey} below is the public
 * fallback the JEX source ships with and is not expected to authenticate
 * against the live Webcash gateway.
 */
@ConfigurationProperties(prefix = "wecms.nxisas")
public record NxisasGatewayProperties(
        String url,
        String apiKey,
        Integer timeoutSeconds,
        Integer sessionIdleSeconds
) {

    private static final String DEFAULT_URL =
            "https://sgw.webcashvietnam.com/domestic/finance/scrap/smart_gateway.jsp";
    private static final String DEFAULT_API_KEY = "fxAeocJbuweNTgv5FBns";
    private static final int DEFAULT_TIMEOUT = 60;
    private static final int DEFAULT_SESSION_IDLE = 300;

    public NxisasGatewayProperties {
        if (url == null || url.isBlank()) url = DEFAULT_URL;
        if (apiKey == null || apiKey.isBlank()) apiKey = DEFAULT_API_KEY;
        if (timeoutSeconds == null || timeoutSeconds <= 0) timeoutSeconds = DEFAULT_TIMEOUT;
        if (sessionIdleSeconds == null || sessionIdleSeconds <= 0) sessionIdleSeconds = DEFAULT_SESSION_IDLE;
    }
}
