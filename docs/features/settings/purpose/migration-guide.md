# Purpose — Migration Guide (JEX → Spring)

How each JEX layer became its Spring counterpart. Authoritative narrative mapping is preserved at
[references/migration/purpose-settings.md](../../../references/migration/purpose-settings.md); this is
the feature-folder summary. Legacy analysis: [docs/jex/features/purpose/migration-guide.md](../../../../../docs/jex/features/purpose/migration-guide.md).

---

## Layer-by-layer

| JEX layer | JEX artifact | Spring counterpart |
|---|---|---|
| Screen | `comm_0187_001_view.jsp` + `comm_0187_001.ts` | `<PurposePanel>` (Settings modal panel — no route) |
| List read | `comm_0187_001_r001` (IDO `BUSI_USAG_SET_INFM_R001`) | `GET /purpose-settings` → `PurposeSettingsService.list` → `repo.findActiveForTenant` |
| Name search | `comm_0187_001_r002` | **merged** into the list endpoint's `?keyword=` branch |
| Create | `comm_0187_001_c001` (IDO `_C001`) | `POST /purpose-settings` → `service.create` (PK via `wb_get_next_val`) |
| Toggle visibility | `comm_0187_001_u001` | folded into Update (`disp_yn` field on `PUT`) |
| Edit | `comm_0187_001_u002` REC sub-rule | `PUT /purpose-settings/{use_usag_cd}` → `service.update` |
| Soft-delete | `comm_0187_001_u002` REC2 sub-rule (UPDATE `USE_YN='N'`) | `DELETE /purpose-settings/{use_usag_cd}` → `service.delete` (`USE_YN='N'`) |
| Reorder | `comm_0187_001_u004` | `PUT /purpose-settings/order` → `service.reorder` |
| Excel parse | `comm_0187_004_r001` | `POST /purpose-settings/excel/preview` → `PurposeExcelService.preview` |
| Excel insert | `comm_0187_004_c001` | `POST /purpose-settings/excel/import` → `PurposeExcelService.import_` |
| Entity | `BusiUsagSetInfm` + `BusiUsagSetInfmPk` | `BusiUsagSet` + `BusiUsagSetId` (`@IdClass`) |
| DAO | `BusiUsagSetInfmDAO` | Spring Data `PurposeSettingsRepository` (+ `PurposeInUseRepository`) |
| Key-gen | `SeqHelper` → `wb_get_next_val` | ported `wb_get_next_val('busi_usag_set_infm','use_usag_cd',…)` branch (V82), called via `nextUseUsagCd` |
| "In use" check | inline JSP count IDO (`_R007`) | `v_purpose_in_use` view (V81) + `PurposeInUseRepository` (recursive, OQ-5) |

---

## Identifier translation (slug, not WSVC id)

Every refactor identifier surface carries the kebab slug **`purpose-settings`** — REST URI, Java
package (`…domain.setting.purposesettings`), class names (`PurposeSettingsController`,
`PurposeSettingsService`, `BusiUsagSet`), DTO records, i18n keys (`audit.purpose_settings.*`), log
tags, and the audit `WSVC_ID`. The menu permission code is the route-aligned slug
**`settings-core-purpose`**. Legacy WSVC ids (`comm_0187_001`, …) appear only in JEX file:line
citations and migration prose.

---

## Behaviour changes from legacy (intentional)

| Area | Legacy | Refactor |
|---|---|---|
| List scope | per-group `USAG_GRP_CD` + the level-2 bucket | level-2 bucket only (`usag_grp_cd IS NULL`) |
| Name search | separate `_r002` endpoint | merged into list `?keyword=` |
| Edit / delete | single bulk `_u002` (REC + REC2 arrays) | split into single-row `PUT` + `DELETE` |
| Delete guard | `hasActiveChildren` only | **recursive** — children OR row/child referenced by an active transaction (OQ-5) |
| Lang-detail table | `BUSI_USAG_SET_INFM_LANG_DETL` | dropped — single-table model (name/desc live on the master row) |
| Excel | one-shot upload | two-step preview → import (VAS Account precedent) |
| Authorization | menu visibility only (no explicit check) | explicit `@menuAuthz.canAccess('settings-core-purpose')` (F-3 server-side enforcement) |
| Audit `WSVC_ID` | `comm_0187_001` | slug `purpose-settings` |

---

## Dropped / dead legacy flows (not ported)

| Legacy flow | JEX artifact | Disposition |
|---|---|---|
| Reset to system defaults | `comm_0187_001_u003` | **Out of scope** — wires into `SYS_USAG_SET_INFM` + the per-group `USAG_GRP_CD` model that no longer exists in the refactor. |
| Deleted/unused list + Restore | `comm_0187_003_r001` / `comm_0187_003_u001` REC | **DEAD LEGACY** — the "trash bin" / restore UI is not in the refactor design; soft-deleted rows stay queryable in the DB but have no UI surface. |
| Permanent (physical) delete | `comm_0187_003_u001` REC2 (`deleteByPk`) | **DEAD LEGACY** — the refactor never physically deletes a purpose row. |
| ERP account-code mapping | `comm_0187_002_*` (`BUSI_USAG_ACCT_INFM_DETL`) | **Separate feature** — not part of Purpose Settings. |

See [refactor-gap-analysis.md](refactor-gap-analysis.md) GAP-1 / GAP-2 for the classification + evidence.

---

## Schema migration (Flyway)

| Version | Purpose |
|---|---|
| V80 | `CREATE TABLE BUSI_USAG_SET_INFM` (15 cols, column-for-column DEV parity) + `busi_usag_set_infm_parent_ix` |
| V81 | `CREATE OR REPLACE VIEW v_purpose_in_use` (parent-child branch live; consumer-table branches are the extension point) |
| V82 | extend `wb_get_next_val` with the `busi_usag_set_infm.use_usag_cd` branch (10-digit zero-padded, per-tenant MAX+1) |

Demo seed for tenant `VN_VNM0004_000001` lands in the V75–V83 groundwork window (see `wecms-api/CLAUDE.md` migration table).
