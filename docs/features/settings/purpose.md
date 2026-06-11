# Settings > Purpose — Refactor Feature Spec

> **Primary documentation home moved to [purpose/](purpose/README.md)** (feature folder — README,
> business-flow, functional-specification, database-mapping, interface-specification, source-code-index,
> migration-guide, test-scenarios, refactor-gap-analysis). **This file is preserved as the deep
> design narrative + OQ decision log** and remains authoritative for the Phase-1/2 design rationale.
> Where this Phase-2 plan and the final implementation differ (e.g. authorization is now
> `@menuAuthz.canAccess('settings-core-purpose')`, not the planned `hasRole('ADMIN')`), the feature
> folder reflects the **as-built** state.

**Module:** Settings
**Menu:** Purpose
**Refactor screen ID / slug:** `purpose-settings`
**Frontend placement:** Settings modal panel ([components/settings/purpose/PurposePanel.tsx](../../../../wecms-web/components/settings/purpose/PurposePanel.tsx) — static-UI shell already exists).
**View Setting screenId (per [lib/viewSettingScreens.ts](../../../../wecms-web/lib/viewSettingScreens.ts)):** *not adopted in Phase 2* — the Purpose panel is the approved modal-baseline reference; no toolbar list-icon button. A screenId may be reserved later if the panel ever exposes the popover; see Open Question OQ-FE-1.
**JEX counterparts:** WSVCs `comm_0187_001` (list view), `comm_0187_001_r001` (list), `comm_0187_001_r002` (name search), `comm_0187_001_c001` (create), `comm_0187_001_u002` (bulk update + soft-delete), `comm_0187_001_u004` (reorder), `comm_0187_004_c001` (Excel upload). Plus the read-only sub-screens `comm_0187_001_u003` (reset to system defaults — JEX-only) and `comm_0187_003_*` (Unused / restore — JEX-only).
**Status:** **Implemented (backend)** — Purpose Settings endpoints are live; see wecms-api/CLAUDE.md. The frontend static-UI panel and child components (`PurposeTable`, `PurposeRow`, `PurposeFormRow`, `RowActions`, `ExcelUploadDialog`) already exist and consume `mockPurposeTree` from [mockData.ts](../../../../wecms-web/components/settings/purpose/mockData.ts); Phase 3 will replace the mock with live API calls.

> Legacy WSVC ids appear in this document only as JEX file:line citations and as migration-traceability prose. Every new identifier surface (REST URI, Java package, View Setting screenId, log tags, audit `WSVC_ID`) carries the kebab-case slug `purpose-settings`, never `comm_0187_001`. Cross-tree rule: workspace [CLAUDE.md §Cross-Tree Rules](../../../../CLAUDE.md); backend identifier rules: [wecms-api/CLAUDE.md §Refactor identifier naming](../../../CLAUDE.md); frontend rules: [wecms-web/CLAUDE.md §Routing + §13 View Setting](../../../../wecms-web/CLAUDE.md).

---

## 1. Overview

The Purpose screen is the per-tenant master list of **transaction purposes** (called "Use Codes" / "Usage Codes" in the legacy schema — column `USE_USAG_CD`). A purpose tags every payment / transfer / expense write so the tenant can categorize cash flow for reporting and reconciliation (Daily Cash, Daily Financial, Cash Flow reports, ERP exports). The screen lets a tenant administrator:

- **List** all active purposes for the current `BIZ_KEY`, sorted by `DISP_ORD`.
- **Add** a new purpose — supplying the user-visible code (`HAND_USE_USAG_CD`), name (`USE_USAG_NM`), description (`USE_USAG_CONT`), and an optional `PARENT_USE_USAG_CD` to nest under a level-1 parent.
- **Edit** an existing purpose in place (rename, change description, re-parent).
- **Soft-delete** a purpose by flipping `USE_YN='N'`, blocked when the purpose is still in use by any active child or any pending payment row.
- **Reorder** purposes via drag-and-drop, persisting the new `DISP_ORD` integers in one bulk update.
- **Bulk upload** purposes from an Excel template (Figma 9438:19204 Excel chip; legacy `comm_0187_004_c001`).

Hierarchy is **fixed at two levels** — a level-1 parent (`PARENT_USE_USAG_CD IS NULL`) may carry any number of level-2 children, but children never carry grandchildren. The frontend `PurposeNode` type ([types.ts](../../../../wecms-web/components/settings/purpose/types.ts)) already encodes this constraint; the backend must enforce it on every create / update.

