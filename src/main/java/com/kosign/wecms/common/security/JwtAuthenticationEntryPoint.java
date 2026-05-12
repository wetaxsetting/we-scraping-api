package com.kosign.wecms.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosign.wecms.common.exception.TraceId;
import com.kosign.wecms.common.i18n.MessageResolver;
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

    /**
     * Dedicated session-expired code (legacy {@code SES001} parity per
     * {@code WECMS DEV/.../jex.wabooks.custom_v2.js:107}). The frontend
     * Axios interceptor watches the HTTP 401 status to surface the
     * re-auth popup; the typed code disambiguates the response from
     * {@code WBE1009} (bad credentials, returned by {@link com.kosign.wecms.domain.auth.service.AuthService}
     * on a wrong-password attempt) so future logic can branch on
     * "credentials wrong" vs "session no longer valid" without parsing
     * messages.
     */
    private static final String CODE_SESSION_EXPIRED = "WBE1401";

    private final ObjectMapper objectMapper;
    private final MessageResolver messages;

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
                CODE_SESSION_EXPIRED,
                messages.resolve(CODE_SESSION_EXPIRED),
                request.getRequestURI(),
                traceId);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
