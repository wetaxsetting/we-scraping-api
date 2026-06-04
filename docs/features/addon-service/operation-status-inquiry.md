# Add-on Service > Operation Status Inquiry — Refactor Feature Spec

> **✅ Feature complete (2026-06-04).** Inquire / Update / Save / Export are all implemented and frontend-wired. The status notes further down this file are **design-history** from when parts were still deferred — for the current, code-accurate state see the consolidated folder: [operation-status-inquiry/README.md](operation-status-inquiry/README.md) (business-flow · functional-specification · database-mapping · interface-specification · source-code-index · migration-guide · test-scenarios · refactor-gap-analysis).

**Module:** Add-on Service
**Menu:** Operation Status Inquiry (Vietnamese tax-authority operating status of customers / suppliers)
**Route:** [/addon/operation-status](../../../../wecms-web/app/(dashboard)/addon/operation-status/page.tsx)
**Refactor screen ID (proposed):** `addon-operation-status`
**JEX counterpart (legacy):** `aosv_0001_001` (view) / `aosv_0001_001_r001` (read service) — no JEX feature doc exists yet under [docs/jex/features/](../../../../docs/jex/features/)
**Status:** **UI complete + Spring Boot read endpoint working end-to-end** for the read path. The page renders rows from `BUSA_DOC_MSTR`'s sister table `OPRT_STTS_INFO` via `GET /api/v1/addon/operation-status`. **Inquire (`/inquire`), Update (`/update`), and Export (`/export`) are ALL wired** on the frontend ([OperationStatusClient](../../../../wecms-web/components/addon/operation-status/OperationStatusClient.tsx) / [InquireOperationStatusDialog](../../../../wecms-web/components/addon/operation-status/InquireOperationStatusDialog.tsx) / [OperationStatusPage](../../../../wecms-web/components/addon/operation-status/OperationStatusPage.tsx)) to live backend endpoints (verified 2026-06-02). The ONLY inert piece is the inquire-dialog **Save/persist** action — there is no backend upsert endpoint yet, so the dialog cannot persist its result.

JEX is **read-only reference**. This spec captures the refactor design; JEX behaviour is the functional source of truth.

---

## Overview

Listing of every counterparty (customer or supplier) whose **Vietnamese tax-authority operating status** has been polled into WeCMS. Each row carries the company's Vietnamese tax code (MST), official company name, registered office address, the live "operation status" string returned by the General Department of Taxation (GDT — Tổng cục Thuế), and the last poll timestamp. The finance team uses this page to:

1. Spot counterparties whose tax-registration is suspended / closing — those flags invalidate input-VAT credit on outbound invoices.
2. Reconcile counterparty MST against the e-invoice listing (`addon-sales-einvoices` / `addon-expenses-einvoices`) which tags each row with `COMPANYSTATUS` from this same table.
3. Trigger a refresh against GDT (legacy "Inquire Operation Status" — out of refactor R&D scope; see TODO).

The page is **read-only on the WeCMS-managed side**; the underlying status string is owned by GDT and is overwritten on every successful pull.

---

## Business purpose

Vietnamese Decree 123/2020 + Circular 78/2021 require every B2B sale to be invoiced to a counterparty whose tax-registration is in good standing on the day of issuance. Issuing an e-invoice to a buyer flagged as "ngừng hoạt động" (ceased) or "tạm ngừng" (temporarily suspended) puts the seller's input-VAT credit at risk on the buyer side and creates an audit-trail problem on the seller side.

WeCMS keeps a per-counterparty snapshot in `OPRT_STTS_INFO`, refreshed from the GDT lookup endpoint via the legacy NXISAS / OSMC bridge. This page is the operator-facing window into that table. Refactor scope is the **read window only** — the GDT pull mechanism is an integration concern documented under TODO.

---

## Current status

