package com.kosign.wecms.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

import java.time.Instant;

/**
 * Standard envelope for every endpoint:
 * <pre>{@code
 * {
 *   "status":    { "error": false, "code": "WBI0000", "message": "Success" },
 *   "data":      { ... },
 *   "timestamp": "2026-05-08T14:46:49Z",
 *   "path":      "/api/v1/auth/login",
 *   "trace_id":  "20260508144649PT06592776769567DEVL"
 * }
 * }</pre>
 *
 * <p>{@code timestamp} / {@code path} / {@code traceId} are populated
 * by {@code GlobalExceptionHandler} on error responses so the frontend
 * can render them in the error dialog and ops can grep server logs by
 * the same traceId. Success responses set them to {@code null} and
 * Jackson's {@code NON_NULL} drops them from the wire — successes stay
 * a clean {@code { status, data }} envelope.
 *
 * <p>Field names serialize as lowercase snake_case via the global
 * Jackson naming strategy ({@link com.kosign.wecms.config.JacksonConfig});
 * no per-field {@code @JsonProperty} required.
 */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final CommonHead status;

    private final T data;

    private final Instant timestamp;

    private final String path;

    private final String traceId;

    private ApiResponse(CommonHead status, T data, Instant timestamp, String path, String traceId) {
        this.status    = status;
        this.data      = data;
        this.timestamp = timestamp;
        this.path      = path;
        this.traceId   = traceId;
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(CommonHead.success(), data, null, null, null);
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(CommonHead.success(message), data, null, null, null);
    }

    public static ApiResponse<Void> error(String code, String message) {
        return new ApiResponse<>(CommonHead.error(code, message), null, null, null, null);
    }

    /**
     * Error envelope including the request-scoped tracing metadata
     * ({@code timestamp}, {@code path}, {@code traceId}) — produced by
     * {@code GlobalExceptionHandler} so the frontend can surface a
     * traceable identifier in its error dialog.
     */
    public static ApiResponse<Void> error(String code, String message, String path, String traceId) {
        return new ApiResponse<>(CommonHead.error(code, message), null, Instant.now(), path, traceId);
    }
}
