package com.kosign.wecms.domain.banking.nxisas;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Client-side tuning knobs for the NXISAS bank-scraping flow (LOGIN →
 * OTP/QnA/CAPTCHA → ACCT_LIST). The gateway URL and API key are NOT
 * here — they live in
 * {@link com.kosign.wecms.config.CooconProperties} ({@code smartApiUrl}
 * / {@code smartApiKey}) since the NXISAS endpoint and the FX-rate
 * endpoint hit the same Coocon smart-gateway with the same credentials.
 * Per-environment hosts (Sonny, 2026-05-28):
 * <ul>
 *   <li><b>DEV / SIT / local R&amp;D</b>:
 *       {@code https://dev-sgw.webcashvietnam.com:8443/domestic/finance/scrap/smart_gateway.jsp}</li>
 *   <li><b>UAT / PROD</b>:
 *       {@code https://sgw.webcashvietnam.com/domestic/finance/scrap/smart_gateway.jsp}</li>
 * </ul>
 * Both hosts are configured via the single {@code wecms.coocon.smart-api-*}
 * yaml block — there is no separate {@code wecms.nxisas.url} /
 * {@code wecms.nxisas.api-key}. The two tuning fields below default to
 * the legacy JEX values (60 s timeout, 5 min session idle) and only
 * need overriding when a developer wants to tune the spike.
 */
@ConfigurationProperties(prefix = "wecms.nxisas")
public record NxisasGatewayProperties(
        Integer timeoutSeconds,
        Integer sessionIdleSeconds
) {

    private static final int DEFAULT_TIMEOUT = 60;
    private static final int DEFAULT_SESSION_IDLE = 300;

    public NxisasGatewayProperties {
        if (timeoutSeconds == null || timeoutSeconds <= 0) timeoutSeconds = DEFAULT_TIMEOUT;
        if (sessionIdleSeconds == null || sessionIdleSeconds <= 0) sessionIdleSeconds = DEFAULT_SESSION_IDLE;
    }
}