- **UI:** complete (Figma node 12346:109158). Page composes [`<OperationStatusPage>`](../../../../wecms-web/components/addon/operation-status/OperationStatusPage.tsx) (5-column table with checkbox + Company / Tax code · Address · Operation Status · Last Update) + [`<OperationStatusActionBar>`](../../../../wecms-web/components/addon/operation-status/OperationStatusActionBar.tsx) (Excel · Print · Update · Inquire Operation Status CTA) + a single-row filter (status dropdown trigger + [`<SearchInput>`](../../../../wecms-web/components/shared/SearchInput.tsx)). Uses [`<DataTableShell footerMode="paginationOnly">`](../../../../wecms-web/components/shared/DataTableShell.tsx) per [§7a / §7a.1](../../../../wecms-web/CLAUDE.md) — Figma has pagination but **no totals row**, so no sibling footer table is rendered.
- **Frontend wiring:** [`<OperationStatusClient>`](../../../../wecms-web/components/addon/operation-status/OperationStatusClient.tsx) calls [`fetchOperationStatus(q?)`](../../../../wecms-web/lib/api/addon.ts) via `useApiData`; rows pass through [`mapOperationCompanyRow`](../../../../wecms-web/lib/api/mappers.ts) which coerces the GDT free-text Vietnamese status into the canonical 3-state enum (`active_certificate_issued | suspended | not_registered`).
- **Backend:** Spring Boot read endpoint is wired end-to-end. [`OperationStatusController`](../../../src/main/java/com/kosign/wecms/domain/operation/controller/OperationStatusController.java) (`GET /api/v1/addon/operation-status`) → [`OperationStatusService`](../../../src/main/java/com/kosign/wecms/domain/operation/service/OperationStatusService.java) → [`OprtSttsInfoRepository`](../../../src/main/java/com/kosign/wecms/domain/operation/repository/OprtSttsInfoRepository.java) → entity [`OprtSttsInfo`](../../../src/main/java/com/kosign/wecms/domain/operation/entity/OprtSttsInfo.java) (composite PK `(BIZ_KEY, TAXCODE)`) → table `OPRT_STTS_INFO`. Filters by tenant `BIZ_KEY`, optional `q` (case-insensitive substring match on `companyname` OR `taxcode`), order by `searchdatetime DESC`. Schema in [V16](../../../src/main/resources/db/migration/V16__sprint1_operation_status_schema.sql); seed in [V17](../../../src/main/resources/db/migration/V17__sprint1_seed_data.sql) (5 demo rows for tenant `VN_VNM0004_000001`).
- **Pagination:** server-side pagination is **NOT yet wired**. The frontend computes a 15-row pseudo-page client-side; the backend ignores `page` / `size`.
- **Status filter dropdown:** the trigger renders ("All Operation Status") but the popover is not built — selection cannot change the wire request.
- **Update / Inquire / Export buttons:** **wired** (verified 2026-06-02) — Inquire (`/inquire`), Update (`/update`), and Export (`/export`) all call live backend endpoints from [`<OperationStatusClient>`](../../../../wecms-web/components/addon/operation-status/OperationStatusClient.tsx) / [`<InquireOperationStatusDialog>`](../../../../wecms-web/components/addon/operation-status/InquireOperationStatusDialog.tsx) / [`<OperationStatusPage>`](../../../../wecms-web/components/addon/operation-status/OperationStatusPage.tsx). The ONLY inert piece is the inquire-dialog **Save/persist** action — no backend upsert endpoint exists yet, so the dialog cannot persist its result.
- **i18n:** EN / KO / VI keys live under the `operation.*` prefix (`operation.title`, `operation.col.*`, `operation.status.*`, `operation.filter.*`, `operation.inquire_cta`). KM is missing.
- **View Setting:** **NOT registered.** This page is not in [`SCREEN_IDS`](../../../../wecms-web/lib/viewSettingScreens.ts) and has no row in `SYS_SCR_OPTION_WECMS`. Adoption is a Phase 4 deliverable.

---

## Refactor goal

Migrate from the JEX `aosv_0001_001_r001` action JSP path to a dedicated Spring Boot REST endpoint owned by `domain/operation/`. Preserve the legacy WSVC field IDs verbatim on the wire (UPPER_SNAKE) so traceability stays intact. Introduce server-side pagination + the missing legacy filter (`COMPANYSTATUS`) on the same MR that wires the dropdown, and adopt View Setting + Excel export per the global rules in [§13](../../../../wecms-web/CLAUDE.md) / [§13b](../../../../wecms-web/CLAUDE.md). The GDT-pull "Inquire" / "Update" semantics stay deferred — refactor R&D scope is local-only per [project_refactoring_scope.md](../../planning/) memory.

---

## Scope

**In scope (refactor goal):**
- Read-only listing of `OPRT_STTS_INFO` rows for the current tenant (`BIZ_KEY = TenantContext.currentBizKey()`).
- Per-row fields: tax code (MST), company name, address, operation status (raw GDT string + canonical refactor enum), last poll timestamp.
- Filter: keyword search (taxcode / company name partial match), operation-status dropdown.
- Server-side pagination + sort.
- Update button as a local-data refresh (`refreshKey++` → refetch list endpoint).
- Excel export (per [§13b](../../../../wecms-web/CLAUDE.md)) — landing in same MR as View Setting adoption.
- View Setting column metadata + Excel column intersection per [§13](../../../../wecms-web/CLAUDE.md), keyed by `WEB_SVC_ID = addon-operation-status`.

**Not in scope (refactor R&D):**
- **UI redesign.** The Figma frame (12346:109158) is approved as-is.
- **GDT live pull** (legacy "Inquire Operation Status" CTA — `nxisas_oprtstts_*`). The R&D scope is local-only; no NXISAS bridge, no GDT lookup. The button stays inert in Phase 1; full integration deferred indefinitely.
- **Update via `aosv_0001_001_u001`** — the legacy update flow writes the GDT-pulled snapshot back into `OPRT_STTS_INFO`. Refactor reads only; no writes from this page.
- **Add Operation Status modal** (legacy `aosv_0001_002.*`) — out of refactor scope; manual operator-driven adds are deferred.
- **Per-row history / audit** — the legacy `EXT_TRAN_CALL_HIS` audit log is not exposed on this page in either system.
- **Bank-matching dashboard alerts** driven by suspended-counterparty flags — separate feature.

---

## Legacy reference (JEX)

