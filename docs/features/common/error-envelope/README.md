# Error Envelope — Common Infrastructure (Backend)

**Part of:** Common → error-envelope
**Last verified:** 2026-06-23

> **This is cross-cutting infrastructure, not an endpoint.** There is **no** REST URI for
> `error-envelope`. This module is the shared response envelope, exception funnel, trace-id
> propagation, i18n message resolution, and audit-column base that **every** WeCMS REST endpoint
> sits on top of. When you read "applies to every endpoint" below, that is literal — `ApiResponse`
> wraps every success, `GlobalExceptionHandler` catches every uncaught throwable, `TraceIdFilter`
> stamps every request, and `BaseEntity` audits every legacy-table write.

## What this module owns

| Concern | Source of truth |
|---|---|
| Success / error envelope shape | [`common/response/ApiResponse.java`](../../../../src/main/java/com/kosign/wecms/common/response/ApiResponse.java) + [`CommonHead.java`](../../../../src/main/java/com/kosign/wecms/common/response/CommonHead.java) |
| Exception → HTTP status → code mapping | [`common/exception/GlobalExceptionHandler.java`](../../../../src/main/java/com/kosign/wecms/common/exception/GlobalExceptionHandler.java) |
| Business error type | [`common/exception/BusinessException.java`](../../../../src/main/java/com/kosign/wecms/common/exception/BusinessException.java) |
| Trace id generation + MDC + `X-Trace-Id` | [`common/exception/TraceId.java`](../../../../src/main/java/com/kosign/wecms/common/exception/TraceId.java) + [`TraceIdFilter.java`](../../../../src/main/java/com/kosign/wecms/common/exception/TraceIdFilter.java) |
| i18n message resolution | [`common/i18n/MessageResolver.java`](../../../../src/main/java/com/kosign/wecms/common/i18n/MessageResolver.java) + [`RelativeTimeFormatter.java`](../../../../src/main/java/com/kosign/wecms/common/i18n/RelativeTimeFormatter.java) |
| Audit columns + legacy dtm conversion | [`common/entity/BaseEntity.java`](../../../../src/main/java/com/kosign/wecms/common/entity/BaseEntity.java) + [`LegacyDtmConverter.java`](../../../../src/main/java/com/kosign/wecms/common/entity/LegacyDtmConverter.java) |
| Tenant request scope | [`common/tenant/TenantContext.java`](../../../../src/main/java/com/kosign/wecms/common/tenant/TenantContext.java) |
| One-shot transactional retry | [`common/tx/TransactionalRetry.java`](../../../../src/main/java/com/kosign/wecms/common/tx/TransactionalRetry.java) |

## Documents

| Doc | Scope |
|---|---|
| [business-flow.md](business-flow.md) | Request lifecycle — `TraceIdFilter` → handler → `ApiResponse.ok` / `BusinessException` → `GlobalExceptionHandler` → envelope; MDC + `X-Trace-Id` |
| [functional-specification.md](functional-specification.md) | Envelope shape, wire naming, status semantics, trace-id format, i18n resolution, audit columns |
| [database-mapping.md](database-mapping.md) | Shared audit-column mapping (`BaseEntity`) + `LegacyDtmConverter` + tenant context — **no dedicated envelope table** |
| [interface-specification.md](interface-specification.md) | The envelope contract every endpoint returns + the full exception → HTTP → code mapping table + error-code registry |
| [migration-guide.md](migration-guide.md) | Retired `COMMON_HEAD` / `REC` → `ApiResponse`; JEX ML codes → `WBExxxx` (no verbatim reuse) |
| [refactor-gap-analysis.md](refactor-gap-analysis.md) | Vocab: `COMMON_HEAD`/`REC` = DEAD LEGACY; lowercase snake_case = APPROVED DEVIATION; km i18n = PARTIAL |
| [source-code-index.md](source-code-index.md) | Full `common/` infra file map (response / exception / i18n / entity / security / tenant / tx / legacy / web + tests) |
| [test-scenarios.md](test-scenarios.md) | Per-exception-type → expected envelope + status; trace-id presence; i18n locale selection |

## Related

- Common domain hub: [`../README.md`](../README.md)
- Frontend twin (Next.js error handling): [`../../../../../wecms-web/docs/features/common/error-envelope/README.md`](../../../../../wecms-web/docs/features/common/error-envelope/README.md)
- Shared error-code registry (both systems): [`../../../../../docs/_shared/error-code-registry.md`](../../../../../docs/_shared/error-code-registry.md)
- Security baseline (auth / API / audit policies): [`../../../../../docs/security/`](../../../../../docs/security/)
- Common source index (audit deliverable): [`../../../audits/common-source-index.md`](../../../audits/modules/common-source-index.md)
- Shared audit-log writer (uses this envelope + locale fan-out): [`../audit-log/README.md`](../audit-log/README.md)
- API naming + error-handling rules: `wecms-api/CLAUDE.md` §"API Naming & Serialization Standards", §"Error handling", §"Exception → response mapping", §"Error codes in use", §"i18n Rules"
