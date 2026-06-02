# features/accounting/

Feature specs for the **accounting / WABOOKS** domain in the refactor.

## Purpose

Accounting covers chart-of-accounts setup, daily cash & financial reports, and the VAS (Vietnam Accounting Standards) account mapping that drives WABOOKS journal entries and finance reporting.

## Scope

| File | Refactor scope |
|---|---|
| [chart-of-accounts.md](../../features/settings/chart-of-accounts.md) | Per-tenant chart of accounts CRUD, restricted-root rules, soft-delete revive flow |
| [daily-cash.md](../../features/banking/reports/daily-cash.md) | Daily cash position report (multi-bank / multi-account aggregation) |
| [daily-financial.md](../../features/banking/reports/daily-financial.md) | Daily financial summary report |
| [vas-account.md](../../features/settings/vas-account.md) | VAS account mapping & validation (powers WABOOKS journal entries) |

## Document standards

- One markdown file per refactor feature.
- Top-of-file frontmatter table must include: `JEX counterpart`, `Refactor status`, `Owner`, `Sprint`.
- Cite JEX entities (`ChartAcctInfm`, `VasAcctInfm`, etc.) and the live SQL where it differs from JEX docs.
- Cross-link to companion docs in [`../../backend/`](../../references/api-spec), [`../../frontend/`](../../../../wecms-web/docs), [`../../database/`](../../references/database/), [`../../migration/`](../../references/migration/), [`../../planning/`](../../planning/).

## Naming convention

- kebab-case slug matching the JEX counterpart under [`docs/jex/features/`](../../../../docs/jex/features).
- No spaces, no uppercase, no version suffixes.

## What does NOT go here

- Per-bank report formats → [`../banking/cash-flow.md`](../../features/banking/reports/cash-flow.md)
- Approval flow for accounting actions → [`../approval/approval-line.md`](../../features/settings/approval-line.md)
- Implementation detail (controllers / pages / schema) → respective `backend/`, `frontend/`, `database/` folders.
