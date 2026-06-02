# Add-on Service > Operation Status Inquiry — Refactor Implementation Plan

**Feature spec:** [../../features/addon-service/operation-status-inquiry.md](../../features/addon-service/operation-status-inquiry.md)
**Module:** Add-on Service
**Route:** `/addon/operation-status`
**JEX counterpart:** `aosv_0001_001` (view) / `aosv_0001_001_r001` (read service) — `OPRT_STTS_INFO` table
**Owner:** TBD (Refactor team — Sonny + Arthur)
**Sprint:** TBD (post-Sprint 1 stabilisation; coordinate with Sales / Expenses e-Invoices since all three pages consume the same `OPRT_STTS_INFO` table for buyer-status display)

---

## Phase 1 — Current System Analysis

### 1.1 Identify JEX endpoints (JSP, IDO)

| Asset | Path | Notes |
|---|---|---|
| Read service | `WSVC.aosv_0001_001_r001.xml` | Input: `PAGE_NO`, `PAGE_PER_CNT`, `SRCH_WD`, `COMPANYSTATUS`. Output: `TOT_CNT` + `REC[]` (`TAXCODE`, `COMPANYNAME`, `COMPANYSTATUS`, `OFFICEADD`, `SEARCHDATETIME`, `REPRESENTATIVEFIRSTNAME`). |
| Read action JSP | [`aosv_0001_001_r001_act.jsp`](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/aosv/aosv_0001_001_r001_act.jsp) | Validates `PAGE_NO` / `PAGE_PER_CNT` only. Always-on `BIZ_KEY` filter. Optional `COMPANYSTATUS` ILIKE + `SRCH_WD` OR over (`taxCode`, `companyname`). Sort via `SortHelper.getSortDynamic("aosv_0001_001_r001")` (View-Setting-driven). Pagination via `PageInfo`. |
| Read IDO (paged) | [`IDO.OPRT_STTS_INFO_R001.xml`](../../../../WECMS%20DEV/WECMS_ETC/xml/service/IDO/IDO.OPRT_STTS_INFO_R001.xml) | `SELECT BIZ_KEY, TAXCODE, COMPANYNAME, COMPANYSTATUS, OFFICEADD, SEARCHDATETIME, REPRESENTATIVEFIRSTNAME, REG_ID, REG_DTM, MOD_ID, MOD_DTM FROM OPRT_STTS_INFO WHERE 1=1 ??`. Default IDO. |
| Read IDO (count) | `IDO.OPRT_STTS_INFO_R001_CNT.xml` | Pair-mate to `_R001` for `TOT_CNT`. |
| View service | `WSVC.aosv_0001_001.xml` (and `_view.jsp`) | The hosting page; `viewUseYn=Y`, `actUseYn=N`. |
| Update service (out of scope) | `WSVC.aosv_0001_001_u001.xml` + `_u001_act.jsp` | Used by the legacy "Update" / "Inquire" buttons to write the GDT-pulled snapshot back into `OPRT_STTS_INFO`. **Refactor does NOT port this** in Phase 2 — R&D scope is local-only. |
| Add-modal services (out of scope) | `WSVC.aosv_0001_002.*` (`_r001`, `_c001`) + view JSP | Manual-entry add-status modal. Out of refactor scope. |
| Write IDOs (out of scope) | `IDO.OPRT_STTS_INFO_C001.xml` / `_U001.xml` / `_D001.xml` | Default write IDOs. Not consumed in Phase 2. |
| Legacy entity / DAO | [`OprtSttsInfo.java`](../../../../WECMS%20DEV/WECMS/src/wabooks/entity/OprtSttsInfo.java) + `OprtSttsInfoDAO.java` | Composite PK `(BIZ_KEY, TAXCODE)`. |
| Legacy TS | [`aosv_0001_001.ts`](../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/aosv/aosv_0001_001.ts) | Loads via `aosv_0001_001_r001`, Excel export, status legend (12 Vietnamese strings → short EN labels), Update via `aosv_0001_001_u001`. |

### 1.2 Analyse business logic

- **Tenant isolation** via session-derived `BIZ_KEY`. No cross-tenant read.
- **No required date range** (unlike Sales e-Invoices). Only `PAGE_NO` / `PAGE_PER_CNT` are validated.
- **Status filter** is partial-match against the raw stored string. The localisation helper `wb_get_operat_stts(value)` is **commented out** in the action JSP (with a flow.team change note linked) — legacy itself does not localise the filter. Refactor follows the same pattern (no DB function port).
- **Search filter** is OR over `taxcode` and `companyname`. Whitelist is fixed in the action JSP code (NOT View-Setting-driven, unlike `einv_0001_001_r001`). Address is NOT searchable on the wire.
- **Sort** is View-Setting-driven via `SortHelper`. Default sort is owned by the View Setting seed; in practice rows ship `SEARCHDATETIME DESC`.
- **No "hide drafts / hand-registered / empty-detail" baseline filters** apply (the legacy table is GDT-sourced; every row is intended to be visible). This page is parity-from-day-one for baseline filters — only `BIZ_KEY` matters.