| Asset | Path |
|---|---|
| JEX list service ID (read) | `aosv_0001_001_r001` |
| JEX page service ID (view) | `aosv_0001_001` |
| WSVC (read) | [`WECMS DEV/WECMS_ETC/xml/service/WSVC/WSVC.aosv_0001_001_r001.xml`](../../../../WECMS%20DEV/WECMS_ETC/xml/service/WSVC/WSVC.aosv_0001_001_r001.xml) |
| WSVC (page) | `WECMS DEV/WECMS_ETC/xml/service/WSVC/WSVC.aosv_0001_001.xml` |
| WSVC (update — out of scope) | `WECMS DEV/WECMS_ETC/xml/service/WSVC/WSVC.aosv_0001_001_u001.xml` |
| WSVC (add-modal — out of scope) | `WECMS DEV/WECMS_ETC/xml/service/WSVC/WSVC.aosv_0001_002*.xml` |
| Action JSP (read) | [`WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/aosv/aosv_0001_001_r001_act.jsp`](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/aosv/aosv_0001_001_r001_act.jsp) |
| View JSP | `WECMS DEV/WECMS/web/view/jex/wabooks/aosv/aosv_0001_001_view.jsp` |
| TypeScript | `WECMS DEV/WECMS_STATIC/ts-src/js/jex/wabooks/aosv/aosv_0001_001.ts` |
| IDO (paged read) | [`WECMS DEV/WECMS_ETC/xml/service/IDO/IDO.OPRT_STTS_INFO_R001.xml`](../../../../WECMS%20DEV/WECMS_ETC/xml/service/IDO/IDO.OPRT_STTS_INFO_R001.xml) |
| IDO (count) | `WECMS DEV/WECMS_ETC/xml/service/IDO/IDO.OPRT_STTS_INFO_R001_CNT.xml` |
| IDO (write — out of scope) | `WECMS DEV/WECMS_ETC/xml/service/IDO/IDO.OPRT_STTS_INFO_C001.xml`, `_U001.xml`, `_D001.xml` |
| Legacy entity | `WECMS DEV/WECMS/src/wabooks/entity/OprtSttsInfo.java` |
| Legacy DAO | `WECMS DEV/WECMS/src/wabooks/dao/OprtSttsInfoDAO.java` |
| Live DEV URL | `https://dev.wecms.vn/aosv_0001_001.act` |

**Verified WSVC contract** (per `WSVC.aosv_0001_001_r001.xml` lines 31–51):

- **Input:** `PAGE_NO` (15), `PAGE_PER_CNT` (15), `SRCH_WD` (100), `COMPANYSTATUS` (255).
- **Output (top-level):** `TOT_CNT` (15) + `REC` (RECORD).
- **Output (`REC` row):** `TAXCODE` (100), `COMPANYNAME` (255), `COMPANYSTATUS` (255), `OFFICEADD` (255), `SEARCHDATETIME` (14), `REPRESENTATIVEFIRSTNAME` (255).

The legacy WSVC does NOT expose `BIZ_KEY`, `REG_*`, or `MOD_*` on the wire — they are owned by the action JSP / DAO layer. The refactor preserves the same wire-field set and adds the standard pagination metadata (`TOTAL_COUNT` / `TOTAL_PAGES` / `PAGE_NUMBER` / `PAGE_SIZE`) inside `REC`.

**Verified action-JSP behaviour** (per [`aosv_0001_001_r001_act.jsp:38-64, 96-122`](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/aosv/aosv_0001_001_r001_act.jsp)):

