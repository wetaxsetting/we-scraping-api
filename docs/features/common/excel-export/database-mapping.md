# Excel Export — Database Mapping

**Part of:** Common → excel-export
**Last verified:** 2026-06-23

## No export-owned table

The export pipeline has **no table of its own**. It owns no entity, no
repository, and no Flyway migration. It is a pure orchestration layer over two
existing concerns:

1. **View Setting visibility** — keyed on `SYS_SCR_OPTION_WECMS` (system
   defaults) + `USER_SCR_OPTION_WECMS` (per-user overrides), read read-only via
   `ScreenOptionService.load(screenId, dymAreaId)`
   (`ExcelExportService.java:253`).
2. **The list query the provider reuses** — each provider delegates `fetch(...)`
   to the screen's existing list service, so the export reads exactly the same
   tenant-scoped tables the on-screen list reads. No new query path.

> Per the workspace legacy-compatibility rule, refactor-only constructs with no
> legacy table are forbidden — the export feature deliberately introduces none.

## What the pipeline touches (read-only)

| Concern | Table(s) | Accessed via |
|---|---|---|
| Column visibility / order / SIZE | `SYS_SCR_OPTION_WECMS` + `USER_SCR_OPTION_WECMS` | `ScreenOptionService.load(...)` — see [`../view-setting/database-mapping.md`](../view-setting/database-mapping.md) |
| Row data (per screen) | whatever the reused list service reads | `provider.fetch(request)` → list service |
| ERP audit (opt-in, payment results only) | `ERP_TRNS_HSTR` (write, `ERP_TYPE='W'`) | `provider.recordErpExport(...)` — provider-owned domain writer, NOT `common/export` |

## Visibility intersection key space

`SYS_SCR_OPTION_WECMS.WEB_SVC_ID` = the export `screenId` (semantic kebab slug,
e.g. `banking-account-inquiry-vnd`). The visibility check resolves each DB
`FIELD_ID` to canonical form via `FieldIdAlias.resolve(...)` before intersecting
with the provider allow-list (`ExcelExportService.java:262`), so a seed using a
legacy/`_2`/`_4` FIELD_ID still matches.

`USE_YN='Y'` ⇒ visible; `SIZE` (CSS px string) ⇒ enriches column width when the
wire spec omits `widthChars` (`ExcelExportService.java:260-268`).

## Export-only (unseeded) screens

Screens that pass a `screenId` to the pipeline but are **not** seeded in
`SYS_SCR_OPTION_WECMS` (they render no View Setting popover) have no persisted
visibility row. `load(...)` throws `WBE2001`; the service catches it and skips
the visibility intersection (`ExcelExportService.java:251-257, :278-280`).
Documented export-only screens: addon e-Invoices, operation-status, cash-flow,
firm-banking-history, erp-transaction-history (`ExcelExportService.java:244-250`).

## Tenant scope

Every row query is bounded by `BIZ_KEY` (`TenantContext.currentBizKey()`),
inherited from the reused list service. The export must never cross tenants
(CLAUDE.md:1016).

## Related

- View Setting schema: [`../view-setting/database-mapping.md`](../view-setting/database-mapping.md)
- Legacy table baseline: [`../../../references/database/legacy-table-inventory.md`](../../../references/database/legacy-table-inventory.md)