### 1.3 Identify DB tables used

| Table | Status (Sprint 1) | Notes |
|---|---|---|
| `OPRT_STTS_INFO` | ✅ created (V16), seeded with 5 demo rows (V17), in [PROD Active Table List](../../../CLAUDE.md). Composite PK `(BIZ_KEY, TAXCODE)`. | Read source for the listing. |
| `EXT_TRAN_CALL_HIS` | ✅ created (V16), **NOT seeded**, in PROD Active List. | GDT-poll audit log. Sprint 1 created the schema in anticipation of the GDT bridge port; nothing reads / writes it yet. |
| `SYS_SCR_OPTION_WECMS` / `USER_SCR_OPTION_WECMS` | ✅ created (V30) — base View Setting tables; **no seed yet** for `addon-operation-status`. | Per-screen seed lands in Phase 3. |
| Cross-feature reference | `OPRT_STTS_INFO` is also read (joined as `COMPANYSTATUS`) by Sales / Expenses e-Invoices (`addon-sales-einvoices` / `addon-expenses-einvoices`). | Schema changes here MUST be coordinated with those features. |

### 1.4 Capture request / response structure

**Wire shape (legacy `aosv_0001_001_r001`):**

```http
POST /aosv_0001_001_r001.jct
Content-Type: application/json

{
  "PAGE_NO": "1",
  "PAGE_PER_CNT": "10",
  "SRCH_WD": "0102345678",   // optional — partial match on taxCode / companyname
  "COMPANYSTATUS": "ngừng"   // optional — partial match on companystatus
}
→
{
  "COMMON_HEAD": { "ERROR": false, "CODE": "WBI0000", "MESSAGE": "Success" },
  "REC": {
    "TOT_CNT": "5",
    "REC": [
      { "TAXCODE": "0102345678", "COMPANYNAME": "CUSTOMER ABC LTD",
        "COMPANYSTATUS": "NNT đang hoạt động (đã được cấp GCN ĐKT)",
        "OFFICEADD": "...", "SEARCHDATETIME": "20251125094000",
        "REPRESENTATIVEFIRSTNAME": "..." }
    ]
  }
}
```

**Wire shape (current refactor `GET /api/v1/addon/operation-status`):**

```http
GET /api/v1/addon/operation-status?q=0102
Authorization: Bearer <JWT>
→
{
  "COMMON_HEAD": { "ERROR": false, "CODE": "WBI0000", "MESSAGE": "Success" },
  "REC": {
    "ROWS": [
      { "TAXCODE": "0102345678", "COMPANY_NM": "CUSTOMER ABC LTD",
        "COMPANY_STATUS": "NNT đang hoạt động (đã được cấp GCN ĐKT)",
        "STATUS_CD": "active_certificate_issued",
        "OFFICE_ADD": "...", "LAST_UPDATE": "20251125094000" }
    ],
    "TOTAL_COUNT": 5
  }
}
```

The refactor adds `STATUS_CD` (canonical 3-state coercion) on the wire and renames legacy `COMPANYNAME` → `COMPANY_NM`, `OFFICEADD` → `OFFICE_ADD`, `SEARCHDATETIME` → `LAST_UPDATE`. **Per the WSVC-parity rule used on Sales e-Invoices (IR-7), Phase 2 should re-evaluate whether to keep the legacy field names verbatim** (`COMPANYNAME`, `OFFICEADD`, `SEARCHDATETIME`) for traceability. Decide in Phase 2 task #1 below.

---

## Phase 2 — API Design (Spring Boot)

### 2.1 Define REST endpoints

| Method | Path | Purpose | JEX counterpart |
|---|---|---|---|
| `GET` | `/api/v1/addon/operation-status` | List operation statuses (paged + filtered + sorted) | `aosv_0001_001_r001` |
| `POST` | `/api/v1/addon/operation-status/inquire` | Bulk lookup-by-MST against the GDT — Phase 2 ships as a stub returning `WBE_NOT_IMPLEMENTED` (or equivalent); real wiring deferred until / unless the NXISAS bridge is ported. | `nxisas_oprtstts_*` (legacy NXISAS path; OUT of refactor R&D scope) |
| `PUT` | `/api/v1/addon/operation-status/{taxcode}` | Manual update of a single MST's status. **Out of Phase 2 scope** — refactor reads only. Listed here for completeness because the original task brief asks for it. If a future sprint genuinely needs manual edits, this is where the endpoint lives; Phase 2 ships nothing for it. | `aosv_0001_001_u001` |
| `POST` | `/api/v1/addon/operation-status/export` | Excel export of the filtered list (per [§13b](../../../CLAUDE.md)). | (no legacy equivalent — JEX exports inline via the TS path) |

The path-param shape uses `{taxcode}` deliberately — `OPRT_STTS_INFO`'s PK is `(BIZ_KEY, TAXCODE)` and `BIZ_KEY` is session-scoped, so `TAXCODE` is the right URL identifier. Do not introduce a synthetic `id` PK.

