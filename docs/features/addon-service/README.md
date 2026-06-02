# features/addon-service/

Feature specs for **add-on / supplementary** modules in the refactor.

## Purpose

Add-ons are auxiliary modules that ship alongside the core WeCMS feature set: e-invoice integrations (sales / expenses) and operational-status inquiry views. They are first-class features but conceptually distinct from the banking / accounting / approval / settings domains because they are optional per tenant.

## Scope

| File | Refactor scope |
|---|---|
| [sales-e-invoices.md](sales-e-invoices.md) | Sales e-invoice fetch / display / status sync |
| [expenses-e-invoices.md](expenses-e-invoices.md) | Expense e-invoice fetch / display / status sync |
| [operation-status-inquiry.md](operation-status-inquiry.md) | Operational status / job monitoring view |

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
- Planning: [`../../planning/addon/`](../../planning/addon/)
