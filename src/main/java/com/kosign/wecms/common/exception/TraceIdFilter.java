package com.kosign.wecms.common.exception;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Establishes a per-request {@link TraceId} early in the filter chain so
 * every log line emitted during the request — including those from
 * {@code JwtAuthenticationFilter}, controllers, services, and
 * {@code GlobalExceptionHandler} — carries the same identifier in MDC.
 *
 * <p>Also echoes the id back to the client in the {@code X-Trace-Id}
 * response header so a curl / browser-devtools user can correlate
 * without inspecting the JSON body.
 *
 * <p>Runs at {@link Ordered#HIGHEST_PRECEDENCE} so the id is set before
 * Spring Security's chain runs — auth failures hit
 * {@code JwtAuthenticationEntryPoint} with the traceId already in MDC.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Trace-Id";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {
        String traceId = TraceId.generate();
        MDC.put(TraceId.MDC_KEY, traceId);
        response.setHeader(HEADER_NAME, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(TraceId.MDC_KEY);
        }
    }
}
