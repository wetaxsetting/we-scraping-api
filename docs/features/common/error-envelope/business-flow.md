# Business Flow — Request Lifecycle Through the Error Envelope

**Part of:** Common → error-envelope
**Last verified:** 2026-06-23

This module has no business flow of its own — it is the chassis every request rides. The flow below
is the lifecycle of **any** `/api/v1/**` call as it passes through the trace-id filter, the controller,
and either the success path or the global exception funnel.

## 1. Request entry — `TraceIdFilter`

`TraceIdFilter` runs at `Ordered.HIGHEST_PRECEDENCE` (`TraceIdFilter.java:30`), so it executes
**before** Spring Security's chain — auth failures already carry the trace id.

1. Generate a JEX-style trace id (`TraceId.generate()`, `TraceId.java:33-39`):
   `yyyyMMddHHmmss` + `"PT"` + 14 `SecureRandom` digits + `"DEVL"`.
   Example: `20260508144649PT06592776769567DEVL`.
2. Put it in SLF4J `MDC` under key `traceId` (`TraceIdFilter.java:42`; `TraceId.MDC_KEY`, `TraceId.java:24`).
3. Echo it back in the `X-Trace-Id` response header (`TraceIdFilter.java:43`; `HEADER_NAME`, `TraceIdFilter.java:33`).
4. `chain.doFilter(...)`, then **always** `MDC.remove(...)` in `finally` (`TraceIdFilter.java:44-48`) so the id never leaks across pooled threads.

Every log line emitted during the request carries the id via the `%X{traceId}` MDC pattern in
`application.yml` — so a user reading the id from the error dialog can grep server logs by it.

## 2a. Success path

```
Controller method
  → service does work
  → return ApiResponse.ok(data)          // CommonHead.success() = {error:false, code:"WBI0000", message:"Success"}
```

`ApiResponse.ok(T)` (`ApiResponse.java:53-55`) constructs `{ status, data, timestamp:null, path:null, traceId:null }`.
`@JsonInclude(NON_NULL)` (`ApiResponse.java:32`) drops the three null tracing fields, so a success serializes
to a clean `{ status, data }`. The `X-Trace-Id` **header** is still present (set in step 1) even though the
body omits `trace_id`.

## 2b. Error path — `BusinessException` (the common case)

```
Service detects a known business condition
  → throw new BusinessException("WBExxxx", args…)        // BusinessException.java:15
  → propagates out of the controller
  → @ExceptionHandler(BusinessException.class)           // GlobalExceptionHandler.java:61
       message = messages.resolve(code, args)            // localized via Accept-Language
       log.warn(...)                                     // warn, not error — known path
       return ResponseEntity.ok(buildError(code, message, req))   // HTTP 200
```

`buildError(...)` (`GlobalExceptionHandler.java:208-210`) calls
`ApiResponse.error(code, message, req.getRequestURI(), TraceId.current())` (`ApiResponse.java:71-73`),
which sets `timestamp = Instant.now()`, `path`, and `traceId` — so the error body carries all three.
`TraceId.current()` (`TraceId.java:43-46`) reads the same MDC id the filter set, falling back to a fresh
id only outside an HTTP request (e.g. unit tests).

**HTTP 200 for a business error is intentional** — the frontend reads `status.error`, not the HTTP code
(`GlobalExceptionHandler.java:47-50`).

## 2c. Error path — framework / unexpected

Validation, client mistakes, security, persistence, timeout, and the catch-all are all handled by their
own `@ExceptionHandler` methods in `GlobalExceptionHandler`, each picking a category-appropriate HTTP
status and code (full table in [interface-specification.md](interface-specification.md)). Two log levels:
`warn` for known/4xx paths, `error` with stack trace for 5xx faults (`GlobalExceptionHandler.java:183,192,203`).

> JWT 401 is the one exception that does **not** pass through `GlobalExceptionHandler` — it fires at
> `JwtAuthenticationEntryPoint` *before* the controller chain, so `@RestControllerAdvice` never sees it.
> It uses the same envelope shape and reads the same MDC trace id.

## 3. Cross-cutting state during the request

- **TenantContext** (`TenantContext.java`) is a `ThreadLocal<Principal>` populated by
  `JwtAuthenticationFilter` from the signed JWT (`userId`, `userNm`, `bizKey`, `langCd`, `authGrCd`,
  `prdtType`) and cleared at request end. Services read tenant/locale/product-type from here, never
  from a request body.
- **Audit columns** — when a service persists an entity extending `BaseEntity`, `@PrePersist` /
  `@PreUpdate` (`BaseEntity.java:36-46`) stamp `REG_DTM` / `MOD_DTM`; `LegacyDtmConverter` writes them
  as legacy `varchar(14)` UTC strings.
- **One-shot retry** — key-allocation flows wrap the allocate+insert in `TransactionalRetry.runOnce(...)`
  (`TransactionalRetry.java:34-37`, `REQUIRES_NEW`) so a PK collision rolls back a poisoned session and
  the caller can retry once in a fresh transaction.

## Sequence (text)

```
HTTP request
  → [TraceIdFilter] generate id → MDC + X-Trace-Id header
    → [JwtAuthenticationFilter] populate TenantContext from JWT (or 401 via entry point)
      → [Controller] @RequestMapping handler
        → [Service] work + (optional) BaseEntity audit stamp / wb_get_next_val via TransactionalRetry
        ├─ success → ApiResponse.ok(data)             → 200 { status, data }
        └─ throw   → GlobalExceptionHandler            → { status, timestamp, path, trace_id } @ mapped status
  ← [TraceIdFilter finally] MDC.remove(traceId)
```
