# features/settings/

Canonical docs for the refactor **Settings** module.

## Doc convention (read first)

Each `{module}.md` **flat file is now a HIGH-LEVEL SUMMARY** (banner + Feature Overview +
Menu/Route Mapping + Current Status + Detailed Documentation). The **authoritative detail lives in
the `{module}/` folder** (≈9 docs each: scope, JEX traceability, REST endpoints, DB tables,
business rules, validation, error cases, audits). When the summary and the folder disagree, the
folder wins; when either disagrees with the backend/frontend code, **the code wins**.

Three modules have **no folder** — they are summary-only:

- **change-password** — a slice of the `auth` domain (header profile dropdown), not a Settings screen.
- **environment-settings** — generic cross-feature tenant-flag infrastructure shared by the
  Preferences and Firm Banking Settings panels.
- **zalo-oa** — refactor-native (no JEX counterpart, so there is no parity or traceability
  detail to split out) and still behind open product decisions. One summary is the honest
  size of it; it grows a folder when the message catalogue is agreed and the feature is
  wired to real events.

Status legend: ✅ Implemented (full-stack evidenced) · 🟡 Partial (backend implemented; frontend
not re-verified this pass) · ⚪ Legacy/Deprecated or not a standalone module.

## Settings menu → docs

| # | Settings item | Folder | Summary | Status |
|---|---|---|---|---|
| 1 | Purpose | [purpose/](purpose/README.md) | [purpose.md](purpose.md) | ✅ Implemented (full-stack; parity + audit + SIT/UAT verified) |
| 2 | VAS Account | [vas-account/](vas-account/README.md) | [vas-account.md](vas-account.md) | ✅ Implemented (full-stack; legacy `comm_0217_*` parity + SIT/UAT verified) |
| 3 | Contacts | [contacts/](contacts/README.md) | [contacts.md](contacts.md) | ✅ Implemented (full-stack; parity verified, Export GAP-01 closed 2026-06-08, SIT/UAT verified) |
| 4 | Approval Line | [approval-line/](approval-line/README.md) | [approval-line.md](approval-line.md) | ✅ Implemented (full-stack; AL-GAP-01/02/03 resolved; SIT/UAT verified) |
| 5 | Chart of Accounts | [chart-of-accounts/](chart-of-accounts/README.md) | [chart-of-accounts.md](chart-of-accounts.md) | ✅ Implemented (full-stack; legacy `comm_0223_*` parity + final audit + SIT/UAT verified) |
| 6 | Login Credentials | [login-credentials/](login-credentials/README.md) | [login-credentials.md](login-credentials.md) | ✅ Implemented (backend + frontend wired, 2026-06-02) |
| 7 | Bank Account | [bank-account/](bank-account/README.md) | [bank-account.md](bank-account.md) | 🟡 Backend implemented (frontend not re-verified this pass) |
| 8 | Preferences | [preferences/](preferences/README.md) | [preferences.md](preferences.md) | 🟡 Backend implemented (frontend not re-verified this pass) |
| 9 | Users | [user-management/](user-management/README.md) | summary only — flat [users.md](users.md) is the **deprecated** plan, superseded by the folder | ✅ Implemented (parity verified 2026-06-05) |
| 10 | Transaction History (ERP) | [transaction-history/](transaction-history/README.md) | [transaction-history.md](transaction-history.md) | 🟡 Backend implemented (read-only audit ledger; frontend not re-verified) |
| 11 | Firm Banking Settings | [firm-banking-settings/](firm-banking-settings/README.md) | [firm-banking-settings.md](firm-banking-settings.md) | 🟡 Backend implemented + unit tests (frontend not re-verified) |
| 12 | Firm Banking History | [firm-banking-history/](firm-banking-history/README.md) | [firm-banking-history.md](firm-banking-history.md) | ✅ Implemented (parity verified 2026-06-05; XLSX export) |
| 13 | Zalo OA | — (summary only) | [zalo-oa.md](zalo-oa.md) | 🟡 Backend implemented (client + service + controller + scheduler + 15 unit tests; no frontend API module; not wired to any business event) |

> **"Transaction History (ERP)" — not bank transactions.** This Settings screen (folder `transaction-history/`,
> backend slug/route `erp-transaction-history`) is the read-only audit ledger of **files exchanged between WeCMS
> and the customer's ERP system**. It is unrelated to **Banking → Transaction History**
> ([../banking/current-savings-vnd/transaction-history/](../banking/current-savings-vnd/transaction-history/README.md)),
> which lists actual bank-account transactions. The doc folder is named `transaction-history` for slug-traceability;
> always read it as **ERP** Transaction History.

**Folderless summary docs (not Settings-menu screens):**

| Module | Folder | Summary | Status |
|---|---|---|---|
| Change Password | _summary only — no folder_ (slice of the `auth` domain) | [change-password.md](change-password.md) | ✅ Implemented full-stack in `auth` (`POST /api/v1/auth/change-password` + `ChangePasswordDialog`), ⚪ **not a standalone module** — reached from the header profile dropdown, not the Settings menu (legacy `comm_0401_001` / `comm_0400_001_u001`) |
| Environment Settings | _summary only — no folder_ (generic tenant-flag store) | [environment-settings.md](environment-settings.md) | 🟡 Backend implemented (`/api/v1/settings/environment-settings/{code}`), ⚪ **not a standalone module** — shared backend for the Preferences (`00074`/`00077`) and Firm Banking Settings (`00078`/`00079`) toggles |

