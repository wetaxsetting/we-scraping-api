package com.kosign.wecms.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosign.wecms.common.exception.TraceId;
import com.kosign.wecms.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Returns HTTP 401 Unauthorized with the standard {@link ApiResponse}
 * envelope when an authenticated request reaches the security chain
 * without a valid SecurityContext. This is the typical aftermath of
 * {@link JwtAuthenticationFilter} rejecting an expired / forged Bearer
 * token: the filter clears the context and lets the request continue,
 * the request hits an authenticated matcher, and Spring Security's
 * default {@code Http403ForbiddenEntryPoint} would return 403. That
 * confuses every REST-shaped client (RFC 7235 reserves 401 for
 * "credentials missing or invalid" and 403 for "authenticated but
 * forbidden"), and specifically breaks the frontend's session-expired
 * popup, which watches for 401 to surface the re-auth dialog.
 *
 * Body matches the {@code ApiResponse} contract (carrying {@code path}
 * + {@code traceId} alongside {@code status}) so the Next.js client can
 * read {@code status.code} / {@code status.message} the same way as for
 * any other error response.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {
        String traceId = TraceId.current();
        log.warn("Auth entry-point reject {} {} ({})", request.getMethod(), request.getRequestURI(), authException.getMessage());
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ApiResponse<Void> body = ApiResponse.error(
                "WBE1009",
                "Session expired or invalid token.",
                request.getRequestURI(),
                traceId);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
