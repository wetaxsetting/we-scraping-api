# File Management — Common Pattern + Storage Policy (Backend)

**Part of:** Common → file-management
**Last verified:** 2026-06-23

Shared file **upload + download + attachment pattern** for the WeCMS refactor backend. This is documentation only — it describes a cross-cutting **pattern and storage policy** plus the concrete file primitives that already exist in code. It does **not** introduce a new module.

> **KEY FRAMING — there is NO generic `/api/v1/attachments` endpoint.** File upload / download / attachment in the refactor is a Common **pattern + storage policy**, not a standalone resource. The only concrete attachment table that exists today, `TRCO_INFO_FILE`, is **owned by Settings → `contacts`**. The read-only ERP file download is **owned by Settings → `erp-transaction-history`**. This hub documents the shared rules + the file primitives and **cross-links** the owning features. It does **not** duplicate contacts / ERP CRUD.

## What this covers

- The shared **upload flow** (validate → server-generated save-name → tenant-scoped storage → file-id allocation → DB row), as implemented by `ContactsFileService`.
- The shared **download flow** (locate row → tenant-scope check → resolve physical path → stream), as implemented by `ContactsFileService.openForDownload` and `ErpTrnsHistoryService.resolveDownload`.
- The **storage policy** — tenant-scoped `<storage-root>/<biz_key>/<slug>/<save_file_nm>` layout, server-generated UUID save-names, path-traversal guards, per-module storage-root config keys.
- The file **primitives** — `TrcoInfoFile` entity, the `wb_get_next_val('trco_info_file', …)` file-id branch via `LegacyFunctionRepository`, the per-module `*FileResolver` + `*StorageProperties`.

## What this does NOT cover (owned elsewhere)

- Contacts CRUD, list, detail, bulk import/export → **Settings `contacts`**.
- ERP transaction-history list / counts / tab filtering → **Settings `erp-transaction-history`**.
- Excel/XLSX export of list data → the **`common/export/`** pipeline (a different "produce a file" feature — not attachment upload/download).

## Documents index

| Doc | Purpose |
|---|---|
| [business-flow.md](business-flow.md) | Upload + download + ERP download flows, end-to-end |
| [functional-specification.md](functional-specification.md) | Validation rules, storage policy, save-name + title defaults |
| [database-mapping.md](database-mapping.md) | `TRCO_INFO_FILE` columns / PK / entity, file-id allocation, ERP filesystem-vs-metadata split |
| [interface-specification.md](interface-specification.md) | Concrete endpoints (contacts files + ERP download) + error codes + storage config keys |
| [migration-guide.md](migration-guide.md) | Legacy JEX file WSVC → refactor mapping; storage path scheme divergence |
| [refactor-gap-analysis.md](refactor-gap-analysis.md) | Gap vocabulary — generic `/attachments` = MISSING-by-design (C4 open decision); contacts-specific = APPROVED |
| [source-code-index.md](source-code-index.md) | Every file:line backing this pattern (backend + frontend + tests) |
| [test-scenarios.md](test-scenarios.md) | Oversize / bad-extension / missing-file / path-traversal / tenant-isolation cases |

## Related

- Common hub: [`../README.md`](../README.md)
- WEB twin: [`../../../../../wecms-web/docs/features/common/file-management/README.md`](../../../../../wecms-web/docs/features/common/file-management/README.md)
- Owning feature — Contacts (attachment table `TRCO_INFO_FILE`): [`../../settings/contacts/README.md`](../../settings/contacts/README.md)
- Owning feature — ERP Transaction History (file download): there is **no** `docs/features/settings/erp-transaction-history/` hub today; ERP docs live under references — [`../../../references/business-rules/settings/erp-transaction-history.md`](../../../references/business-rules/settings/erp-transaction-history.md), [`../../../references/database/settings/erp-transaction-history-schema.md`](../../../references/database/settings/erp-transaction-history-schema.md), [`../../../references/migration/erp-transaction-history-mapping.md`](../../../references/migration/erp-transaction-history-mapping.md). The menu/permission slug is `settings-erp-transaction-history`.
- Sibling Common feature — Excel export: [`../excel-export/README.md`](../excel-export/README.md)
- Audit deliverables: [`../../../audits/common-source-index.md`](../../../audits/common-source-index.md)
