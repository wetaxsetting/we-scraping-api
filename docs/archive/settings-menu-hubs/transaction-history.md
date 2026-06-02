# Transaction History (ERP)

> Canonical Settings-menu entry for **Settings → ERP → Transaction History**. Detailed design lives
> in the linked docs below.

## Overview
ERP Transaction History is a read-only, tenant-scoped log of ERP-interface transaction records with
filtering, four status counts, and file download. Slug: `erp-transaction-history`. Mirrors live JEX
`comm_0219_001_r001` (list + 4 counts) and `comm_0219_001_r002` (download). Upload (`_c001`) and
batch writers are out of scope.

- Backend design: [../business-rules/settings/erp-transaction-history.md](../../references/business-rules/settings/erp-transaction-history.md)
- DB schema: [../database/settings/erp-transaction-history-schema.md](../../references/database/settings/erp-transaction-history-schema.md)
- Feature spec: [../features/settings/erp-transaction-history.md](../../features/settings/transaction-history.md)
- JEX → Spring mapping: [../migration/erp-transaction-history-mapping.md](../../references/migration/erp-transaction-history-mapping.md) ·
  [../migration/api-mapping/erp-transaction-history-mapping.md](../../references/migration/erp-transaction-history-mapping.md)
- Parity audit: [../audits/erp-transaction-history-audit.md](../../references/audits/erp-transaction-history-audit.md)
- Task plan: [../planning/settings/erp-transaction-history-plan.md](../../planning/settings/erp-transaction-history-plan.md)

## Related APIs
Base URI `/api/v1/settings/erp-transaction-history` (JWT, snake_case wire):
- `GET /` — paged, filtered list + four status counts (mirrors `comm_0219_001_r001`)
- `GET /download` — file download (mirrors `comm_0219_001_r002`); path composed +
  existence-checked by `ErpTrnsHistoryFileResolver`

## Database Tables
- `ERP_TRNS_HSTR` — ERP transaction history rows, `@IdClass` composite key, tenant-scoped.

## Business Rules
- Read + download only (no create/update/delete in scope).
- Sort fields restricted by `ErpTrnsHistorySortField` whitelist.
- Date-range + keyword filters validated; keyword escaped.
- i18n key resolution applied in `ErpTrnsHistoryMapper`.
- Layout mirrors `domain/setting/firmbankinghistory/` — no new shared infrastructure.

## Validation
- Date range validated (from ≤ to); pagination clamped.
- Download requires a resolvable, existing file path.

## Error Cases
- Missing/invalid required params → `WBE_VALIDATION`.
- Download of a missing file → not-found error.
- Standard envelope; `WBE9999`.

## Notes
Web/UI: [../../../wecms-web/docs/settings/transaction-history.md](../../../../wecms-web/docs/archive/settings-transaction-history.md).
