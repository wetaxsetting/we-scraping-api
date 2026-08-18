package com.kosign.wecms.domain.setting.zalooa.client;

/**
 * Port for the two Zalo HTTP surfaces this feature needs: the OAuth token
 * endpoint and the template-message endpoint.
 *
 * <p>Deliberately thin. It knows how to talk HTTP to Zalo and nothing else — no
 * persistence, no token caching, no template resolution, no tenant awareness.
 * Token storage and expiry live in {@code ZaloTokenService}; template resolution
 * and send logging in {@code ZaloMessageService}. Keeping this seam narrow is
 * what makes {@link MockZaloApiClient} a complete substitute.
 *
 * <p>Implementations are mutually exclusive via inverse
 * {@code @ConditionalOnProperty} guards on {@code wecms.zalo.enabled}:
 * <ul>
 *   <li>{@link RestClientZaloApiClient} — real adapter, active only when
 *       {@code enabled=true}.</li>
 *   <li>{@link MockZaloApiClient} — the default; every method throws, so a
 *       billable message can never leave a developer laptop or CI by accident.</li>
 * </ul>
 *
 * <p>Vendor reference (verified against the WeBill365 production implementation,
 * {@code webill365-vn-api/docs/features/zalo-oa-integration.md}, 2026-08-17):
 * <pre>
 *   POST https://oauth.zaloapp.com/v4/oa/access_token
 *        Content-Type: application/x-www-form-urlencoded
 *        secret_key: &lt;app secret&gt;
 *        app_id, grant_type=authorization_code|refresh_token, code|refresh_token
 *
 *   POST https://business.openapi.zalo.me/message/template
 *        Content-Type: application/json
 *        access_token: &lt;access token&gt;
 *        { phone, template_id, template_data, tracking_id }
 * </pre>
 */
public interface ZaloApiClient {

    /**
     * Exchange the one-time OAuth {@code code} from the Zalo consent redirect for
     * the first token pair ({@code grant_type=authorization_code}).
     *
     * @param code authorization code, single-use — worthless once exchanged
     * @throws ZaloApiException if Zalo refuses the code or the call fails
     */
    ZaloTokenGrant exchangeAuthorizationCode(String code);

    /**
     * Trade a refresh token for a new pair ({@code grant_type=refresh_token}).
     *
     * <p>The returned {@code refreshToken} <b>replaces</b> the one passed in —
     * Zalo rotates it and invalidates the old value immediately. The caller must
     * persist both halves or the connection dies at the next refresh.
     *
     * @throws ZaloApiException if the refresh token is expired/revoked or the call fails
     */
    ZaloTokenGrant refreshAccessToken(String refreshToken);

    /**
     * Send one approved template message.
     *
     * <p>Returns an outcome for <b>both</b> success and Zalo-side business
     * rejections (non-zero {@code error}) so the caller can always write a
     * {@code ZALO_SEND_HIS} row. Only transport-level faults throw.
     *
     * @throws ZaloApiException on transport failure, non-2xx, or an unusable body
     */
    ZaloSendOutcome sendTemplateMessage(String accessToken, ZaloSendCommand command);
}
