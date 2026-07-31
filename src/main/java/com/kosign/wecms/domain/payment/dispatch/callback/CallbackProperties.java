package com.kosign.wecms.domain.payment.dispatch.callback;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Inbound EFS-callback access model (01301 / 01302). Bound from {@code wecms.dispatch.callback.*}.
 *
 * <h2>Legacy behaviour — verified 2026-07-29, and it is NOT an IP allow-list</h2>
 * The legacy callbacks are JEX WSVC endpoints with no session and no JWT. Their <b>only</b>
 * application-level authentication is the per-VAN shared secret:
 * {@code SecurityUtil.VerifyMac(CRYPT_KEY, inputEV, inputVV, "", false)} at
 * {@code FILE_DEPOSIT_TRANSFER_API1_act.jsp:102-109}, throwing {@code CD_ENC_VRFY_ERR} on mismatch.
 * Confirmed by inspection:
 * <ul>
 *   <li>Neither callback JSP reads {@code request.getRemoteAddr()}, {@code X-Forwarded-For} or any
 *       IP value.</li>
 *   <li>{@code WECMS_BATCH/web/WEB-INF/web.xml} declares only the generic {@code JexMainFilter} —
 *       no {@code security-constraint}, no remote-address valve.</li>
 *   <li>{@code x-coocon-accessIp} ({@code util/ConnectionUtil.java:191}) is a header WeCMS
 *       <b>sends</b> on <b>outbound</b> Coocon calls. It is not an inbound restriction, and an
 *       earlier revision of this Javadoc citing it as the legacy counterpart was wrong.</li>
 * </ul>
 *
 * <p><b>Therefore the allow-list is not a legacy-parity feature.</b> Legacy relied on HMAC plus
 * whatever network isolation the deployment provided (firewall / private routing), which is not
 * provable from this repository. The refactor keeps the identical HMAC gate and <i>adds</i> the
 * allow-list as defence-in-depth. This class is a <b>deployment-security control</b>, not a port.
 *
 * <h2>Why it is now fail-closed (H-1)</h2>
 * The previous implementation skipped enforcement entirely when {@code allowedIps} was empty, so a
 * protected environment that forgot the variable silently accepted callbacks from anywhere while
 * appearing to be protected. A control that advertises protection it does not provide is worse than
 * no control. {@link Protection#ENFORCE} (the default) now rejects when the list is empty or
 * unparseable; opting out requires writing {@link Protection#DISABLED} explicitly, which is logged
 * as a warning on every startup.
 *
 * <p>Enforcement is independent of the payload HMAC — the crypto check in the callback services
 * remains the primary authentication and is unchanged by this class.
 */
@Slf4j
@Component
@ConfigurationProperties(prefix = "wecms.dispatch.callback")
@Getter
@Setter
public class CallbackProperties {

    /** How the source-IP allow-list is enforced. */
    public enum Protection {
        /**
         * Enforce the allow-list. A request under {@link #pathPrefix} is rejected with 403 unless
         * its resolved client IP matches a rule — <b>including</b> when the list is empty. This is
         * the default: forgetting to configure the list denies, it does not allow.
         */
        ENFORCE,
        /**
         * Explicitly disable IP filtering; the payload HMAC is the only gate. Intended for local
         * development and mock runs. Logged as a WARN at startup so it can never be mistaken for
         * the default in a deployed environment.
         */
        DISABLED
    }

    /** Path prefix the inbound EFS callbacks are mounted under. */
    private String pathPrefix = "/api/v1/payment/dispatch/callbacks";

    /**
     * Allowed gateway source IPs. Literal IPv4/IPv6 addresses and CIDR blocks
     * (e.g. {@code 203.0.113.7}, {@code 203.0.113.0/24}). Blank entries are ignored; anything else
     * that fails to parse aborts startup — see {@link #validate()}.
     */
    private List<String> allowedIps = new ArrayList<>();

    /**
     * Enforcement mode. Defaults to {@link Protection#ENFORCE} so an unset or empty allow-list
     * fails closed in every environment that does not explicitly opt out.
     */
    private Protection protection = Protection.ENFORCE;

    /**
     * Whether an {@code X-Forwarded-For} header may be consulted at all.
     *
     * <p><b>Necessary but no longer sufficient:</b> the header is honoured only when the immediate
     * peer ({@code request.getRemoteAddr()}) is itself in {@link #trustedProxies}. Previously this
     * flag alone was enough, which let any client forge its apparent source IP.
     */
    private boolean trustForwardedFor = true;

    /**
     * Reverse proxies / load balancers whose {@code X-Forwarded-For} may be believed. Same syntax
     * as {@link #allowedIps} (literals and CIDR).
     *
     * <p>Empty (the default) means <b>no</b> proxy is trusted, so {@code X-Forwarded-For} is always
     * ignored and only the direct peer address is matched. That is the safe default: a deployment
     * that terminates TLS at a proxy must name that proxy here deliberately.
     */
    private List<String> trustedProxies = new ArrayList<>();

    private IpAllowList allowList = IpAllowList.empty();
    private IpAllowList proxyList = IpAllowList.empty();

    /**
     * Parse and validate both lists at startup.
     *
     * <p>Fails fast (Option A) on a malformed entry, because a typo'd rule is indistinguishable at
     * request time from a legitimately-absent one. An <b>empty but well-formed</b> list does not
     * abort startup — that case is handled at request time (Option B, 403) so a node can still boot
     * and serve every non-callback endpoint while the gateway IPs are being provisioned.
     *
     * @throws IllegalStateException wrapping the offending entry, aborting context refresh
     */
    @PostConstruct
    void validate() {
        try {
            this.allowList = IpAllowList.parse(allowedIps);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "wecms.dispatch.callback.allowed-ips is misconfigured: " + e.getMessage(), e);
        }
        try {
            this.proxyList = IpAllowList.parse(trustedProxies);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "wecms.dispatch.callback.trusted-proxies is misconfigured: " + e.getMessage(), e);
        }

        if (protection == Protection.DISABLED) {
            log.warn("CALLBACK IP FILTERING IS DISABLED (wecms.dispatch.callback.protection=DISABLED). "
                    + "Inbound VAN callbacks under {} are gated by payload HMAC only. "
                    + "This must never be the setting in SIT / UAT / PROD.", pathPrefix);
            return;
        }
        if (allowList.isEmpty()) {
            log.warn("Callback IP allow-list is EMPTY while protection=ENFORCE — every request under {} "
                    + "will be rejected with 403. Set wecms.dispatch.callback.allowed-ips to the VAN "
                    + "gateway source IP(s).", pathPrefix);
        } else {
            log.info("Callback IP allow-list active: {} rule(s), {} trusted proxy rule(s), pathPrefix={}",
                    allowList.size(), proxyList.size(), pathPrefix);
        }
    }

    /** Validated allow-list. Never null; empty when unconfigured. */
    public IpAllowList allowList() {
        return allowList;
    }

    /** Validated trusted-proxy list. Never null; empty means no proxy is trusted. */
    public IpAllowList trustedProxyList() {
        return proxyList;
    }
}
