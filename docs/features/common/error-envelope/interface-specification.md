# Interface Specification — Error Envelope

**Part of:** Common → error-envelope
**Last verified:** 2026-06-23

The contract **every** WeCMS endpoint returns. No endpoint of its own — this is the shared response
+ exception surface.

## 1. The envelope every endpoint returns

`ApiResponse<T>` (`common/response/ApiResponse.java`) + `CommonHead` (`common/response/CommonHead.java`).

| Field | Type (wire) | On success | On error |
|---|---|---|---|
| `status.error` | boolean | `false` | `true` |
| `status.code` | string | `WBI0000` | `WBExxxx` / `WBIxxxx` |
| `status.message` | string (localized) | `"Success"` or caller message | localized via `MessageResolver` |
| `data` | `T` \| absent | present | absent (`null`, dropped by `NON_NULL`) |
| `timestamp` | ISO-8601 UTC \| absent | absent | `Instant.now()` |
| `path` | string \| absent | absent | `req.getRequestURI()` |
| `trace_id` | string \| absent | absent (header still set) | JEX-style id from MDC |

Factories:

| Factory | Result | `ApiResponse.java` |
|---|---|---|
| `ApiResponse.ok(data)` | `{ status:success, data }` | `:53-55` |
| `ApiResponse.ok(message, data)` | success with custom message | `:57-59` |
| `ApiResponse.error(code, message)` | error, no tracing fields | `:61-63` |
| `ApiResponse.error(code, message, path, traceId)` | full error envelope | `:71-73` |

`GlobalExceptionHandler.buildError(...)` always uses the four-arg form so every handled error carries
`timestamp` + `path` + `trace_id` (`GlobalExceptionHandler.java:208-210`).

Response header (all responses): `X-Trace-Id: <id>` (`TraceIdFilter.java:43`).

## 2. Exception → HTTP status → code mapping

Single funnel: `@RestControllerAdvice GlobalExceptionHandler` (`GlobalExceptionHandler.java`). Verified
handler-by-handler against the source.

| Exception | HTTP | Code | Handler | Log level |
|---|---|---|---|---|
| `BusinessException` | **200** | `ex.getCode()` | `:61-66` | warn |
| `MethodArgumentNotValidException` | 200 | `WBE_VALIDATION` | `:68-76` | warn |
| `ConstraintViolationException` | 200 | `WBE_VALIDATION` | `:81-89` | warn |
| `MissingServletRequestParameterException` | 200 | `WBE_VALIDATION` | `:92-97` | warn |
| `MethodArgumentTypeMismatchException` | 200 | `WBE_VALIDATION` | `:100-105` | warn |
| `HttpMessageNotReadableException` | **400** | `WBE_BAD_REQUEST` | `:110-116` | warn |
| `IllegalArgumentException` | **400** | `WBE_VALIDATION` | `:148-153` | warn |
| `HttpRequestMethodNotSupportedException` | **405** | `WBE_METHOD_NOT_ALLOWED` | `:119-124` | warn |
| `HttpMediaTypeNotSupportedException` | **415** | `WBE_UNSUPPORTED_MEDIA_TYPE` | `:127-132` | warn |
| `NoResourceFoundException` | **404** | `WBE_NOT_FOUND` | `:137-142` | warn |
| `AuthenticationException` | **401** | `WBE1401` ⚠️ | `:163-168` | warn |
| `AccessDeniedException` | **403** | `WBE1003` | `:170-175` | warn |
| `DataAccessException` | **500** | `WBE_DB` | `:181-186` | error + stack |
| `TimeoutException` | **504** | `WBE_TIMEOUT` | `:190-195` | error + stack |
| `Exception` (catch-all) | **500** | `WBE9999` | `:201-206` | error + stack |

> ⚠️ **Code drift vs. CLAUDE.md.** `wecms-api/CLAUDE.md` §"Exception → response mapping" lists
> `AuthenticationException → WBE1009`. The **current code emits `WBE1401`** (session-expired —
> `GlobalExceptionHandler.java:167`, with an explicit comment that `WBE1009` stays reserved for
> wrong-credentials at login). The code is the source of truth: the mapping is **`WBE1401`**. The CLAUDE.md
> table is stale on this single row — flag for correction, do not propagate `WBE1009` here.

### JWT 401 — separate path

A request with a missing/invalid JWT is rejected at `JwtAuthenticationEntryPoint`, which fires **before**
the controller chain, so `@RestControllerAdvice` never sees it. It emits the same envelope shape with
`status.code = WBE1009` (per CLAUDE.md §"JWT filter") and reads the MDC trace id so body + log correlate.
The frontend's Axios interceptor watches HTTP 401 to trigger the session-expired re-auth dialog.

## 3. Error-code registry (envelope-level codes)

These are the codes the envelope + global handler can emit. Feature-specific `WBExxxx` codes (approval-line,
chart-of-accounts, e-invoice, export, …) are **not** duplicated here — see the shared registry cross-link
below.

| Code | Meaning | HTTP |
|---|---|---|
| `WBI0000` | Success | 200 |
| `WBE1003` | Access denied (authenticated but unauthorised) | 403 |
| `WBE1009` | ID/password mismatch · user not in tenant · JWT 401 (entry point) | 200 / 401 |
| `WBE1010` | Account not activated | 200 |
| `WBE1011` | Account locked | 200 |
| `WBE1128` | Account deleted | 200 |
| `WBE1401` | Session expired (`AuthenticationException` via global handler) | 401 |
| `WBE_VALIDATION` | Bean / constraint / param validation failure | 200 / 400 |
| `WBE_BAD_REQUEST` | Malformed JSON body | 400 |
| `WBE_METHOD_NOT_ALLOWED` | Wrong HTTP method | 405 |
| `WBE_UNSUPPORTED_MEDIA_TYPE` | Wrong/missing `Content-Type` | 415 |
| `WBE_NOT_FOUND` | No handler matched the URI | 404 |
| `WBE_DB` | Persistence-layer fault | 500 |
| `WBE_TIMEOUT` | Async / external timeout | 504 |
| `WBE9999` | Unexpected/unmapped server error | 500 |

> Every code must exist in `messages.properties` (+ `messages_en.properties`) before being thrown.
> New category → add the handler + the row above; never introduce a parallel envelope.

## 4. Adding a new error code (contract)

1. Add the WBE/WBI key + EN message to `messages.properties` (+ `messages_en.properties`).
2. `throw new BusinessException("WBExxxx", args…)` — never a raw string message
   (`BusinessException.java:15`).
3. If a brand-new category that `BusinessException` doesn't fit, add an `@ExceptionHandler` in
   `GlobalExceptionHandler` and the row to the table in §2. Do not introduce a second envelope shape.

## Cross-links

- **Full feature-code registry (do not duplicate here):** [`../../../../../docs/_shared/error-code-registry.md`](../../../../../docs/_shared/error-code-registry.md)
- Envelope shape + wire naming: [functional-specification.md](functional-specification.md)
- Request lifecycle: [business-flow.md](business-flow.md)
