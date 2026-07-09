# wecms-api — Documentation

Backend documentation for the **Spring Boot 3.5.13** side of the WeCMS JEX → Spring/Next.js refactor (`wecms-api`). Local-only R&D (PostgreSQL, GitLab); no DEV/UAT/PROD deploy, no bank integration, no coexistence with the live JEX system.

> **Reorganized to feature-first (2026-06-01).** Documentation is now keyed off the live WeCMS
> **menu structure**: start at [features/](features/) and drill into the menu group → screen you need.
> Supporting detail (API contracts, schema, JEX→Spring mapping, parity audits) lives under
> [references/](references/); cross-feature design under [architecture/](architecture/). The earlier
> workspace-level `docs/refactor-wecms/` tree remains **deprecated read-only legacy**.
>
> **Structure update (2026-06-23).** The `archive/`, `planning/`, and the top-level `migration/` folders were
> **removed from `wecms-api/docs/` by project decision** (their history/plans/open-questions are no longer kept
> in source control). The remaining top-level folders are **`architecture/` · `audits/` · `features/` ·
> `references/` · `testing/`** (plus the shared-pointer files at the root). Migration narratives live under
> [references/migration/](references/migration/).

> **Field-name casing (project-wide standard).** Legacy JEX APIs used UPPER_SNAKE field names. The Spring/Next.js refactor intentionally uses lowercase snake_case JSON field names via global Jackson SNAKE_CASE configuration. This is an approved modernization under CTO directive dated 2026-05-08 and is not a legacy parity gap. Source-verified inventory + status table: [audits/field-casing-parity-audit-2026-06-06.md](audits/field-casing-parity-audit-2026-06-06.md).

## Start Here

> **You do not need JEX knowledge to begin development.** This is a **Spring Boot 3.5.13** codebase —
> start from the rules, the feature docs, and the code. JEX is **historical / functional reference only**
> (it tells you *what* a feature must do); you consult it only when porting a feature.
>
> **Source-of-truth split:** JEX is the **functional / behavioral** source of truth (*what* the system does);
> the **Spring Boot code is the implementation source of truth** (*how* it is built). Build from the Spring code,
> never from JEX.

**New to this codebase? Read in this order.** All documentation is written in **English**. WeCMS is a refactor of
the legacy **JEX** system, so a few business terms are Korean (결재라인 → *approval line*, 사용용도 → *purpose*);
when a JEX or Korean term is unfamiliar, check the glossary (step 3) — you never have to read JEX itself to get
started. This guide is for new joiners, including developers in **Cambodia** and **Vietnam**.

1. **[../CLAUDE.md](../CLAUDE.md)** — the project rules (how to build, naming, what not to touch). Read first.
2. **[README.md](README.md)** — *(this file)* — how the docs are organized and where things live.
3. **[glossary.md](glossary.md)** — business + technical terms (JEX/Korean → English). Skim it before the feature docs.
4. **Feature README** — open the screen you're working on: `features/<module>/<screen>/README.md`
   (e.g. [features/settings/contacts/README.md](features/settings/contacts/README.md)). Each feature folder has the
   same 9-doc set.
5. **`business-flow.md`** — what the feature does end-to-end (user flow + backend orchestration).
6. **`interface-specification.md`** — the API request/response contract (lowercase snake_case wire shape).
7. **`source-code-index.md`** — the map from this feature's docs to the actual backend/frontend code + tests.
8. **Relevant audits** — for parity history, decisions, and known gaps, see the **[audits index](audits/README.md)**
   (governance docs, per-module triplets, per-feature parity audits).

**Also useful:** [WORKFLOW.md](WORKFLOW.md) (dev flow) · [permissions.md](permissions.md) (roles) ·
[error-codes.md](error-codes.md) (`WBExxxx` codes) · frontend docs at [../../wecms-web/docs/](../../wecms-web/docs/).

## Layout

| Folder / file | Contents |
|---|---|
| [Start Here](#start-here) · [audits/README.md](audits/README.md) | Onboarding read-order + the **audits index** (governance, module triplets, parity & historical audits) |
| [api-spec/](references/api-spec/) | REST endpoint contracts (`*-api.md`), per-domain API specs, [api-spec/api-mapping.md](references/api-spec/api-mapping.md) cross-feature index |
| [architecture/](architecture/) | Cross-feature system architecture (module boundaries, request flow, auth, error model, i18n) |
| [business-rules/](references/business-rules/) | Service / domain design — entities, repositories, services, security, settings, per-feature business logic |
| [features/](features/) | Per-feature functional specs (the spine linking backend + frontend + migration + DB), grouped by domain |
| [settings/](features/settings/) | **Settings-menu canonical entries** (one per Settings menu item) — see the Settings section below |
| [database/](references/database/) | Schema design, ERD, Flyway, indexing, table-mapping, [database/manual-tenant-setup.md](references/database/manual-tenant-setup.md) |
| [migration/](references/migration/) | JEX → Spring service/field mapping, jex-analysis, risk/rollout, [migration/MIGRATION_PLAYBOOK.md](references/migration/MIGRATION_PLAYBOOK.md) |
| [audits/](audits/) | Parity audits + documentation governance/closure reports (refactor vs JEX) — DB, API, migration, security, UX |
| [references/](references/) | Reference notes (api, external, figma, jex) |
| [testing/](testing/) | Refactor backend test plans (integration, performance, regression, sit, uat) |

> Removed 2026-06-23 (project decision): `planning/` (sprint/phase/roadmap plans + open questions), `archive/`
> (superseded feature docs + structure/classification reports + settings-menu hubs), and the top-level
> `migration/` stray. Their removal does **not** indicate missing functionality.

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

Each feature spec links out to the frontend UI doc in `wecms-web/docs/features/addon-service/`. *(The former
`planning/addon/*-plan.md` task plans were removed with `planning/` on 2026-06-23 — project decision.)*

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
| Current & Savings (VND) | [account-inquiry-vnd](./features/banking/current-savings-vnd/account-inquiry/README.md) · [transaction-history-vnd](./features/banking/current-savings-vnd/transaction-history/README.md) · [smart-organizer-vnd](./features/banking/smart-organizer/README.md) |
| Current & Savings (FCY) | [account-inquiry-fcy](./features/banking/current-savings-fcy/account-inquiry/README.md) · [transaction-history-fcy](./features/banking/current-savings-fcy/transaction-history/README.md) · [smart-organizer-fcy](./features/banking/smart-organizer/README.md) |
| Internal Transfer | [own-account-transfer](./features/banking/internal-transfer-vnd/own-account-transfer/README.md) · [transfer-history](./features/banking/internal-transfer-vnd/transfer-history/README.md) |
| Reports | [daily-cash](./features/banking/reports/daily-cash/README.md) · [daily-financial](./features/banking/reports/daily-financial/README.md) · [cash-flow](./features/banking/reports/cash-flow/README.md) |

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
| bug-reports | [../../docs/bug-reports/](../../docs/bug-reports/) |
| change-log | [../../docs/change-log/](../../docs/change-log/) |

## Related

- Frontend docs: [../../wecms-web/docs/](../../wecms-web/docs/)
- JEX functional spec (read-only reference): [../../docs/jex/](../../docs/jex/)
- Project rules: [../CLAUDE.md](../CLAUDE.md) · workspace rules: [../../CLAUDE.md](../../CLAUDE.md)