## Related reference docs (per screen, cross-linked — not duplicated)

| Screen | Business rules | DB schema | JEX → Spring mapping | Parity audit |
|---|---|---|---|---|
| Purpose | [purpose-settings.md](../../references/business-rules/settings/purpose-settings.md) | [purpose-settings-schema.md](../../references/database/settings/purpose-settings-schema.md) | [purpose-settings.md](../../references/migration/purpose-settings.md) | [purpose-settings-audit.md](../../references/audits/purpose-settings-audit.md) |
| VAS Account | [vas-account.md](../../references/business-rules/vas-account.md) | [vas-account.md](../../references/database/vas-account.md) | [vas-account.md](../../references/migration/vas-account.md) | [vas-account.md](../../references/audits/vas-account.md) |
| Contacts | [contacts-design.md](../../references/business-rules/contacts-design.md) | [contacts-schema.md](../../references/database/contacts-schema.md) | [contacts-mapping.md](../../references/migration/contacts-mapping.md) | — |
| Approval Line | [approval-line.md](../../references/business-rules/approval-line.md) | — | [approval-line.md](../../references/migration/approval-line.md) · [jex-analysis](../../references/migration/jex-analysis/approval-line-add-flow-analysis.md) | — |
| Chart of Accounts | [chart-of-accounts.md](../../references/business-rules/chart-of-accounts.md) | [chart-of-accounts.md](../../references/database/chart-of-accounts.md) | [chart-of-accounts.md](../../references/migration/chart-of-accounts.md) | [final-parity-audit](../../audits/chart-of-accounts-final-parity-audit.md) |
| Bank Account | — | — | — | — |
| Transaction History (ERP) | [erp-transaction-history.md](../../references/business-rules/settings/erp-transaction-history.md) | [erp-transaction-history-schema.md](../../references/database/settings/erp-transaction-history-schema.md) | [erp-transaction-history-mapping.md](../../references/migration/erp-transaction-history-mapping.md) | [erp-transaction-history-audit.md](../../references/audits/erp-transaction-history-audit.md) |
| Firm Banking Settings | — | [firm-banking-settings-schema.md](../../references/database/settings/firm-banking-settings-schema.md) | [firm-banking-settings-mapping.md](../../references/migration/firm-banking-settings-mapping.md) | [parity](../../references/audits/firm-banking-settings-parity-checklist.md) · [review](../../references/audits/firm-banking-settings-review-2026-05-09.md) |
| Firm Banking History | — | [firm-banking-history-schema.md](../../references/database/settings/firm-banking-history-schema.md) | [firm-banking-history-mapping.md](../../references/migration/firm-banking-history-mapping.md) | [parity](../../references/audits/firm-banking-history-parity-checklist.md) · [review](../../references/audits/firm-banking-history-review-2026-05-08.md) |

Task plans: _(the historical `planning/` and `archive/planning/` task-plan folders were removed 2026-06-23 by project decision)_.
Frontend UI: [wecms-web/docs/features/settings/](../../../../wecms-web/docs/features/settings/).

**Data model (ERD):** [Settings ERD](../../references/database/erd/settings-erd.md) ([Mermaid](../../references/database/erd/diagrams/settings-erd.mmd)) — covers bank-account, chart-of-accounts, contacts, approval line, firm-banking settings/history, ERP, VAS, purpose, environment, login credentials. Cross-module map: [full-project ERD](../../references/database/erd/full-project-erd.md) · shared hub: [common ERD](../../references/database/erd/common-erd.md). Coverage: [database-coverage-matrix.md](../../references/database/database-coverage-matrix.md) · ownership: [table-ownership.md](../../references/database/table-ownership.md).

## Standards

- Each Settings screen has a `{module}/` folder (authoritative detail) plus a flat `{module}.md`
  high-level summary; kebab-case slug matching the JEX counterpart. The two folderless docs
  (change-password, environment-settings) are summary-only because they are not standalone screens.
- For audit-relevant screens (firm-banking, login-credentials, contacts, chart-of-accounts), document
  the `AUDIT_LOGS_INFM` write + the **slug** used for `WSVC_ID` per the workspace CLAUDE.md rule.
- View Setting (column visibility / order / size) is a cross-cutting global feature, **not** a Settings
  entry — see workspace CLAUDE.md and [docs/jex/features/view-setting.md](../../../../docs/jex/features/view-setting.md).
- **Environment Settings** ([environment-settings.md](environment-settings.md)) is a generic per-tenant flag
  store (`/api/v1/settings/environment-settings/{code}`), **not** a Settings-menu screen — shared
  infrastructure consumed by feature panels: Preferences (`00074` Use VAS Account / `00077` Use Purpose)
  and Firm Banking Settings (`00078` Auto Transfer / `00079` Auto Match).

> The concise per-screen "menu-entry hub" stubs that previously lived in `docs/settings/` were archived under
> `archive/settings-menu-hubs/` and **removed 2026-06-23 by project decision**; their index value is folded into
> the tables above (the canonical per-screen specs live in the `{module}/` folders).