### 2.2 Define request / response DTO

**`GET` list response — target wire shape (Phase 2):**

```json
{
  "COMMON_HEAD": { "ERROR": false, "CODE": "WBI0000", "MESSAGE": "Success" },
  "REC": {
    "ROWS": [
      {
        "TAXCODE": "0102345678",
        "COMPANYNAME": "CUSTOMER ABC LTD",
        "COMPANYSTATUS": "NNT đang hoạt động (đã được cấp GCN ĐKT)",
        "STATUS_CD": "active_certificate_issued",
        "OFFICEADD": "33 Phạm Hùng, Mỹ Đình 2, Nam Từ Liêm, Hà Nội",
        "SEARCHDATETIME": "20251125094000",
        "REPRESENTATIVEFIRSTNAME": "Nguyễn Văn A"
      }
    ],
    "TOTAL_COUNT": 25,
    "TOTAL_PAGES": 3,
    "PAGE_NUMBER": 0,
    "PAGE_SIZE": 10
  }
}
```

**Query parameters:**

| Param | Type | Required | Notes |
|---|---|---|---|
| `SRCH_WD` (or `q` if the team chooses to keep the existing name) | `String` | No | Up to 100 chars (legacy WSVC length). Partial match on `companyname` OR `taxcode`. Empty / blank = no filter. |
| `COMPANYSTATUS` | `String` | No | Up to 255 chars. Partial match `ILIKE '%X%'` on the raw stored string. Empty / blank = no filter. |
| `page` | `Integer` (zero-based) | No, default `0` | |
| `size` | `Integer` | No, default `10` (canonical `PAGE_SIZE_OPTIONS[0]`) | Backend clamps to `MAX_PAGE_SIZE = 100`. |
| `sortField` | `String` | No | View Setting `FIELD_ID` (`COMPANY` / `OPERATION_STATUS` / `LAST_UPDATE`). Resolved through `OperationStatusSortField` whitelist; unknown values fall back silently to default ORDER BY. |
| `sortDirection` | `String` | No | `ASC` / `DESC`. Lenient parse — blank / unknown → null → default ORDER BY. |

**Inquire / export request shapes** are deferred to Phase 5 — they depend on whether the GDT bridge lands and on the View Setting / Excel export adoption schedule.

### 2.3 Define validation rules

- Path identifiers (`{taxcode}`) — only checked when the PUT/Inquire endpoint exists; bean-validate with `@Pattern("^\\d+$")` (Vietnamese MSTs are numeric).
- Query strings — `@Size(max = 100)` on `SRCH_WD`, `@Size(max = 255)` on `COMPANYSTATUS`. `@Min(0)` on `page`; `@Min(1) @Max(100)` on `size`.
- `sortField` / `sortDirection` — no `@Pattern` (per [§ Common table sort](../../../CLAUDE.md) — the enum-`parse(...)` step in the service is the validator; unknown wire values must NOT throw, they fall back to default ORDER BY).
- Tenant scope — enforced by `JwtAuthenticationFilter`; controller does not re-validate.
- Wildcard escaping — `%`, `_`, `\` in `SRCH_WD` / `COMPANYSTATUS` MUST be neutralised with `ESCAPE '\'` (mirrors the Sales e-Invoices Phase-2 simplification).

---

## Phase 3 — Database Mapping

### 3.1 Map existing tables

`OPRT_STTS_INFO` is already in local Flyway (V16) with the columns Sprint 1 needed. The table is in the [PROD Active Table List](../../../CLAUDE.md). Audit query against DEV before Phase 4 closes (mirror the V23 corrective-migration pattern from Sales e-Invoices) — if DEV carries any column not in V16, ship a `V{N}__addon_operation_status_schema_parity.sql` corrective migration with `ALTER TABLE ADD COLUMN IF NOT EXISTS`.

| Local column (V16) | Type | DEV equivalent | Notes |
|---|---|---|---|
| `BIZ_KEY` | VARCHAR(20) NOT NULL | (verified — same length per V24 BIZ_KEY rule) | PK part 1 |
| `TAXCODE` | VARCHAR(100) NOT NULL | | PK part 2 |
| `COMPANYNAME` | VARCHAR(100) | | Legacy WSVC declares 255; **possible parity gap** — confirm column length in audit. |
| `COMPANYSTATUS` | VARCHAR(255) | | Free-text from GDT |
| `OFFICEADD` | VARCHAR(255) | | |
| `SEARCHDATETIME` | VARCHAR(14) | | `YYYYMMDDhhmmss` |
| `REPRESENTATIVEFIRSTNAME` | VARCHAR(255) | | Not surfaced by the static UI today |
| `REG_ID`, `REG_DTM`, `MOD_ID`, `MOD_DTM` | (audit columns from `BaseEntity`) | | TIMESTAMPTZ on local vs VARCHAR(14) on DEV — known refactor-only deviation (BaseEntity convention). |

`EXT_TRAN_CALL_HIS` is created in V16 but not consumed yet. Its eventual readers are: (a) a future GDT-pull audit log; (b) a possible "history of inquiries" detail view. Do NOT seed it in Phase 3 — defer to whichever sprint actually writes to it.

