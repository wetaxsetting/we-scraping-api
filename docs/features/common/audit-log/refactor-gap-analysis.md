# audit-log — Refactor Gap Analysis (Backend)

**Part of:** Common → audit-log
**Last verified:** 2026-06-23

Legacy JEX audit-write behaviour vs. the refactor shared writer. Vocabulary:
**MATCH** · **APPROVED DEVIATION** · **DEAD LEGACY** · **PARTIAL** · **MISSING**.

| Aspect | Verdict | Detail |
|---|---|---|
| One row per locale, sharing one seq-no | **MATCH** | `SUPPORTED_LOCALES` fan-out; verified DEV-side seq-sharing reproduced (`AuditLogService.java:96-97, 188-209`). |
| Filter-tuple read query shape | **MATCH** | `AuditLogEntryRepository` finders mirror `comm_0220_003_r001_act.jsp:74-86` + `ORDER BY REG_DTM DESC`. |
| Tenant scoping from session | **MATCH** | `bizKey`/`regId`/`regNm` from `TenantContext` (`:159-166`). |
| Audit failure isolation | **MATCH** | `REQUIRES_NEW` + swallow-on-failure preserves the "audit must not strand business write" intent (Phase 1 §11.1). |
| `WSVC_ID` = route slug (was legacy page id) | **APPROVED DEVIATION** | Override 2026-05-09; refactor writes the slug, not `comm_0220_001` (`AuditLogContext.java:26-30`). |
| `WSVC_ID` column `varchar(20)` → `varchar(100)` | **APPROVED DEVIATION** | DBA-run widen script, approved 2026-06-09 (`AuditLogsInfm.java:56-61`). |
| KM locale added (legacy DEV had EN/KO/VI only) | **APPROVED DEVIATION** | Refactor extends coverage forward; legacy rows untouched (`AuditLogService.java:60-65`). |
| Seq-no race (`MAX(seq_no)+1`, not `nextval`) | **APPROVED DEVIATION / PARTIAL** | Race accepted for legacy parity; two concurrent callers can collide on the PK. Hardening to `nextval('seq')` is a documented future item (`AuditLogService.java:67-77`). No retry on the audit path (unlike feature key allocators). |
| `nextAuditLogsSeqNo` arg names vs values | **PARTIAL** | Declared `(bizKey, wsvcId, langCd)` but called with `(bizKey, refNo, refNo1)`; preserves the deleted `AuditLogSeqNoRepository` positional contract. ⚠️ The V140 branch's `$4/$5` predicate body was not read this pass — confirm it groups on REF_NO/REF_NO1, not WSVC_ID/LANG_CD. |
| `REF_NO3` / `REF_NO4` / `REF_NO5` columns | **DEAD LEGACY** | Mapped on the entity but never written by `AuditLogContext`/`append`. Preserved for schema parity. |
| Refactor audit READ API as a Common surface | **MISSING (by design)** | No controller, no generic `/audit-logs` Common endpoint. Reads are per-feature (Login Credentials, Bank Account). Intentional — not a defect. |
| Approval Line audit rows | **MISSING (by design — parity)** | Legacy `comm_0222_*` writes no `AUDIT_LOGS_INFM` rows; refactor matches (writes none). |
| ERP Transaction History audit rows | **MISSING (by design — parity)** | Legacy writes none on the read-only ledger path; refactor matches. |
| Runtime `REQUIRES_NEW` rollback isolation test | **PARTIAL** | Only the annotation is pinned (`AuditLogServiceTransactionAnnotationTest`); the live rollback-isolation integration test is deferred to a future Testcontainers `@SpringBootTest`. See `test-scenarios.md`. |

## Consumers (verified — write `append` with their slug)

FirmBankingSettingsService, ContactsService, ChartOfAccountsService, LoginCredentialsService, plus others
in the payment/banking/setting domains (24 files reference `AuditLogContext`/`append`). The full consumer
sweep is in `source-code-index.md`.

## Open items

- **OD — seq-no hardening:** migrate audit seq from `MAX+1` to a true sequence if contention becomes real.
  Owner: PO / DBA. Not actioned.
- **Verify V140 branch body** (`V140__wb_get_next_val.sql`) for the `audit_logs_infm` seq predicate (the `$4/$5` grouping columns).
