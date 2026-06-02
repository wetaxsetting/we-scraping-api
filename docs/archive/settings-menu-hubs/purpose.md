# Purpose

> Canonical Settings-menu entry for **Settings → Purpose**. Detailed design lives in the
> linked docs below; this file is the single API-side index for the menu item.

## Overview
Purpose (사용용도) is a per-tenant master list of transaction purpose codes used to classify
cash transactions (Deposit, Sale, …). Supports list, detail, create, update, delete, display
re-ordering, enable/disable, and Excel bulk upload. Slug: `purpose-settings`. Mirrors live JEX
`comm_0187_001_r001/_r002/_c001/_u002/_u004` + `comm_0187_004_c001`.

- Backend design: [../business-rules/settings/purpose-settings.md](../../references/business-rules/settings/purpose-settings.md)
- DB schema: [../database/settings/purpose-settings-schema.md](../../references/database/settings/purpose-settings-schema.md)
- Feature spec: [../features/settings/purpose-settings.md](../../features/settings/purpose.md)
- JEX → Spring mapping: [../migration/purpose-settings.md](../../references/migration/purpose-settings.md)
- Parity audit: [../audits/purpose-settings-audit.md](../../references/audits/purpose-settings-audit.md)

## Related APIs
Base URI `/api/v1/settings/purpose-settings` (JWT, lowercase snake_case wire):
- `GET /` — list rows (incl. `used_in_transactions` flag)
- `GET /{useUsagCd}` — detail
- `POST /` — create
- `PUT /{useUsagCd}` — update
- `DELETE /{useUsagCd}` — delete (guarded; see Business Rules)
- `PUT /reorder` — bulk `disp_ord` reorder
- `POST /upload` — Excel bulk import → `{ inserted, skipped, errors[] }`

## Database Tables
- `BUSI_USAG_SET_INFM` — master table, composite key `(BIZ_KEY, USE_USAG_CD)`, extends `BaseEntity`.
- `v_purpose_in_use` — `@Immutable` view backing the in-use guard.

## Business Rules
- Tenant-scoped by `BIZ_KEY`.
- `disp_ord` controls display order; reorder payload must be contiguous with no duplicates
  (`PurposeReorderValidator`).
- Enable/disable toggle flips the active flag without deleting.
- **In-use guard:** a purpose referenced by existing transactions cannot be deleted.

## Validation
- `use_usag_cd` / `purpose name` required, length-bounded; unique per tenant.
- Reorder items validated for no-duplicate / contiguous sequence.
- Excel rows validated per-row; invalid rows collected into `errors[]` rather than aborting.

## Error Cases
- Duplicate purpose code → validation error.
- Delete of in-use purpose → blocked with a domain error.
- Standard envelope `{ status, data, timestamp?, path?, trace_id? }`; generic `WBE_VALIDATION` / `WBE9999`.

## Notes
Phase 2 backend design contract — see linked backend doc for package layout, DTOs, and audit-log
integration. Web/UI behavior: [../../../wecms-web/docs/settings/purpose.md](../../../../wecms-web/docs/archive/settings-purpose.md).