### 3.2 Identify required fields

The Phase 2 listing endpoint requires every column already in V16 — no schema addition needed. The View Setting seed in Phase 3 only writes to `SYS_SCR_OPTION_WECMS`.

### 3.3 Add missing columns if needed

**No new columns required for Phase 2.** The `updated_at` audit timestamp (`MOD_DTM`) is already inherited from `BaseEntity` (TIMESTAMPTZ); legacy `SEARCHDATETIME` is the GDT-poll timestamp and is distinct from `MOD_DTM` (which records DB-row-level audit, not poll cadence). Do NOT collapse the two.

**Phase 3 deliverables:**

| # | Task | Migration |
|---|---|---|
| 1 | Seed `SYS_SCR_OPTION_WECMS` for `WEB_SVC_ID = 'addon-operation-status'`, `DYM_AREA_ID = 'default'`. Columns: `CHECKBOX` (DISP_ORD=0, FIX_YN='Y', USE_YN='Y'), `COMPANY` (DISP_ORD=1), `ADDRESS` (DISP_ORD=2), `OPERATION_STATUS` (DISP_ORD=3), `LAST_UPDATE` (DISP_ORD=4), `REPRESENTATIVEFIRSTNAME` (DISP_ORD=5, USE_YN='N'). Use Figma WB365 widths in `SIZE`. Idempotent via `INSERT … ON CONFLICT DO NOTHING`. | New: `V{N}__addon_operation_status_screen_options.sql` |
| 2 | Run a column-for-column DEV parity audit against `wecmsvndev.OPRT_STTS_INFO`. If DEV carries any column not in V16, ship the additive corrective migration alongside the seed migration. | Possibly: `V{N+1}__addon_operation_status_schema_parity.sql` (only if audit finds drift) |
| 3 | Confirm `OPRT_STTS_INFO` row coverage in V17 is sufficient for stakeholder demos. Today: 5 rows for tenant `VN_VNM0004_000001`. If sparse, add a `V{N+2}__addon_operation_status_demo_seed.sql` (max 30 rows per the Data Migration Rule, copying real DEV rows — never fabricated). | Possibly: `V{N+2}__addon_operation_status_demo_seed.sql` |

---

## Phase 4 — Service Layer Refactor

### 4.1 Move JEX logic → Spring service

The current `OperationStatusService` covers ~70% of the legacy `aosv_0001_001_r001_act.jsp` behaviour. Phase 4 closes the gap.

**Phase 4 deliverables (in roughly the order they unblock each other):**

| # | Task | Where |
|---|---|---|
| 1 | Decide whether to **rename** the keyword query param from `q` to `SRCH_WD` for legacy WSVC parity. Mirror the Sales e-Invoices precedent. | `OperationStatusController`, `lib/api/addon.ts` |
| 2 | Add server-side filter param `COMPANYSTATUS` (legacy partial match `companystatus ILIKE '%X%'`). Wire input goes into a single helper that builds the WHERE chain, shared by both the page query and the (later) export query. | `OperationStatusService`, `OprtSttsInfoRepository` |
| 3 | Tighten the OR-clause to `(companyname ILIKE '%X%' OR taxcode ILIKE '%X%')` with `ESCAPE '\'` for `%` / `_` / `\` in user input. The current `containingIgnoreCase` + plain `containing` split produces subtly different match semantics on Vietnamese accented input. | `OperationStatusService`, `OprtSttsInfoRepository` |
| 4 | Add server-side pagination — `page` + `size` query params, switch repository call to `Pageable`-aware variant, response carries `TOTAL_COUNT` / `TOTAL_PAGES` / `PAGE_NUMBER` / `PAGE_SIZE`. Default `size=10`, max `100`. | `OperationStatusController`, `OperationStatusService`, `OprtSttsInfoRepository` |
| 5 | Add server-side sort per [§ Common table sort](../../../CLAUDE.md) — write `OperationStatusSortField` whitelist enum (`COMPANY → companyname`, `OPERATION_STATUS → companystatus`, `LAST_UPDATE → searchdatetime`). Default sort `SEARCHDATETIME DESC` + secondary `TAXCODE ASC`. | New: `domain/operation/service/OperationStatusSortField.java` |
| 6 | Decide on the wire field-name parity (Phase 2 IR-equivalent) — keep refactor names (`COMPANY_NM` etc.) or revert to legacy WSVC names (`COMPANYNAME` etc.). Whichever choice lands, the frontend mapper [`mapOperationCompanyRow`](../../../../wecms-web/lib/api/mappers.ts) absorbs the translation. | `OperationStatusListResponse`, `OperationStatusRow`, `mapOperationCompanyRow` |
| 7 | Keep the canonical `STATUS_CD` enum (`active_certificate_issued / suspended / not_registered`) on the wire as a derived field. Computed in `OperationStatusService.resolveStatusCd(...)` (already implemented). Mirror in DTO via `@JsonProperty("STATUS_CD")`. | `OperationStatusService`, `OperationStatusRow` |
| 8 | Excel export endpoint — `POST /api/v1/addon/operation-status/export` + `OperationStatusExportProvider implements ExportRowProvider` per [§ Excel export](../../../CLAUDE.md). Allowed FIELD_IDs match the table column set; subheader = `<count> companies · As of <today>`. | New: `domain/operation/export/OperationStatusExportProvider.java` + `OperationStatusController#export` |
| 9 | (Deferred — only if PO commits) Inquire endpoint stub `POST /api/v1/addon/operation-status/inquire` returning `WBE_NOT_IMPLEMENTED` until the NXISAS bridge is ported. | `OperationStatusController#inquire` |

