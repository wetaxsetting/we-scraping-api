# Audit Log — Source Code Index (Backend)


> ⚠️ **Flyway numbering in this document is PRE-REBASELINE (note added 2026-08-17).**
> Bare `V<n>` references below were written against the migration set as it stood **before** the
> [2026-06-09 rebaseline](../../../references/database/flyway-migration-baseline.md), which renumbered every migration to a
> one-table-per-migration scheme and dropped all seed / helper-index / view migrations. **Treat every
> bare `V<n>` here as HISTORICAL** — it does not identify the same migration today. To find the current
> migration for a table, run `ls src/main/resources/db/migration/ | grep <table>` or see
> [flyway-migration-baseline.md](../../../references/database/flyway-migration-baseline.md) §5.

**Part of:** Common → audit-log
**Last verified:** 2026-06-23

Corrected file map. All paths under `wecms-api/src/main/java/com/kosign/wecms/`.

## Module files (`common/auditlog/`)

| File | Role |
|---|---|
| `common/auditlog/service/AuditLogService.java` | Cross-feature writer. `append(AuditLogContext)` — `@Transactional(REQUIRES_NEW)`, never throws, locale fan-out, seq alloc via funnel. |
| `common/auditlog/service/AuditLogContext.java` | `@Builder record` — caller-supplied event context (`wsvcId`, `refNo`, `refNo1`, `refNo2`, `activityKey`, `detailKey`, `msgArgs`). |
| `common/auditlog/entity/AuditLogsInfm.java` | `@Entity @Table(AUDIT_LOGS_INFM)`, `@IdClass(AuditLogsInfmId)`. |
| `common/auditlog/entity/AuditLogsInfmId.java` | Composite PK `(bizKey, auditLogsSeqNo, wsvcId, langCd)`. |
| `common/auditlog/repository/AuditLogEntryRepository.java` | `JpaRepository<AuditLogsInfm, AuditLogsInfmId>` — read finders for per-feature audit history. |

## Shared dependencies (outside the module)

| File | Used for |
|---|---|
| `common/legacy/LegacyFunctionRepository.java` | `nextAuditLogsSeqNo(bizKey, wsvcId, langCd)` (`:239-241`) → `wb_get_next_val`. Single executable funnel. |
| `common/i18n/MessageResolver.java` | `resolve(key, locale, args)` per-locale string resolution. |
| `common/tenant/TenantContext.java` | `currentBizKey()` / `currentUserId()` / `currentUserNm()`. |
| `common/enums/LangCd.java` | `EN, KO, VI, KM` + `toLocale()`. |

## Tests

| File | Covers |
|---|---|
| `src/test/java/.../common/auditlog/service/AuditLogServiceTest.java` | Fan-out (4 rows, shared seq), per-locale activity/detail, tenant scoping, swallow-on-failure (repo throws / seq throws / seq null), validation guards. |
| `src/test/java/.../common/auditlog/service/AuditLogServiceTransactionAnnotationTest.java` | Reflection pin: `append` carries `@Transactional(propagation = REQUIRES_NEW)`. |

## ⚠️ Corrections vs the stale flat doc (`audit-log.md`)

| Stale claim | Verified reality |
|---|---|
| Entity `AuditLogEntry` + `AuditLogEntryId` | **`AuditLogsInfm` + `AuditLogsInfmId`** — there is no `AuditLogEntry*` class. |
| Repositories include `AuditLogSeqNoRepository` (native `@Query`) | **Deleted.** Seq now flows through `LegacyFunctionRepository.nextAuditLogsSeqNo(...)`. Only `AuditLogEntryRepository` exists. |
| Schema "V63 / V66" | ⚠️ UNVERIFIED — migration files not read this pass. Verified fact: seq allocation is **centralized in V140** (`V140__wb_get_next_val.sql`) via `wb_get_next_val`. Do not cite V66 as the live seq branch. *(Earlier "V155" was the pre-2026-06-09-renumber number.)* |

## Cross-references

- `AuditLogServiceIntegrationTest` is referenced in `AuditLogServiceTest`'s Javadoc as the home for runtime
  `REQUIRES_NEW` rollback isolation. ⚠️ UNVERIFIED — that integration test file was **not found** in the
  test tree this pass (only the unit test + the annotation pin exist). Treat the runtime rollback test as
  **deferred / not yet present**, consistent with `AuditLogServiceTransactionAnnotationTest`'s note that
  rollback isolation is "verified manually until a future MR introduces Testcontainers".
