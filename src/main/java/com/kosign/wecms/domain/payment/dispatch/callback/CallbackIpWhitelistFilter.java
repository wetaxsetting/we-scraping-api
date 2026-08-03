package com.kosign.wecms.domain.payment.dispatch.callback;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Network-layer gate for the inbound EFS callbacks (01301 / 01302). Enforces the
 * {@link CallbackProperties#allowList() source-IP allow-list} for requests under the configured
 * {@code pathPrefix}; every other request passes straight through untouched.
 *
 * <p>This is <b>defence-in-depth, not authentication</b>. The callbacks authenticate by the per-VAN
 * HMAC ({@code VV}) verified inside the callback services — that is the legacy gate
 * ({@code FILE_DEPOSIT_TRANSFER_API1_act.jsp:102-109}) and it is unchanged. See
 * {@link CallbackProperties} for why the allow-list is a deployment-security control rather than a
 * legacy port.
 *
 * <h2>H-1 — fail closed</h2>
 * <b>Previous behaviour:</b> an empty allow-list short-circuited the filter and every request was
 * allowed. A protected environment that forgot {@code WECMS_CALLBACK_ALLOWED_IPS} therefore
 * accepted callbacks from any source while appearing protected.
 * <b>Now:</b> under {@link CallbackProperties.Protection#ENFORCE} (the default) an empty or
 * non-matching list rejects with 403. Opting out is explicit ({@code protection: DISABLED}) and is
 * logged at startup.
 *
 * <h2>Forwarded headers</h2>
 * {@code X-Forwarded-For} is honoured <b>only</b> when {@code trustForwardedFor} is on <i>and</i>
 * the immediate peer is itself listed in {@code trustedProxies}. Without that second condition any
 * client could set the header and choose its own apparent source IP — which is what made the old
 * hardcoded {@code trust-forwarded-for: true} unsafe. When the peer is untrusted the header is
 * ignored entirely and the direct peer address is matched instead.
 *
 * <p>Per RFC 7239 / de-facto convention the header is a left-to-right chain
 * {@code client, proxy1, proxy2}; the <b>left-most</b> entry is the originating client, so that is
 * what is matched. Malformed or empty entries fall back to the peer address rather than failing open.
 *
 * <p>Not a Spring {@code @Component} — it is wired explicitly into the security chain so it never
 * double-registers as a servlet filter for every request.
 */
@Slf4j
@RequiredArgsConstructor
public class CallbackIpWhitelistFilter extends OncePerRequestFilter {

    private static final String XFF = "X-Forwarded-For";

    private final CallbackProperties props;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String uri = request.getRequestURI();
        if (uri == null || !uri.startsWith(props.getPathPrefix())) {
            chain.doFilter(request, response);   // not a callback — untouched
            return;
        }
        if (props.getProtection() == CallbackProperties.Protection.DISABLED) {
            chain.doFilter(request, response);   // explicit, startup-logged opt-out
            return;
        }

        String peer = request.getRemoteAddr();
        IpAllowList allowList = props.allowList();

        // FAIL CLOSED: an unconfigured allow-list denies. Previously this branch allowed everything.
        if (allowList.isEmpty()) {
            log.warn("Callback rejected — allow-list is empty and protection=ENFORCE (peer={} uri={}). "
                    + "Configure wecms.dispatch.callback.allowed-ips.", peer, uri);
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "callback source not allowed");
            return;
        }

        String clientIp = resolveClientIp(request, peer);
        if (allowList.matches(clientIp)) {
            chain.doFilter(request, response);
            return;
        }

        // Log the decision inputs only — never headers wholesale and never the payload.
        log.warn("Callback rejected — source IP {} not in allow-list (peer={} uri={})", clientIp, peer, uri);
        response.sendError(HttpServletResponse.SC_FORBIDDEN, "callback source not allowed");
    }

    /**
     * The IP the allow-list is matched against.
     *
     * @return the left-most {@code X-Forwarded-For} entry when the peer is a trusted proxy and the
     *         entry is usable; otherwise the direct peer address
     */
    private String resolveClientIp(HttpServletRequest request, String peer) {
        if (!props.isTrustForwardedFor()) {
            return peer;
        }
        if (!props.trustedProxyList().matches(peer)) {
            // Untrusted (or unknown) peer: a forwarded header here is unverifiable and must not
            // influence the decision. Ignoring it means a forged header simply has no effect.
            if (StringUtils.hasText(request.getHeader(XFF))) {
                log.debug("Ignoring {} from untrusted peer {} — not in trusted-proxies", XFF, peer);
            }
            return peer;
        }
        String forwarded = request.getHeader(XFF);
        if (!StringUtils.hasText(forwarded)) {
            return peer;
        }
        // "client, proxy1, proxy2" — left-most is the originating client.
        for (String hop : forwarded.split(",")) {
            String candidate = hop.trim();
            if (!candidate.isEmpty()) {
                return candidate;
            }
        }
        return peer;   // header present but only separators/whitespace
    }
}