### 4.2 Define business rules

- **Tenant scope** is non-negotiable — every query passes through `TenantContext.currentBizKey()` (already enforced).
- **Filter precedence:** `BIZ_KEY` (always) → `COMPANYSTATUS` (if present) → `SRCH_WD` (if present). The three predicates are AND-combined; the SRCH_WD predicate is itself an OR over (taxcode, companyname).
- **No localisation** of `COMPANYSTATUS`. Legacy parity — `wb_get_operat_stts(value)` is commented out in legacy and not in local Flyway. Do NOT port it.
- **Status canonical key (`STATUS_CD`)** is a frontend-display aid — never a wire filter. Filtering goes through the raw Vietnamese substring (`COMPANYSTATUS`).
- **Default ORDER BY:** `SEARCHDATETIME DESC, TAXCODE ASC`. The secondary key on `TAXCODE` keeps pagination deterministic when two rows share the same poll timestamp.
- **Audit log writes** to `EXT_TRAN_CALL_HIS` are owned by the (deferred) Inquire endpoint. The list endpoint emits no per-row audit (legacy parity).

### 4.3 Handle bulk processing logic

- **Bulk Inquire** (legacy "Update" button — re-poll every visible row against GDT) is a deferred integration. Refactor R&D ships the button as a local `refreshKey++` refetch.
- **Bulk Excel export** rides through the shared [`ExcelExportService`](../../../src/main/java/com/kosign/wecms/common/export/ExcelExportService.java) with `Pageable.unpaged()` capped at 50,000 rows (`MAX_ROWS` constant). For tenant `VN_VNM0004_000001` this is well under the cap; the cap exists for cross-tenant consistency.
- **Selection-aware single-row Inquire** (checkbox a single row → click "Inquire Operation Status") — same deferred integration. UI ships the selection state in Phase 4 frontend wiring; the wire call is stubbed.

---

## Phase 5 — Integration

Phase 5 is the **frontend ↔ backend wire-up**. Status: ⏳ partially done (read endpoint already wired end-to-end; everything else pending).

**Phase 5 deliverables (in roughly the order they unblock each other):**

1. **Replace `jex.createAjaxUtil` calls with the shared REST AJAX path.** This is **already done** for the read path — `<OperationStatusClient>` calls `fetchOperationStatus(q?)` via Axios + `useApiData`. Subsequent Inquire / Update / Excel calls follow the same pattern (Axios + JWT auto-attach via the global interceptor). Do NOT introduce per-feature HTTP wrappers — go through `lib/api/addon.ts` and the shared `useExcelExport()` hook.
2. **Wire empty-row guard.** Today the table renders zero rows with no message when the API returns empty. Add an empty-state placeholder per the Account Inquiry / Approval Box pattern.
3. **Wire the keyword search.** Replace the in-memory `useMemo` filter with a debounced (300 ms) `q` (or `SRCH_WD`) query param. Reset to `page=0` on change. Decide whether the local 3-column filter (name + tax code + address) collapses to 2 columns (wire parity) or stays as a refactor-only superset.
4. **Wire the operation-status filter.** Build (or reuse) a shared `<FilterPopover>` triggered by the existing dropdown trigger. The option list comes from a small reference module (or, post-View-Setting, from the `OperationStatusFilterOptions` config). Selection emits the raw Vietnamese substring as the `COMPANYSTATUS` query param.
5. **Adopt `usePagination()` per [§4](../../../../wecms-web/CLAUDE.md).** Wrapper sends `page` / `size` to the backend; remove the hardcoded `DEFAULT_PAGE_SIZE = 15`. Filter `onChange` calls `resetPage()`.
6. **Adopt `useTableSort()` per [§13a](../../../../wecms-web/CLAUDE.md).** Wrapper passes `sortField` / `sortDirection` to the backend and `toggleSort` down to the table. Migrate headers to `<TableHeaderCell sortFieldId="COMPANY" …>`.
7. **Consume View Setting per [§13](../../../../wecms-web/CLAUDE.md).** Add a `COLUMN_REGISTRY` keyed by `FIELD_ID` next to `<OperationStatusPage>`; thread `useViewSetting(SCREEN_IDS.ADDON_OPERATION_STATUS, DEFAULT_AREA_ID)` through the wrapper to drive `<colgroup>` widths, column visibility/order, and the Excel-export column set. **Do NOT render `<ViewSettingPopover>` and do NOT add a list-view icon to `<OperationStatusActionBar>`** unless Figma adds one — current toolbar is Inquire + Update + Print + Excel only.
8. **Wire the Print button** to the shared `printReport(...)` helper.
9. **Wire the Excel button** to the shared `useExcelExport()` hook (depends on the backend endpoint).
10. **Wire the Update button** to a `refreshKey++` refetch (Phase 1) and later to the GDT-pull endpoint when it lands (deferred).
11. **Wire the Inquire Operation Status CTA** — selection-aware (single / bulk) when GDT bridge lands; inert with explanatory tooltip until then.
12. **Backward compatibility during migration:** the refactor R&D system **does not coexist** with the live JEX system per [project_refactoring_scope.md](../) memory. There is no "parallel run" requirement at this stage — the live JEX `aosv_0001_001.act` page remains the production path; the refactor page lives only at `localhost:3000`. Phase 7 below documents the (future) parallel-run plan if / when refactor goes beyond R&D.
13. **Test UI with new APIs** — run through the verification checklist in §13.6 before declaring Phase 5 done.

