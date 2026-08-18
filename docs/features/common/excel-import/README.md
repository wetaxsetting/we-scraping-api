# Excel Import (Backend)

**Part of:** Common → excel-import
**Last verified:** 2026-06-23

Hub for the WeCMS refactor **Excel Import** capability — the "Download template → Browse file → preview rows → confirm Upload" flow that several Settings screens use to bulk-create records from an `.xlsx`/`.xls` workbook.

## Architecture in one line

> **Excel Import is a shared FRONTEND shell + per-feature BACKEND services. There is NO single shared backend import service.**

This is the central fact of this module. The frontend `<ExcelUploadDialog>` shell (preview chrome, error-cell highlighting, summary chips) is genuinely shared and reusable; the backend is **not** — every owning feature implements its own preview/import endpoints, DTOs, validation, persistence, and audit write. A common backend import service is **open decision C3** (MISSING) in [refactor-gap-analysis.md](refactor-gap-analysis.md).

Do **not** read this hub as "there is a `/api/v1/common/excel/import` endpoint" — there is not. Each owning feature owns its own contract (see [interface-specification.md](interface-specification.md)).

## What the backend actually exposes

Two distinct architectures exist across the owning features — verified, not assumed:

| Owning feature | Server-side preview? | Import endpoint | Parse location | Atomicity | Audit | Docs (this folder cross-links the owning feature) |
|---|---|---|---|---|---|---|
| **Purpose Settings** | ✅ `POST .../excel/preview` (multipart POI parse) | `POST .../excel/import` (JSON rows) | server (Apache POI) | atomic (`@Transactional`) | ✅ slug `purpose-settings` | `../../settings/purpose/README.md` |
| **VAS Account** | ✅ `POST .../excel/preview` (multipart POI parse) | `POST .../excel/import` (JSON rows, A/R option) | server (Apache POI) | atomic (`@Transactional`) | ✅ slug `vas-account` | `../../settings/vas-account/README.md` |
| **Chart of Accounts** | ❌ no multipart preview endpoint | `POST .../bulk-upload` (JSON rows) | client (browser) | per-row, **non-atomic** | (see owning feature) | `../../settings/chart-of-accounts/README.md` |
| **Contacts** | ❌ no multipart preview; split into `preflight` + `resolve-recipients` | `POST .../excel/import` (JSON rows) | client (browser) | per-row, **non-atomic** | (see owning feature) | `../../settings/contacts/README.md` |

Only **Purpose Settings** and **VAS Account** follow the canonical multipart-`preview` + JSON-`import` two-step pattern with server-side POI parsing. Chart of Accounts and Contacts parse the file in the browser and post JSON rows — they have **no server-side preview endpoint**. This divergence is the PARTIAL finding in the gap analysis.

> ⚠️ **Premise correction.** An earlier framing assumed CoA/Contacts had no import endpoints. They **do** (`bulk-upload`, `excel/import` + `excel/preflight` + `excel/resolve-recipients`) — they just use a different (client-parse, non-atomic) architecture. Verified in [source-code-index.md](source-code-index.md).

## Documents

| Doc | Scope |
|---|---|
| [business-flow.md](business-flow.md) | End-to-end preview → validate → import → persist → audit flow per confirmed feature |
| [functional-specification.md](functional-specification.md) | Preview validation rules, error reporting, import execution semantics, duplicate/idempotency handling (generalised + cross-linked) |
| [database-mapping.md](database-mapping.md) | No own table — per-feature target tables + `wb_get_next_val` usage |
| [interface-specification.md](interface-specification.md) | The per-feature `POST .../preview` + `POST .../import` contract pattern, WBE204x codes, DTO shapes |
| [migration-guide.md](migration-guide.md) | Legacy JEX excel-upload WSVC → refactor preview/import, and the per-feature spread |
| [refactor-gap-analysis.md](refactor-gap-analysis.md) | Gap vocab — shared BE service = MISSING/C3; per-feature divergence = PARTIAL |
| [source-code-index.md](source-code-index.md) | Per-feature backend services/controllers + shared FE shell + tests |
| [test-scenarios.md](test-scenarios.md) | Preview reject paths, header mismatch, duplicate rows, import success + audit |

## Related

- **WEB twin:** [`../../../../../wecms-web/docs/features/common/excel-import/README.md`](../../../../../wecms-web/docs/features/common/excel-import/README.md)
- **Sibling — Excel Export:** [`../excel-export/README.md`](../excel-export/README.md) (the export half is genuinely shared backend; import is not)
- **Audit log writer:** [`../audit-log/README.md`](../audit-log/README.md) — the shared `AUDIT_LOGS_INFM` writer that import flows append to
- **Common docs hub:** [`../README.md`](../README.md)
- **Owning features (table-specific validation lives there — not duplicated here):**
  - Purpose Settings — [`../../settings/purpose/README.md`](../../settings/purpose/README.md)
  - VAS Account — [`../../settings/vas-account/README.md`](../../settings/vas-account/README.md)
  - Chart of Accounts — [`../../settings/chart-of-accounts/README.md`](../../settings/chart-of-accounts/README.md)
  - Contacts — [`../../settings/contacts/README.md`](../../settings/contacts/README.md)
- **Audit deliverables (common module):** [`../../../audits/common-source-index.md`](../../../audits/modules/common-source-index.md) · [`../../../audits/common-gap-analysis.md`](../../../audits/modules/common-gap-analysis.md) · [`../../../audits/common-docs-audit.md`](../../../audits/modules/common-docs-audit.md)
