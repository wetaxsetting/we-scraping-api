# features/common/ (Backend)

**Cross-cutting system domain** — shared, screen-agnostic capabilities wired once and consumed product-wide
(Banking / Payment / Add-on / Settings). This is **not** a WeCMS top-menu group; it is a **system feature
group**. Formalized 2026-06-01 (OQ-DOC-2); standardized into per-module folders 2026-06-23.

## Doc convention (read first)

Each module has a `{module}/` **folder** carrying the authoritative 9-file detail
(`README` · `business-flow` · `functional-specification` · `database-mapping` · `interface-specification` ·
`migration-guide` · `refactor-gap-analysis` · `source-code-index` · `test-scenarios`). Two modules
(`view-setting`, `audit-log`) also keep a flat `{module}.md` **high-level summary** with a banner pointing
into the folder. When a summary and the folder disagree, the **folder wins**; when either disagrees with the
**code, the code wins**.

Status legend: ✅ Implemented · 🟡 Partial (live but with a deferred slice / open decision) · ⚪ WEB-only · ❌ MISSING.

## Common modules → docs

| # | Module | Folder | Summary | Status |
|---|---|---|---|---|
| 1 | View Setting (Screen Options) | [view-setting/](view-setting/README.md) | [view-setting.md](view-setting.md) | ✅ Implemented |
| 2 | Audit Log (shared writer) | [audit-log/](audit-log/README.md) | [audit-log.md](audit-log.md) | ✅ Implemented (write-path; no REST endpoint) |
| 3 | Excel Export | [excel-export/](excel-export/README.md) | — | ✅ Implemented (shared `common/export` pipeline; 20 registered providers) |
| 4 | Excel Import | [excel-import/](excel-import/README.md) | — | 🟡 Partial (shared FE shell + per-feature BE services; no shared BE service — OQ C3) |
| 5 | File Management (upload/download/attachment) | [file-management/](file-management/README.md) | — | ✅ Implemented (contacts upload + ERP download; generic `/attachments` not built — OQ C4) |
| 6 | Notification | [notification/](notification/README.md) | — | ✅ Implemented (`domain/payment/notification` on `BLTB_*`; General `TYPE='G'` branch not ported — OQ C1) |
| 7 | Navigation (product menu tree) | [navigation/](navigation/README.md) | — | ✅ Implemented (V137–V139 catalogue; per-menu `@menuAuthz` F-3 enforcement) |
| 8 | Common Code / Lookup | [common-code-lookup/](common-code-lookup/README.md) | — | 🟡 Partial (Country endpoint live; banks/currencies domain-embedded — OQ C6) |
| 9 | Table Sort | [table-sort/](table-sort/README.md) | — | ✅ Implemented (query-param contract; `common/web` + 28 per-domain whitelist enums) |
| 10 | Dialog / Popup | [dialog-popup/](dialog-popup/README.md) | — | ⚪ WEB-only (no backend surface; API README is a stub → frontend) |
| 11 | Error Envelope (response/exception/trace/i18n) | [error-envelope/](error-envelope/README.md) | — | ✅ Implemented (applies to every endpoint) |
| 12 | Bank Cut-off Validation | [bank-cutoff-validation/](bank-cutoff-validation/bank-cutoff-validation.md) | — | ✅ Implemented (shared `CutoffValidator`; all 5 payment product types; Shinhan CITAD 16:30 + VCB) |

## Cross-linked — owned by a domain (no Common folder)

These appeared in the original "Common" wish-list but their real implementation is owned by a feature domain;
documented there, summarized here only as a pointer.

| Capability | Real owner | Doc |
|---|---|---|
| Approval (flow / status / references) | Payment `approval-box` (execution) + Settings `approval-line` (config) | [../payment/approval-box/README.md](../payment/approval-box/README.md) · [../settings/approval-line/README.md](../settings/approval-line/README.md) |
| Reporting Account (chart-account selection) | Settings `chart-of-accounts` | [../settings/chart-of-accounts/README.md](../settings/chart-of-accounts/README.md) |
| Attachment (concrete table) | Settings `contacts` (`TRCO_INFO_FILE`) | [../settings/contacts/README.md](../settings/contacts/README.md) (shared mechanics in [file-management/](file-management/README.md)) |

## DEAD / MISSING

| Item | State |
|---|---|
| **Dashboard** (`SYS_DSHBRD_INFM` / `USER_DSHBRD_INFM`) | ❌ MISSING — no code, tables never migrated. README note only; no stub folder (OQ C5). |
| **Old `domain/notification/` + `NOTIFICATION` table (V238)** | DEAD LEGACY — removed 2026-06-10, superseded by `notification/` on `BLTB_*`. Do not document as live. |

## What does NOT live here

- Per-table column lists for View Setting → on the **owning** feature spec under `../banking/` / `../payment/` /
  `../addon-service/` / `../settings/`, referenced from the screen registry.
- Per-feature audit-write specifics → on each owning feature doc (Common owns only the shared `audit-log` writer).
- Frontend hook / dialog architecture → [`../../../../wecms-web/docs/architecture/`](../../../../wecms-web/docs/architecture/).

## Audit deliverables (planning baseline)

- [`../../audits/common-docs-audit.md`](../../audits/common-docs-audit.md) — docs-vs-code re-audit
- [`../../audits/common-gap-analysis.md`](../../audits/common-gap-analysis.md) — gap vocab A–G + open decisions (C1–C6)
- [`../../audits/common-source-index.md`](../../audits/common-source-index.md) — validated source-code index

## Naming traceability

Every Common slug matches its frontend counterpart under
[`../../../../wecms-web/docs/features/common/`](../../../../wecms-web/docs/features/common/) and, where a JEX
twin exists, the `docs/jex/features/` slug (e.g. `view-setting`). Refactor identifiers are kebab-case slugs,
never legacy JEX WSVC ids.
