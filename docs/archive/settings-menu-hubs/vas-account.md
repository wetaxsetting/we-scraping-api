# VAS Account

> Canonical Settings-menu entry for **Settings → VAS Account**. Detailed design lives in the
> linked docs below.

## Overview
VAS Account is a per-tenant master list of value-added-service / virtual accounts used when
classifying transactions (paired with Purpose in the transaction setup dialog). Supports list,
detail, create, update, delete, and Excel import/export. Slug: `vas-account`.

- Backend design: [../business-rules/vas-account.md](../../references/business-rules/vas-account.md)
- DB schema: [../database/vas-account.md](../../references/database/vas-account.md)
- Feature spec: [../features/accounting/vas-account.md](../../features/settings/vas-account.md)
- JEX → Spring mapping: [../migration/vas-account.md](../../references/migration/vas-account.md)
- Parity audit: [../audits/vas-account.md](../../references/audits/vas-account.md)

## Related APIs
Base URI `/api/v1/settings/vas-account` (JWT, snake_case wire):
- `GET /` — paged list
- `GET /{vasAcctSeqNo}` — detail
- `POST /` — create
- `PUT /{vasAcctSeqNo}` — update
- `DELETE /{vasAcctSeqNo}` — delete
- Excel preview / import / export (`VasAccountExcelService`, `VasAccountExportProvider` via
  `ExportableScreenRegistry`).

## Database Tables
- `BUSI_VAS_ACCT_INFM` — master table, composite key `(BIZ_KEY, VAS_ACCT_SEQ_NO)`.
- Sequence allocation done Java-side (`VasAcctSeqNoGenerator`) — replaces the legacy
  `wb_get_next_val` branch.

## Business Rules
- Tenant-scoped by `BIZ_KEY`.
- Seq number generated server-side, never client-supplied.
- Excel import returns a structured preview before commit; rows validated individually.

## Validation
- VAS account code / name required and length-bounded.
- Sortable fields restricted by `VasAccountSortField` whitelist.

## Error Cases
- Duplicate VAS account → validation error.
- Invalid Excel rows surfaced in the import result, not fatal.
- Standard envelope; `WBE_VALIDATION` / `WBE9999`.

## Notes
"Use VAS Account" is a tenant feature toggle owned by **Preferences** (`BUSI_ENV_SET_INFM`) — see
[preferences.md](../../features/settings/preferences.md). Web/UI: [../../../wecms-web/docs/settings/vas-account.md](../../../../wecms-web/docs/archive/settings-vas-account.md).
