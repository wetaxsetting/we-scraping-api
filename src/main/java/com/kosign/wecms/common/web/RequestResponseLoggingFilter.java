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
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

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

    /**
     * F-06: redact values of sensitive JSON fields before any body is logged, so
     * the DEBUG request/response log (active under the {@code local} profile)
     * never captures passwords, tokens, or bank/GDT credential ciphertext. Matches
     * the field NAME only and replaces its string value with {@code "***"}.
     */
    private static final Pattern SENSITIVE_FIELD = Pattern.compile(
            "(?i)(\"(?:user_pw|password|passwd|pwd|user_password|new_password"
          + "|current_password|old_password|access_token|refresh_token|token"
          + "|secret|authorization|cert_info_detl_con\\d?)\"\\s*:\\s*)\"[^\"]*\"");

    /**
     * Content types whose response body is binary and must never be
     * stringified into the log. The XLSX MIME type is the motivating case:
     * {@code application/vnd.openxmlformats-officedocument.spreadsheetml.sheet}
     * contains the substring {@code "xml"} (inside "open<b>xml</b>formats"),
     * so the generic {@link #isTextual} heuristic wrongly treated it as text
     * and dumped the ZIP body ({@code PK ... xl/workbook.xml ...}) to the log.
     * This explicit set is checked first. {@code image/*} is matched by prefix.
     */
    private static final Set<String> BINARY_CONTENT_TYPES = Set.of(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-excel",
            "application/octet-stream",
            "application/pdf"
    );

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
        String contentType = res.getContentType();

        // Binary / download responses (XLSX export, PDF, octet-stream, images)
        // must never be stringified — dumping a ZIP/PDF body produces noise like
        // `PK ... xl/workbook.xml ...` in the log. Log only the metadata.
        if (isBinaryDownload(contentType)) {
            log.debug("\n<-- {} ({} ms) content-type={} size={} bytes\n[binary response body omitted]\n",
                    res.getStatus(),
                    tookMs,
                    contentType,
                    res.getContentAsByteArray().length);
            return;
        }

        // Jackson writes JSON responses as UTF-8 regardless of what the
        // servlet response advertises via getCharacterEncoding() — which
        // defaults to ISO-8859-1 per the Servlet API. Force UTF-8 for
        // JSON bodies so the log doesn't mojibake non-ASCII payloads
        // (e.g. Korean error messages from the NXISAS scraping gateway).
        // The actual response bytes on the wire are unaffected; this
        // only fixes the log rendering.
        String encoding = isJson(contentType)
                ? StandardCharsets.UTF_8.name()
                : res.getCharacterEncoding();
        String body = readBody(res.getContentAsByteArray(), encoding, contentType);
        log.debug("\n<-- {} ({} ms)\n{}\n", res.getStatus(), tookMs, body);
    }

    /**
     * True when the response content type denotes a binary download whose body
     * must not be logged. Matches the explicit {@link #BINARY_CONTENT_TYPES}
     * set plus any {@code image/*} type. Parameters (e.g. {@code ;charset=…})
     * are stripped before comparison.
     */
    private boolean isBinaryDownload(String contentType) {
        if (contentType == null) return false;
        String ct = contentType.toLowerCase(Locale.ROOT);
        int semi = ct.indexOf(';');
        if (semi >= 0) ct = ct.substring(0, semi);
        ct = ct.trim();
        return ct.startsWith("image/") || BINARY_CONTENT_TYPES.contains(ct);
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
            text = SENSITIVE_FIELD.matcher(text).replaceAll("$1\"***\"");
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
