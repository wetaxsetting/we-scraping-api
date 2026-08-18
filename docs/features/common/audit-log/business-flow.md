# Audit Log — Business Flow (Backend)

**Part of:** Common → audit-log
**Last verified:** 2026-06-23

The single append flow, end-to-end. Source of truth: `common/auditlog/service/AuditLogService.java`.

## Entry point

```
auditLogService.append(AuditLogContext ctx)
```

Annotated `@Transactional(propagation = Propagation.REQUIRES_NEW)`
(`AuditLogService.java:125`). The body is a `try { doAppend(ctx); } catch (RuntimeException ex) { log.warn(...) }`
— the method **never throws** (`AuditLogService.java:126-143`).

## Step sequence (`doAppend`, `AuditLogService.java:150-213`)

| # | Step | Code |
|---|---|---|
| 1 | Null-guard the context | `if (ctx == null) throw IllegalArgumentException` (`:151`) |
| 2 | Require `wsvcId`, `refNo`, `refNo1`, `detailKey` (non-blank) | `requireField(...)` (`:154-157`) |
| 3 | Read tenant identity from `TenantContext` — `bizKey`, `regId` (userId), `regNm` (userNm) | `:159-161` |
| 4 | Require `bizKey` + `regId` present (authenticated request) | `if (bizKey==null \|\| regId==null) throw IllegalStateException` (`:162-166`) |
| 5 | Defensive fallback `regNm = regId` when display name is null | `:171` |
| 6 | **Allocate the seq-no ONCE** via the funnel | `legacyFunctions.nextAuditLogsSeqNo(bizKey, ctx.refNo(), ctx.refNo1())` (`:176-177`) |
| 7 | Null-guard the allocator result | `if (seqNoRaw==null) throw IllegalStateException` (`:178-183`) |
| 8 | Compute `regDtm = OffsetDateTime.now(UTC)` as `yyyyMMddHHmmss` | `:185` |
| 9 | **Locale fan-out** — loop `SUPPORTED_LOCALES = {EN, KO, VI, KM}` | `:188-209` |
| 9a | Resolve `activityKey` (null/blank → null) + `detailKey` per locale via `MessageResolver` | `:190-193` |
| 9b | Build one `AuditLogsInfm` row, all sharing the step-6 `seqNo`, distinguished by `lang_cd` | `:195-208` |
| 9c | `entryRepository.save(entry)` | `:208` |

All four locale rows are persisted inside the one `REQUIRES_NEW` transaction.

## `REQUIRES_NEW` semantics (verified — class Javadoc `AuditLogService.java:37-52`)

```
Caller business tx              Audit tx (REQUIRES_NEW)
─────────────────────          ───────────────────────
commits                   →     commits (or fails → swallowed, see below)
rolls back                →     STILL commits if it completed — audit is a forensic record
audit insert fails        →     caught internally → log.warn → swallowed; caller proceeds
```

A missed audit row is preferable to a stranded business write (Phase 1 §11.1 / §13 OQ-6 trade-off).

## Failure handling (verified)

- Any `RuntimeException` inside `doAppend` (validation, missing tenant, seq race, DB outage) is caught
  in `append` and logged at WARN with `wsvcId / refNo / refNo1 / refNo2` via the null-safe `safe(...)`
  reader (`AuditLogService.java:137-142, 227-235`). Nothing propagates to the caller.
- Because seq allocation (step 6) precedes the save loop, a thrown/null allocator result means **no row
  is saved** — verified by `AuditLogServiceTest.append_seqNoAllocator{Throws,ReturnsNull}_swallowed`.

## Locale fan-out detail

- `SUPPORTED_LOCALES = List.of(LangCd.EN, LangCd.KO, LangCd.VI, LangCd.KM)` (`AuditLogService.java:96-97`).
- EN-first iteration matches legacy JEX iteration order. Insert order is cosmetic — the read query filters
  by the caller's `lang_cd`, so each user reads pre-localized strings without server-side re-translation
  (Javadoc `:54-65`).
- A locale missing the i18n key falls back to the key string itself (a translation gap, not a crash —
  `AuditLogContext.java:36-40`).
