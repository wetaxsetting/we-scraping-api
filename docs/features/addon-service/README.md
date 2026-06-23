# features/addon-service/

Feature specs for **add-on / supplementary** modules in the refactor.

> ⚠️ **This README and the root-level single-file specs are high-level summaries.**
> The detailed source of truth is the per-module folder documentation under
> `docs/features/addon-service/{module}/` (+ the implementation code). When a
> summary and the folder docs/code disagree, the **folder docs and code win**.

## Purpose

Add-ons are auxiliary modules that ship alongside the core WeCMS feature set: e-invoice integrations (sales / expenses) and operational-status inquiry views. They are first-class features but conceptually distinct from the banking / accounting / approval / settings domains because they are optional per tenant.

## Scope

| File | Refactor scope |
|---|---|
| [sales-e-invoices/](sales-e-invoices/) ✅ · [sales-e-invoices.md](sales-e-invoices.md) | Sales e-invoice list / export / scrape-intake / history (**consolidated, code-accurate docs in the folder**; the single file is a concise high-level summary) |
| [expenses-e-invoices/](expenses-e-invoices/) ✅ · [expenses-e-invoices.md](expenses-e-invoices.md) | Expense e-invoice — shares the Sales backend (`type=EXPENSE` / `DOC_TYPE='B'`); folder documents the deltas (single file is a concise high-level summary) |
| [operation-status-inquiry.md](operation-status-inquiry.md) · [operation-status-inquiry/](operation-status-inquiry/) ✅ | Tax-authority operating-status inquiry (consolidated feature docs in the folder; the single file is a concise high-level summary) |
| [authorization-and-product-type.md](authorization-and-product-type.md) ✅ | **Module-wide authorization & product-type (F/M/H, W question) matrix** — `@menuAuthz` gate wiring, `PRODUCT_MENU` (V243) membership, admin / normal-with / normal-without behaviour. Source-verified 2026-06-06 (supersedes the older "product gate deferred" notes). |

> **Status (all three screens ✅ Implemented, verified through 2026-06-23):**
> - **Sales / Expenses e-Invoices** — shared backend (`domain/einvoice`) implemented end-to-end: list + multi-currency totals + pagination/sort + two XLSX exports + two-phase scrape intake + GDT inquiry/detail history. `resolveDocType` maps `SALES→'I'`, `EXPENSE`/`BILL`→`'B'`, rejects `'E'`/`'S'` with `WBE2002`. (The historical "static UI only / `EXPENSE→'E'` bug / `WBE0001`" claims are stale.)
> - **Operation Status Inquiry** — `domain/operation` implemented end-to-end: list + inquire (live OSMC→GDT bridge) + **save** (`POST /operation-status` → `#create`) + bulk update + XLSX export. (The historical "Save is a stub / no upsert endpoint" claim is stale.)
> - **Remaining gap (both e-Invoice pages):** invoice-**status** filter is client-side (backend IR-4 status param not implemented — PO decision pending).

## Route / permission / screenId mapping

| Screen | Route | Menu permission code (`@menuAuthz`) | View Setting / export screenId |
|---|---|---|---|
| Sales e-Invoices | `/addon/sales-einvoices` | `addon-einvoices-sales` | `addon-sales-einvoices` |
| Expenses e-Invoices | `/addon/expenses-einvoices` | `addon-einvoices-expenses` | `addon-expenses-einvoices` |
| Operation Status Inquiry | `/addon/operation-status` | `addon-operation-status-inquiry` | `addon-operation-status` |

> ⚠️ The **menu permission code** and the **View-Setting/export screenId** are different strings (word order flips for e-Invoices: `einvoices-sales` vs `sales-einvoices`). The runtime `@menuAuthz` gate checks the permission code; the export / column-settings layer checks the screenId. Product gating: **F + M get Add-on; HAENGSUNG (H) omits it** (V243 nav seed).

## Document standards

- Frontmatter table: `JEX counterpart`, `Refactor status`, `Owner`, `Sprint`.
- For each integration, document the third-party API contract reference and the local data model.
- If an add-on is tenant-scoped (enabled per biz_key), document the enablement check explicitly.

## Naming convention

- kebab-case slug matching JEX counterpart.
- No spaces, no uppercase.

## Related

- Backend per-feature design: [`../../backend/addon/`](../../references/api-spec/addon/)
- Frontend per-feature design: [`../../frontend/addon/`](../../../../wecms-web/docs/features/addon-service/)
- Database per-feature schema: [`../../database/addon/`](../../references/database/addon/)
- Data model (ERD): [Add-on Service ERD](../../references/database/erd/addon-service-erd.md) ([Mermaid](../../references/database/erd/diagrams/addon-service-erd.mmd)) — e-invoice, operation status, card transactions. Cross-module map: [full-project ERD](../../references/database/erd/full-project-erd.md). Coverage: [database-coverage-matrix.md](../../references/database/database-coverage-matrix.md) · ownership: [table-ownership.md](../../references/database/table-ownership.md).
- Planning: `../../planning/addon/` _(removed — historical `planning/` folder deleted by project decision)_
