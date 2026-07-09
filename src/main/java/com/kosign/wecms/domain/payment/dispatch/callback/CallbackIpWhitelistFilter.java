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
 * {@link CallbackProperties#getAllowedIps() source-IP allow-list} for requests under
 * {@link CallbackProperties#getPathPrefix()}; all other requests pass straight
 * through. Payload authentication (VV/EV crypto) is done separately in the callback
 * services — this filter is defence-in-depth, the refactor counterpart of the legacy
 * firewall / {@code x-coocon-accessIp} restriction (B7).
 *
 * <p>When the allow-list is empty the filter is a no-op (rely on VV crypto) — that is
 * the R&D default until the gateway IPs are provisioned. Not a Spring {@code @Component}
 * (it is wired explicitly into the security chain) so it never double-registers as a
 * servlet filter for every request.
 */
@Slf4j
@RequiredArgsConstructor
public class CallbackIpWhitelistFilter extends OncePerRequestFilter {

    private final CallbackProperties props;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String uri = request.getRequestURI();
        if (uri == null || !uri.startsWith(props.getPathPrefix()) || props.getAllowedIps().isEmpty()) {
            chain.doFilter(request, response);
            return;
        }

        String clientIp = resolveClientIp(request);
        if (props.getAllowedIps().contains(clientIp)) {
            chain.doFilter(request, response);
            return;
        }

        log.warn("Callback rejected — source IP {} not in allow-list (uri={})", clientIp, uri);
        response.sendError(HttpServletResponse.SC_FORBIDDEN, "callback source not allowed");
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (props.isTrustForwardedFor()) {
            String xff = request.getHeader("X-Forwarded-For");
            if (StringUtils.hasText(xff)) {
                return xff.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
