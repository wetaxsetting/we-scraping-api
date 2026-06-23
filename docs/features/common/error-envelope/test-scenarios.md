# Test Scenarios — Error Envelope

**Part of:** Common → error-envelope
**Last verified:** 2026-06-23

Scenario matrix for the envelope, the exception funnel, trace-id presence, and i18n locale selection.
Primary automated coverage: `GlobalExceptionHandlerTest`, `RelativeTimeFormatterTest`,
`LegacyHashPasswordEncoderTest`, `LogMaskTest` (see [source-code-index.md](source-code-index.md)).

## 1. Success envelope

| ID | Given | Expect |
|---|---|---|
| EE-S1 | Controller returns `ApiResponse.ok(dto)` | HTTP 200; body `{ status:{error:false, code:"WBI0000", message:"Success"}, data:{…} }`; **no** `timestamp`/`path`/`trace_id` in body (NON_NULL dropped) |
| EE-S2 | `ApiResponse.ok("Custom", dto)` | `status.message == "Custom"`, code still `WBI0000` |
| EE-S3 | Any success | `X-Trace-Id` response **header** present even though `trace_id` body field absent |

## 2. Exception → envelope + status (per handler)

Each row: throw the exception from a test controller, assert the mapped HTTP status, `status.error==true`,
`status.code`, and presence of `timestamp`/`path`/`trace_id`.

| ID | Exception thrown | HTTP | `status.code` | Log level |
|---|---|---|---|---|
| EE-E1 | `BusinessException("WBE1009")` | **200** | `WBE1009` | warn |
| EE-E2 | `@Valid` body fails (`MethodArgumentNotValidException`) | 200 | `WBE_VALIDATION` | warn |
| EE-E3 | `@Validated` path/param (`ConstraintViolationException`) | 200 | `WBE_VALIDATION` | warn |
| EE-E4 | missing `@RequestParam` | 200 | `WBE_VALIDATION` | warn |
| EE-E5 | param type mismatch | 200 | `WBE_VALIDATION` | warn |
| EE-E6 | malformed JSON body | **400** | `WBE_BAD_REQUEST` | warn |
| EE-E7 | `IllegalArgumentException` | **400** | `WBE_VALIDATION` | warn |
| EE-E8 | wrong HTTP method | **405** | `WBE_METHOD_NOT_ALLOWED` | warn |
| EE-E9 | wrong `Content-Type` | **415** | `WBE_UNSUPPORTED_MEDIA_TYPE` | warn |
| EE-E10 | no handler for URI | **404** | `WBE_NOT_FOUND` | warn |
| EE-E11 | `AuthenticationException` | **401** | **`WBE1401`** ⚠️ (code value — not `WBE1009`; see [interface-specification.md](interface-specification.md) §2) | warn |
| EE-E12 | `AccessDeniedException` | **403** | `WBE1003` | warn |
| EE-E13 | `DataAccessException` | **500** | `WBE_DB` | error + stack |
| EE-E14 | `TimeoutException` | **504** | `WBE_TIMEOUT` | error + stack |
| EE-E15 | unmapped `RuntimeException` (e.g. NPE) | **500** | `WBE9999` | error + stack |

> EE-E11 is the regression guard for the `WBE1401` vs `WBE1009` drift — assert the **code** is `WBE1401`
> so a future "fix" back to `WBE1009` fails the test.

## 3. Error envelope content

| ID | Assert |
|---|---|
| EE-C1 | Every error body carries `timestamp` (ISO-8601 UTC), `path` (== request URI), `trace_id` |
| EE-C2 | `trace_id` matches `^\d{14}PT\d{14}DEVL$` (format from `TraceId.generate()`) |
| EE-C3 | Body `trace_id` == `X-Trace-Id` response header == the MDC value in the matching log line |
| EE-C4 | `status.message` is **localized** (no stack trace, no internal hostname leaked) |
| EE-C5 | `WBE9999` and other 5xx log the full stack at `error` level with the request-scoped trace id |

## 4. Trace-id propagation

| ID | Given | Expect |
|---|---|---|
| EE-T1 | Request enters `TraceIdFilter` | MDC `traceId` set before Security chain; `X-Trace-Id` header set |
| EE-T2 | Request completes (success or error) | `MDC.remove("traceId")` ran in `finally` — no leak to the next pooled-thread request |
| EE-T3 | `TraceId.current()` outside an HTTP request (unit test) | Generates a fresh id rather than returning null |
| EE-T4 | JWT 401 (entry point) | Envelope `WBE1009`; body `trace_id` == the same MDC id (entry point reads MDC) |

## 5. i18n locale selection

| ID | Given | Expect |
|---|---|---|
| EE-L1 | `Accept-Language: ko` → JWT `langCd=KO` | `MessageResolver.resolve("WBE1009")` returns the KO message (or code-fallback if `messages_ko.properties` absent) |
| EE-L2 | `Accept-Language: vi` | VI message (or fallback) |
| EE-L3 | `Accept-Language: km` | KM resolution attempted; **PARTIAL** — no `messages_km.properties`, so falls back to code/EN |
| EE-L4 | unknown / missing `Accept-Language` | defaults to `EN` |
| EE-L5 | bundle has no key for the code | `resolve(...)` returns the **code string itself** (`MessageResolver.java:22`) — never a blank message |
| EE-L6 | `MessageResolver.resolve(code, Locale, args)` (explicit) | resolves in the passed locale ignoring request scope (audit fan-out path) |

## 6. RelativeTimeFormatter (`RelativeTimeFormatterTest`)

| ID | Input | Expect |
|---|---|---|
| EE-R1 | null / blank / non-14/8-digit / non-numeric | `""` (empty — blank cell) |
| EE-R2 | future timestamp (clock skew, `diff < 0`) | `relative_time.just_now` |
| EE-R3 | minute / hour / day / week / month buckets | matches the byte-identical bucketing vs. frontend `humanizeDatetime` (`RelativeTimeFormatter.java:30-39`) |
| EE-R4 | UTC parse | elapsed correct regardless of viewer zone (diverges from legacy local-zone parse) |

## 7. Audit columns + retry (BaseEntity / TransactionalRetry)

| ID | Given | Expect |
|---|---|---|
| EE-A1 | persist a `BaseEntity` subclass with null timestamps | `@PrePersist` stamps `REG_DTM` + `MOD_DTM` (`OffsetDateTime.now()`) |
| EE-A2 | update an existing entity | `@PreUpdate` refreshes `MOD_DTM` only; `REG_DTM`/`REG_ID` immutable (`updatable=false`) |
| EE-A3 | read a row with a malformed `varchar(14)` dtm | `LegacyDtmConverter` returns `null` rather than throwing |
| EE-A4 | concurrent key allocation collides on PK | `TransactionalRetry.runOnce` rolls back the poisoned session; caller retries once in a fresh tx |

## Cross-links

- Mapping table being tested: [interface-specification.md](interface-specification.md)
- Request lifecycle: [business-flow.md](business-flow.md)
