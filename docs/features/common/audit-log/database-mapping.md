# Audit Log — Database Mapping (Backend)


> ⚠️ **Flyway numbering in this document is PRE-REBASELINE (note added 2026-08-17).**
> Bare `V<n>` references below were written against the migration set as it stood **before** the
> [2026-06-09 rebaseline](../../../references/database/flyway-migration-baseline.md), which renumbered every migration to a
> one-table-per-migration scheme and dropped all seed / helper-index / view migrations. **Treat every
> bare `V<n>` here as HISTORICAL** — it does not identify the same migration today. To find the current
> migration for a table, run `ls src/main/resources/db/migration/ | grep <table>` or see
> [flyway-migration-baseline.md](../../../references/database/flyway-migration-baseline.md) §5.

**Part of:** Common → audit-log
**Last verified:** 2026-06-23

Maps the `AUDIT_LOGS_INFM` table to the JPA entity. Source of truth:
`common/auditlog/entity/AuditLogsInfm.java` + `AuditLogsInfmId.java`.

## Table

`AUDIT_LOGS_INFM` — `@Table(name = "AUDIT_LOGS_INFM")` (`AuditLogsInfm.java:35`), in the PROD Active
Table List. The entity carries `@IdClass(AuditLogsInfmId.class)` (`:36`).

## Primary key — `AuditLogsInfmId` (`AuditLogsInfmId.java:27-32`)

`(BIZ_KEY, AUDIT_LOGS_SEQ_NO, WSVC_ID, LANG_CD)` — the `LANG_CD` component is what keeps the locale
fan-out unique while all four locale rows share one `AUDIT_LOGS_SEQ_NO`.

| PK field | Java type |
|---|---|
| `bizKey` | `String` |
| `auditLogsSeqNo` | `BigDecimal` |
| `wsvcId` | `String` |
| `langCd` | `String` |

## Column mapping (verified — `AuditLogsInfm.java`)

| Column | Java field | Type / length | Null | PK | Notes |
|---|---|---|---|---|---|
| `BIZ_KEY` | `bizKey` | `String` len 20 | NN | ✓ | Tenant. |
| `AUDIT_LOGS_SEQ_NO` | `auditLogsSeqNo` | `BigDecimal` | NN | ✓ | Shared across the locale fan-out. |
| `WSVC_ID` | `wsvcId` | `String` len **100** | NN | ✓ | Refactor route **slug**. ⚠️ See widen note below. |
| `LANG_CD` | `langCd` | `String` len 3 | NN | ✓ | `EN`/`KO`/`VI`/`KM`. |
| `REF_NO` | `refNo` | `String` len 50 | NN | | Logical entity name (e.g. `TB_FIRM_CONF`). |
| `REF_NO1` | `refNo1` | `String` len 50 | NN | | First key column of the referenced row. |
| `REF_NO2` | `refNo2` | `String` len 50 | nullable | | Second key column. |
| `REF_NO3` | `refNo3` | `String` len 50 | nullable | | Unused by the writer today. |
| `REF_NO4` | `refNo4` | `String` len 50 | nullable | | Unused by the writer today. |
| `REF_NO5` | `refNo5` | `String` len 50 | nullable | | Unused by the writer today. |
| `AUDIT_LOGS_ACTV` | `auditLogsActv` | `String` len 50 | nullable | | Activity category (locale-resolved). |
| `AUDIT_LOGS_ACTV_DTL` | `auditLogsActvDtl` | `TEXT` | NN | | Activity detail (locale-resolved). |
| `REG_ID` | `regId` | `String` len 100 | NN | | Actor user id (from `TenantContext`). |
| `REG_NM` | `regNm` | `String` len 100 | NN | | Actor display name (falls back to `regId`). |
| `REG_DTM` | `regDtm` | `String` len 14 | NN | | `yyyyMMddHHmmss` UTC string. |

## ⚠️ `WSVC_ID` length divergence (verified — entity Javadoc `AuditLogsInfm.java:56-61`)

The JEX base column is `varchar(20)`; route slugs can exceed 20 chars, so the live JEX column is widened
to `varchar(100)` via the DBA-run script `Claude Code (AI)/sql/audit_logs_infm_wsvc_id_widen_20260609.sql`.
The entity maps `length = 100` to match the widened column. This is an **approved schema divergence
(2026-06-09)** — flag it in any parity review.

## Seq-no allocation (verified)

Allocation funnels through `LegacyFunctionRepository.nextAuditLogsSeqNo(...)` →
`wb_get_next_val` (the live function is **`V140__wb_get_next_val.sql`**; the `audit_logs_infm` branch is
verified by `AuditLogService.java:69-71` as "V140 #17"). Single-executor rule: the only
executable `wb_get_next_val` call site is `LegacyFunctionRepository` (`em.createNativeQuery`).

```java
// LegacyFunctionRepository.java:239-241
public String nextAuditLogsSeqNo(String bizKey, String wsvcId, String langCd) {
    return nextVal("audit_logs_infm", "audit_logs_seq_no", bizKey, wsvcId, langCd, null, null, null);
}
```

⚠️ **Parameter-name vs actual-value mismatch.** The declared names are `(bizKey, wsvcId, langCd)` and the
funnel comment says "per (biz_key, wsvc_id, lang_cd) MAX+1", but the caller passes
`nextAuditLogsSeqNo(bizKey, ctx.refNo(), ctx.refNo1())` (`AuditLogService.java:176-177`) — i.e. the
2nd/3rd args are actually `REF_NO` / `REF_NO1`, bound to `wb_get_next_val` positions `$3/$4/$5`. The
service explicitly notes this preserves the deleted `AuditLogSeqNoRepository`'s positional contract
verbatim. ⚠️ UNVERIFIED: the exact `$4/$5` predicate inside the V140 `audit_logs_infm` branch body was
not read in this pass — confirm against the `V140__wb_get_next_val.sql` migration before relying on the per-column grouping.

## Corrections vs the stale flat doc

- Entity is **`AuditLogsInfm`** (+ `AuditLogsInfmId`), **not** `AuditLogEntry`/`AuditLogEntryId`.
- There is **no `AuditLogSeqNoRepository`** — that native-`@Query` repo was deleted; seq flows through the
  `LegacyFunctionRepository` funnel.
- Schema versions: the entity Javadoc references the table as added in `V63`. ⚠️ UNVERIFIED — the V63/V66
  migration files were not read in this pass; the **authoritative, verified fact** is that seq allocation is
  centralized in **V140** (`V140__wb_get_next_val.sql`) through `wb_get_next_val`. Treat any "V66 audit_logs_infm branch" claim as
  superseded by V140. *(Earlier docs cited "V155"; that was the pre-2026-06-09-renumber number — `V155` on the live disk is `mrec_pic_store`, and the renumbered `wb_get_next_val` is V140.)*
