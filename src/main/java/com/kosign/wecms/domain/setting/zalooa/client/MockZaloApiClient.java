package com.kosign.wecms.domain.setting.zalooa.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Default/offline {@link ZaloApiClient}, active whenever {@code wecms.zalo.enabled}
 * is {@code false} or absent — which is every profile that has not deliberately
 * provisioned Zalo credentials.
 *
 * <p><b>Every method throws. That is the point.</b> Unlike a bank-gateway stub,
 * an accidental live call here has two costs that cannot be undone: each template
 * message is billed against the tenant's ZBS wallet, and it arrives on a real
 * person's phone. A mock that returned a cheerful fake success would also let a
 * send path ship untested and look healthy in local runs. Failing loudly is the
 * safer default in both directions.
 *
 * <p>Mutually exclusive with {@link RestClientZaloApiClient} through the inverse
 * {@code @ConditionalOnProperty} guard, so there is no duplicate-bean conflict.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "wecms.zalo", name = "enabled", havingValue = "false", matchIfMissing = true)
public class MockZaloApiClient implements ZaloApiClient {

    private static final String DISABLED =
            "Live Zalo is disabled — set wecms.zalo.enabled=true and provision "
                    + "WECMS_ZALO_APP_ID + WECMS_ZALO_APP_SECRET. Operation: ";

    @Override
    public ZaloTokenGrant exchangeAuthorizationCode(String code) {
        log.warn("MockZaloApiClient.exchangeAuthorizationCode — Zalo disabled; no request sent.");
        throw new UnsupportedOperationException(DISABLED + "exchangeAuthorizationCode");
    }

    @Override
    public ZaloTokenGrant refreshAccessToken(String refreshToken) {
        log.warn("MockZaloApiClient.refreshAccessToken — Zalo disabled; no request sent.");
        throw new UnsupportedOperationException(DISABLED + "refreshAccessToken");
    }

    @Override
    public ZaloSendOutcome sendTemplateMessage(String accessToken, ZaloSendCommand command) {
        log.warn("MockZaloApiClient.sendTemplateMessage template={} — Zalo disabled; no message sent.",
                command == null ? "null" : command.templateId());
        throw new UnsupportedOperationException(DISABLED + "sendTemplateMessage");
    }
}
