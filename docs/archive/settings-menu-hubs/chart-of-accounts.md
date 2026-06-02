# Chart of Accounts

> Canonical Settings-menu entry for **Settings → Chart of Accounts**. Detailed design lives in the
> linked docs below.

## Overview
Chart of Accounts is the per-tenant hierarchical account tree (WABOOKS accounting). Supports flat
list, parent-picker tree, create, update, delete, with per-mutation audit logging. Slug:
`chart-of-accounts`.

- Backend design: [../business-rules/chart-of-accounts.md](../../references/business-rules/chart-of-accounts.md)
- DB schema: [../database/chart-of-accounts.md](../../references/database/chart-of-accounts.md)
- Feature spec: [../features/accounting/chart-of-accounts.md](../../features/settings/chart-of-accounts.md)
- JEX → Spring mapping: [../migration/chart-of-accounts.md](../../references/migration/chart-of-accounts.md)
- Task plan: [../planning/phase-1/chart-of-accounts-plan.md](../../planning/phase-1/chart-of-accounts-plan.md)

## Related APIs
Base URI `/api/v1/settings/chart-of-accounts` (JWT, snake_case wire):
- `GET /` — list `{ items, total, restricted_codes }`
- `GET /tree` — parent-picker tree `{ nodes }`
- `POST /` — create
- `PUT /{chartAcctSeqNo}` — update
- `DELETE /{chartAcctSeqNo}` — delete

## Database Tables
- `CHART_ACCT_INFM` — account master, composite key `(BIZ_KEY, CHART_ACCT_SEQ_NO)`, extends `BaseEntity`.
- `AUDIT_LOGS_INFM` — written per mutation (`REQUIRES_NEW`) by `ChartOfAccountsAuditLogger`.

## Business Rules
- Tenant-scoped by `BIZ_KEY`; `@Transactional` mutations.
- `ChartOfAccountsTreeBuilder` projects flat rows → `level` / `has_children` / breadcrumb.
- `ChartOfAccountsValidator` guards: cycle, max depth, self-parent, restricted-code.
- `CalRule` = `P` (PLUS) / `M` (MINUS); `AcctStts` = `A` (active) / `I` (inactive).

## Validation
- Account code/name required; code unique per tenant; restricted codes rejected.
- Sort fields restricted by whitelist (`CHART_ACCT_CD`, `CHART_ACCT_NM_EN`, `STTS`, `MOD_DTM`).

## Error Cases
- Cycle / self-parent / depth-exceeded / restricted-code → validation errors.
- Delete of a node with children → blocked.
- Standard envelope; `WBE_VALIDATION` / `WBE9999`.

## Notes
Web/UI: [../../../wecms-web/docs/settings/chart-of-accounts.md](../../../../wecms-web/docs/archive/settings-chart-of-accounts.md).
