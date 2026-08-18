# Audit Log — Migration Guide (JEX → Refactor, Backend)


> ⚠️ **Flyway numbering in this document is PRE-REBASELINE (note added 2026-08-17).**
> Bare `V<n>` references below were written against the migration set as it stood **before** the
> [2026-06-09 rebaseline](../../../references/database/flyway-migration-baseline.md), which renumbered every migration to a
> one-table-per-migration scheme and dropped all seed / helper-index / view migrations. **Treat every
> bare `V<n>` here as HISTORICAL** — it does not identify the same migration today. To find the current
> migration for a table, run `ls src/main/resources/db/migration/ | grep <table>` or see
> [flyway-migration-baseline.md](../../../references/database/flyway-migration-baseline.md) §5.

**Part of:** Common → audit-log
**Last verified:** 2026-06-23

How the legacy JEX audit behaviour maps onto the refactor shared writer.

## Legacy JEX behaviour

- Each JEX CRUD-with-audit page inserted directly into `AUDIT_LOGS_INFM`, one row per supported language,
  all sharing one `AUDIT_LOGS_SEQ_NO` (verified DEV-side 2026-05-09: `audit_logs_seq_no` identical across
  KO/VI/EN for one event).
- `WSVC_ID` carried the **legacy JEX page id verbatim** — e.g. `comm_0220_001` for firm-banking-settings.
- The detail-audit read query lived at `comm_0220_003_r001_act.jsp:74-86`, filtering by
  `(BIZ_KEY, REF_NO, REF_NO1, REF_NO2, WSVC_ID, LANG_CD)` and ordering `REG_DTM DESC`.
- Seq allocation went through the legacy `SeqHelper.getNextVal(...)` → `wb_get_next_val('audit_logs_infm',
  'audit_logs_seq_no', ...)` path.

## Refactor mapping

| Legacy | Refactor |
|---|---|
| Per-page inline `AUDIT_LOGS_INFM` insert | Single shared `AuditLogService.append(AuditLogContext)` |
| Per-page language loop | `SUPPORTED_LOCALES = {EN, KO, VI, KM}` fan-out inside the service |
| `WSVC_ID = 'comm_0220_001'` | `WSVC_ID = 'firm-banking-settings'` (route slug) |
| `SeqHelper.getNextVal(...)` | `LegacyFunctionRepository.nextAuditLogsSeqNo(...)` → `wb_get_next_val` (V140) |
| `comm_0220_003_r001` read query | Per-feature `AuditLogEntryRepository` derived finders |
| Audit insert in caller's tx | `@Transactional(REQUIRES_NEW)`, swallow-on-failure |

## WSVC_ID slug override (2026-05-09)

The earlier "audit DB constant" bucket — keeping `AUDIT_LOGS_INFM.WSVC_ID = 'comm_0220_001'` so the legacy
JEX detail-audit query could find refactor-written rows — was **retired 2026-05-09**. Refactor writes now
use the route slug. Rationale: the refactor is local-only R&D and JEX never queries the refactor DB, so the
cross-system compatibility motivation evaporated (workspace CLAUDE.md §"Cross-Tree Rules"; wecms-api
CLAUDE.md §"Refactor identifier naming").

KM is added to the locale set going forward; pre-existing JEX rows (legacy WSVC ids, EN/KO/VI only) stay
as-is — they are immutable history.

## Coexistence reads (verified)

Where the refactor runs against a shared DB alongside live JEX (per-feature decision, not Common-wide), the
read finders union the refactor slug with the legacy page id so the history popup shows rows written by
both systems:

- Login Credentials reads `WSVC_ID IN ('login-credentials', 'comm_0218_001', 'comm_0218_004')`.
- Bank Account reads `WSVC_ID IN ('bank-account', 'comm_0183_001')`, `REF_NO IN ('ACCT_MSTR','ACCT_AUTH_INFO')`.

Writes always use the slug only (CLAUDE.md identifier rule). The union is read-only.

## Schema note

The JEX base `WSVC_ID` column is `varchar(20)`; the refactor widens it to `varchar(100)` via a DBA-run
script to fit slugs (approved divergence 2026-06-09). See `database-mapping.md`.

⚠️ UNVERIFIED: the V63/V66 migration files were not read in this pass; seq allocation is centralized in **V140** (`V140__wb_get_next_val.sql`). *(Earlier "V155" was the pre-2026-06-09-renumber number.)*
