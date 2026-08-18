# Source Code Index — Error Envelope & Common Infrastructure

**Part of:** Common → error-envelope
**Last verified:** 2026-06-23

Full file map of the `common/` infrastructure that the error envelope sits within. Paths are relative to
`wecms-api/src/main/java/com/kosign/wecms/`. Verified against the tree on 2026-06-23.

## response/ — the envelope

| File | Role |
|---|---|
| [`common/response/ApiResponse.java`](../../../../src/main/java/com/kosign/wecms/common/response/ApiResponse.java) | `{ status, data, timestamp?, path?, trace_id? }` envelope; `ok` / `error` factories; `@JsonInclude(NON_NULL)` |
| [`common/response/CommonHead.java`](../../../../src/main/java/com/kosign/wecms/common/response/CommonHead.java) | `status` block — `error` / `code` / `message`; `success` / `error` factories |

## exception/ — the funnel + trace id

| File | Role |
|---|---|
| [`common/exception/GlobalExceptionHandler.java`](../../../../src/main/java/com/kosign/wecms/common/exception/GlobalExceptionHandler.java) | Single `@RestControllerAdvice`; 15 handlers mapping exception → HTTP → code |
| [`common/exception/BusinessException.java`](../../../../src/main/java/com/kosign/wecms/common/exception/BusinessException.java) | `RuntimeException` holding `code` + `Object[] args` |
| [`common/exception/TraceId.java`](../../../../src/main/java/com/kosign/wecms/common/exception/TraceId.java) | `generate()` (JEX-style id) + `current()` (MDC read); `MDC_KEY="traceId"` |
| [`common/exception/TraceIdFilter.java`](../../../../src/main/java/com/kosign/wecms/common/exception/TraceIdFilter.java) | `OncePerRequestFilter` @ HIGHEST_PRECEDENCE; MDC put/remove + `X-Trace-Id` header |

## i18n/ — message resolution

| File | Role |
|---|---|
| [`common/i18n/MessageResolver.java`](../../../../src/main/java/com/kosign/wecms/common/i18n/MessageResolver.java) | `MessageSource` wrapper; request-locale + explicit-locale `resolve(...)`; code-as-fallback |
| [`common/i18n/RelativeTimeFormatter.java`](../../../../src/main/java/com/kosign/wecms/common/i18n/RelativeTimeFormatter.java) | Server-side "X ago"; UTC parse; `relative_time.*` keys; frontend `lib/datetime.ts` mirror |

## entity/ — shared audit base

| File | Role |
|---|---|
| [`common/entity/BaseEntity.java`](../../../../src/main/java/com/kosign/wecms/common/entity/BaseEntity.java) | `@MappedSuperclass`; `REG_ID`/`REG_DTM`/`MOD_ID`/`MOD_DTM`; `@PrePersist`/`@PreUpdate` |
| [`common/entity/LegacyDtmConverter.java`](../../../../src/main/java/com/kosign/wecms/common/entity/LegacyDtmConverter.java) | `OffsetDateTime ⇄ varchar(14) yyyyMMddHHmmss` UTC; tolerant read |

## tenant/ + tx/

| File | Role |
|---|---|
| [`common/tenant/TenantContext.java`](../../../../src/main/java/com/kosign/wecms/common/tenant/TenantContext.java) | `ThreadLocal<Principal>`; `currentUserId/UserNm/BizKey/LangCd/AuthGrCd/PrdtType` |
| [`common/tx/TransactionalRetry.java`](../../../../src/main/java/com/kosign/wecms/common/tx/TransactionalRetry.java) | `runOnce(Supplier)` in `REQUIRES_NEW` — one-shot retry across a tx boundary |

## security/ — auth + masking (envelope-adjacent)

