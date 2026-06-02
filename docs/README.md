# wecms-api — Documentation

Backend documentation for the **Spring Boot 3.5.13** side of the WeCMS JEX → Spring/Next.js refactor (`wecms-api`). Local-only R&D (PostgreSQL, GitLab); no DEV/UAT/PROD deploy, no bank integration, no coexistence with the live JEX system.

> **Reorganized to feature-first (2026-06-01).** Documentation is now keyed off the live WeCMS
> **menu structure**: start at [features/](features/) and drill into the menu group → screen you need.
> Supporting detail (API contracts, schema, JEX→Spring mapping, parity audits) lives under
> [references/](references/); cross-feature design under [architecture/](architecture/). The earlier
> workspace-level `docs/refactor-wecms/` tree remains **deprecated read-only legacy**. A small number
> of deep cross-links to cross-system material (`docs/jex/`, `docs/_shared/`) and to not-yet-written
> stubs are tracked in [planning/open-questions/documentation-follow-up.md](planning/open-questions/documentation-follow-up.md).

## Layout

| Folder / file | Contents |
|---|---|
| [api-spec/](references/api-spec/) | REST endpoint contracts (`*-api.md`), per-domain API specs, [api-spec/api-mapping.md](references/api-spec/api-mapping.md) cross-feature index |
| [architecture/](architecture/) | Cross-feature system architecture (module boundaries, request flow, auth, error model, i18n) |
| [business-rules/](references/business-rules/) | Service / domain design — entities, repositories, services, security, settings, per-feature business logic |
| [features/](features/) | Per-feature functional specs (the spine linking backend + frontend + migration + DB), grouped by domain |
| [settings/](features/settings/) | **Settings-menu canonical entries** (one per Settings menu item) — see the Settings section below |
| [database/](references/database/) | Schema design, ERD, Flyway, indexing, table-mapping, [database/manual-tenant-setup.md](references/database/manual-tenant-setup.md) |
| [migration/](references/migration/) | JEX → Spring service/field mapping, jex-analysis, risk/rollout, [migration/MIGRATION_PLAYBOOK.md](references/migration/MIGRATION_PLAYBOOK.md) |
| [audits/](references/audits/) | Parity audits (refactor vs JEX) — DB, API, migration, security, UX |
| [planning/](planning/) | Sprint / phase / roadmap task plans, open questions |
| [references/](references/) | Reference notes (api, external, figma, jex) |
| [reports/](archive/reports/) | Structure & classification reports |
| [archive/](archive/) | Archived / superseded feature docs |
| [testing/](testing/) | Refactor backend test plans (integration, performance, regression, sit, uat) |

### Settings menu (`settings/`)

Canonical API-side doc per **Settings** menu item (backend / API / DB / business logic). Each file
cross-links to the detailed `business-rules/`, `features/`, `database/`, and `migration/` docs.

| Settings item | Doc |
|---|---|
| Purpose | [settings/purpose.md](features/settings/purpose.md) |
| VAS Account | [settings/vas-account.md](features/settings/vas-account.md) |
| Contacts | [settings/contacts.md](features/settings/contacts.md) |
| Approval Line | [settings/approval-line.md](features/settings/approval-line.md) |
| Chart of Accounts | [settings/chart-of-accounts.md](features/settings/chart-of-accounts.md) |
| Login Credentials | [settings/login-credentials.md](features/settings/login-credentials.md) |
| Bank Account | [settings/bank-account.md](features/settings/bank-account.md) |
| Preferences | [settings/preferences.md](features/settings/preferences.md) |
| Users | [settings/users.md](features/settings/users.md) |
| Transaction History (ERP) | [settings/transaction-history.md](features/settings/transaction-history.md) |
| Firm Banking Settings | [settings/firm-banking-settings.md](features/settings/firm-banking-settings.md) |
| Firm Banking History | [settings/firm-banking-history.md](features/settings/firm-banking-history.md) |

### Add-on Service menu (`features/addon-service/`)

Add-on Service docs are split across **three layers** (the feature spec is the hub; granular contracts
and schema stay in their own sections and are cross-linked, not duplicated):

| Layer | Location | Contents |
|---|---|---|
| **Feature spec** (menu-level) | [`features/addon-service/`](features/addon-service/) | Scope, business rules, validation, error handling, integration flow; links out to the two layers below |
| **API contract** | [`api-spec/addon/`](references/api-spec/addon/) | Endpoint/DTO contracts (`*-api.md`) |
| **DB schema** | [`database/addon/`](references/database/addon/) | Table/column schema (`*-db.md`) |

Each feature spec also links to its `planning/addon/*-plan.md` task plan and the frontend UI doc in
`wecms-web/docs/features/addon-service/`.

