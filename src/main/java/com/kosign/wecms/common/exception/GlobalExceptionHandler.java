package com.kosign.wecms.common.exception;

import com.kosign.wecms.common.i18n.MessageResolver;
import com.kosign.wecms.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.concurrent.TimeoutException;

/**
 * Single funnel for every exception that propagates out of a controller.
 * Each handler:
 * <ul>
 *   <li>Resolves the user-facing message via {@link MessageResolver}
 *       so the user gets it in their accept-language locale.</li>
 *   <li>Wraps the response in the standard {@link ApiResponse} envelope
 *       with {@code timestamp} / {@code path} / {@code traceId} populated
 *       — the {@code traceId} is the same value that
 *       {@link TraceIdFilter} placed in MDC on entry, so a user reading
 *       the id from the error dialog can grep it directly against
 *       server logs.</li>
 *   <li>Logs at the appropriate level — {@code warn} for known business
 *       paths (don't spam {@code error} for "wrong password"),
 *       {@code error} with stack trace for unexpected exceptions.</li>
 *   <li>Returns the canonical HTTP status for the failure category —
 *       business errors stay HTTP 200 so the frontend's existing
 *       envelope-driven flow continues to work; client mistakes use
 *       4xx; server faults use 5xx.</li>
 * </ul>
 *
 * <p>The {@code BusinessException} → HTTP 200 contract is documented in
 * {@code wecms-api-main/CLAUDE.md} ("Response envelope") and is
 * preserved here intentionally — clients inspect {@code status.error},
 * not the HTTP code, for known business outcomes.
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final MessageResolver messages;

    // ---------- Business + validation (HTTP 200 envelope) ----------

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex, HttpServletRequest req) {
        String message = messages.resolve(ex.getCode(), ex.getArgs());
        log.warn("Business exception code={} message={}", ex.getCode(), message);
        return ResponseEntity.ok(buildError(ex.getCode(), message, req));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        String firstError = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .orElseGet(() -> messages.resolve("WBE_VALIDATION"));
        log.warn("Validation failed: {}", firstError);
        return ResponseEntity.ok(buildError("WBE_VALIDATION", firstError, req));
    }

    /** Path-variable / request-param validation failures (e.g. {@code @Pattern}
     *  on a {@code @PathVariable}) raise {@link ConstraintViolationException}
     *  rather than {@link MethodArgumentNotValidException}. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest req) {
        String firstError = ex.getConstraintViolations().stream()
                .findFirst()
                .map(cv -> cv.getPropertyPath() + ": " + cv.getMessage())
                .orElseGet(() -> messages.resolve("WBE_VALIDATION"));
        log.warn("Constraint violation: {}", firstError);
        return ResponseEntity.ok(buildError("WBE_VALIDATION", firstError, req));
    }

    /** A required {@code @RequestParam} omitted before bean validation can fire. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException ex, HttpServletRequest req) {
        String message = ex.getParameterName() + ": is required";
        log.warn("Missing parameter: {}", message);
        return ResponseEntity.ok(buildError("WBE_VALIDATION", message, req));
    }

    /** A {@code @RequestParam} present but unconvertible to the target type. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        String message = ex.getName() + ": invalid value";
        log.warn("Type mismatch: {}", message);
        return ResponseEntity.ok(buildError("WBE_VALIDATION", message, req));
    }

    // ---------- Client mistakes (HTTP 4xx) ----------

    /** Malformed JSON body — Jackson can't parse the request. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMessageNotReadable(HttpMessageNotReadableException ex, HttpServletRequest req) {
        String detail = ex.getMostSpecificCause().getMessage();
        log.warn("Malformed request body: {}", detail);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildError("WBE_BAD_REQUEST", messages.resolve("WBE_BAD_REQUEST"), req));
    }

    /** Wrong HTTP method (POST against a GET-only endpoint, etc.). */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest req) {
        log.warn("Method not allowed: {} {}", ex.getMethod(), req.getRequestURI());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(buildError("WBE_METHOD_NOT_ALLOWED", messages.resolve("WBE_METHOD_NOT_ALLOWED"), req));
    }

    /** Wrong / missing {@code Content-Type}. */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex, HttpServletRequest req) {
        log.warn("Unsupported media type: {}", ex.getContentType());
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(buildError("WBE_UNSUPPORTED_MEDIA_TYPE", messages.resolve("WBE_UNSUPPORTED_MEDIA_TYPE"), req));
    }

    /** No handler matched the request URI (Spring 6.x: thrown when
     *  {@code spring.mvc.throw-exception-if-no-handler-found} is on, OR when a
     *  {@code @RequestMapping} doesn't cover the path). */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NoResourceFoundException ex, HttpServletRequest req) {
        log.warn("No handler for {}", req.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(buildError("WBE_NOT_FOUND", messages.resolve("WBE_NOT_FOUND"), req));
    }

    /** Bad call into a service / helper — null where a value is required, etc.
     *  Treat as a client problem until the controller proves otherwise; the
     *  fallback {@link #handleUnexpected} catches the genuinely-server-side
     *  cases. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest req) {
        log.warn("Illegal argument: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildError("WBE_VALIDATION", ex.getMessage() != null ? ex.getMessage() : messages.resolve("WBE_VALIDATION"), req));
    }

    // ---------- Security (HTTP 401 / 403) ----------

    /** Spring Security pre-auth failures that aren't already routed through
     *  {@code JwtAuthenticationEntryPoint} (e.g. an authenticated request with
     *  a permission-violation downstream). Emits {@code WBE1401} (session
     *  expired) — same code as the entry point so the frontend's 401 →
     *  re-auth popup has a single canonical signal. {@code WBE1009} stays
     *  reserved for wrong-credentials at login. */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex, HttpServletRequest req) {
        log.warn("Authentication failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(buildError("WBE1401", messages.resolve("WBE1401"), req));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
        log.warn("Access denied for {} {}", req.getMethod(), req.getRequestURI());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(buildError("WBE1003", messages.resolve("WBE1003"), req));
    }

    // ---------- Server faults (HTTP 5xx) ----------

    /** Persistence layer faults — connection failures, constraint violations
     *  reaching the controller, query timeouts (when not separately wrapped). */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataAccess(DataAccessException ex, HttpServletRequest req) {
        log.error("Database error on {} {}", req.getMethod(), req.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildError("WBE_DB", messages.resolve("WBE_DB"), req));
    }

    /** Async / external-call timeouts. Caller-facing message stays generic
     *  so we don't leak internal hostnames or operation timings. */
    @ExceptionHandler(TimeoutException.class)
    public ResponseEntity<ApiResponse<Void>> handleTimeout(TimeoutException ex, HttpServletRequest req) {
        log.error("Timeout on {} {}", req.getMethod(), req.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                .body(buildError("WBE_TIMEOUT", messages.resolve("WBE_TIMEOUT"), req));
    }

    /** The genuine catch-all. {@link NullPointerException} and any other
     *  unmapped {@link RuntimeException} land here. Logs the full stack
     *  trace at error level — the {@code traceId} in MDC ties the log
     *  entry to the user-visible id in the error dialog. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex, HttpServletRequest req) {
        log.error("Unhandled exception on {} {}", req.getMethod(), req.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildError("WBE9999", messages.resolve("WBE9999"), req));
    }

    private ApiResponse<Void> buildError(String code, String message, HttpServletRequest req) {
        return ApiResponse.error(code, message, req.getRequestURI(), TraceId.current());
    }
}
