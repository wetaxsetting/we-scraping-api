package com.kosign.wecms.common.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Logs every HTTP request + response (method, URI, query, status, duration,
 * and body) at DEBUG. Runs just after {@link com.kosign.wecms.common.exception.TraceIdFilter}
 * so each line carries the same {@code traceId} MDC value.
 *
 * <p>Body capture uses Spring's content-caching wrappers — required because
 * the raw servlet streams are one-shot. Binary responses (XLSX export,
 * file downloads) are not stringified; only their size is logged.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@Slf4j
public class RequestResponseLoggingFilter extends OncePerRequestFilter {

    private static final int MAX_BODY_CHARS = 8_000;
    private static final ObjectMapper PRETTY = new ObjectMapper();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {

        ContentCachingRequestWrapper req = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper res = new ContentCachingResponseWrapper(response);

        long start = System.currentTimeMillis();
        try {
            chain.doFilter(req, res);
        } finally {
            long took = System.currentTimeMillis() - start;
            try {
                logRequest(req);
                logResponse(res, took);
            } catch (Exception logEx) {
                log.warn("Failed to log request/response", logEx);
            }
            res.copyBodyToResponse();
        }
    }

    private void logRequest(ContentCachingRequestWrapper req) {
        String query = req.getQueryString();
        String body = readBody(req.getContentAsByteArray(), req.getCharacterEncoding(), req.getContentType());
        log.debug("\n--> {} {}{}\n{}\n",
                req.getMethod(),
                req.getRequestURI(),
                query != null ? "?" + query : "",
                body);
    }

    private void logResponse(ContentCachingResponseWrapper res, long tookMs) {
        String body = readBody(res.getContentAsByteArray(), res.getCharacterEncoding(), res.getContentType());
        log.debug("\n<-- {} ({} ms)\n{}\n", res.getStatus(), tookMs, body);
    }

    private String readBody(byte[] buf, String encoding, String contentType) {
        if (buf == null || buf.length == 0) {
            return "<empty>";
        }
        if (!isTextual(contentType)) {
            return "<binary " + buf.length + " bytes>";
        }
        try {
            String charset = encoding != null ? encoding : StandardCharsets.UTF_8.name();
            String text = new String(buf, charset);
            if (isJson(contentType)) {
                try {
                    text = PRETTY.writerWithDefaultPrettyPrinter()
                            .writeValueAsString(PRETTY.readTree(text));
                } catch (Exception ignored) {
                    // fall through with raw text
                }
            }
            return text.length() > MAX_BODY_CHARS
                    ? text.substring(0, MAX_BODY_CHARS) + "\n...<truncated " + (text.length() - MAX_BODY_CHARS) + " chars>"
                    : text;
        } catch (Exception e) {
            return "<unreadable " + buf.length + " bytes>";
        }
    }

    private boolean isJson(String contentType) {
        return contentType != null && contentType.toLowerCase().contains("json");
    }

    private boolean isTextual(String contentType) {
        if (contentType == null) return true;
        String ct = contentType.toLowerCase();
        return ct.contains("json")
                || ct.contains("xml")
                || ct.contains("text")
                || ct.contains("form-urlencoded")
                || ct.contains("javascript");
    }
}
