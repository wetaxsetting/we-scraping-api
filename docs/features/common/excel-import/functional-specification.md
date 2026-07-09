# Excel Import — Functional Specification (Backend)

**Part of:** Common → excel-import
**Last verified:** 2026-06-23

Generalised spec for the import capability. Table-specific column lists / required fields live in each owning feature's `functional-specification.md` (cross-linked). This doc covers the rules that are **common to the pattern** plus where they diverge.

## 1. Preview validation (canonical features: Purpose, VAS Account)

Preview is a **read-only** transaction. It parses the first sheet with Apache POI and returns a per-row, per-cell result. Three workbook-level rejections short-circuit before per-cell validation:

| Check | Code | Meaning | Source |
|---|---|---|---|
| Row count over cap (`MAX_ROWS = 500`) | `WBE2041` | "Excel file exceeds the {0}-row limit." | `PurposeExcelService.java:221`; `VasAccountExcelService.java:224` |
| Header row does not match template | `WBE2042` | "Excel header row is invalid ({0})." | `PurposeExcelService.java:586,596`; `VasAccountExcelService.java:450,455` |
| Workbook unreadable / corrupt | `WBE2043` | "Uploaded file is not a readable Excel workbook." | `PurposeExcelService.java:336`; `VasAccountExcelService.java:275` |

> ⚠️ `WBE2040` is **not** an Excel code — it is VAS Account "not found" (`VasAccountService.java:304,373,432`). Do not list it in the import family. `WBE2044` is **retired/reserved** — do not reuse.

### Header detection

The header is located at a fixed POI row index (Purpose: `HEADER_ROW_INDEX = 2`, data from row 3; VAS: row index 2, columns B/C). Header matching is **locale-aware and whitespace-tolerant** (EN/KO/VI labels accepted; leading/trailing/run-on whitespace normalised). The frontend `templateValidator.ts` performs an equivalent client-side check **before** upload (see WEB docs) — the server check is authoritative.

### Per-cell rules (illustrative — Purpose Settings)

- **Code:** required, ≤ 20 chars, no in-file duplicate (compared upper-cased), no DB duplicate against active tenant rows.
- **Name:** required, ≤ 255 chars.
- **Description:** ≤ 500 chars.
- **Parent:** ≤ 20 chars + two-pass resolution (must be an in-file root OR a DB level-1 row) → `parent_not_found` / `parent_not_root`.

VAS Account code rules differ (3–15 chars, regex `^[A-Za-z0-9_]+$`, name ≤ 50). The exact per-column rules for each feature are in that feature's spec — not duplicated here.

### Preview response shape

Each preview returns `{ rows[], totalCount, completedCount, errorCount }`. Each row carries `rowIndex`, the mapped fields, a `valid` boolean, and `errors: [{ field, code, message }]`. The frontend tints error cells and shows the Completed / Error chips from `completedCount` / `errorCount`.

## 2. Import execution semantics

| Property | Purpose Settings | VAS Account | Chart of Accounts | Contacts |
|---|---|---|---|---|
| Server preview endpoint | yes (multipart) | yes (multipart) | **no** | **no** |
| Re-validates at import | yes | yes | (per owning feature) | (per owning feature) |
| Atomic | **yes** (`@Transactional`) | **yes** | **no** (per-row) | **no** (per-row) |
| Upload option | none (append only) | `A` / `R` | (per owning feature) | (per owning feature) |
| Key generation | `wb_get_next_val` | Java MAX+1 | (per owning feature) | (per owning feature) |
| Audit row | 1 (slug `purpose-settings`) | 1 (slug `vas-account`) | (per owning feature) | (per owning feature) |
| Result | `insertedCount` | `insertedCount`, `replacedCount` | per-row response | per-row `errors[]` |

**Re-validation rule (canonical).** Import never trusts the preview `valid` flag — it re-validates against live DB. A row that fails at import surfaces `WBE_VALIDATION` with a 1-based row index.

## 3. Idempotency / duplicate handling

There is **no replay/idempotency key** for import (consistent with the workspace decision to not add refactor-only idempotency tables — see memory `project_oat_idempotency_dropped.md`). Re-submitting the same workbook will:

- **Purpose / VAS Append:** be caught by the DB-duplicate check during re-validation (the code already exists), failing the row.
- **VAS Replace (`R`):** soft-delete all live tenant rows, then insert — so a second Replace produces the same end state (effectively idempotent for the active set), but it is a destructive operation gated behind an explicit upload-option choice.

⚠️ **UNVERIFIED:** duplicate/idempotency semantics for CoA `bulk-upload` and Contacts `excel/import` beyond "per-row, non-atomic, capped at 500" — see those features' specs and confirm before relying on them.

## 4. Error reporting

- **Workbook-level** failures (`WBE2041/2042/2043`) are returned as a single business error on the preview call.
- **Per-cell** failures are returned inline in the preview rows (`errors[]`), so the UI can tint specific cells.
- **Import-time** row failures (canonical features) surface as `WBE_VALIDATION` with the offending 1-based row index; because import is atomic the whole batch rolls back.

## Related

- [interface-specification.md](interface-specification.md) — exact endpoints, DTOs, codes
- [business-flow.md](business-flow.md) · [database-mapping.md](database-mapping.md) · [README.md](README.md)
- Owning-feature specs: [`../../settings/purpose/functional-specification.md`](../../settings/purpose/functional-specification.md) · [`../../settings/vas-account/functional-specification.md`](../../settings/vas-account/functional-specification.md) · [`../../settings/chart-of-accounts/functional-specification.md`](../../settings/chart-of-accounts/functional-specification.md) · [`../../settings/contacts/functional-specification.md`](../../settings/contacts/functional-specification.md)
