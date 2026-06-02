# features/settings/

Canonical doc per **Settings** menu item for the refactor. One file per screen — the full
"Refactor Feature Spec" (scope, JEX traceability, REST endpoints, DB tables, business rules,
validation, error cases). This folder consolidates what previously lived across `settings/`,
`features/accounting/`, `features/approval/`, `features/contacts/`, and `features/admin/`.

## Settings menu → docs

| # | Settings item | Doc | Status |
|---|---|---|---|
| 1 | Purpose | [purpose.md](purpose.md) | Phase 1 design |
| 2 | VAS Account | [vas-account.md](vas-account.md) | Implemented |
| 3 | Contacts | [contacts.md](contacts.md) | Implemented |
| 4 | Approval Line | [approval-line.md](approval-line.md) | Implemented |
| 5 | Chart of Accounts | [chart-of-accounts.md](chart-of-accounts.md) | Implemented |
| 6 | Login Credentials | [login-credentials.md](login-credentials.md) | Phase 3 — wired |
| 7 | Bank Account | [bank-account.md](bank-account.md) | Implemented |
| 8 | Preferences | [preferences.md](preferences.md) | Implemented |
| 9 | Users | [users.md](users.md) | Plan |
| 10 | Transaction History (ERP) | [transaction-history.md](transaction-history.md) | Phase 1 design |
| 11 | Firm Banking Settings | [firm-banking-settings.md](firm-banking-settings.md) | Implemented |
| 12 | Firm Banking History | [firm-banking-history.md](firm-banking-history.md) | Implemented |

**ProfileMenu (not a Settings-menu item, kept here for proximity):**
[change-password.md](change-password.md) — self-service password change reached from the header
profile dropdown (legacy `comm_0401_001` / `comm_0400_001_u001`).

## Related reference docs (per screen, cross-linked — not duplicated)

| Screen | Business rules | DB schema | JEX → Spring mapping | Parity audit |
|---|---|---|---|---|
| Purpose | [purpose-settings.md](../../references/business-rules/settings/purpose-settings.md) | [purpose-settings-schema.md](../../references/database/settings/purpose-settings-schema.md) | [purpose-settings.md](../../references/migration/purpose-settings.md) | [purpose-settings-audit.md](../../references/audits/purpose-settings-audit.md) |
| VAS Account | [vas-account.md](../../references/business-rules/vas-account.md) | [vas-account.md](../../references/database/vas-account.md) | [vas-account.md](../../references/migration/vas-account.md) | [vas-account.md](../../references/audits/vas-account.md) |
| Contacts | [contacts-design.md](../../references/business-rules/contacts-design.md) | [contacts-schema.md](../../references/database/contacts-schema.md) | [contacts-mapping.md](../../references/migration/contacts-mapping.md) | — |
| Approval Line | [approval-line.md](../../references/business-rules/approval-line.md) | — | [approval-line.md](../../references/migration/approval-line.md) · [jex-analysis](../../references/migration/jex-analysis/approval-line-add-flow-analysis.md) | — |
| Chart of Accounts | [chart-of-accounts.md](../../references/business-rules/chart-of-accounts.md) | [chart-of-accounts.md](../../references/database/chart-of-accounts.md) | [chart-of-accounts.md](../../references/migration/chart-of-accounts.md) | — |
| Bank Account | — | — | — | — |
| Transaction History (ERP) | [erp-transaction-history.md](../../references/business-rules/settings/erp-transaction-history.md) | [erp-transaction-history-schema.md](../../references/database/settings/erp-transaction-history-schema.md) | [erp-transaction-history-mapping.md](../../references/migration/erp-transaction-history-mapping.md) | [erp-transaction-history-audit.md](../../references/audits/erp-transaction-history-audit.md) |
| Firm Banking Settings | — | [firm-banking-settings-schema.md](../../references/database/settings/firm-banking-settings-schema.md) | [firm-banking-settings-mapping.md](../../references/migration/firm-banking-settings-mapping.md) | [parity](../../references/audits/firm-banking-settings-parity-checklist.md) · [review](../../references/audits/firm-banking-settings-review-2026-05-09.md) |
| Firm Banking History | — | [firm-banking-history-schema.md](../../references/database/settings/firm-banking-history-schema.md) | [firm-banking-history-mapping.md](../../references/migration/firm-banking-history-mapping.md) | [parity](../../references/audits/firm-banking-history-parity-checklist.md) · [review](../../references/audits/firm-banking-history-review-2026-05-08.md) |

Task plans: [planning/settings/](../../archive/planning/settings/) · [planning/phase-1/](../../planning/phase-1/).
Frontend UI: [wecms-web/docs/features/settings/](../../../../wecms-web/docs/features/settings/).

**Data model (ERD):** [Settings ERD](../../references/database/erd/settings-erd.md) ([Mermaid](../../references/database/erd/diagrams/settings-erd.mmd)) — covers bank-account, chart-of-accounts, contacts, approval line, firm-banking settings/history, ERP, VAS, purpose, environment, login credentials. Cross-module map: [full-project ERD](../../references/database/erd/full-project-erd.md) · shared hub: [common ERD](../../references/database/erd/common-erd.md). Coverage: [database-coverage-matrix.md](../../references/database/database-coverage-matrix.md) · ownership: [table-ownership.md](../../references/database/table-ownership.md).

## Standards

- One canonical doc per Settings screen; kebab-case slug matching the JEX counterpart.
- For audit-relevant screens (firm-banking, login-credentials, contacts, chart-of-accounts), document
  the `AUDIT_LOGS_INFM` write + the **slug** used for `WSVC_ID` per the workspace CLAUDE.md rule.
- View Setting (column visibility / order / size) is a cross-cutting global feature, **not** a Settings
  entry — see workspace CLAUDE.md and [docs/jex/features/view-setting.md](../../../../docs/jex/features/view-setting.md).
- **Environment Settings** ([environment-settings.md](environment-settings.md)) is a generic per-tenant flag
  store (`/api/v1/settings/environment-settings/{code}`), **not** a Settings-menu screen — shared
  infrastructure consumed by feature panels (first consumer: Firm Banking Settings Auto-Transfer / Auto-Match).

> The concise per-screen "menu-entry hub" stubs that previously lived in `docs/settings/` are archived
> at [archive/settings-menu-hubs/](../../archive/settings-menu-hubs/); their index value is folded into
> the tables above.