| File | Role |
|---|---|
| [`common/security/JwtAuthenticationEntryPoint.java`](../../../../src/main/java/com/kosign/wecms/common/security/JwtAuthenticationEntryPoint.java) | JWT 401 — emits envelope (`WBE1009`) before the controller chain; reads MDC trace id |
| [`common/security/JwtAuthenticationFilter.java`](../../../../src/main/java/com/kosign/wecms/common/security/JwtAuthenticationFilter.java) | Bearer parse; sets authorities; populates `TenantContext` |
| [`common/security/JwtTokenProvider.java`](../../../../src/main/java/com/kosign/wecms/common/security/JwtTokenProvider.java) | HS256/HS512 issue/parse |
| [`common/security/WecmsPasswordEncoder.java`](../../../../src/main/java/com/kosign/wecms/common/security/WecmsPasswordEncoder.java) | Coexistence SHA-256 encoder; never upgrades |
| [`common/security/LegacyHashPasswordEncoder.java`](../../../../src/main/java/com/kosign/wecms/common/security/LegacyHashPasswordEncoder.java) | Replicates legacy `public.hash()` SHA-256→base64 |
| [`common/security/LogMask.java`](../../../../src/main/java/com/kosign/wecms/common/security/LogMask.java) | Masks sensitive values in logs (paired with the trace-id log line) |

## legacy/ + web/

| File | Role |
|---|---|
| [`common/legacy/LegacyFunctionRepository.java`](../../../../src/main/java/com/kosign/wecms/common/legacy/LegacyFunctionRepository.java) | **Sole** executor of `wb_get_next_val` (key-gen funnel) |
| [`common/web/RequestResponseLoggingFilter.java`](../../../../src/main/java/com/kosign/wecms/common/web/RequestResponseLoggingFilter.java) | Request/response logging (emits trace-id-tagged lines) |
| [`common/web/SortDirection.java`](../../../../src/main/java/com/kosign/wecms/common/web/SortDirection.java) | `{ASC, DESC}` lenient `parse` |
| [`common/web/SortableField.java`](../../../../src/main/java/com/kosign/wecms/common/web/SortableField.java) | Per-domain sort whitelist interface |

## Other common packages (not envelope-owned, listed for orientation)

`common/auditlog/` (shared `AUDIT_LOGS_INFM` writer — see [`../audit-log/README.md`](../audit-log/README.md)),
`common/export/` (Excel pipeline), `common/firmbanking/`, `common/enums/`
(`LangCd`, `PrdtType`, `AcctTypeCd`, `FirmBankingSvcType`, `InoutDv`, `TranTp`, `YesOrNo`, `AStts`,
`VasAcctType`).

## Tests

| Test | Covers |
|---|---|
| [`common/exception/GlobalExceptionHandlerTest.java`](../../../../src/test/java/com/kosign/wecms/common/exception/GlobalExceptionHandlerTest.java) | Per-exception → envelope + status + code |
| [`common/security/LegacyHashPasswordEncoderTest.java`](../../../../src/test/java/com/kosign/wecms/common/security/LegacyHashPasswordEncoderTest.java) | Legacy `public.hash()` byte-parity |
| [`common/security/LogMaskTest.java`](../../../../src/test/java/com/kosign/wecms/common/security/LogMaskTest.java) | Sensitive-value masking |
| [`common/i18n/RelativeTimeFormatterTest.java`](../../../../src/test/java/com/kosign/wecms/common/i18n/RelativeTimeFormatterTest.java) | UTC bucketing parity with frontend |
| [`common/web/RequestResponseLoggingFilterTest.java`](../../../../src/test/java/com/kosign/wecms/common/web/RequestResponseLoggingFilterTest.java) | Logging filter behaviour |
| [`common/auditlog/service/AuditLogServiceTest.java`](../../../../src/test/java/com/kosign/wecms/common/auditlog/service/AuditLogServiceTest.java) + `…TransactionAnnotationTest` | Audit writer (locale fan-out, `REQUIRES_NEW`) |
| [`common/enums/FirmBankingSvcTypeTest.java`](../../../../src/test/java/com/kosign/wecms/common/enums/FirmBankingSvcTypeTest.java) | Enum `code()`/`fromCode()` |

## Cross-links

- Test scenarios narrative: [test-scenarios.md](test-scenarios.md)
- Audit deliverable: [`../../../audits/common-source-index.md`](../../../audits/modules/common-source-index.md)
