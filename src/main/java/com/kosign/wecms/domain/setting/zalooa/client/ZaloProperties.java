package com.kosign.wecms.domain.setting.zalooa.client;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Externalised Zalo OA configuration, bound from {@code wecms.zalo.*}.
 *
 * <p>Mirrors the {@code EfsProperties} convention: {@code enabled} is the master
 * switch, and while it is {@code false} (the default) {@link MockZaloApiClient}
 * is the active {@link ZaloApiClient} bean and no live call can leave the
 * process. That matters more here than for a bank gateway — every Zalo template
 * message is <b>billed</b> (210–400 VND depending on template and addressing
 * mode) and lands on a real customer's phone, so an accidental send during local
 * development costs money and reaches a stranger.
 *
 * <p><b>The two URLs are different hosts</b> — OAuth lives on
 * {@code oauth.zaloapp.com}, messaging on {@code business.openapi.zalo.me} — so
 * there is no single base URL to configure and {@link ZaloClientConfig} builds a
 * {@code RestClient} without one. Both are absolute and defaulted in code
 * because they are vendor-fixed, not environment-specific.
 *
 * <p><b>Secrets:</b> {@link #appId} / {@link #appSecret} are provisioned through
 * {@code .env} only ({@code WECMS_ZALO_APP_ID} / {@code WECMS_ZALO_APP_SECRET}).
 * The reference implementation (WeBill365) hardcoded both into
 * {@code application-local.yml} and they are now in its git history — that is
 * finding §10.1 of {@code webill365-vn-api/docs/features/zalo-oa-integration.md}
 * and exactly the mistake F-05 (committed-secret cleanup) closed in this repo.
 * Do not add a default value to either field.
 */
@Component
@ConfigurationProperties(prefix = "wecms.zalo")
@Getter
@Setter
public class ZaloProperties {

    /**
     * Master switch. {@code false} (default) → {@link MockZaloApiClient} is the
     * active bean, so no billable message and no token exchange can occur. Set
     * {@code true} only where {@link #appId} + {@link #appSecret} are provisioned.
     */
    private boolean enabled = false;

    /** Zalo Developer App id. Empty by default — supplied via {@code .env}. */
    private String appId = "";

    /**
     * Zalo Developer App secret, sent as the {@code secret_key} header on every
     * token call. Empty by default — supplied via {@code .env}, never yaml.
     */
    private String appSecret = "";

    /** OAuth token endpoint. Vendor-fixed; overriding is for tests only. */
    private String tokenUrl = "https://oauth.zaloapp.com/v4/oa/access_token";

    /** Template-message endpoint. Vendor-fixed; overriding is for tests only. */
    private String notifyUrl = "https://business.openapi.zalo.me/message/template";

    /** Connect timeout (ms) — TCP establishment only. */
    private int connectTimeoutMs = 30_000;

    /** Read timeout (ms) — waiting for the vendor response. */
    private int readTimeoutMs = 60_000;

    /**
     * Treat an access token as expired this many seconds before its real expiry,
     * so a token cannot lapse mid-flight between the check and the vendor call.
     * 300 s matches the reference implementation's {@code isExpired()} margin.
     */
    private long expirySkewSeconds = 300;

    /**
     * Staleness threshold (days) for the scheduled refresh sweep
     * ({@code ZaloTokenRefreshScheduler}). Zalo's refresh token is valid
     * ~3 months and <b>rotates on every
     * use</b>, so a connection that sends no messages for a quarter would lose
     * its authorisation entirely and need a human to redo the OAuth popup. The
     * sweep exists to keep the rotation alive, not to keep access tokens warm —
     * that happens on demand in {@code ZaloTokenService}.
     *
     * <p>29 days matches the reference implementation's interval.
     */
    private int refreshStaleDays = 29;
}
