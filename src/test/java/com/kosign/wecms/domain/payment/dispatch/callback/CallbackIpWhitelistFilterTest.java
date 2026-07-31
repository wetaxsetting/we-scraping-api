package com.kosign.wecms.domain.payment.dispatch.callback;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * H-1 — the inbound VAN-callback source-IP gate.
 *
 * <h2>Classification (verified 2026-07-29, not assumed)</h2>
 * This is a <b>deployment-security requirement, not a legacy-parity issue</b>. The legacy callbacks
 * authenticate solely by the per-VAN HMAC —
 * {@code SecurityUtil.VerifyMac(CRYPT_KEY, inputEV, inputVV, "", false)} at
 * {@code FILE_DEPOSIT_TRANSFER_API1_act.jsp:102-109}. Neither callback JSP reads any IP,
 * {@code WECMS_BATCH/web/WEB-INF/web.xml} declares only {@code JexMainFilter} (no
 * {@code security-constraint}, no remote-address valve), and {@code x-coocon-accessIp}
 * ({@code util/ConnectionUtil.java:191}) is a header WeCMS <b>sends outbound</b>, not an inbound
 * control. Legacy therefore relied on HMAC plus unprovable network isolation.
 *
 * <p>The refactor keeps that same HMAC gate untouched and adds this allow-list on top. The defect
 * being fixed is that the addition <b>failed open</b>: an empty list skipped enforcement entirely,
 * so a protected environment that forgot the variable accepted callbacks from anywhere while
 * appearing protected.
 */
class CallbackIpWhitelistFilterTest {

    private static final String CALLBACK_URI = "/api/v1/payment/dispatch/callbacks/bulk-reception-result";
    private static final String GATEWAY_IP = "203.0.113.7";
    private static final String PROXY_IP = "10.20.30.40";
    private static final String STRANGER_IP = "198.51.100.99";

    private CallbackProperties props;

    @BeforeEach
    void setUp() {
        props = new CallbackProperties();
    }

    /** Build the filter after mutating {@link #props} — validate() wires the parsed lists. */
    private CallbackIpWhitelistFilter filter() {
        props.validate();
        return new CallbackIpWhitelistFilter(props);
    }