The "Purpose" panel is the **approved modal baseline** for the entire WeCMS web (per [wecms-web/CLAUDE.md §UI Implementation Baseline](../../../../wecms-web/CLAUDE.md) — it's listed alongside Account Inquiry, Approval Box, and Sales e-Invoices as one of four approved visual references). Refactor visual fidelity is therefore non-negotiable: Figma 9438:19204 (read-only) + 9448:19960 (inline add/edit).

JEX file citations (source of truth for behavior — read-only):

| Artifact | Path |
|---|---|
| View JSP | [comm_0187_001_view.jsp](../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/comm/comm_0187_001_view.jsp) |
| TypeScript | [comm_0187_001.ts](../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0187_001.ts) |
| List action | [comm_0187_001_r001_act.jsp](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0187_001_r001_act.jsp) |
| Name-search action | [comm_0187_001_r002_act.jsp](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0187_001_r002_act.jsp) |
| Create action | [comm_0187_001_c001_act.jsp](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0187_001_c001_act.jsp) |
| Bulk update + soft-delete action | [comm_0187_001_u002_act.jsp](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0187_001_u002_act.jsp) |
| Reorder action | [comm_0187_001_u004_act.jsp](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0187_001_u004_act.jsp) |
| Reset-to-system action (JEX-only) | [comm_0187_001_u003_act.jsp](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0187_001_u003_act.jsp) |
| Excel upload action | comm_0187_004_c001_act.jsp |
| WSVC list | [WSVC.comm_0187_001_r001.xml](../../../../WECMS%20DEV/WECMS_ETC/xml/service/WSVC/WSVC.comm_0187_001_r001.xml) |
| WSVC create | [WSVC.comm_0187_001_c001.xml](../../../../WECMS%20DEV/WECMS_ETC/xml/service/WSVC/WSVC.comm_0187_001_c001.xml) |
| WSVC update | [WSVC.comm_0187_001_u002.xml](../../../../WECMS%20DEV/WECMS_ETC/xml/service/WSVC/WSVC.comm_0187_001_u002.xml) |
| Entity | [BusiUsagSetInfm.java](../../../../WECMS%20DEV/WECMS/src/wabooks/entity/BusiUsagSetInfm.java) + [BusiUsagSetInfmPk.java](../../../../WECMS%20DEV/WECMS/src/wabooks/entity/BusiUsagSetInfmPk.java) |
| DAO | [BusiUsagSetInfmDAO.java](../../../../WECMS%20DEV/WECMS/src/wabooks/dao/BusiUsagSetInfmDAO.java) |
| IDO default (R001/C001/U001/D001) | `WECMS_ETC/xml/service/IDO/IDO.BUSI_USAG_SET_INFM_*.xml` |

---

## 2. Refactor scope

| Concern | Decision |
|---|---|
| List (`comm_0187_001_r001`) | **In scope** — wired full-stack for Phase 2 / 3. |
| Name-search (`comm_0187_001_r002`) | **Merged into list** — the refactor exposes a single `GET /api/v1/settings/purpose-settings?keyword=…` and the keyword is just a `WHERE use_usag_nm ILIKE …` branch (matches JEX `r002`'s only behavioural difference). |
| Create (`comm_0187_001_c001`) | **In scope.** |
| Edit (`comm_0187_001_u002` REC) | **In scope** — exposed as `PUT /api/v1/settings/purpose-settings/{useUsagCd}` (single-row PUT, not the legacy bulk `REC` shape). |
| Soft-delete (`comm_0187_001_u002` REC2) | **In scope** — exposed as `DELETE /api/v1/settings/purpose-settings/{useUsagCd}` (returns 200 on success, 409 with `WBE1199` when the purpose has active children). |
| Reorder (`comm_0187_001_u004`) | **In scope** — `PUT /api/v1/settings/purpose-settings/order` with the full ordered list of `(useUsagCd, dispOrd)` pairs. |
| Excel bulk upload (`comm_0187_004_c001`) | **In scope for Phase 3** — `POST /api/v1/settings/purpose-settings/upload` accepting parsed rows (frontend parses the XLSX via the shared `<ExcelUploadDialog>` shell; backend validates + bulk-inserts inside one transaction). OQ-1 documents whether to share the legacy template format. |
| Reset to system defaults (`comm_0187_001_u003`) | **Out of scope.** The legacy reset wires into the system-default purpose set in `SYS_USAG_SET_INFM` and a per-group `USAG_GRP_CD` flow that no longer exists in the refactor. Defer; revisit if PO requires it for a future feature. |
| Unused list + restore (`comm_0187_003_*`) | **Out of scope.** The legacy "trash bin" view of `USE_YN='N'` rows is not in the Figma reference set. Soft-deleted rows stay queryable via the DB; no UI surface in Phase 2. |
| Legacy `USAG_GRP_CD` filter | **Dropped on the wire.** The refactor scopes the list to `USAG_GRP_CD IS NULL` only — matches the JEX comment "the page is showing the new level-2 hierarchy" ([comm_0187_001_r001_act.jsp:50](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0187_001_r001_act.jsp)). The column stays in the DB for legacy compatibility but is not surfaced. |
| Reserved system codes filter | **Preserved.** The list query excludes `1000000301`, `3000000401`-`3000000408` exactly as in legacy ([comm_0187_001_r001_act.jsp:58-67](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0187_001_r001_act.jsp)). |
| Language-detail table (`BUSI_USAG_SET_INFM_LANG_DETL`) | **Out of scope.** The legacy create JSP comment "Single-table model — USE_USAG_NM and USE_USAG_CONT live on the master row, the lang-detail table is no longer used" ([comm_0187_001_c001_act.jsp:86-88](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0187_001_c001_act.jsp)) is authoritative — refactor follows the single-table model, no IDO for the lang-detail table. |
| Purpose-Account mapping (`BUSI_USAG_ACCT_INFM_DETL`) | **Out of scope.** Separate feature (consumed by ERP debit/credit mapping); not part of the Purpose Settings panel. |
| `AUDIT_LOGS_INFM` writes | **In scope.** Every Create / Update / Delete / Reorder writes a row keyed by `WSVC_ID = 'purpose-settings'` (slug, per the 2026-05-09 cutover in workspace [CLAUDE.md](../../../../CLAUDE.md)). Read endpoints do NOT audit. |

---

## 3. Operations matrix

| # | Operation | HTTP | URI | Auth | Tenant scope | Maps to JEX | Audit |
|---|---|---|---|---|---|---|---|
| 1 | List + search | GET | `/api/v1/settings/purpose-settings` | JWT | implicit via `TenantContext.currentBizKey()` | `comm_0187_001_r001` (+ `r002` keyword merge) | No |
| 2 | Detail | GET | `/api/v1/settings/purpose-settings/{useUsagCd}` | JWT | implicit | `comm_0187_001_r001` (single-PK filter — convenience for edit pre-fetch) | No |
| 3 | Create | POST | `/api/v1/settings/purpose-settings` | JWT (ADMIN — see §6) | implicit | `comm_0187_001_c001` | 3 rows (EN / KO / VI) |
| 4 | Update | PUT | `/api/v1/settings/purpose-settings/{useUsagCd}` | JWT (ADMIN) | implicit | `comm_0187_001_u002` REC sub-rule | 3 rows |
| 5 | Soft-delete | DELETE | `/api/v1/settings/purpose-settings/{useUsagCd}` | JWT (ADMIN) | implicit | `comm_0187_001_u002` REC2 sub-rule | 3 rows |
| 6 | Reorder | PUT | `/api/v1/settings/purpose-settings/order` | JWT (ADMIN) | implicit | `comm_0187_001_u004` | 1 summary row |
| 7a | Excel preview | POST | `/api/v1/settings/purpose-settings/excel/preview` | JWT | implicit | `comm_0187_004_r001` (parse + validate; no DB writes) | No |
| 7b | Excel import | POST | `/api/v1/settings/purpose-settings/excel/import` | JWT (ADMIN) | implicit | `comm_0187_004_c001` | 1 summary row per batch |

**Not in scope** (no endpoint, no UI):
- Reset to system defaults (`comm_0187_001_u003`).
- Unused / restore (`comm_0187_003_r001`, `_u001`).
- Standardization settings (`comm_0187_002`).

---

## 4. Naming conventions — slug vs. legacy WSVC id

Canonical refactor slug: **`purpose-settings`**. Every new identifier surface MUST carry the slug; legacy WSVC ids appear ONLY in JEX file:line citations and in migration-traceability prose under [docs/refactor-wecms/migration/purpose-settings.md](../../references/migration/purpose-settings.md).

| Surface | Refactor identifier | Legacy WSVC id |
|---|---|---|
| Frontend panel mount | `<PurposePanel>` inside [`<SettingsModal>`](../../../../wecms-web/components/settings/SettingsModal.tsx) | ❌ never `comm_0187_001` |
| Spring REST URI | `/api/v1/settings/purpose-settings/...` | ❌ never `/api/v1/comm_0187_001/...` |
| Java package | `com.kosign.wecms.domain.setting.purposesettings` | ❌ never `com.kosign.wecms.domain.comm.comm_0187` |
| Java class names | `PurposeSettingsController`, `PurposeSettingsService`, `BusiUsagSet` (entity), `BusiUsagSetId` (composite PK) | ❌ never `Comm0187Service`, `BusiUsagSetInfm` (legacy entity name) |
| DTO records | `PurposeSettingRow`, `PurposeSettingDetail`, `PurposeSettingCreateRequest`, `PurposeSettingUpdateRequest`, `PurposeReorderRequest`, `PurposeUploadRequest`, `PurposeUploadResult` | ❌ never `Comm0187R001Response` |
| i18n message keys | `audit.purpose_settings.*`, `settings.purpose_settings.*` (snake-case inside the slug to match the rest of the namespace) | ❌ never `audit.comm_0187_001.*` |
| Frontend wire types | `ApiPurposeSettingRow`, `ApiPurposeSettingListResponse`, … in [lib/types/api.ts](../../../../wecms-web/lib/types/api.ts) | ❌ never |
| Frontend API client | `lib/api/purposeSettings.ts` (new file) | ❌ never |
| Frontend mappers | `mapPurposeSettingRow` in [lib/api/mappers.ts](../../../../wecms-web/lib/api/mappers.ts) | ❌ never |
| Log / MDC tags | `purpose-settings` | ❌ never |
| `AUDIT_LOGS_INFM.WSVC_ID` | `purpose-settings` | ❌ never `comm_0187_001` for new writes |

If you find yourself reaching for `comm_0187_001` outside JEX file:line citations or migration-traceability prose, **stop** — that's a bug. Use `purpose-settings`.

---

## 5. Field list — wire contract

JEX entity field references: [BusiUsagSetInfm.java](../../../../WECMS%20DEV/WECMS/src/wabooks/entity/BusiUsagSetInfm.java). DB types verified against DEV via `postgres` MCP (2026-05-12).

### 5.1 List / Detail row (wire output)

| Wire field (snake_case) | Type | Source DB column | Notes |
|---|---|---|---|
| `use_usag_cd` | string (10) | `USE_USAG_CD` | System PK — opaque, NOT user-facing on add. Generated server-side via `wb_get_next_val('busi_usag_set_infm','use_usag_cd', biz_key)` (see [docs/refactor-wecms/database/manual-tenant-setup.md](../../references/database/manual-tenant-setup.md) for the legacy generator helper pattern); see OQ-3. |
| `hand_use_usag_cd` | string (20) | `HAND_USE_USAG_CD` | **User-visible code** (e.g. `BD01`, `BD01-01`, `P01`). Required, unique within tenant + `USAG_GRP_CD IS NULL` + `USE_YN='Y'`. |
| `use_usag_nm` | string (255) | `USE_USAG_NM` | Display name. Required, unique within tenant + `USAG_GRP_CD IS NULL` + `USE_YN='Y'`. |
| `use_usag_cont` | string (500) | `USE_USAG_CONT` | Free-form description; nullable. |
| `parent_use_usag_cd` | string (20) | `PARENT_USE_USAG_CD` | `null` for level-1 nodes; references another active row's `use_usag_cd` for level-2 nodes. |
| `disp_yn` | string `Y`/`N` | `DISP_YN` | On/off toggle state — controls whether the purpose surfaces in payment dropdowns. Defaults to `Y` on create. |
| `disp_ord` | integer | `DISP_ORD` | Per-tenant sort order, 1-based, no required gaps. |
| `used_in_transactions` | boolean | derived | `true` when at least one active child OR one active payment row references this purpose. Used to gate the delete button on the frontend (`<RowActions deleteDisabled />` in [PurposePanel.tsx](../../../../wecms-web/components/settings/purpose/PurposePanel.tsx)). See §7.3 for the resolution rule. |
| `mod_id` | string (100) | `MOD_ID` | Last editor login id — surfaced for audit display only, never editable. |
| `mod_dtm` | string `yyyyMMddHHmmss` | `MOD_DTM` | UTC timestamp, legacy format preserved on the wire. |

**Wire envelope** (per workspace [CLAUDE.md API Naming](../../../../CLAUDE.md)): `{ status: { error, code, message }, data: { list: [...] } }`. Lowercase snake_case property names, global Jackson `SNAKE_CASE` strategy, no per-field `@JsonProperty`.

### 5.2 Create request (wire input)

| Wire field | Required | Length | Validation |
|---|---|---|---|
| `hand_use_usag_cd` | **yes** | 1-20 | `@NotBlank` + `@Size(max=20)`; service-layer duplicate check within `(biz_key, USAG_GRP_CD IS NULL, use_yn='Y')`. Matches `WBW1152` from legacy. |
| `use_usag_nm` | **yes** | 1-255 | `@NotBlank` + `@Size(max=255)`; service-layer duplicate check (same scope). Matches `WBW1138` from legacy. |
| `use_usag_cont` | no | 0-500 | `@Size(max=500)`. |
| `parent_use_usag_cd` | no | 0-20 | When provided, service-layer must verify the parent exists in the same tenant, is `USE_YN='Y'`, and is itself a level-1 node (`PARENT_USE_USAG_CD IS NULL`). Three-level nesting is forbidden. |
| `disp_yn` | no | 1 | Defaults to `Y`. Validated against the `YesOrNo` enum vocabulary. |

Server-derived on create:
- `use_usag_cd` ← `wb_get_next_val(...)` (or whatever the refactor decides per OQ-3).
- `biz_key` ← `TenantContext.currentBizKey()`.
- `usag_grp_cd` ← `NULL` (refactor exclusively uses the level-2 hierarchy bucket).
- `disp_ord` ← `MAX(disp_ord) + 1` within the tenant scope (so new rows sort to the end).
- `mod_id` ← `TenantContext.currentUserId()`.
- `mod_dtm` ← `Instant.now()` formatted to `yyyyMMddHHmmss` UTC.

### 5.3 Update request (wire input)

| Wire field | Required | Length | Validation |
|---|---|---|---|
| `use_usag_cd` | **yes** | exact 10 | Path param; service verifies the row exists in `(biz_key, use_usag_cd)`. |
| `hand_use_usag_cd` | **yes** | 1-20 | Required per legacy `comm_0187_001_u002_act.jsp:40`. No duplicate check on `hand_use_usag_cd` in the legacy update path — preserved as-is (OQ-4). |
| `use_usag_nm` | **yes** | 1-255 | Required + duplicate check **excluding self** (legacy `findDuplicateNameForUpdate` at [comm_0187_001_u002_act.jsp:54-72](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0187_001_u002_act.jsp)). |
| `use_usag_cont` | no | 0-500 | |
| `parent_use_usag_cd` | no | 0-20 | Same level-1 + active validation as Create. Reparenting between level-1 parents is permitted; demoting a level-1 with children to a level-2 is **forbidden** (would create three-level nesting). |
| `disp_yn` | no | 1 | Optional toggle update. |

### 5.4 Delete request

Single-row delete via `DELETE /api/v1/settings/purpose-settings/{useUsagCd}`. No request body.

Service-layer pre-check: `hasActiveChildren(...)` from legacy [comm_0187_001_u002_act.jsp:75-91](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0187_001_u002_act.jsp). When a level-1 row has at least one active child (`parent_use_usag_cd = ? AND use_yn = 'Y'`), the legacy throws `WBE1199`. The refactor returns `HTTP 409` with `status.code = WBE1199`.

Refactor extension (**OQ-5 DECIDED 2026-05-12 — recursive hard-fail, parent + children**): the legacy `hasActiveChildren` check covers parent-child references only. The refactor MUST ALSO block delete when:

1. The selected purpose itself is referenced by any active banking-transaction-history / payment / transfer record, OR
2. ANY child (level-2 descendant) of the selected purpose is referenced by such a record.

Behavior is **hard-fail with error popup** — no soft warning, no force-delete override. Confirmed from Figma comments: *"If the child data is used in other areas, does that mean the parent cannot be deleted?" → "Yes it is."*

Concrete consumer tables to inspect during Phase 2 design: `PYMN_*`, `TRNS_*`, `EXPS_*` — the exact JOINs land in [docs/refactor-wecms/backend/settings/purpose-settings.md §3.1](../../references/business-rules/settings/purpose-settings.md) once Phase 2 owner enumerates them. The list endpoint's `used_in_transactions` wire field uses the **same union of references** so the UI can pre-disable the delete button when either the row itself OR any of its active children is in use — the frontend never needs to re-check after the response.

### 5.5 Reorder request

```json
{
  "items": [
    { "use_usag_cd": "0000000001", "disp_ord": 1 },
    { "use_usag_cd": "0000000005", "disp_ord": 2 },
    ...
  ]
}
```

- Service validates that every row exists for the current tenant, that no `use_usag_cd` is repeated, and that no `disp_ord` is repeated.
- The legacy reorder ([comm_0187_001_u004_act.jsp:33-40](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0187_001_u004_act.jsp)) requires `USE_USAG_CD` + `DISP_ORD` per row — the refactor mirrors this exactly.
- Mutation runs inside one `@Transactional` block; rollback on any row failure.

### 5.6 Excel preview + import (two-step)

Phase 3 implementation moved from a single `POST /upload` endpoint to a **two-step preview / import flow** matching the VAS Account precedent (legacy `comm_0187_004_r001` + `_c001`). The static-UI Purpose wrapper (`<ExcelUploadDialog>` under [components/settings/purpose/](../../../../wecms-web/components/settings/purpose/ExcelUploadDialog.tsx)) consumes the shared [`<ExcelUploadDialog>`](../../../../wecms-web/components/shared/excel-upload/ExcelUploadDialog.tsx) shell and orchestrates both round-trips.

**Step 1 — preview.** `POST /api/v1/settings/purpose-settings/excel/preview` accepts a multipart `file` part (the user's XLSX). The backend `PurposeExcelService.preview(MultipartFile)`:

- Parses the workbook (Apache POI), reading the header from row 3 (POI index 2) — the template ships a "PURPOSE" title cell + spacer above the column headers; service skips them.
- Validates each data row (required + length + in-file duplicate `hand_use_usag_cd` + against-DB duplicate + parent-must-exist + parent-must-be-level-1).
- Returns a `PurposeExcelPreview` payload listing every row (valid + invalid) with per-cell pre-localized error messages plus the `total_count` / `completed_count` / `error_count` chips.

Errors thrown directly (file-level rejection, not per-row): `WBE_VALIDATION` (empty multipart), `WBE2041` (row count > 500), `WBE2042` (header doesn't match the canonical EN/KO/VI labels), `WBE2043` (corrupt workbook / wrong extension).

**Step 2 — import.** `POST /api/v1/settings/purpose-settings/excel/import` accepts the previewed rows as JSON:

```json
{
  "rows": [
    {
      "hand_use_usag_cd": "BD02",
      "use_usag_nm": "Operating Expenses",
      "use_usag_cont": "Day-to-day operating spend",
      "parent_use_usag_cd": null
    },
    ...
  ]
}
```

- Service re-runs the same validation against the live DB state (race-safety — the preview's `valid` flag is never trusted).
- Insert order is **roots first, children second** so the children can resolve their parent's freshly-generated `use_usag_cd` (mirrors legacy `orderRootsFirst`).
- Atomic — any row's validation / parent-resolve / PK-collision failure rolls back the entire batch with `status.code = WBE_VALIDATION`.
- Returns `PurposeExcelImportResult` carrying `inserted_count`. The flow is pure-append — Purpose does NOT have a reset/replace mode (unlike VAS Account; the legacy `BUSI_USAG_SET_INFM` has no equivalent wipe path).

Per-cell error category codes used by the preview UI: `required`, `length`, `dup_file`, `dup_db`, `parent_not_found`, `parent_not_root` (mirror Spring `PurposeExcelService.ERR_*`).

---

## 6. Permission model

Tenant administrators only.

- **Read** (`GET .../purpose-settings` and `.../purpose-settings/{useUsagCd}`) — any authenticated user of the tenant. Surfaced in payment / transfer dropdowns by every other module.
- **Mutate** (POST / PUT / DELETE / upload / reorder) — `ROLE_ADMIN` only. Resolved from `BUSI_ADMN_INFM` at login finalize per [wecms-api/CLAUDE.md §Authority model](../../../CLAUDE.md); JWT carries `authGrCd = "ADMIN"`. The Phase 2 backend lands `@PreAuthorize("hasRole('ADMIN')")` on every mutating method.
- Legacy parity check: the JEX action JSPs do not perform an explicit authority check — JEX relies on menu visibility (the Settings → Purpose menu entry is admin-only per `USER_MENU_AUTH_INFM`). The refactor keeps the same effective permission boundary but enforces it via the explicit `@PreAuthorize` annotation rather than menu visibility. This is documented in OQ-6.

---

## 7. Validation & business rules

### 7.1 Required fields

| Operation | Required |
|---|---|
| Create | `hand_use_usag_cd`, `use_usag_nm` |
| Update | `use_usag_cd` (path), `hand_use_usag_cd`, `use_usag_nm` |
| Delete | `use_usag_cd` (path) |
| Reorder | `items[].use_usag_cd`, `items[].disp_ord` (per [comm_0187_001_u004_act.jsp:37-40](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0187_001_u004_act.jsp)) |
| Upload | per-row `hand_use_usag_cd`, `use_usag_nm` |

### 7.2 Uniqueness

| Field | Scope | Refactor enforcement |
|---|---|---|
| `hand_use_usag_cd` | `(biz_key, USAG_GRP_CD IS NULL, use_yn='Y')` | Service-layer pre-check on Create only (legacy parity — Update does not check). Throws `WBW1152` on conflict. |
| `use_usag_nm` | `(biz_key, USAG_GRP_CD IS NULL, use_yn='Y')` | Service-layer pre-check on Create AND Update (Update excludes self). Throws `WBW1138` on conflict. |

The DB does NOT have a unique constraint on either column (only the PK on `(biz_key, use_usag_cd)`). Uniqueness is enforced at the service layer because the legacy `usag_grp_cd is null AND use_yn='Y'` scope is a partial-index pattern that the legacy schema never materialized. The refactor preserves the same service-layer guard (an optional partial unique index is OQ-7).

### 7.3 `used_in_transactions` resolution

Backend computes this on every list / detail read by `LEFT JOIN`ing against a `purpose_in_use_v` view (to be created in docs/refactor-wecms/database/purpose-settings.md (Needs Confirmation)):

```sql
CREATE OR REPLACE VIEW v_purpose_in_use AS
SELECT DISTINCT biz_key, use_usag_cd FROM (
  -- a row is "in use" when:
  --   (a) another active purpose lists it as PARENT_USE_USAG_CD
  SELECT biz_key, parent_use_usag_cd AS use_usag_cd
  FROM busi_usag_set_infm
  WHERE parent_use_usag_cd IS NOT NULL AND use_yn = 'Y'
  -- UNION  (b) an active payment / transfer / expense row references it
  --        — actual UNION branches added by Phase 2 once consumer tables are confirmed; see OQ-5
) t;
```

The view name follows the local-DB naming convention; legacy doesn't have an equivalent view because legacy resolves "in use" inside the `_u002_act.jsp` JSP. The refactor moves the resolution into a single DB view so the list endpoint can `LEFT JOIN` once rather than running N+1 lookups per page render.

### 7.4 Hierarchy rules

- **Exactly two levels.** Level-1 rows have `parent_use_usag_cd IS NULL`; level-2 rows have `parent_use_usag_cd` set to an active level-1 row's `use_usag_cd`.
- **Reparenting** within level-2 is permitted (move a child from parent A to parent B).
- **Promotion** of a level-2 row to level-1 is permitted (set `parent_use_usag_cd = null`).
- **Demotion** of a level-1 row that has active children is forbidden (would create grandchildren). Service rejects with `BusinessException("WBE_VALIDATION", args)` per OQ-8 — no new `WBE_PURPOSE_*` code.
- **Self-reference** is forbidden (a row cannot have `parent_use_usag_cd = use_usag_cd`).

### 7.5 Reserved system codes

Excluded from every list response — same set as legacy [comm_0187_001_r001_act.jsp:58-67](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0187_001_r001_act.jsp):

```
1000000301, 3000000401, 3000000402, 3000000403, 3000000404,
3000000405, 3000000406, 3000000407, 3000000408
```

Codified as a Java constant `PurposeSettingsService.RESERVED_SYSTEM_CODES` (immutable `Set<String>`) and applied to every read.

---

## 8. Screen states (frontend)

Sourced from [PurposePanel.tsx](../../../../wecms-web/components/settings/purpose/PurposePanel.tsx) + Figma 9438:19204 (read) + 9448:19960 (inline add/edit).

| State | Trigger | UI |
|---|---|---|
| **List (idle)** | Panel mount, GET resolves | Two-level tree table sorted by `DISP_ORD`; level-1 rows expandable; per-row `<RowActions>` chip (edit pencil + delete trash). "+ Add Purpose" button at the bottom of the panel. Empty Excel Upload chip in the panel toolbar. |
| **List (loading)** | Panel mount, GET in flight | Skeleton rows (TBD by frontend in Phase 3) + the existing chrome. |
| **List (error)** | GET fails | `useDialog().open({ type: "error", errorCode: status.code })`; static mock retained as last-good fallback per `feedback_figma_first` not-applicable since no Figma URL was provided for the error state. |
| **Add (inline)** | Click "+ Add Purpose" | `<PurposeFormRow mode={{ kind: "add" }}>` mounts in a sticky-bottom slot; "+ Add Purpose" button replaced by an inert spacer to keep panel rhythm steady (per [PurposePanel.tsx:26-31](../../../../wecms-web/components/settings/purpose/PurposePanel.tsx)). Save → POST → optimistic insert OR success popup + refetch; Cancel → discard local state. |
| **Edit (inline)** | Click row pencil | `<PurposeFormRow mode={{ kind: "edit", node, parentCode }}>` mounts at the bottom seeded from the row; original row stays visible at its normal place. Save → PUT → mutate node in place + refetch; Cancel → discard. |
| **Delete confirmation** | Click row trash | `useDialog().open({ type: "confirmation", … })`; on confirm, DELETE → refetch. On 409 / `WBE1199`, dialog flips to `type: "error"` with localized message. |
| **Reorder** | Drag-and-drop a row | UI mutates `DISP_ORD` locally for visual feedback; PUT `/order` fires on drop (debounced? — TBD in Phase 3); on error, revert to pre-drag state. |
| **Excel upload — Step 1 (download template)** | Click Excel chip | `<ExcelUploadDialog>` opens; user downloads the template (provided by backend per OQ-1 or by a frontend static asset). |
| **Excel upload — Step 2 (browse)** | User uploads XLSX | Frontend parses; rows render with per-cell error highlights per the shared shell contract. |
| **Excel upload — Step 3 (preview + confirm)** | Click Upload | POST `/upload`; on success, panel refetches; on validation errors, the dialog stays open with row-level highlights. |
| **Disabled toggle** | Click row toggle | Frontend optimistic flip on `disp_yn`; PUT against the same row updates DB. |

### Search / filter

The Figma read-state shows **no search input** in the panel header. Legacy `comm_0187_001_r002` (name keyword search) is therefore an implementation-internal endpoint without a corresponding visible filter, kept only for parity with the legacy `_view.jsp` filter row that the refactor design omits. Refactor wires the `?keyword=` query parameter on the list endpoint anyway so a future Figma frame can add the filter without a backend change. OQ-FE-2.

---

## 9. Audit-log expectations

Every Create / Update / Delete / Reorder / Upload writes rows into `AUDIT_LOGS_INFM` via the shared `AuditLogService` (per [docs/refactor-wecms/features/settings/firm-banking-settings.md §11](firm-banking-settings.md)).

| Column | Value |
|---|---|
| `BIZ_KEY` | `TenantContext.currentBizKey()` |
| `AUDIT_LOGS_SEQ_NO` | `wb_get_next_val('audit_logs_infm','audit_logs_seq_no', biz_key)` |
| `WSVC_ID` | `'purpose-settings'` (refactor slug, per the 2026-05-09 cutover) |
| `LANG_CD` | one row per supported locale: `EN`, `KO`, `VI` |
| `REF_NO` | `'BUSI_USAG_SET_INFM'` (table name; mirrors the firm-banking pattern) |
| `REF_NO1` | `use_usag_cd` (system code, identifies the row) |
| `REF_NO2` | `hand_use_usag_cd` (user-visible code, surfaced in the audit row body for readability) |
| `REF_NO3` | NULL |
| `REF_NO4` / `REF_NO5` | NULL |
| `AUDIT_LOGS_ACTV` | one of `CREATE`, `UPDATE`, `DELETE`, `REORDER`, `UPLOAD` |
| `AUDIT_LOGS_ACTV_DTL` | localized human-readable summary — e.g. `"Added purpose BD02 (Operating Expenses)"`, `"Renamed BD02 → BD02-A"`, `"Soft-deleted BD02"`. Resolved via `MessageResolver` from per-feature i18n keys (`audit.purpose_settings.create`, `.update`, `.delete`, `.reorder`, `.upload`). |
| `REG_ID` | `TenantContext.currentUserId()` |
| `REG_NM` | resolved from `USER_INFM.user_nm` at write time |
| `REG_DTM` | `Instant.now()` formatted to `yyyyMMddHHmmss` UTC |

Append runs inside `@Transactional(propagation = REQUIRES_NEW)` so an audit insert failure does not roll back the business transaction.

---

## 10. Error codes

**OQ-8 decided 2026-05-12 (Sonny):** reuse existing legacy / generic codes only. **Do not introduce a `WBE_PURPOSE_*` family.** New Purpose-specific codes may be added later if PO / Sonny explicitly requests them — until then, the table below is the complete code surface for this feature.

| Code | HTTP | Meaning | Origin |
|---|---|---|---|
| `WBW1152` | 409 | Duplicate `hand_use_usag_cd` within tenant + active scope. | **Legacy** ([`comm_0187_001_c001_act.jsp:111`](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0187_001_c001_act.jsp)) — preserved verbatim. |
| `WBW1138` | 409 | Duplicate `use_usag_nm` within tenant + active scope (on create AND on update — excluding self on update). | **Legacy** ([`comm_0187_001_c001_act.jsp:116`](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0187_001_c001_act.jsp), [`_u002_act.jsp:135`](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0187_001_u002_act.jsp)) — preserved verbatim. |
| `WBE1199` | 409 | Cannot delete — purpose has active children, **OR** the purpose / any of its active children is referenced by an active banking-transaction-history / payment / transfer record (the recursive OQ-5 case). | **Legacy** ([`comm_0187_001_u002_act.jsp:157`](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0187_001_u002_act.jsp)) — **scope broadened in the refactor** to cover both situations under one code. The wire code stays `WBE1199`; the i18n message resolves to a localized "cannot delete — this purpose or one of its sub-purposes is in use" string covering both conditions generically. |
| `WBE_VALIDATION` | 400 | Bean-Validation failure (length / `@NotBlank`) **OR** any Purpose-specific service-layer validation failure: invalid `parent_use_usag_cd` (does not resolve to an active level-1 row), self-parent (`parent_use_usag_cd == use_usag_cd`), demote-blocked (assigning a parent to a row that itself has active children — would create three levels), or invalid reorder payload (duplicate `use_usag_cd`, duplicate `disp_ord`). | **Existing generic** — refactor reuses it for every Purpose-specific validation case. The structured message carries the specific reason via `MessageResolver` args. |
| `WBE1009` | 401 | JWT missing / invalid. | **Existing generic** — handled by the shared `JwtAuthenticationFilter`. |
| `WBE9999` | 500 | Unexpected server error. | **Existing generic** — global handler catch-all. |

**Phase 2 implementation notes:**

- Throw `BusinessException("WBW1152")` / `("WBW1138")` / `("WBE1199")` from the service for the three legacy cases.
- For the broadened `WBE1199` (recursive delete pre-check from OQ-5), the same code carries both meanings — frontend renders one localized message that covers both ("Cannot delete — this purpose or one of its sub-purposes is in use.").
- For every other validation failure (parent invalid, self-parent, demote-blocked, reorder invalid), throw `BusinessException("WBE_VALIDATION", new Object[]{ <field>, <reason> })`. The resolver renders a contextual message — e.g. `"Validation failed: parent_use_usag_cd must reference an active level-1 purpose."`.
- **No new keys** added to [messages.properties](../../../src/main/resources/messages.properties) for Purpose-specific reasons. Verify the legacy codes' message strings still exist in the refactor `messages.properties`; if any of `WBW1152` / `WBW1138` / `WBE1199` is missing, port the legacy `dictionary_*.properties` text into the refactor message bundle (this is text reuse, not a new code).

---

## 11. Risks & open questions

### OQ-1 — Excel template format — **DECIDED 2026-05-12**

**Resolution:** Follow the existing VAS Account Excel upload template pattern ([components/settings/vas-account/ExcelUploadDialog.tsx](../../../../wecms-web/components/settings/vas-account/ExcelUploadDialog.tsx)) — same shared `<ExcelUploadDialog>` shell wiring, same step layout, same Download / Re-Select / Preview / Upload flow. The Purpose-specific column set (currently `HAND_USE_USAG_CD`, `USE_USAG_NM`, `USE_USAG_CONT`, `PARENT_USE_USAG_CD`, `DISP_YN`) and validation rules in the existing static-UI [components/settings/purpose/ExcelUploadDialog.tsx](../../../../wecms-web/components/settings/purpose/ExcelUploadDialog.tsx) are correct as-is.

**Final columns / validation rules can be adjusted later** if PO or Sonny provides different requirements — Phase 2 should match whatever the per-feature wrapper carries at wire-up time. No frontend static-UI change required.

### OQ-2 — Inline-form parent dropdown — **DECIDED 2026-05-12**

**Resolution:** The current static-UI inline add / edit row in [`<PurposePanel>`](../../../../wecms-web/components/settings/purpose/PurposePanel.tsx) + [`<PurposeFormRow>`](../../../../wecms-web/components/settings/purpose/PurposeFormRow.tsx) is correct and stays as-is. The parent selector **must show only parent-level (level-1) Purpose items** — child (level-2) rows are never selectable as a parent. This matches Figma's two-level Purpose management direction and the data-model constraint that the hierarchy is fixed at two levels (no grandchildren).

**Implementation note for Phase 3:** when the parent selector is wired against live data, filter the candidate list to `nodes.filter(n => n.parent_use_usag_cd === null && n.use_yn === 'Y')` — i.e. active level-1 rows only. The backend `validateParent(...)` check is still authoritative (rejects level-2 as parent with `BusinessException("WBE_VALIDATION", args)` per OQ-8), but the frontend MUST NOT expose level-2 codes in the selector at all.

No static-UI change required — proceed with the current panel implementation.

### OQ-3 — `use_usag_cd` generation — **DECIDED 2026-05-12**

**Resolution:** Port the existing legacy DB function `wb_get_next_val(...)` (option (a) in the original analysis) and call it from the refactor service. This is a **documented exception** to the "refactor does not add legacy PL/pgSQL helpers" policy in [wecms-api/CLAUDE.md](../../../CLAUDE.md) — Sonny approved porting this specific function because reproducing its per-table semantics in application code would duplicate logic that already exists on DEV.

**Function (verified against DEV via `postgres` MCP, 2026-05-12):**

```sql
wb_get_next_val(
    tablename  varchar,
    fieldname  varchar,
    param1     varchar DEFAULT NULL,   -- biz_key for this table
    param2     varchar DEFAULT NULL,   -- usag_grp_cd for this table (empty '' for refactor scope)
    param3..6  varchar DEFAULT NULL    -- unused for busi_usag_set_infm
) RETURNS text
```

**`busi_usag_set_infm.use_usag_cd` branch (refactor scope: `usag_grp_cd` empty):**

```sql
when lower($1) = 'busi_usag_set_infm' and lower($2) = 'use_usag_cd'
     and coalesce(trim($3), '') <> ''
     and coalesce(trim($4), '') = '' then
    newSeqNo := (
        select coalesce(lpad((max(use_usag_cd)::numeric + 1)::varchar, 10, '0'),
                        '0000000001')
        from busi_usag_set_infm
        where biz_key = $3
          and use_usag_cd ~ '^0[0-9]{9}$'
    );
```

Range: `0000000001 .. 0999999999`. The leading-`0` regex avoids collision with the legacy group-prefixed codes (`1xxxxxxxxx`, `3xxxxxxxxx`, `4xxxxxxxxx`, `6xxxxxxxxx`, `91xxxxxxxx`) — confirmed in the function's own comment block.

**Call site:** `wb_get_next_val('busi_usag_set_infm', 'use_usag_cd', :bizKey, '')`. Spring service invokes via a `@Query(value = "SELECT wb_get_next_val(...)", nativeQuery = true)` repository method.

**Concurrency caveat:** the function uses no advisory lock / `FOR UPDATE` / serializable txn — bare `MAX+1`. Concurrent calls from two threads against the same `biz_key` could in principle produce the same code. The refactor mitigates this two ways: (1) wrap the call + the immediate `INSERT` in one `@Transactional` block so the PK constraint catches a collision; (2) on `DataIntegrityViolationException` for the PK, retry once. Phase 2 owner documents the retry policy in the service Javadoc.

**`disp_ord` branch — Phase 2 verification needed.** Sonny's direction says to use the same function for `disp_ord`. The branch surfaced from DEV requires `coalesce(trim($4), '') <> ''` (i.e. `usag_grp_cd` non-empty) — which is the opposite of the refactor's `usag_grp_cd IS NULL` scope. Two possibilities for Phase 2 owner to verify:

- **(p1)** The function has a sibling `disp_ord` branch for the `usag_grp_cd` empty case that wasn't surfaced in the initial extraction — re-grep the DEV function and confirm. If found, call `wb_get_next_val('busi_usag_set_infm', 'disp_ord', :bizKey, '')`.
- **(p2)** The function has only the non-empty branch — in which case the refactor service computes `disp_ord` itself via `repo.findMaxDispOrd(bizKey).orElse(0) + 1` filtered by `usag_grp_cd IS NULL AND use_yn = 'Y'`. Same semantics, no DB-function surface for `disp_ord`.

Default if unverified: **(p2)** in-service computation, since the refactor's `findMaxDispOrd` repository method already exists in [backend §4](../../references/business-rules/settings/purpose-settings.md). Either way, the result is identical (next integer after the current max).

### OQ-4 — `hand_use_usag_cd` duplicate check on update

Legacy `_u002_act.jsp` does NOT check `hand_use_usag_cd` uniqueness on update (only `use_usag_nm` is checked). **Decision needed:** preserve legacy behaviour (no check) or tighten to match Create? Default: **preserve legacy** — the JEX behaviour is intentional ("user can rename the visible code at will, system identifier is `use_usag_cd`"). Phase 2 confirms with PO.

### OQ-5 — Delete pre-check on payment / transfer references — **DECIDED 2026-05-12**

**Resolution:** Recursive hard-fail. Block delete whenever the selected purpose OR any of its active children is referenced by a banking-transaction-history / payment / transfer record. No soft-warn, no force-delete. Source: Figma comments — *"If the child data is used in other areas, does that mean the parent cannot be deleted?" → "Yes it is."*

**Phase 2 work still required (not blocking the decision):** enumerate the consumer table set (`PYMN_*`, `TRNS_*`, `EXPS_*` — exact list TBD when consumer entities land in the refactor) and wire each as a UNION branch of the `v_purpose_in_use` view (see [database doc §2.2](../../references/database/settings/purpose-settings-schema.md)). Service-layer delete handler returns 409 + `WBE1199` (per OQ-8 — single wire code covers both children case and transaction-reference case) when either (a) `v_purpose_in_use` has a row for the deleted `use_usag_cd`, or (b) `v_purpose_in_use` has a row for any active child whose `parent_use_usag_cd` equals the deleted `use_usag_cd`.

### OQ-6 — Permission boundary

Legacy enforces admin-only via menu visibility (`USER_MENU_AUTH_INFM`). The refactor lands `@PreAuthorize("hasRole('ADMIN')")` on mutations. **Risk:** any tenant user who knows the URL can hit the read endpoint. That matches legacy intent (read is broadly available for payment dropdowns), but should be confirmed.

### OQ-7 — Partial unique index

Optional Phase 2 hardening: `CREATE UNIQUE INDEX busi_usag_set_infm_hand_uq ON busi_usag_set_infm (biz_key, hand_use_usag_cd) WHERE usag_grp_cd IS NULL AND use_yn = 'Y'` + sibling on `use_usag_nm`. Provides DB-level defence against race-condition duplicates that slip past the service-layer pre-check. Off by default; Phase 2 decides.

### OQ-8 — Error code finalization — **DECIDED 2026-05-12**

**Resolution (Sonny):** reuse existing legacy / generic codes only — no new `WBE_PURPOSE_*` family. The complete code surface for this feature is `WBW1152`, `WBW1138`, `WBE1199`, `WBE_VALIDATION`, `WBE1009`, `WBE9999` per the table in §10. `WBE1199`'s scope broadens to cover both the legacy "has children" case and the OQ-5 transaction-reference case — same wire code, one localized message that covers both.

New Purpose-specific codes may be added later only if PO / Sonny explicitly requests them. Phase 2 owner does NOT add new keys to `messages.properties` for parent-invalid / self-parent / demote-blocked / reorder-invalid — those throw `BusinessException("WBE_VALIDATION", args)` with contextual message-args.

### OQ-FE-1 — View Setting adoption

The Purpose panel is the modal baseline reference and Figma shows no list-icon button. **Decision:** do NOT adopt View Setting in Phase 2. If a future Figma frame adds the toolbar list-icon, reserve `SCREEN_IDS.SETTINGS_PURPOSE_SETTINGS = "purpose-settings"` in [lib/viewSettingScreens.ts](../../../../wecms-web/lib/viewSettingScreens.ts) + a Flyway seed.

### OQ-FE-2 — Search input

The Figma read-state shows no search input on the panel. Backend still wires `?keyword=` for parity; frontend Phase 3 decides whether to surface a search input or wait for an explicit Figma frame.

---

## 12. References

- Workspace [CLAUDE.md](../../../../CLAUDE.md) — Cross-Tree Rules, slug naming, audit `WSVC_ID` cutover.
- [wecms-api/CLAUDE.md](../../../CLAUDE.md) — API naming, identifier rules, package conventions.
- [wecms-web/CLAUDE.md](../../../../wecms-web/CLAUDE.md) — Settings shell + panel composition (§3a), settings UI consistency (§3b), Excel upload (§3a), View Setting (§13), Routing.
- Similar refactor feature: [docs/refactor-wecms/features/settings/firm-banking-settings.md](firm-banking-settings.md) (closest analog — CRUD on a tenant master table inside the Settings modal).
- Similar refactor feature: [docs/refactor-wecms/features/settings/erp-transaction-history.md](transaction-history.md) (Settings panel, read-only, audit-log integration).
- Sibling docs (Phase 1):
  - [docs/refactor-wecms/migration/purpose-settings.md](../../references/migration/purpose-settings.md) — JEX → Spring mapping.
  - docs/refactor-wecms/backend/purpose-settings.md (Needs Confirmation) — Spring Boot design.
  - [docs/refactor-wecms/frontend/purpose-settings.md](../../../../wecms-web/docs/features/settings/purpose.md) — Next.js design.
  - docs/refactor-wecms/database/purpose-settings.md (Needs Confirmation) — DB schema + Flyway plan.
  - [docs/refactor-wecms/planning/purpose-settings-task-plan.md](../../archive/planning/settings/purpose-settings-task-plan.md) — task ordering + effort.