---

## Phase 6 — Testing

### 6.1 Unit tests (service layer)

- `OperationStatusServiceTest` — assert tenant scope on every repo call, assert the WHERE chain order (BIZ_KEY → COMPANYSTATUS → SRCH_WD), assert `resolveStatusCd` coerces every documented Vietnamese variant correctly, assert default sort + secondary key, assert pagination clamps to `[0, 100]`.
- `OperationStatusSortFieldTest` — assert every whitelisted constant maps to a real entity property (Hibernate metamodel check); assert lenient `parse(null/blank/unknown)` returns null.
- `OperationStatusExportProviderTest` — assert `screenId = "addon-operation-status"`, assert `allowedFieldIds()` set matches the seed migration, assert subheader composition.

### 6.2 Integration tests (API)

- `OperationStatusControllerIT` — JWT-protected endpoint test (`@SpringBootTest` + `MockMvc`):
  - `GET /api/v1/addon/operation-status` returns 401 without JWT.
  - With a valid JWT, returns `WBI0000` envelope and the seeded 5 rows for tenant `VN_VNM0004_000001`.
  - `?q=ABC` filters to rows whose `COMPANYNAME` or `TAXCODE` matches.
  - `?COMPANYSTATUS=ngừng` filters to rows whose status contains "ngừng".
  - `?page=0&size=2` returns 2 rows + `TOTAL_COUNT=5` + `TOTAL_PAGES=3`.
  - `?sortField=LAST_UPDATE&sortDirection=ASC` reverses the default order.
  - Cross-tenant isolation: a JWT issued for a non-`VN_VNM0004_000001` tenant returns zero rows even when bypassing the filter.

### 6.3 Regression testing (UI)

- Manual + AI-assisted test session (Mr. Youvita per the team roster (Needs Confirmation)):
  - Empty-state when filter excludes every row.
  - Loading state.
  - Pagination chrome interacts with server-side pagination correctly (page-size change resets to page 1; arrow nav fetches the right slice).
  - Sort glyph cycle on every sortable header.
  - Search debounce — typing a query does not fire 6 requests.
  - Status filter popover — selecting a value updates the wire query.
  - Excel export — clicking the icon downloads `Operation Status Inquiry_DDMMYYYY.xlsx` with the correct columns + subheader; pre-formatted amount fields (none on this page) round-trip correctly.
  - Print — popup window renders without sidebar / top nav / pagination chrome.
  - Inquire / Update buttons — Phase-1 inert state + tooltip; Phase-2 refresh behaviour.
  - Resize: 375 / 768 / 1024 / 1440 — table scrolls horizontally inside the shared scroll container; pagination row stays anchored.
  - Multi-language: switch EN / KO / VI; status badges + column headers + filter labels translate; the raw Vietnamese `COMPANYSTATUS` stays raw (legacy parity — do not translate the status string content).

---

## Phase 7 — Deployment Strategy

The refactor is **R&D-only — no production deployment is in scope** per [project_refactoring_scope.md](../) memory. The phase below is documented for completeness in case the refactor matures past R&D.

### 7.1 Parallel run (JEX + Spring)

If / when the refactor moves past R&D:

- **No coexistence in current scope.** The refactor app does not write to or read from the JEX live database; both systems would point at distinct deployments of the same schema if a parallel run ever happens.
- A parallel run for this page would mean: JEX `aosv_0001_001` continues to serve the operator UI; the refactor `/addon/operation-status` is exposed to a small operator subset behind a feature flag; both pages read from the same `OPRT_STTS_INFO` table; only the JEX page can write (Inquire / Update). Discrepancies are reconciled by the JEX page's writes flowing back into the schema both pages read from.
- Audit log (`EXT_TRAN_CALL_HIS`) writes — only the JEX side writes during a parallel run; refactor stays read-only.

### 7.2 Gradual switch

If / when a parallel run succeeds:

1. Switch operator group A to the refactor page; group B stays on JEX. Compare bug rates over one cycle.
2. Switch all operators to the refactor page; JEX stays as a fallback link.
3. Decommission the JEX route once the refactor has carried Update / Inquire functionality (i.e. the GDT bridge is ported).

### 7.3 Rollback plan

- The refactor page sits at a distinct URL (`/addon/operation-status` on the Next.js app vs `/aosv_0001_001.act` on the JEX app). Rollback = restore the sidebar entry to the JEX URL; no schema change to revert.
- Excel export endpoint rollback = remove the per-resource `@PostMapping`; the shared `ExcelExportService` is unaffected.
- View Setting seed rollback = `DELETE FROM SYS_SCR_OPTION_WECMS WHERE WEB_SVC_ID = 'addon-operation-status'` (still leaves `USER_SCR_OPTION_WECMS` rows; clean those too if rolling back fully).

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| **Missing JEX logic.** The refactor copies the visible WSVC contract but may miss session-side / SortHelper behaviour the legacy enforces silently. | Medium | Medium — display drift, sort-order mismatch | Read `aosv_0001_001_r001_act.jsp` end-to-end during Phase 4 task #5; assert default sort matches DEV's by spot-checking a known counterparty's position across 3 pages on both systems. |
| **Hidden business rules.** `wb_get_operat_stts(value)` is commented out in legacy with a flow.team change link. There may be a Phase 2 product decision pending (port the function, add server-side localisation, etc.) that would change wire semantics. | Low | Medium — filter behaviour drift if the function is later un-commented | Verify with PO before Phase 4 closes whether the 3-state canonical enum (`STATUS_CD`) covers all observed Vietnamese variants. If new variants surface, extend the regex in `mapOperationCompanyRow` + the badge colour map. |
| **Data inconsistency** between local seed (V17 — 5 rows) and the live DEV table (24K+ rows on the audit-log side, hundreds on `OPRT_STTS_INFO`). Stakeholder demos may show an empty status filter dropdown if the local seed has only one distinct `COMPANYSTATUS` value. | Medium | Low — perception risk only | Phase 3 task #3 — confirm seed coverage; if sparse, add a `V{N}__addon_operation_status_demo_seed.sql` (max 30 real DEV rows). |
| **Bulk Inquire API performance.** Legacy "Update" button re-polls every visible row against GDT — sequentially, no async. With 100 rows on a page the round-trip can take minutes. | High (when the GDT bridge lands) | Medium — UX freeze during pull | When the bridge lands (deferred), implement Inquire as an async job: enqueue the work, return a job-id, poll status. Mirror legacy's `EXT_TRAN_CALL_HIS` per-row audit row. |
| **`OPRT_STTS_INFO` parity gap with DEV.** V16 created the schema for Sprint 1 needs; DEV may carry columns the audit didn't catch. | Medium | Low — read endpoint still works; Phase 4 columns may be missing | Phase 3 task #2 — column-for-column DEV parity audit; ship corrective `ALTER TABLE ADD COLUMN IF NOT EXISTS` migration if drift is found. |
| **Buyer-status display drift across features.** Sales / Expenses e-Invoices read `COMPANYSTATUS` from this same table for the Buyer cell. A canonical-key change here could regress those pages. | Medium | High — buyer-status cell breaks on 3 features at once | Coordinate the canonical-key contract via the View Setting `STATUS_CD` (refactor-only field). Sales / Expenses e-Invoices Buyer cell should read `STATUS_CD` not the raw string for tinting. |
| **`STATUS_CD` 3-state collapse loses information.** Legacy TS legend has 12 distinct strings; refactor coerces to 3. Operators who relied on the richer legend may not notice the simplification until they hit a counterparty with an unfamiliar status. | Low | Low — verbatim raw string is still on the wire | Render the raw `COMPANYSTATUS` string in the cell tooltip (or as a sub-line) so the full information is one hover away. PO decides Phase 4. |
| **Audit log churn from Inquire calls.** When the GDT bridge eventually lands, every Inquire call writes one `EXT_TRAN_CALL_HIS` row per polled `TAXCODE`. A bulk Inquire of 100 rows = 100 audit rows. Legacy already does this — the table is sized for it. Just don't accidentally write extra rows from the read endpoint. | Low | Low — audit log clutter | Read endpoint is `@Transactional(readOnly = true)`; assert no `EXT_TRAN_CALL_HIS` repository injection. |
| **Wire-shape divergence between refactor and legacy WSVC.** Today's response uses `COMPANY_NM` / `OFFICE_ADD` / `LAST_UPDATE`; legacy WSVC uses `COMPANYNAME` / `OFFICEADD` / `SEARCHDATETIME`. The frontend mapper bridges, but reviewers tracing JEX docs will be confused. | High (today) | Low — translation already lives in mapper | Phase 4 task #6 — pick one wire shape and lock it in. Sales e-Invoices precedent is to revert to legacy WSVC names verbatim. |

---

## Open Questions

These are the decision-blocking questions for Phase 2. Each maps to a Phase deliverable above.