    private MockHttpServletRequest request(String peer) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", CALLBACK_URI);
        req.setRequestURI(CALLBACK_URI);
        req.setRemoteAddr(peer);
        return req;
    }

    private record Outcome(boolean passed, int status) { }

    private Outcome run(MockHttpServletRequest req) throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        filter().doFilter(req, res, chain);
        boolean passed = res.getStatus() == 200 && res.getErrorMessage() == null;
        if (passed) {
            verify(chain, times(1)).doFilter(req, res);
        } else {
            verify(chain, never()).doFilter(req, res);
        }
        return new Outcome(passed, res.getStatus());
    }

    // ── 1 + 2. direct source IP ─────────────────────────────────────────────

    @Test
    @DisplayName("1: an allowed direct source IP is accepted")
    void allowedDirectIpAccepted() throws Exception {
        props.setAllowedIps(List.of(GATEWAY_IP));

        assertThat(run(request(GATEWAY_IP)).passed()).isTrue();
    }

    @Test
    @DisplayName("2: a blocked direct source IP is rejected with 403")
    void blockedDirectIpRejected() throws Exception {
        props.setAllowedIps(List.of(GATEWAY_IP));

        Outcome outcome = run(request(STRANGER_IP));

        assertThat(outcome.passed()).isFalse();
        assertThat(outcome.status()).isEqualTo(403);
    }

    // ── 3 + 4. fail closed ──────────────────────────────────────────────────

    @Test
    @DisplayName("3: an EMPTY allow-list fails CLOSED — this is the H-1 regression")
    void emptyAllowListFailsClosed() throws Exception {
        props.setAllowedIps(List.of());   // the exact pre-fix condition that allowed everyone

        Outcome outcome = run(request(GATEWAY_IP));

        assertThat(outcome.passed()).isFalse();
        assertThat(outcome.status()).isEqualTo(403);
    }

    @Test
    @DisplayName("4: a MISSING allow-list fails closed while protection=ENFORCE")
    void missingAllowListFailsClosed() throws Exception {
        // Property never set at all — the default must not be permissive.
        assertThat(props.getProtection()).isEqualTo(CallbackProperties.Protection.ENFORCE);

        assertThat(run(request(GATEWAY_IP)).status()).isEqualTo(403);
    }

    @Test
    @DisplayName("3b: an allow-list of only blank entries is still empty, and still fails closed")
    void blankOnlyAllowListFailsClosed() throws Exception {
        props.setAllowedIps(List.of("", "   "));

        assertThat(run(request(GATEWAY_IP)).status()).isEqualTo(403);
    }

    // ── 5 + 6. startup validation of malformed entries ──────────────────────

    @Nested
    @DisplayName("startup validation")
    class Validation {

        @Test
        @DisplayName("5: an invalid IP entry aborts startup rather than being silently dropped")
        void invalidIpRejected() {
            props.setAllowedIps(List.of(GATEWAY_IP, "not-an-ip"));

            assertThatThrownBy(props::validate)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("allowed-ips")
                    .hasMessageContaining("not-an-ip");
        }

        @Test
        @DisplayName("6: an invalid CIDR prefix aborts startup")
        void invalidCidrRejected() {
            props.setAllowedIps(List.of("203.0.113.0/33"));

            assertThatThrownBy(props::validate)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("0..32");
        }

        @Test
        @DisplayName("6b: a non-numeric CIDR prefix aborts startup")
        void nonNumericCidrRejected() {
            props.setAllowedIps(List.of("203.0.113.0/abc"));

            assertThatThrownBy(props::validate)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not a number");
        }

        @Test
        @DisplayName("6c: a malformed trusted-proxy entry also aborts startup")
        void invalidTrustedProxyRejected() {
            props.setTrustedProxies(List.of("10.0.0.300"));

            assertThatThrownBy(props::validate)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("trusted-proxies");
        }

        @Test
        @DisplayName("6d: a hostname is rejected — the allow-list must never depend on DNS")
        void hostnameRejected() {
            props.setAllowedIps(List.of("gateway.example.com"));

            assertThatThrownBy(props::validate).isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("blank entries are tolerated (trailing comma in an env var is a formatting artefact)")
        void blankEntriesTolerated() {
            props.setAllowedIps(List.of(GATEWAY_IP, "", "  "));

            props.validate();

            assertThat(props.allowList().size()).isEqualTo(1);
        }
    }

    // ── 7-10. forwarded headers ─────────────────────────────────────────────

    @Test
    @DisplayName("7: a forged X-Forwarded-For from an UNTRUSTED peer is ignored")
    void forgedForwardedForIgnored() throws Exception {
        props.setAllowedIps(List.of(GATEWAY_IP));
        props.setTrustForwardedFor(true);
        props.setTrustedProxies(List.of());          // nobody is trusted

        MockHttpServletRequest req = request(STRANGER_IP);
        req.addHeader("X-Forwarded-For", GATEWAY_IP);   // attacker claims to be the gateway

        assertThat(run(req).status())
                .as("the header must not let an untrusted peer choose its own source IP")
                .isEqualTo(403);
    }

    @Test
    @DisplayName("8: a TRUSTED proxy forwarding an allowed original IP is accepted")
    void trustedProxyWithAllowedOriginAccepted() throws Exception {
        props.setAllowedIps(List.of(GATEWAY_IP));
        props.setTrustForwardedFor(true);
        props.setTrustedProxies(List.of(PROXY_IP));

        MockHttpServletRequest req = request(PROXY_IP);
        req.addHeader("X-Forwarded-For", GATEWAY_IP);

        assertThat(run(req).passed()).isTrue();
    }

    @Test
    @DisplayName("9: a TRUSTED proxy forwarding a blocked original IP is rejected")
    void trustedProxyWithBlockedOriginRejected() throws Exception {
        props.setAllowedIps(List.of(GATEWAY_IP));
        props.setTrustForwardedFor(true);
        props.setTrustedProxies(List.of(PROXY_IP));

        MockHttpServletRequest req = request(PROXY_IP);
        req.addHeader("X-Forwarded-For", STRANGER_IP);

        assertThat(run(req).status()).isEqualTo(403);
    }

    @Test
    @DisplayName("10: a multi-hop chain resolves to the LEFT-MOST entry (the originating client)")
    void multipleForwardedHopsUseLeftMost() throws Exception {
        props.setAllowedIps(List.of(GATEWAY_IP));
        props.setTrustForwardedFor(true);
        props.setTrustedProxies(List.of(PROXY_IP));

        MockHttpServletRequest req = request(PROXY_IP);
        req.addHeader("X-Forwarded-For", GATEWAY_IP + ", 10.1.1.1, 10.1.1.2");

        assertThat(run(req).passed()).isTrue();
    }

    @Test
    @DisplayName("10b: a chain whose left-most entry is blocked is rejected even if a later hop is allowed")
    void multiHopDoesNotScanForAnAllowedEntry() throws Exception {
        props.setAllowedIps(List.of(GATEWAY_IP));
        props.setTrustForwardedFor(true);
        props.setTrustedProxies(List.of(PROXY_IP));

        MockHttpServletRequest req = request(PROXY_IP);
        req.addHeader("X-Forwarded-For", STRANGER_IP + ", " + GATEWAY_IP);

        assertThat(run(req).status()).isEqualTo(403);
    }

    @Test
    @DisplayName("10c: a header of only separators falls back to the peer address, not to allow-all")
    void malformedHeaderFallsBackToPeer() throws Exception {
        props.setAllowedIps(List.of(PROXY_IP));
        props.setTrustForwardedFor(true);
        props.setTrustedProxies(List.of(PROXY_IP));

        MockHttpServletRequest req = request(PROXY_IP);
        req.addHeader("X-Forwarded-For", " , , ");

        assertThat(run(req).passed()).as("falls back to the trusted peer, which is allow-listed").isTrue();
    }

    @Test
    @DisplayName("trust-forwarded-for=false ignores the header even from a trusted proxy")
    void trustForwardedForOffIgnoresHeader() throws Exception {
        props.setAllowedIps(List.of(GATEWAY_IP));
        props.setTrustForwardedFor(false);
        props.setTrustedProxies(List.of(PROXY_IP));

        MockHttpServletRequest req = request(PROXY_IP);
        req.addHeader("X-Forwarded-For", GATEWAY_IP);

        assertThat(run(req).status()).isEqualTo(403);
    }

    // ── CIDR ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("a CIDR block admits addresses inside it and rejects those outside")
    void cidrMatching() throws Exception {
        props.setAllowedIps(List.of("203.0.113.0/24"));

        assertThat(run(request("203.0.113.1")).passed()).isTrue();
        assertThat(run(request("203.0.113.254")).passed()).isTrue();
        assertThat(run(request("203.0.114.1")).status()).isEqualTo(403);
    }

    @Test
    @DisplayName("an IPv4 rule never matches an IPv6 address")
    void ipv4RuleDoesNotMatchIpv6() throws Exception {
        props.setAllowedIps(List.of("0.0.0.0/0"));   // every IPv4 address

        assertThat(run(request("::1")).status()).isEqualTo(403);
    }

    // ── 11. non-callback endpoints ──────────────────────────────────────────

    @Test
    @DisplayName("11: non-callback endpoints are unaffected, even from a blocked IP")
    void nonCallbackEndpointsUnaffected() throws Exception {
        props.setAllowedIps(List.of(GATEWAY_IP));

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/banking/accounts");
        req.setRequestURI("/api/v1/banking/accounts");
        req.setRemoteAddr(STRANGER_IP);

        assertThat(run(req).passed()).isTrue();
    }

    @Test
    @DisplayName("11b: non-callback endpoints are unaffected even when the allow-list is empty")
    void nonCallbackUnaffectedByFailClosed() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        req.setRequestURI("/api/v1/auth/me");
        req.setRemoteAddr(STRANGER_IP);

        assertThat(run(req).passed()).isTrue();
    }

    // ── 12. explicit local/mock opt-out ─────────────────────────────────────

    @Test
    @DisplayName("12: protection=DISABLED is an explicit, documented opt-out that allows any source")
    void disabledProtectionAllowsAny() throws Exception {
        props.setProtection(CallbackProperties.Protection.DISABLED);
        props.setAllowedIps(List.of());

        assertThat(run(request(STRANGER_IP)).passed()).isTrue();
    }

    @Test
    @DisplayName("12b: DISABLED must be written explicitly — it is never the default")
    void disabledIsNeverTheDefault() {
        assertThat(new CallbackProperties().getProtection())
                .as("a config that forgets `protection` must ENFORCE, not allow-all")
                .isEqualTo(CallbackProperties.Protection.ENFORCE);
    }

    // ── 13. deployed profiles cannot default to allow-all ───────────────────

    @Test
    @DisplayName("13: the default object + an unset allow-list can never produce allow-all")
    void deployedDefaultsCannotAllowAll() throws Exception {
        // Exactly what a sit/uat/prod node looks like if WECMS_CALLBACK_ALLOWED_IPS resolved empty:
        // ENFORCE (yaml default) + empty list + no trusted proxies.
        CallbackProperties deployed = new CallbackProperties();
        deployed.validate();

        assertThat(deployed.getProtection()).isEqualTo(CallbackProperties.Protection.ENFORCE);
        assertThat(deployed.allowList().isEmpty()).isTrue();
        assertThat(deployed.trustedProxyList().isEmpty()).isTrue();

        props = deployed;
        assertThat(run(request(GATEWAY_IP)).status()).isEqualTo(403);
    }
}