| Add-on Service item | Doc |
|---|---|
| Sales e-Invoices | [features/addon-service/sales-e-invoices.md](features/addon-service/sales-e-invoices.md) |
| Expenses e-Invoices | [features/addon-service/expenses-e-invoices.md](features/addon-service/expenses-e-invoices.md) |
| Operation Status Inquiry | [features/addon-service/operation-status-inquiry.md](features/addon-service/operation-status-inquiry.md) |

### Banking menu (`features/banking/`)

Canonical API-side feature hub per **Banking** menu item (3-layer pattern: hub →
[`api-spec/banking/`](references/api-spec/banking/) contracts + [`database/banking/`](references/database/banking/) schema).
Full index in [features/banking/README.md](features/banking/README.md).

| Group | Items |
|---|---|
| Current & Savings (VND) | [account-inquiry-vnd](features/banking/current-savings-vnd/account-inquiry.md) · [transaction-history-vnd](features/banking/current-savings-vnd/transaction-history.md) · [smart-organizer-vnd](features/banking/current-savings-vnd/smart-organizer.md) |
| Current & Savings (FCY) | [account-inquiry-fcy](features/banking/current-savings-fcy/account-inquiry.md) · [transaction-history-fcy](features/banking/current-savings-fcy/transaction-history.md) · [smart-organizer-fcy](features/banking/current-savings-fcy/smart-organizer.md) |
| Internal Transfer | [own-account-transfer](features/banking/own-account-transfer/own-account-transfer.md) · [transfer-history](features/banking/own-account-transfer/transfer-history.md) |
| Reports | [daily-cash](features/banking/reports/daily-cash.md) · [daily-financial](features/banking/reports/daily-financial.md) · [cash-flow](features/banking/reports/cash-flow.md) |

Smart Organizer is backend-implemented (`SmartOrganizerController` + `OrganizerRuleController` + V172–V174) with a Next.js route; its feature docs carry residual `Needs Confirmation` detail to reconcile against the shipped code.

### Payment menu (`features/payment/`)

Family-hub per **Payment** menu group (each hub covers its Scheduled / Result + Details sub-items as sections).
Full index + cross-sibling / shared-infra authority: [features/payment/README.md](features/payment/README.md).

| Group | Hub |
|---|---|
| Approval Management → Approval Box | [features/payment/approval-box/](features/payment/approval-box/README.md) |
| Internal Transfer | [features/payment/internal-transfer/](features/payment/internal-transfer/README.md) ⭐ |
| Vendor Payment | [features/payment/vendor-payment/](features/payment/vendor-payment/README.md) 🟡 stub |
| Expense Payment | [features/payment/expense-payment/](features/payment/expense-payment/README.md) 🟡 stub |
| Payroll | [features/payment/payroll/](features/payment/payroll/README.md) 🟡 stub |
| Overseas Payment | [features/payment/overseas-payment/](features/payment/overseas-payment/README.md) 🆕 |
| Add-on → Exchange Rate Inquiry | [features/payment/exchange-rate-inquiry.md](features/payment/exchange-rate-inquiry.md) |
| Corporate Card → Card Transactions | [features/payment/card-transactions.md](features/payment/card-transactions.md) |

### Shared (cross-system) docs — pointers only

These apply to **both** the JEX and refactor systems and are maintained once at the workspace root:

| Here | Authoritative source |
|---|---|
| [WORKFLOW.md](WORKFLOW.md) | [../../docs/WORKFLOW.md](../../docs/WORKFLOW.md) |
| [glossary.md](glossary.md) | [../../docs/glossary.md](../../docs/glossary.md) |
| [permissions.md](permissions.md) | [../../docs/_shared/permission-roles.md](../../docs/_shared/permission-roles.md) |
| [error-codes.md](error-codes.md) | [../../docs/_shared/error-code-registry.md](../../docs/_shared/error-code-registry.md) |
| [security/](architecture/security/) | [../../docs/security/](../../docs/security/) + [../../docs/_shared/security-baseline.md](../../docs/_shared/security-baseline.md) |
| [bug-reports/](archive/shared-pointers/) | [../../docs/bug-reports/](../../docs/bug-reports/) |
| [change-log/](archive/shared-pointers/) | [../../docs/change-log/](../../docs/change-log/) |

## Related

- Frontend docs: [../../wecms-web/docs/](../../wecms-web/docs/)
- JEX functional spec (read-only reference): [../../docs/jex/](../../docs/jex/)
- Project rules: [../CLAUDE.md](../CLAUDE.md) · workspace rules: [../../CLAUDE.md](../../CLAUDE.md)