| # | Question | Owner | Phase to resolve |
|---|---|---|---|
| 1 | **Is there an external API for operation status?** Yes — GDT lookup endpoint via the legacy NXISAS bridge (`nxisas_oprtstts_*`). **Refactor R&D scope: deferred**, no integration in Phase 2. The Inquire / Update buttons stay locally-bound until / unless NXISAS is ported. Confirmed against the Sprint 1 audit memory. | (decided — see [project_refactoring_scope.md](../)) | (resolved — answer is "deferred") |
| 2 | **What triggers the status update?** Today: legacy operator clicks Inquire (single MST) or Update (re-poll every visible row). Both go through the `aosv_0001_001_u001` write service after the GDT response is parsed. Legacy also writes one `EXT_TRAN_CALL_HIS` row per attempted poll. **Refactor R&D: no triggers** — the page is read-only, the underlying DEV data is whatever the live JEX system last polled. | PO + Sonny | Phase 4 — confirms whether to ship a stub "Update" endpoint or leave the button as `refreshKey++` only. |
| 3 | **Is history tracking required?** Legacy persists every Inquire / Update call into `EXT_TRAN_CALL_HIS` (Sprint 1 created the schema in V16 but nothing reads / writes it yet). PO has not asked for a "history of inquiries" detail page on the refactor side; if they do, it lands as a separate feature spec. Confirmed against [project_refactoring_scope.md](../). | PO | Phase 5 — confirms whether to ship a placeholder detail route now or wait for explicit PO ask. |
| 4 | **Can bulk inquiry be async?** Yes — when the GDT bridge eventually lands. A 100-row bulk Inquire that polls sequentially blocks the UI for minutes; the right design is to enqueue a background job (Spring `@Async` or a job table polled by a scheduler), return a job-id immediately, and let the UI poll for completion. Out of refactor R&D scope. | PO + Arthur | When the NXISAS port lands (deferred). |
| 5 | **Wire field-name parity with legacy WSVC.** Keep refactor names (`COMPANY_NM` / `OFFICE_ADD` / `LAST_UPDATE` — current state) or revert to legacy WSVC names (`COMPANYNAME` / `OFFICEADD` / `SEARCHDATETIME` — Sales e-Invoices precedent)? | Sonny | Phase 4 task #6. |
| 6 | **Keyword query param name** — `q` (current) or `SRCH_WD` (legacy WSVC parity)? | Sonny | Phase 4 task #1. |
| 7 | **Frontend search columns.** Today the local filter searches name + tax code + address; legacy wire searches name + tax code only. Collapse to wire parity, OR keep address as a refactor-only addition (and document)? | PO + Arthur | Phase 5 task #3. |
| 8 | **`REPRESENTATIVEFIRSTNAME` exposure.** Legacy WSVC includes it; refactor doesn't surface it. Add to the wire response with `USE_YN='N'` in the View Setting seed (so future opt-in is seed-only)? | Arthur | Phase 4 task #6. |
| 9 | **PUT endpoint** for manual operator edits (`aosv_0001_001_u001` parity). **Out of refactor scope** per the read-only design choice; but the original task brief lists it. Confirm with PO that read-only is the correct Phase 2 scope. | PO | Phase 2 (already implicit — refactor R&D is local-only and read-only for this page). |

---

## Cross-references

- Feature spec: [../../features/addon-service/operation-status-inquiry.md](../../features/addon-service/operation-status-inquiry.md)
- Frontend page (today): [`wecms-web/components/addon/operation-status/`](../../../../wecms-web/components/addon/operation-status/)
- Backend (today): [`wecms-api/src/main/java/com/kosign/wecms/domain/operation/`](../../../src/main/java/com/kosign/wecms/domain/operation/)
- Sister planning docs: [`../addon/sales-e-invoices-plan.md`](sales-e-invoices-plan.md), [`../addon/expenses-e-invoices-plan.md`](expenses-e-invoices-plan.md) — share the same `OPRT_STTS_INFO` data source for the buyer-status cell
- Refactor frontend rules: [wecms-web/CLAUDE.md](../../../../wecms-web/CLAUDE.md) §4 · §6d · §7a · §7a.1 · §13 · §13a · §13b
- Refactor backend rules: [wecms-api/CLAUDE.md](../../../CLAUDE.md) — Excel export · View Setting · Common table sort · PROD Active Table List
- Legacy WSVC: [`WECMS DEV/WECMS_ETC/xml/service/WSVC/WSVC.aosv_0001_001_r001.xml`](../../../../WECMS%20DEV/WECMS_ETC/xml/service/WSVC/WSVC.aosv_0001_001_r001.xml)
- Legacy IDO: [`WECMS DEV/WECMS_ETC/xml/service/IDO/IDO.OPRT_STTS_INFO_R001.xml`](../../../../WECMS%20DEV/WECMS_ETC/xml/service/IDO/IDO.OPRT_STTS_INFO_R001.xml)
- Legacy action JSP: [`WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/aosv/aosv_0001_001_r001_act.jsp`](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/aosv/aosv_0001_001_r001_act.jsp)
