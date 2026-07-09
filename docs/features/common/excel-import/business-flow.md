# Excel Import — Business Flow (Backend)

**Part of:** Common → excel-import
**Last verified:** 2026-06-23

End-to-end flow for the import capability. Because there is **no shared backend service** (open decision C3), the flow below is the *pattern* — the canonical two-step (server-side preview + import) is implemented only by **Purpose Settings** and **VAS Account**. Chart of Accounts and Contacts use a client-parse / JSON-rows variant with no server preview. Per-feature specifics live in each owning feature's docs (cross-linked below).

## Canonical flow (Purpose Settings · VAS Account)

```
[FE] Download template (static asset under public/templates/)
      │
[FE] Browse / Re-Select .xlsx  →  templateValidator (client header check)
      │  multipart POST .../excel/preview  (file part)
      ▼
[BE] PREVIEW  (read-only @Transactional)
      ├─ Parse first sheet via Apache POI WorkbookFactory
      ├─ Row-cap check (MAX_ROWS=500) ......... → WBE2041 on overflow
      ├─ Header-row check (locale-aware) ...... → WBE2042 on mismatch
      ├─ Unreadable / corrupt workbook ........ → WBE2043
      ├─ Per-cell validation (required, length, regex, in-file dup, DB dup)
      └─ Return { rows[], totalCount, completedCount, errorCount }
             each row carries valid + per-field errors {field, code, message}
      │  (FE renders preview table; error cells tinted; Upload enabled only when errorCount handling allows)
      ▼
[FE] Confirm Upload  →  JSON POST .../excel/import  (rows[])
      ▼
[BE] IMPORT  (atomic @Transactional)
      ├─ RE-VALIDATE every row against live DB (never trusts preview `valid`)
      ├─ Generate PK / sequence (Purpose: wb_get_next_val; VAS: Java MAX+1)
      ├─ Persist rows (two-pass for parent/child in Purpose)
      ├─ Append ONE audit row to AUDIT_LOGS_INFM (slug WSVC_ID)
      └─ Return { insertedCount [, replacedCount] }
```

### Step notes

- **Preview is advisory.** The `valid` flag returned by preview is not trusted at import — import re-runs validation against the current DB (Purpose: `PurposeExcelService.import_`; VAS: `VasAccountExcelService.import_`). A row that passed preview can still fail import if the DB changed in between (returns `WBE_VALIDATION` with a 1-based row index).
- **Atomicity (canonical features).** Import is one `@Transactional` unit — any row failure rolls back the whole batch. Contrast with CoA/Contacts (per-row, non-atomic — a row failure does not roll back committed rows).
- **Key generation.** Purpose Settings funnels through `wb_get_next_val` via `LegacyFunctionRepository.nextUseUsagCd(...)`. VAS Account uses a Java-side `MAX+1` generator (`VasAcctSeqNoGenerator`) — a deliberate, documented deviation, see [database-mapping.md](database-mapping.md) and the funnel memory.
- **Audit.** Each successful import appends exactly one `AUDIT_LOGS_INFM` row through the shared audit writer (`auditLogService.append(...)`) with the **slug** as `WSVC_ID` (`purpose-settings` / `vas-account`), per the workspace identifier-naming rule. See [`../audit-log/README.md`](../audit-log/README.md).

## VAS Account — Append vs Replace

VAS import carries an `upldOptn` field (`"A"` append / `"R"` reset-and-replace, validated by `@Pattern "^[AR]$"`):

- **Append (`A`):** insert new rows; duplicates checked against live + in-file.
- **Replace (`R`):** soft-delete all live rows for the tenant first, then insert — all within the one import transaction.

Purpose Settings has **no** upload-option field — it is pure append.

## Non-canonical variant (Chart of Accounts · Contacts)

These features have **no server-side preview**. The browser parses the workbook, builds preview rows, then posts JSON:

- **Chart of Accounts:** `POST .../bulk-upload` — per-row independent, **not atomic**.
- **Contacts:** `POST .../excel/import` (per-row, non-atomic, always HTTP 200 with partial `errors[]`), preceded optionally by `POST .../excel/preflight` (duplicate pre-check) and `POST .../excel/resolve-recipients` (bank/recipient resolution for the preview). Capped at 500 rows.

Table-specific column mapping, required fields, and duplicate semantics for **all four** features are documented in the owning feature docs, not duplicated here:

- Purpose — [`../../settings/purpose/business-flow.md`](../../settings/purpose/business-flow.md)
- VAS Account — [`../../settings/vas-account/business-flow.md`](../../settings/vas-account/business-flow.md)
- Chart of Accounts — [`../../settings/chart-of-accounts/business-flow.md`](../../settings/chart-of-accounts/business-flow.md)
- Contacts — [`../../settings/contacts/business-flow.md`](../../settings/contacts/business-flow.md)

## Related

- [functional-specification.md](functional-specification.md) · [interface-specification.md](interface-specification.md) · [README.md](README.md)
- WEB twin flow: [`../../../../../wecms-web/docs/features/common/excel-import/screen-flow.md`](../../../../../wecms-web/docs/features/common/excel-import/screen-flow.md)