1. Always-applied: `WHERE biz_key = ?` (tenant scope from `SessionManager.getLoginUser`).
2. Optional `COMPANYSTATUS` filter — partial match: `companystatus ilike '%' || ? || '%'`. The action JSP **does not** call `wb_get_operat_stts(...)` for localisation (the line is present but commented out, with a flow.team note linking the change). The wire value is matched verbatim against the stored Vietnamese string.
3. Optional `SRCH_WD` filter — OR over two columns: `taxCode ilike '%' || ? || '%' OR companyname ilike '%' || ? || '%'`. The whitelist is **fixed in code** (NOT View-Setting-driven) — unlike Sales e-Invoices which uses `SearchHelper.getSearchColumnDynamic`.
4. Pagination via `new PageInfo(input)` when both `PAGE_NO` and `PAGE_PER_CNT` are present; unpaged otherwise.
5. Sort via `SortHelper.getSortDynamic("aosv_0001_001_r001")` — View-Setting-driven sort (consistent with the rest of the JEX system).
6. Validation: only `validatePageNo` + `validatePagePerCnt`. **No required date range, no required `BIZ_KEY` (it's session-derived), no required `COMPANYSTATUS`.**

---

## Table columns

Default columns + order (5 visible columns the static UI ships today; `FIELD_ID`s proposed for the View Setting seed):

| `FIELD_ID` | Column | Source field | Visible by default |
|---|---|---|---|
| `CHECKBOX` | row-select checkbox | client-side selection state | yes (sys-pinned, hidden in popover) |
| `COMPANY` | Company name + tax code (MST), stacked | `COMPANYNAME` (top line) + `TAXCODE` (sub-line, muted) | yes |
| `ADDRESS` | Registered office address | `OFFICEADD` | yes |
| `OPERATION_STATUS` | Operation status with tick glyph | `COMPANYSTATUS` (raw Vietnamese) → coerced to canonical key on the frontend via [`mapOperationCompanyRow`](../../../../wecms-web/lib/api/mappers.ts); displayed via i18n key `operation.status.<canonical>` | yes |
| `LAST_UPDATE` | Last poll timestamp | `SEARCHDATETIME` (`YYYYMMDDhhmmss`) → frontend formats per [`lib/datetime.ts`](../../../../wecms-web/lib/datetime.ts) | yes |
| `ETC` | (NOT seeded — page has no per-row action column) | — | n/a |

Special `FIELD_ID`s `CHECKBOX` / `ETC` are admin-controlled per [§13](../../../../wecms-web/CLAUDE.md) and locked server-side via [`ScreenOptionService.SPECIAL_FIELD_IDS`](../../../src/main/java/com/kosign/wecms/domain/setting/service/ScreenOptionService.java). This page renders `CHECKBOX` (selection is the entry point to a future bulk-inquire-from-GDT flow) but does **not** render `ETC`.

`REPRESENTATIVEFIRSTNAME` is exposed by the legacy WSVC and lives on the entity, but no current Figma frame surfaces it. It is **registered in the COLUMN_REGISTRY** but seeded with `USE_YN='N'` so a future product decision can opt it in without a schema change.

---

## Filter behaviour

| Filter | UI today | Legacy contract (`aosv_0001_001_r001`) | Refactor target |
|---|---|---|---|
| **Operation status** | [`<FilterDropdown>`](../../../../wecms-web/components/shared/FilterDropdown.tsx) trigger labelled "All Operation Status" — popover **NOT wired**. | Wire field `COMPANYSTATUS` (length 255). Partial match `ilike '%X%'` against the **raw stored** string. Empty / unset = no filter. | Server-side `companyStatus` query param. The dropdown commits the raw Vietnamese substring (e.g. `"ngừng"` to match every "đã / tạm ngừng …" variant) so the wire predicate stays partial-match (legacy parity). Display labels in the dropdown go through i18n; the wire payload is the raw token. |
| **Keyword search** | [`<SearchInput>`](../../../../wecms-web/components/shared/SearchInput.tsx) on the right side of the filter row — **client-side only**. Filters the loaded result set on `companyName + taxCode + address` (case-insensitive substring match in `<OperationStatusPage>` `useMemo`). | Wire field `SRCH_WD` (length 100). OR clause across two columns: `taxCode ilike '%X%' OR companyname ilike '%X%'`. The legacy whitelist is fixed in code — NOT View-Setting-driven (unlike Sales e-Invoices). | Server-side `q` query param (already accepted by [`OperationStatusController#list`](../../../src/main/java/com/kosign/wecms/domain/operation/controller/OperationStatusController.java) and applied in [`OperationStatusService.list`](../../../src/main/java/com/kosign/wecms/domain/operation/service/OperationStatusService.java) via `companyname containing` OR `taxcode containing`). Phase 2 keeps the legacy 2-column OR; address is intentionally NOT searchable on the wire (stays out of scope until PO confirms). The frontend's current 3-column client-side filter is **already a divergence** — collapse to 2 columns when the wire takes over, or document the address column as a refactor-only addition. |

### Always-applied baseline filters (legacy parity)

The legacy `aosv_0001_001_r001_act.jsp` adds exactly **one** baseline filter per request:

1. `WHERE biz_key = ?` — tenant scope from session.

Unlike Sales e-Invoices, there are **no** "hide drafts / hand-registered / empty-detail" baseline filters on this page — the table is GDT-sourced and every row is meant to be visible.

The Spring service already applies the tenant scope via `TenantContext.currentBizKey()` on every repository call. **No additional baseline filters need to land** — this is a parity-from-day-one feature.

---

## Pagination behaviour

**Today (static UI):**

- `<OperationStatusPage>` computes `pageCount = ceil(total / DEFAULT_PAGE_SIZE)` with `DEFAULT_PAGE_SIZE = 15` hardcoded. The shared [`<Pagination>`](../../../../wecms-web/components/shared/Pagination.tsx) renders the chrome but state is **read-only** — clicking page-size or arrows does not refetch / re-slice.
- `total` comes from a `totalCompanies` prop wired from `<OperationStatusClient>` (defaults to `0`).
- The page does not adopt `usePagination()` per [§4](../../../../wecms-web/CLAUDE.md) yet — same migration debt as Sales e-Invoices and most Sprint 1 pages.

**Refactor target (mirror Account Inquiry VND server-side pagination):**

- Server-side `page` (zero-based) + `size` query params on `/api/v1/addon/operation-status`.
- Default `size = 10` per the canonical `PAGE_SIZE_OPTIONS[0]`; options `10 / 20 / 50 / 100`; backend clamps to `MAX_PAGE_SIZE = 100`. Page-size override goes through `usePagination()` per [§4](../../../../wecms-web/CLAUDE.md).
- Backend switches the repository call from the current `findByBizKey...List` finders to a `Pageable`-aware variant; response carries `TOTAL_COUNT` / `TOTAL_PAGES` / `PAGE_NUMBER` / `PAGE_SIZE` inside `REC`.
- Legacy default order `SEARCHDATETIME DESC` is preserved as `Sort` default (legacy parity via the View-Setting-driven `SortHelper`).
- Page resets to 0 on every filter change (`q`, `companyStatus` — mirrors Account Inquiry's `resetPage()` call from `usePagination()`).
- Excel export bypasses pagination via `Pageable.unpaged()` so the spreadsheet contains every matching row (subject to the 50,000-row cap from [§13b](../../../CLAUDE.md)).

---

## Operation status definition

`COMPANYSTATUS` is **GDT-supplied free-form Vietnamese text**. It is not a controlled enum on the JEX side — the live `COMPANYSTATUS` value for any given counterparty is whatever string the GDT lookup endpoint returned on the last successful poll. The legacy code path commented out a `wb_get_operat_stts(value)` localisation helper (see [`aosv_0001_001_r001_act.jsp:48-49`](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/aosv/aosv_0001_001_r001_act.jsp)) — meaning the JEX system also matches the raw stored string, with no locale folding.

The legacy TypeScript ([`aosv_0001_001.ts`](../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/aosv/aosv_0001_001.ts)) carries a 12-line legend mapping observed Vietnamese strings to short EN labels. The refactor collapses that to a 3-state canonical enum used by the frontend for badge styling + i18n:

| Refactor canonical key | Sample raw `COMPANYSTATUS` (Vietnamese) | EN label key | Frontend behaviour |
|---|---|---|---|
| `active_certificate_issued` | `NNT đang hoạt động (đã được cấp GCN ĐKT)` | `operation.status.active_certificate_issued` → "Active – Certificate Issued" | Default green tick |
| `suspended` | `NNT tạm ngừng hoạt động kinh doanh`, `NNT ngừng hoạt động nhưng chưa hoàn thành thủ tục chấm dứt hiệu lực MST` | `operation.status.suspended` → "Suspended" | Warning tint |
| `not_registered` | (anything not matching the two patterns above) | `operation.status.not_registered` → "Not Registered" | Neutral tint |

The mapping lives in [`mapOperationCompanyRow`](../../../../wecms-web/lib/api/mappers.ts) — substring match against the raw `COMPANY_STATUS` (`"đang hoạt động"` → active, `"ngừng"` / `"tạm ngừng"` → suspended, otherwise `not_registered`). The wire field `COMPANY_STATUS` stays raw; the canonical key is a frontend-derived display field (`STATUS_CD`) so future tightening / extra states can land without a wire migration. **The 3-state collapse is a refactor-only simplification** — the legacy 12-string legend is the source of truth when richer display becomes a product requirement.

**Localisation parity:** the refactor does NOT port `wb_get_operat_stts(value, LANG_CD)`. The DB function is not in local Flyway and is commented-out in legacy itself. The wire value is whatever the GDT poll wrote — typically Vietnamese — and the frontend handles regex-driven badge tinting. This is the same Phase 2 simplification chosen for Sales e-Invoices `EINV_INVOICESTATUS` (see [sales-e-invoices-plan.md IR-4](../../planning/addon/sales-e-invoices-plan.md)).

---

## Button behaviour

The toolbar is the page-specific [`<OperationStatusActionBar>`](../../../../wecms-web/components/addon/operation-status/OperationStatusActionBar.tsx) (Figma 12346:109158) — diverges from `<PageActionButtons>` because Figma adds a raised brand-coloured "Inquire Operation Status" CTA next to the standard list / print / Excel cluster. CLAUDE.md §6a notes that page-specific toolbars are allowed when an existing-page predates the rule and documents the divergence in the component header.

| Button | Today (static UI) | Refactor target |
|---|---|---|
| **Inquire Operation Status** (raised brand CTA) | `<Button variant="default">` with the Figma blue chip; **no handler wired**. | Triggers a GDT pull for the **selected rows** (single-tax-code form) when ≥ 1 checkbox is checked, OR a "lookup-by-MST" modal flow when no row is selected. Legacy maps to `nxisas_oprtstts_*` services through OSMC. **Refactor R&D scope: deferred** — local-only environment has no GDT bridge. Phase 1 ships an inert button with a tooltip explaining the deferral; Phase 2 (post-R&D, only if a GDT bridge lands) wires the actual pull. |
| **Update** (small chip) | `<Button variant="outline">` with `<RotateCw>` glyph; **no handler wired**. | Bulk re-pull for **every row currently visible after the filter** (legacy "Update" button → server-side iterates the visible rows and re-polls each MST). **Refactor R&D scope:** wires to `refreshKey++` → refetch `GET /api/v1/addon/operation-status` (mirrors Sales e-Invoices' Phase-1 Update behaviour). Real GDT pull lands when (and if) NXISAS is ported. |
| **Print** | Icon button `/assets/common/ico-print.svg` at `size-5`; **no handler wired**. | Route through the shared [`printReport`](../../../../wecms-web/lib/print.ts) helper per [§6d](../../../../wecms-web/CLAUDE.md). Print source = the page's filtered, in-memory dataset (search + status filter applied) BEFORE pagination. Columns mirror the on-screen table minus the checkbox; render the operation-status text plain (no badge). Default landscape. |
| **Excel** | Icon `/assets/common/ico-excel.svg` at `size-5`; **no handler wired**. | Route through the shared [`useExcelExport()`](../../../../wecms-web/hooks/useExcelExport.ts) hook per [§13b](../../../../wecms-web/CLAUDE.md). New per-resource endpoint `POST /api/v1/addon/operation-status/export` (TODO — write `OperationStatusExportProvider implements ExportRowProvider`). `screenId = "addon-operation-status"`. No chevron-dropdown variant — the Figma frame shows a plain icon. |

**Selection contract** — selecting a checkbox on a row enables the Inquire button (single or bulk). The selection set is local-only state (no wire propagation until Phase 2 GDT bridge). Selection clears on page-size change per the legacy "checkbox-all reset" pattern wired into `usePagination()`'s `onPageSizeChange`.

---

## Data flow (UI → JEX → DB → response)

**Legacy (current production):**

```
[Browser]
   │ jexAjax 'aosv_0001_001_r001' (.jct)
   ▼
[JEX filter chain] apiInit → apiAuthenticate → apiAction
   │
   ▼
[aosv_0001_001_r001_act.jsp]
   │ validateInput(PAGE_NO, PAGE_PER_CNT)
   │ getDynamic0(loginUser, input)  // BIZ_KEY + COMPANYSTATUS ilike + SRCH_WD OR clause
   │ SortHelper.getSortDynamic("aosv_0001_001_r001")  // View-Setting-driven sort
   │ pageInfo = new PageInfo(input)
   │
   ▼
[OprtSttsInfoDAO.getRows] + [getTotCnt: IDO.OPRT_STTS_INFO_R001_CNT]
   │
   ▼
[PostgreSQL: OPRT_STTS_INFO]
   │
   ▼
{ COMMON_HEAD, REC: { TOT_CNT, REC: [...] } }  // legacy envelope
```

**Refactor target:**

```
[Browser: <OperationStatusClient>]
   │ axios GET /api/v1/addon/operation-status?q=&companyStatus=&page=&size=&sortField=&sortDirection=
   │ (Authorization: Bearer <JWT>)
   ▼
[JwtAuthenticationFilter] → TenantContext.currentBizKey()
   │
   ▼
[OperationStatusController#list]
   │ delegates to service
   ▼
[OperationStatusService#list]
   │ resolveStatusCd(...) coercion (raw Vietnamese → canonical key — for response only, not the wire filter)
   │ Sort: OperationStatusSortField.parse() + DEFAULT_SORT (SEARCHDATETIME DESC)
   ▼
[OprtSttsInfoRepository] (Spring Data JPA, Pageable variant)
   │
   ▼
[PostgreSQL: OPRT_STTS_INFO @ localhost:5434/wecms_dev]
   │
   ▼
ApiResponse<OperationStatusListResponse>
   { COMMON_HEAD: { ERROR: false, CODE: WBI0000 },
     REC: { ROWS: [...], TOTAL_COUNT, TOTAL_PAGES, PAGE_NUMBER, PAGE_SIZE } }
   ▼
[mapOperationCompanyRow] → OperationCompanyRow (camelCase + canonical status enum)
   ▼
[<OperationStatusPage>] renders 5-column table
```

---

## Refactor considerations (Spring migration impact)

- **Wire-shape parity.** Preserve UPPER_SNAKE field IDs from `WSVC.aosv_0001_001_r001.xml` (`TAXCODE`, `COMPANYNAME`, `COMPANYSTATUS`, `OFFICEADD`, `SEARCHDATETIME`, `REPRESENTATIVEFIRSTNAME`) on the response. Today's response uses uppercase already — verified in [`OperationStatusRow`](../../../src/main/java/com/kosign/wecms/domain/operation/dto/) — **no migration debt** here, unlike Sales e-Invoices.
- **Filter-name divergence.** The wire filter today uses `q` (refactor convention) instead of legacy `SRCH_WD`. The legacy name is preserved on the *output* (`COMPANYSTATUS`) but not the *input* (`q` ≠ `SRCH_WD`). Either rename the input to `SRCH_WD` for parity OR document the divergence — Phase 2 picks one path; Sales e-Invoices' precedent is to keep the legacy WSVC field name on the input (`SRCH_WD`) so the JEX docs stay valid as functional reference.
- **Locked enum vs free text.** The refactor exposes a 3-state canonical enum (`STATUS_CD`) on the wire alongside the raw string. This is a refactor-only addition — legacy WSVC does not include it. Frontend prefers the canonical key for rendering; the raw string stays the source of truth for filter / sort.
- **Reading without `BIZ_KEY` on the wire.** Legacy reads `BIZ_KEY` from the JEX session; refactor reads from `TenantContext` (populated by `JwtAuthenticationFilter`). Endpoint accepts no `BIZ_KEY` query param — if a request is unauthenticated, the filter rejects with 401 before the controller is reached.
- **Date / timestamp shape.** `SEARCHDATETIME` is `VARCHAR(14)` in `YYYYMMDDhhmmss`. Refactor returns the raw string verbatim; the frontend parses + formats per [`lib/datetime.ts`](../../../../wecms-web/lib/datetime.ts). Do NOT switch to `TIMESTAMPTZ` / ISO-8601 on the wire — column is read column-for-column from DEV (Sprint 1 audit confirmed).
- **No new Flyway migration required for the read endpoint.** V16 already created `OPRT_STTS_INFO` and `EXT_TRAN_CALL_HIS` (audit log, deferred); V17 seeded 5 demo rows. Phase 2 only needs (a) a `SYS_SCR_OPTION_WECMS` seed for `WEB_SVC_ID = 'addon-operation-status'`, (b) optional schema parity audit if any DEV column was missed (run the audit query against DEV `wecmsvndev.OPRT_STTS_INFO` before Phase 4 ships).
- **No GDT integration.** The R&D scope explicitly excludes any external API call. The Inquire / Update buttons stay locally-bound until the broader NXISAS port lands.
- **Shared common-sort + Excel export adoption.** Per [§ Common table sort](../../../CLAUDE.md) and [§ Excel export](../../../CLAUDE.md), Phase 2 adds a per-domain sort whitelist (`OperationStatusSortField`) + an `OperationStatusExportProvider`. No changes to `common/web/` or `common/export/`.

---

## TODO — backend / API / DB / integration

### Backend (Spring Boot) — corrections + extensions

- [ ] Decide whether to **rename the keyword query param** from `q` to `SRCH_WD` for legacy WSVC parity. Sales e-Invoices' precedent is to keep the legacy WSVC field name. Affects [`OperationStatusController`](../../../src/main/java/com/kosign/wecms/domain/operation/controller/OperationStatusController.java) + [`fetchOperationStatus`](../../../../wecms-web/lib/api/addon.ts).
- [ ] Add server-side `companyStatus` query param (legacy `COMPANYSTATUS`). Backend predicate: `companystatus ilike '%' || ? || '%'` — partial match, mirrors legacy [`aosv_0001_001_r001_act.jsp:47-52`](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/aosv/aosv_0001_001_r001_act.jsp).
- [ ] Add server-side pagination (`page` + `size`) — switch the repository call from the current `findByBizKey...List` finders to a `Pageable` variant. Return `TOTAL_COUNT` / `TOTAL_PAGES` / `PAGE_NUMBER` / `PAGE_SIZE` in `OperationStatusListResponse`.
- [ ] Add server-side sort per [§ Common table sort](../../../CLAUDE.md) — write `OperationStatusSortField` whitelist enum mapping `COMPANY → companyname`, `OPERATION_STATUS → companystatus`, `LAST_UPDATE → searchdatetime`. Default sort stays `SEARCHDATETIME DESC` + a deterministic tiebreaker on `TAXCODE ASC`.
- [ ] Tighten the OR-clause in `OperationStatusService.list` to `(companyname ILIKE '%X%' OR taxcode ILIKE '%X%')` — the current `containingIgnoreCase` on `companyname` and plain `containing` on `taxcode` produce subtly different match semantics on Vietnamese accented input. Wrap the user input with the same escaping pattern Sales e-Invoices uses (`ESCAPE '\'`) to neutralise `%` / `_` / `\` injections.
- [ ] Decide whether to expose `REPRESENTATIVEFIRSTNAME` on the wire response. Legacy WSVC includes it; refactor `OperationStatusRow` currently does not surface it. If included, register the column in the View Setting seed with `USE_YN='N'` so future product decisions can opt it in.
- [ ] Write the per-resource Excel export endpoint `POST /api/v1/addon/operation-status/export` + `OperationStatusExportProvider implements ExportRowProvider` per [§ Excel export](../../../CLAUDE.md). Allowed FIELD_IDs match the table column set. Subheader: `<count> companies · As of <today>`.
- [ ] Decide whether the **Inquire / Update** button(s) point at a deferred-but-stubbed Spring endpoint (returning `WBE_NOT_IMPLEMENTED`) or stay as a frontend-only `refreshKey++`. Mirror the Sales e-Invoices Update decision.

### API contract

- [ ] Document the API contract at [`docs/refactor-wecms/backend/addon/operation-status-inquiry.md`](../../references/api-spec/addon/) (new file — TBD).
- [ ] Document the JEX → Spring service mapping at [`docs/refactor-wecms/migration/operation-status-inquiry.md`](../../references/migration/) (new file — TBD; covers `aosv_0001_001_r001` IDO → JPA repository, field-by-field).

### Frontend (Next.js)

- [ ] Wire the operation-status dropdown to a real popover (likely the shared `<FilterPopover>`) that emits `companyStatus` to the wrapper. Today only the trigger renders.
- [ ] Adopt `usePagination()` per [§4](../../../../wecms-web/CLAUDE.md) on `<OperationStatusClient>` and remove the hardcoded `DEFAULT_PAGE_SIZE = 15`. Switch to server-side `page` + `size`.
- [ ] Adopt `useTableSort()` per [§13a](../../../../wecms-web/CLAUDE.md); pass `sortField` / `sortDirection` / `toggleSort` to the table; migrate the `<TableHeaderCell>` calls to `sortFieldId="COMPANY"` / `"OPERATION_STATUS"` / `"LAST_UPDATE"`.
- [ ] Adopt View Setting per [§13](../../../../wecms-web/CLAUDE.md) — declare a `COLUMN_REGISTRY` keyed by `FIELD_ID`, register `SCREEN_IDS.ADDON_OPERATION_STATUS = "addon-operation-status"` in [`lib/viewSettingScreens.ts`](../../../../wecms-web/lib/viewSettingScreens.ts), wire `useViewSetting(SCREEN_IDS.ADDON_OPERATION_STATUS, DEFAULT_AREA_ID)`. **Do NOT render `<ViewSettingPopover>`** unless the Figma frame adds a list-view icon — current toolbar (Inquire + Update + Print + Excel) does not include one.
- [ ] Wire the **Print** handler to the shared `printReport(...)` helper.
- [ ] Wire the **Excel** handler to the shared `useExcelExport()` hook (depends on the backend endpoint above).
- [ ] Wire the **Update** handler to a `refreshKey++` refetch (Phase 1) and later to the GDT-pull endpoint when it lands (deferred).
- [ ] Wire the **Inquire Operation Status** CTA — selection-aware (single / bulk) when GDT bridge lands; inert with explanatory tooltip until then.
- [ ] Add KM (Khmer) translations to `locales/km.json` once the locale ships product-wide.
- [ ] Decide whether the keyword-search column set on the frontend mirrors the wire (2 columns: name + tax code) or keeps its current 3-column local filter (adds address). Document the divergence either way.

### DB / Flyway

- [ ] Seed `SYS_SCR_OPTION_WECMS` with the column set for `WEB_SVC_ID = 'addon-operation-status'`, `DYM_AREA_ID = 'default'`. Columns: `CHECKBOX` (DISP_ORD=0, FIX_YN='Y', USE_YN='Y'), `COMPANY`, `ADDRESS`, `OPERATION_STATUS`, `LAST_UPDATE`, optional `REPRESENTATIVEFIRSTNAME` (USE_YN='N'). Use Figma WB365 column widths in `SIZE`. Idempotent via `INSERT … ON CONFLICT DO NOTHING`. Migration: `V{N}__addon_operation_status_screen_options.sql` (number TBD — next free slot at write time).
- [ ] Confirm `OPRT_STTS_INFO` is in the [PROD Active Table List](../../../CLAUDE.md) — **confirmed** (line 95 of the active list).
- [ ] Run a column-for-column DEV parity audit on `OPRT_STTS_INFO` before Phase 4 closes. V16 created the schema with the columns Sprint 1 needed; legacy DEV may carry additional columns the audit should reconcile (mirrors the V23 corrective-migration pattern from Sales e-Invoices).
- [ ] No new tables required. `EXT_TRAN_CALL_HIS` (Sprint 1 audit-log) is created in V16 but not yet read by any feature — deferred until the GDT-pull bridge lands.

### Integration

- [ ] **GDT live status — DEFERRED.** The legacy `aosv_0001_001` "Inquire" / "Update" path calls `nxisas_oprtstts_*` through the OSMC bridge to poll the GDT lookup endpoint. The refactor R&D scope is **local-only** per [project_refactoring_scope.md](../../planning/) memory — no NXISAS, no GDT, no external API. The UI must render whatever `OPRT_STTS_INFO.COMPANYSTATUS` carries today (snapshot from the last DEV NXISAS poll). Do **NOT** introduce a refactor-side GDT bridge for this listing page.
- [ ] **Audit logging.** Legacy "Inquire" actions emit a row to `EXT_TRAN_CALL_HIS` (created in V16). Refactor: when (and if) the Inquire CTA lands, it MUST write an audit-log row to mirror legacy behaviour. Defer until the integration lands.

### Logging and audit

- [ ] Read endpoint logging today is the default Spring Boot request log + `AppLogManager`-equivalent absent in the R&D codebase. No per-row read audit is required (legacy doesn't write one for `_r001`).
- [ ] When the Inquire / Update CTA lands, emit one `EXT_TRAN_CALL_HIS` row per polled `TAXCODE` with `WEB_SVC_ID = 'addon-operation-status'`, `BIZ_KEY` from session, `RSLT_CD` (success / failure), `START_DATE` / `END_DATE` (poll window), and any `ERR_CONT` from the GDT response. Mirrors the legacy NXISAS audit-trail contract.

---

## Cross-references

- API contract (endpoints/DTOs): [`../../api-spec/addon/operation-status-inquiry-api.md`](../../references/api-spec/addon/operation-status-inquiry-api.md)
- DB schema (tables/columns): [`../../database/addon/operation-status-inquiry-db.md`](../../references/database/addon/operation-status-inquiry-db.md)
- Frontend page design (UI): [`../../../../wecms-web/docs/features/addon-service/operation-status-inquiry.md`](../../../../wecms-web/docs/features/addon-service/operation-status-inquiry.md)
- Sister features: [`sales-e-invoices.md`](sales-e-invoices.md), [`expenses-e-invoices.md`](expenses-e-invoices.md) — both consume `COMPANYSTATUS` from this same `OPRT_STTS_INFO` table on the BUYER cell
- JEX feature spec (functional source of truth): not yet written — see `WECMS DEV/WECMS_ETC/xml/service/WSVC/WSVC.aosv_0001_001_r001.xml` and `WECMS DEV/WECMS/web/view/jex/wabooks/aosv/aosv_0001_001_view.jsp` directly
- Implementation plan: [`../../planning/addon/operation-status-inquiry-plan.md`](../../planning/addon/operation-status-inquiry-plan.md)
