# Settings › Chart of Accounts — Backend Feature (wecms-api)

**Slug:** `chart-of-accounts`
**Module:** Settings (WABOOKS accounting master data)
**Package:** `com.kosign.wecms.domain.setting.chartofaccounts`
**REST base:** `/api/v1/settings/chart-of-accounts`
**Table:** `CHART_ACCT_INFM`
**Legacy WSVC (traceability only):** `comm_0223_001` (view + CRUD) · `comm_0223_002` (Excel upload)
**Status (2026-06-08):** 🟢 **Code-complete for SIT** · 🟡 final closure pending PO ratification only — SIT/UAT verified.

> This folder is the **primary documentation home** for the Chart of Accounts backend feature. It documents the **actual final refactored implementation** (read from source on 2026-06-07/08), not the pre-implementation design intent. Where the design-era docs disagree with shipped code, the shipped behaviour wins and the delta is flagged inline.

---

## What this feature does

Per-tenant chart of accounts master list used by the WABOOKS accounting / financial-report modules to classify booked transactions (banking, payment, card, e-invoice) under a hierarchical accounting code. Administrators can:

- **Create** chart accounts (with a required parent)
- **Update** chart accounts (rename, re-parent, change status / cal-rule)
- **Activate / Deactivate** accounts (single-row via update; **bulk deactivate** via dedicated endpoint)
- **Manage the hierarchy** (parent picker tree, level computation, max depth **3**, cycle/self-parent guards)
- **Import** accounts via Excel (front-end parse + validate → backend per-row bulk create)
- **Search & filter** accounts (server-side `LIKE` on code / EN name + sort whitelist)
- **Soft-delete** accounts (with child re-parent + revive-on-recreate + archive-rename)

The three roots `ACC-01` (REVENUE), `ACC-02` (EXPENSES), `ACC-03` (INVESTMENT) are system-seeded and **restricted** (cannot be edited / deleted). Level 1 is reserved for these roots — every user-created node has a parent.

---

## Documentation index

| File | Contents |
|---|---|
| [README.md](README.md) | This file — overview, status, index, cross-links |
| [business-flow.md](business-flow.md) | End-to-end workflow + per-operation flows (create, update, activate/deactivate, delete, import) |
| [functional-specification.md](functional-specification.md) | Capabilities, business rules, validation, restricted roots, hierarchy, soft-delete semantics |
| [database-mapping.md](database-mapping.md) | `CHART_ACCT_INFM` schema + legacy→entity→DTO→UI column mapping |
| [interface-specification.md](interface-specification.md) | All 9 REST endpoints, request/response DTOs, error codes |
| [source-code-index.md](source-code-index.md) | Controller / service / repository / DTO / entity / validator / tree-builder map + legacy source mapping |
| [migration-guide.md](migration-guide.md) | JEX → refactor service/IDO/DAO mapping + behavioural deltas |
| [test-scenarios.md](test-scenarios.md) | SIT/UAT scenarios (search, create, update, activation, import, validation, errors) |
| [refactor-gap-analysis.md](refactor-gap-analysis.md) | Final parity review (cross-links the authoritative audit) |

---

## Key implemented facts (authoritative)

| Aspect | Value | Source |
|---|---|---|
| Endpoints | **9** — list, tree, detail, create, update, bulk-upload, bulk-delete, bulk-deactivate, delete | `ChartOfAccountsController.java` |
| Max hierarchy depth | **3** (`MAX_HIERARCHY_DEPTH = 3`; level 1 reserved for ACC-0x roots) | `ChartOfAccountsConstants.java:41` |
| Bulk Activate | **Not implemented** (only bulk-deactivate); single-row re-activation via `PUT` status flip. PO-deviation candidate (CA-G1). | `ChartOfAccountsService.java`; audit CA-G1 |
| Excel **Export** | **Not implemented** for Chart of Accounts — only a static **template download** exists. Account Inquiry is the only wired `common/export` consumer. | `ExcelUploadDialog.tsx:33-37` |
| Excel **Import** | FE parses + validates (SheetJS); backend `POST /bulk-upload` accepts a JSON array and runs per-row `create()`. | `useExcelUpload.ts`; `ChartOfAccountsService.bulkUpload` |
| Status | `STTS` `A` (Active) / `I` (Inactive) — distinct from soft-delete `DEL_YN` `N`/`Y` | `ChartOfAccount.java` |
| Authorization | `@PreAuthorize("@menuAuthz.canAccess('settings-core-chart-of-accounts')")` — **live at runtime** | `ChartOfAccountsController.java:79` |
| Audit | One `AUDIT_LOGS_INFM` row per Create/Update/Delete, `WSVC_ID = "chart-of-accounts"` (slug) | `ChartOfAccountsService.java`; `ChartOfAccountsConstants.AUDIT_REF_NO` |

---

## Cross-links (preserved existing docs — do not duplicate)

- **Final parity audit (authoritative gap analysis):** [../../../audits/chart-of-accounts-final-parity-audit.md](../../../audits/chart-of-accounts-final-parity-audit.md)
- **JEX→refactor migration map:** [../../../references/migration/chart-of-accounts.md](../../../references/migration/chart-of-accounts.md)
- **DB reference:** [../../../references/database/chart-of-accounts.md](../../../references/database/chart-of-accounts.md)
- **Backend business-rules reference:** [../../../references/business-rules/chart-of-accounts.md](../../../references/business-rules/chart-of-accounts.md)
- **Original design spec (pre-implementation):** [../chart-of-accounts.md](../chart-of-accounts.md) — kept for traceability; superseded by this folder for implementation truth.
- **Legacy JEX dossier (read-only reference):** [../../../../../docs/jex/features/chart-of-accounts/](../../../../../docs/jex/features/chart-of-accounts/)
- **Frontend counterpart:** [../../../../../wecms-web/docs/features/settings/chart-of-accounts/](../../../../../wecms-web/docs/features/settings/chart-of-accounts/)
- **Open questions ledger:** [../../../planning/open-questions/documentation-follow-up.md](../../../planning/open-questions/documentation-follow-up.md) · memory `project-chart-of-accounts-oq-pending`
