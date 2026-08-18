# Audit Log — Functional Specification (Backend)

**Part of:** Common → audit-log
**Last verified:** 2026-06-23

The behavioural contract of `AuditLogService.append(AuditLogContext)`. Verified against
`common/auditlog/service/AuditLogService.java` + `AuditLogContext.java`.

## Contract summary

| Property | Behaviour | Source |
|---|---|---|
| **Never throws** | All failures (validation, missing tenant, seq race, DB outage) caught → WARN → swallowed. | `AuditLogService.java:126-143` |
| **One call → N rows** | One `append` writes one row per supported locale (`EN, KO, VI, KM`). | `:96-97, :188-209` |
| **Shared seq-no** | All locale rows share a single `AUDIT_LOGS_SEQ_NO`; PK uniqueness comes from `lang_cd`. | `:176-184, 197` |
| **Slug `WSVC_ID`** | `ctx.wsvcId()` is the refactor route slug (e.g. `firm-banking-settings`), never a legacy WSVC id (override 2026-05-09). | `AuditLogContext.java:26-30` |
| **Pre-translated** | `activity` / `detail` resolved per locale via `MessageResolver` at write time → readers don't re-translate. | `:190-193` |
| **Tenant-scoped** | `bizKey`/`regId`/`regNm` from `TenantContext`; only valid in an authenticated request. | `:159-166` |
| **Server time** | `regDtm` = `OffsetDateTime.now(UTC)` formatted `yyyyMMddHHmmss`. | `:99-103, 185` |
| **Tx isolation** | `@Transactional(REQUIRES_NEW)` — independent of the caller's business tx. | `:125` |

## `AuditLogContext` input fields (`AuditLogContext.java:47-70`)

| Field | Required | Maps to | Notes |
|---|---|---|---|
| `wsvcId` | Yes | `WSVC_ID` | Per-feature slug. |
| `refNo` | Yes | `REF_NO` | Entity name, e.g. `TB_FIRM_CONF`. |
| `refNo1` | Yes | `REF_NO1` | First PK component of the referenced row. |
| `refNo2` | No | `REF_NO2` | Second PK component; null for single-column natural PKs. |
| `activityKey` | No | `AUDIT_LOGS_ACTV` | i18n key; null/blank → column left null. |
| `detailKey` | Yes | `AUDIT_LOGS_ACTV_DTL` | i18n key → maps to the NOT-NULL detail column. |
| `msgArgs` | No | (interpolation) | `MessageFormat` args applied to both activity + detail. |

Caller never sets `bizKey`, `regId`, `regNm`, `regDtm`, `langCd`, `auditLogsSeqNo` — the writer derives them.

## Validation rules (verified)

- `ctx == null` → swallowed (no allocator call, no save) — `AuditLogServiceTest.append_nullContext_swallowed`.
- Blank `wsvcId` / `refNo` / `refNo1` / `detailKey` → swallowed before allocation
  (`requireField`, `AuditLogService.java:154-157, 217-222`).
- Empty `TenantContext` (no `bizKey`/`regId`) → swallowed (`:162-166`).
- `detailKey` is the only key whose absence is fatal — it maps to the NOT-NULL `AUDIT_LOGS_ACTV_DTL` column.

## Locale fan-out

- Supported: `EN, KO, VI, KM`. KM is included even though the DEV Vietnam tenant has only EN/KO/VI rows —
  the refactor extends coverage forward; pre-existing JEX rows are immutable history (Javadoc `:60-65`).
- Each row's `lang_cd` = `LangCd.name()` (`"EN"`, `"KO"`, `"VI"`, `"KM"`).

## Seq allocation contract

- Allocated **once per `append`** (not once per locale) — `verify(legacyFunctions, times(1)).nextAuditLogsSeqNo(...)`
  in `AuditLogServiceTest`.
- Funnel call: `legacyFunctions.nextAuditLogsSeqNo(bizKey, ctx.refNo(), ctx.refNo1())` (`AuditLogService.java:176-177`).
  ⚠️ The funnel method's declared parameter names are `(bizKey, wsvcId, langCd)` but the **actual values passed
  are `(bizKey, refNo, refNo1)`** mapping to `wb_get_next_val` positions `$3/$4/$5`. The service comment
  states this preserves the deleted `AuditLogSeqNoRepository` positional behaviour exactly
  (`AuditLogService.java:173-177`). See `database-mapping.md`.
- Legacy `wb_get_next_val` uses `MAX(seq_no::numeric)+1` — race-prone under high concurrency; the refactor
  accepts the race for legacy parity (Javadoc `:67-77`). Hardening to `nextval('seq')` is a deferred item
  (see `refactor-gap-analysis.md`).
