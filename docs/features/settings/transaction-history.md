# Settings > ERP Transaction History — Refactor Feature Spec

**Module:** Settings
**Menu:** ERP Transaction History
**Refactor screen ID / slug:** `erp-transaction-history`
**Frontend placement:** Settings modal panel (component folder will be renamed from `components/settings/transaction-history/` to `components/settings/erp-transaction-history/`).
**View Setting screenId (per [lib/viewSettingScreens.ts](../../../../wecms-web/lib/viewSettingScreens.ts)):** `erp-transaction-history` (`DYM_AREA_ID = "default"`).
**JEX counterparts:** legacy WSVCs `comm_0219_001` (View), `comm_0219_001_r001` (list), `comm_0219_001_c001` (upload), `comm_0219_001_r002` (download). Legacy IDOs: `ERP_TRNS_HSTR_R001`, `ERP_TRNS_HSTR_R001_CNT`, `ERP_TRNS_HSTR_C001`, `ERP_TRNS_HSTR_U001`, `ERP_TRNS_HSTR_D001`.
**Status:** **Phase 1 — design only.** No backend / frontend code yet.

> Legacy WSVC ids appear in this document only as JEX file:line citations and as migration-traceability prose. Every new identifier surface (REST URI, Java package, View Setting screenId, log tags, audit `WSVC_ID`) carries the kebab-case slug `erp-transaction-history`, never `comm_0219_001`. Cross-tree rule: workspace [CLAUDE.md §Cross-Tree Rules](../../../../CLAUDE.md); backend identifier rules: [wecms-api/CLAUDE.md §Refactor identifier naming](../../../CLAUDE.md); frontend rules: [wecms-web/CLAUDE.md §Routing + §13 View Setting](../../../../wecms-web/CLAUDE.md).

---

## 1. Overview

The ERP Transaction History screen is the audit ledger of every Excel / data file
exchanged between **WeCMS and the customer's ERP**. A row is created every time:

- a user uploads an ERP-shaped file into WeCMS for ingestion (e.g. ERP → WeCMS
  scheduled payments / transaction history / approval results) — the file is
  saved to NAS and a `ERP_TRNS_HSTR` row records the upload;
- WeCMS exports / generates an ERP-shaped file out of WeCMS (Account Inquiry
  export, Transaction History export, Exchange Rate batch, etc.) — a row is
  created to log the export;
- the ERP scheduled batch pulls master/detail data from the ERP staging tables
  (`TB_TRNS_MAST` / `TB_TRNS_DETAIL`) and the manual refresh helper
  (`ErpDataRefreshHelper`) writes a row even when zero rows match the criteria
  — the row is the audit handle for "the operator ran a refresh at this time".

The user-facing screen lets an operator browse the ledger, tab between
direction (WeCMS → ERP, ERP → WeCMS, Deleted), filter by date range +
transaction type + free-text keyword (matches `REG_ID`), paginate, and
download the file attached to a given row (when one exists physically on
the NAS).

JEX file citations:

- View: [comm_0219_001_view.jsp](../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/comm/comm_0219_001_view.jsp)
- List action: [comm_0219_001_r001_act.jsp](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0219_001_r001_act.jsp)
- Upload action: [comm_0219_001_c001_act.jsp](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0219_001_c001_act.jsp)
- Download view: [comm_0219_001_r002_view.jsp](../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/api/comm/comm_0219_001_r002_view.jsp)
- TypeScript: [comm_0219_001.ts](../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0219_001.ts)
- Entity: [ErpTrnsHstr.java](../../../../WECMS%20DEV/WECMS/src/wabooks/entity/ErpTrnsHstr.java)
- DAO: [ErpTrnsHstrDAO.java](../../../../WECMS%20DEV/WECMS/src/wabooks/dao/ErpTrnsHstrDAO.java)
- Enums: [ErpType.java](../../../../WECMS%20DEV/WECMS/src/wabooks/enums/ErpType.java) · [ErpTypeCd.java](../../../../WECMS%20DEV/WECMS/src/wabooks/enums/ErpTypeCd.java)
- Batch writers: [ERP_0100_001.java](../../../../WECMS%20DEV/WECMS/src/batch/jex/wabooks/erp/ERP_0100_001.java) (Exchange Rate) and siblings `ERP_0100_002 / 003 / 004`
- Helper: [ErpDataRefreshHelper.java](../../../../WECMS%20DEV/WECMS/src/wabooks/helper/ErpDataRefreshHelper.java)

---

## 2. Refactor scope

| Concern | Decision |
|---|---|
| Read (list + counts) | **In scope** — wired full-stack for Phase 2 / 3. |
| Download attached file | **In scope** — file streamed from a local NAS-equivalent path on developer laptop. |
| Upload (`comm_0219_001_c001`) | **Out of scope for Sprint 0/1.** Refactor R&D is local-only with no NAS / bank integration; uploads happen on the live JEX system. Frontend renders the upload chip as disabled. Open question OQ-1. |
| Batch writers (`ERP_0100_001..004`) + `ErpDataRefreshHelper` | **Out of scope.** Sprint 2+ when scheduled jobs land. The refactor seeds enough history rows to render the screen; new rows are not written by Spring code in Phase 2. |
| Audit trail through `AUDIT_LOGS_INFM` | Keep the WSVC-level audit envelope — refactor writes the audit row with `WSVC_ID = 'erp-transaction-history'` (slug, per the 2026-05-09 cutover) when the list endpoint fires. |
| Soft-delete (`ERP_TYPE = 'D'`) | **Read-only in scope.** The legacy delete flow is driven from other Payment screens (Scheduled Payment "Cancel" path sets the source row's `ERP_TYPE = 'D'`); the ERP Transaction History screen *displays* deleted rows under the Deleted tab but never mutates them itself. The refactor preserves the same read-only contract; `ERP_TRNS_HSTR_D001` / `_U001` IDOs are not re-implemented in this feature. |
| HAENGSUNG product-type variants | **Not preserved (OQ-2, 2026-05-11).** The refactor is product-agnostic — no `prdt_type` on the wire, no per-tab transaction-type filtering, no File-column hide. Deleted tab is always visible. The Deleted-tab transaction-type *subset* filter (`""`, `02`, `03`, `12`, `13`, `16`) is kept because it is universal in the legacy code (applies to all product types). |

---

## 3. Data model — JEX source (read-only reference)

### 3.1 `ERP_TRNS_HSTR` (JEX DEV schema — verified via postgres MCP, 2026-05-11)

| Column | Type | Null | Notes |
|---|---|---|---|
| `BIZ_KEY` | `VARCHAR(20)` | NOT NULL | PK part 1 — tenant identifier (refactor pins to `VN_VNM0004_000001`). |
| `SEQ_NO` | `NUMERIC(10,0)` | NOT NULL | PK part 2 — per-tenant monotonic sequence, generated by legacy `wb_get_next_val('erp_trns_hstr','seq_no', biz_key)`. |
| `ORG_FILE_NM` | `VARCHAR(500)` | NOT NULL | Original filename (uploaded or composed by batch — e.g. `Batch_WeCMS_to_ERP_Exchange_Rate_…_N_records.txt`). |
| `SAVE_FILE_NM` | `VARCHAR(500)` | NOT NULL | Filename on NAS (e.g. random uuid + extension); for batch-only rows this is a synthetic `Batch_WeCMS_ERP_<epoch>.txt` with no physical file behind it. |
| `FILE_SIZE` | `NUMERIC(10,0)` | NOT NULL | Bytes. `0` for batch / refresh rows (no physical file). |
| `ERP_TYPE` | `CHAR(1)` | NOT NULL | `W` = WeCMS → ERP, `E` = ERP → WeCMS, `D` = Deleted (soft-delete of an `E` row). |
| `TRNS_TYPE_CD` | `CHAR(2)` | NOT NULL | Transaction type — see §3.3 (17 codes). |
| `REG_ID` | `VARCHAR(100)` | NOT NULL | Login of the user that triggered the row (or `SYSTEM` for batch). |
| `REG_DTM` | `VARCHAR(14)` | NOT NULL | UTC timestamp as `yyyyMMddHHmmss` (legacy WeCMS DateTime convention). |

PK: `(BIZ_KEY, SEQ_NO)` — verified.
No FK / CHECK / UNIQUE constraints. No triggers. No additional indexes (PK is the only index).

Audit/REG/MOD columns: ONLY `REG_ID` + `REG_DTM`. No `MOD_*`, no `DEL_YN`.

DEV row distribution for tenant `VN_VNM0004_000001` (postgres MCP, 2026-05-11):

| `ERP_TYPE` | Rows |
|---|---|
| `W` (WeCMS → ERP) | 533 |
| `E` (ERP → WeCMS) | 205 |
| `D` (Deleted) | 21 |
| **Total** | **759** |

### 3.2 `ERP_TYPE` (one-character direction code)

| Code | Label (legacy `ErpType.getLabel()`) | i18n key (refactor) | Meaning |
|---|---|---|---|
| `W` | `WeCms -> ERP` | `settings.erp_transaction_history.direction.wecms_to_erp` | WeCMS exported / sent a file or dataset to ERP, OR an automated batch (Exchange Rate ERP_0100_001 etc.) wrote a synthetic history row. |
| `E` | `ERP ->WeCMS` | `settings.erp_transaction_history.direction.erp_to_wecms` | A user uploaded an ERP-produced file into WeCMS for ingestion. |
| `D` | `Deleted` | `settings.erp_transaction_history.direction.deleted` | Soft-deleted `E` row (audit retained). |

Legacy enum: [`ErpType.java`](../../../../WECMS%20DEV/WECMS/src/wabooks/enums/ErpType.java).

### 3.3 `TRNS_TYPE_CD` (transaction type — 17 codes)

Source: [`ErpTypeCd.java`](../../../../WECMS%20DEV/WECMS/src/wabooks/enums/ErpTypeCd.java) (`getLabel()`) and the view JSP [comm_0219_001_view.jsp:217-316](../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/comm/comm_0219_001_view.jsp).

| Code | Label (EN) | Refactor i18n key |
|---|---|---|
| `01` | Transaction History (VND) | `settings.erp_transaction_history.type.01` |
| `02` | Vendor Scheduled Payment | `settings.erp_transaction_history.type.02` |
| `03` | Expense Scheduled Payment | `settings.erp_transaction_history.type.03` |
| `04` | Vendor Payment Result | `settings.erp_transaction_history.type.04` |
| `05` | Expense Payment Result | `settings.erp_transaction_history.type.05` |
| `06` | Payroll Payment Result | `settings.erp_transaction_history.type.06` |
| `07` | Corporate Card Transactions | `settings.erp_transaction_history.type.07` |
| `08` | Transaction History (FCY) | `settings.erp_transaction_history.type.08` |
| `09` | Account Inquiry (VND) | `settings.erp_transaction_history.type.09` |
| `10` | Account Inquiry (FCY) | `settings.erp_transaction_history.type.10` |
| `11` | Approval Result | `settings.erp_transaction_history.type.11` |
| `12` | Payroll Scheduled Payment | `settings.erp_transaction_history.type.12` |
| `13` | Overseas Payment Request | `settings.erp_transaction_history.type.13` |
| `14` | Overseas Request Result | `settings.erp_transaction_history.type.14` |
| `15` | Exchange Rate | `settings.erp_transaction_history.type.15` |
| `16` | Internal Scheduled Transfer | `settings.erp_transaction_history.type.16` |
| `17` | Internal Transfer Result | `settings.erp_transaction_history.type.17` |

Legacy note (not preserved in refactor): in JEX, `15` (Exchange Rate) appears
in the dropdown only when `loginUser.prdtType.value === "H"`
([comm_0219_001_view.jsp:307-314](../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/comm/comm_0219_001_view.jsp)).
**The refactor shows every transaction type regardless of tenant (OQ-2)** —
the entire prdtType branching is removed.

### 3.4 Tab → transaction-type filter (refactor — product-agnostic)

[comm_0219_001.ts:476-553](../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0219_001.ts)
contains a HAENGSUNG-only per-tab type filter. **The refactor does not
mirror it (OQ-2)** — the dropdown shows every code on the `W` and `E`
tabs for every tenant.

The only universal restriction (present in the legacy code for all product
types and preserved in the refactor) is the **Deleted-tab subset**: when
`activeTab === 'D'`, the dropdown narrows to scheduled-payment types only —
`""` (All) + `02`, `03`, `12`, `13`, `16`. Verified in
[comm_0219_001.ts:482-510](../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0219_001.ts)
(the `if (activeTab === 'D') { ... return; }` branch that runs *before*
the product-type check).

### 3.5 File column visibility (refactor — always visible)

[comm_0219_001.ts:558-580](../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0219_001.ts):
the legacy code hides the File column header + cells when `prdtType === 'H'`
AND the active tab is `E`, and widens the Transaction Type column to 520 px.
**The refactor does not mirror this (OQ-2)** — the File column is always
visible across every tab and every tenant, with the standard 280 px width.
Per-row visibility still depends on `fileSize > 0` (rows representing batch
runs with no physical file display an empty File cell — identical to legacy).

---

## 4. JEX endpoint contract (functional spec — read-only)

### 4.1 `comm_0219_001_r001` (list + counts) — input

[WSVC.comm_0219_001_r001.xml](../../../../WECMS%20DEV/WECMS_ETC/xml/service/WSVC/WSVC.comm_0219_001_r001.xml)
+ [comm_0219_001_r001_act.jsp](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0219_001_r001_act.jsp).

| Field | Type / length | Required | Source / behaviour |
|---|---|---|---|
| `FROM_DT` | `YYYYMMDD` (8) | **yes** | Inclusive lower bound on `SUBSTRING(REG_DTM FROM 1 FOR 8)`. Validated via `ValidationHelper.validateDate8`. |
| `TO_DT` | `YYYYMMDD` (8) | **yes** | Inclusive upper bound on `SUBSTRING(REG_DTM FROM 1 FOR 8)`. `ValidationHelper.validatePeriod(fromDt, toDt)` enforces `from ≤ to`. |
| `ERP_TYPE` | `W` / `E` / `D` or empty | no | Empty = All; restricts on `ERP_TYPE`. |
| `TRNS_TYPE_CD` | 2-char code or empty | no | Restricts on `TRNS_TYPE_CD`. |
| `SRCH_WD` | string ≤ 100 | no | `REG_ID ILIKE '%' || ? || '%'` — does NOT match `org_file_nm` / `save_file_nm` (verified at the IDO level — `getDynamic` only adds the `reg_id ilike` branch). |
| `PAGE_NO` | numeric string | no | Page index — 1-based. |
| `PAGE_PER_CNT` | numeric string | no | Page size. Frontend defaults to `15`; selectable in `15 / 20 / 50 / 100`. |

Tenant filter `biz_key = loginUser.bizKey` is appended unconditionally — no tenant input.

Sort: the action calls `SortHelper.getSortDynamic(util, "comm_0219_001_r001")`. JEX
client passes `paramSort` via `_v2common.app.sortData(...)` keyed off the
header `data-sort` attributes (`REG_DTM`, `ERP_TYPE`, `TRNS_TYPE_CD`, `FILE`, `REG_USER`).
Default ordering is the legacy `SortHelper` default for this WSVC — confirmed by reading
[comm_0219_001_r001_act.jsp:149](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0219_001_r001_act.jsp).

### 4.2 `comm_0219_001_r001` — output (`REC` sub-record + counts)

| Field | Origin |
|---|---|
| `REC[]` | Page slice of `ERP_TRNS_HSTR` joined with `WB_GET_USER_NM(REG_ID)` (the row's user display name). |
| `REC[].SEQ_NO` | `ERP_TRNS_HSTR.SEQ_NO` (numeric, sent as string). |
| `REC[].ORG_FILE_NM` | Original filename. |
| `REC[].SAVE_FILE_NM` | NAS filename. |
| `REC[].FILE_SIZE` | Bytes. |
| `REC[].ERP_TYPE` | `W` / `E` / `D`. |
| `REC[].TRNS_TYPE_CD` | 2-char code. |
| `REC[].REG_DTM` | normalised by `DateTimeUtils.normalizeResult(reg_dtm, loginUser)` — returns the same `yyyyMMddHHmmss` string adjusted for the user's locale display (`.normalizeResult` essentially passes through for this format, but stays in the chain for completeness). |
| `REC[].REG_ID` | login id. |
| `REC[].USER_NM` | resolved via `wecmsvndev.wb_get_user_nm(REG_ID)` (legacy 1-arg overload). When the user is deactivated / deleted / `SYSTEM`, the function returns `NULL` and the JS fallback uses `value.REG_ID`. |
| `REC[].FILE_URL` | server-composed string `comm_0219_001_r002.act?SEQ_NO={SEQ_NO}` — pure client-side anchor href. |
| `TOT_CNT` | total rows matching everything (including `ERP_TYPE`). |
| `TOT_CNT_WECMS` | count where `ERP_TYPE='W'` (same WHERE as `TOT_CNT` but with `ERP_TYPE` dropped). |
| `TOT_CNT_ERP` | count where `ERP_TYPE='E'`. |
| `TOT_CNT_DELETED` | count where `ERP_TYPE='D'`. |

The count IDO is [`ERP_TRNS_HSTR_R001_CNT`](../../../../WECMS%20DEV/WECMS_ETC/xml/service/IDO/IDO.ERP_TRNS_HSTR_R001_CNT.xml) — a separate aggregate query that intentionally omits `ERP_TYPE` from the dynamic WHERE (the JSP passes `getDynamicCNT` which drops `erp_type` from the user filter but keeps date range + `TRNS_TYPE_CD` + `SRCH_WD` + `BIZ_KEY`) so all four tab counts respond to the same filter context simultaneously.

### 4.3 `comm_0219_001_c001` (upload — OUT OF SCOPE for Phase 2)

[WSVC.comm_0219_001_c001.xml](../../../../WECMS%20DEV/WECMS_ETC/xml/service/WSVC/WSVC.comm_0219_001_c001.xml)
+ [comm_0219_001_c001_act.jsp](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0219_001_c001_act.jsp).

Input: `FILE_DATA` (FILE, required). Output: empty.

Behaviour: streams every uploaded file (`util.getNextFile(uploadedFile)` walks
the multipart parts), builds a `FileInfo` per file under
`comm_0219_001_c001`, saves to NAS, and inserts one `ERP_TRNS_HSTR` row per
file via `ErpTrnsHstrDAO.create(...)` with `setRegUser(loginUser)`.
`ERP_TYPE` / `TRNS_TYPE_CD` are NOT supplied — DAO `create` requires them via
`validateEntity` → `entity.validate()`. **The JEX action as written is
broken / unused** — production traffic does not exercise the upload path (no
upload UI exists for this screen — the JSP renders no `<input type="file">`).
The action exists as a generic upload endpoint to be exercised by other
Payment / Internal Transfer upload screens that reuse the same `comm_0219_001_c001`
NAS subfolder for file storage.

Refactor implication: do NOT port this endpoint. Upload responsibility belongs
to the Payment / Transfer / E-Invoice / Account Inquiry / etc. upload features
themselves — each one writes its own `ERP_TRNS_HSTR` row from its own service
once those features land. OQ-1.

### 4.4 `comm_0219_001_r002` (file download)

[WSVC.comm_0219_001_r002.xml](../../../../WECMS%20DEV/WECMS_ETC/xml/service/WSVC/WSVC.comm_0219_001_r002.xml)
+ [comm_0219_001_r002_view.jsp](../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/api/comm/comm_0219_001_r002_view.jsp).

Input: `SEQ_NO` (numeric, required).

Behaviour:
1. Load row by `(loginUser.bizKey, SEQ_NO)` — `ValidationHelper.validateErpTrnsHstr` throws if the row does not belong to the tenant.
2. Compose physical path: `<NAS_ROOT>/<biz_key>/comm_0219_001_c001/<save_file_nm>`.
3. If `?cmd=getthumb` → `FileUtils.downloadThumbFileAsBinary(out, util, file, 640)` (image thumbnail at 640 px).
4. Otherwise → `FileUtils.downloadFileAsBinary(out, util, file, save_file_nm)` (full file).

Note: the legacy "Content-Disposition filename" is the **NAS-stored filename**
(`save_file_nm`), not the user's original filename — this is intentional for
the audit trail. The frontend `<a href download>` attribute pre-suggests the
display name, but the server-side header overrides it.

For rows whose `FILE_SIZE = 0` (batch / refresh rows), the file does not exist
on NAS and the download path fails — the JEX frontend already hides the link
in this case (`if (value.FILE_SIZE && parseInt(value.FILE_SIZE) > 0)`).

---

## 5. Validation rules

| Rule | Code (legacy) | Refactor mapping |
|---|---|---|
| `FROM_DT` required | `WbMessageCode.WBE0001` ("required field missing", arg `FROM_DT`) | `WBE_VALIDATION` (Bean Validation `@Pattern`/`@NotBlank` on the request DTO) |
| `TO_DT` required | `WbMessageCode.WBE0001` arg `TO_DT` | `WBE_VALIDATION` |
| `FROM_DT` / `TO_DT` 8-digit calendar date | `ValidationHelper.validateDate8` → throws `WSE0408` invalid format | `WBE_VALIDATION` |
| `from ≤ to` | `ValidationHelper.validatePeriod` → throws `WSE0402` invalid period | `WBE_VALIDATION` (custom `@AssertTrue` or service-side guard, mirrors `FirmBankingHistoryService` pattern) |
| `SEQ_NO` required (download) | `WbMessageCode.WBE0001` arg `SEQ_NO` | `WBE_VALIDATION` (path-variable Bean Validation) |
| Row tenant mismatch | `WbMessageCode.WSE0409` ("multiple/none found") via `ValidationHelper.validateErpTrnsHstr` | `WBE2014` — to be added to `messages.properties` ("ERP transaction history row not found for this tenant"). The refactor merges "not found" + "not your tenant" into one code intentionally — never leak the existence of another tenant's row. |
| File missing on NAS (FILE_SIZE>0 but file disappeared) | JEX falls through to `FileUtils` which throws | `WBE2015` — new code ("ERP transaction history file is unavailable"). |

Refactor never logs the upload validation (`comm_0219_001_c001` is out of scope).

---

## 6. Permissions & audit

- **Login required** (`<login>Y</login>` in WSVC). Every refactor endpoint sits
  under `/api/v1/*` and is therefore `authenticated()` by `SecurityConfig`.
- **WTA_USER_ALLOW_YN = N, NORM_USER_ONLY_YN = Y** — both WSVCs reject WTA
  users; only normal customer users can access. Refactor mirrors this when
  role gating lands (currently a single-user R&D environment — no role
  checking yet).
- **Audit row** — `<mntLogYn>Y</mntLogYn>` on all four WSVCs means the JEX
  filter chain inserts an `AUDIT_LOGS_INFM` row per request. Refactor: the
  list endpoint writes `WSVC_ID = 'erp-transaction-history'` (slug per the
  2026-05-09 cutover); the download endpoint writes
  `WSVC_ID = 'erp-transaction-history/download'` (slug + path segment).
- **No per-record role check.** Any normal customer user can list and download
  any row in the tenant. Refactor preserves this — no per-row ACL.

---

## 7. UI behaviour (legacy JEX reference)

Source: [comm_0219_001_view.jsp](../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/comm/comm_0219_001_view.jsp) + [comm_0219_001.ts](../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0219_001.ts).

- **Tabs** — All / WeCMS → ERP / ERP → WeCMS / Deleted (always visible —
  refactor is product-agnostic, OQ-2). Each tab shows a count chip
  (`TOT_CNT` / `TOT_CNT_WECMS` / `TOT_CNT_ERP` / `TOT_CNT_DELETED`).
- **Filter row** — Date range (Last 30 / 90 / 180 days presets + Enter
  Manually) → Transaction Type dropdown (all 17 codes on W / E tabs; the
  scheduled-payment subset on the Deleted tab — the only universal filter
  preserved from legacy) → keyword search input.
- **Table** — Date (two-line: date + time muted) · Type (WeCMS → ERP / ERP →
  WeCMS / Deleted) · Transaction Type (resolved label) · File (download link
  styled as a blue label, **per-cell empty when FILE_SIZE=0**; the column
  itself stays visible regardless of tab or tenant) · User.
- **Empty state** — single row `colspan=5 (or 4)` "No Data Found".
- **Pagination** — page-size dropdown `15 / 20 / 50 / 100`, default `15`,
  `tms.ui.drawTablePaing` numeric pager.
- **Sort** — header `data-sort` glyphs cycle ASC / DESC; `_v2common.app.sortData`
  passes the resolved param through `paramSort` to the next ajax call.

The refactor screen mirrors the same shape but switches to the global
`<SettingsModal>` panel composition + the shared `<TableHeaderCell>` /
`<ViewSettingPopover>` / `<Pagination>` primitives — see §"Frontend impact"
in [docs/refactor-wecms/frontend/settings/erp-transaction-history.md](../../../../wecms-web/docs/features/settings/transaction-history.md).

---

## 8. Files touched on the refactor side

### Backend (`wecms-api`)

- New package `com.kosign.wecms.domain.setting.erptransactionhistory.{controller,service,repository,domain,dto,mapper}`.
- Entity `ErpTrnsHstr` + composite id `ErpTrnsHstrId`.
- Repository `ErpTrnsHstrRepository` (Spring Data + one JPQL `search()`).
- Service `ErpTrnsHistoryService` — list + counts in one round trip; download as a streaming endpoint.
- Sort whitelist `ErpTrnsHistorySortField` (uppercase FIELD_IDs: `REG_DTM`, `ERP_TYPE`, `TRNS_TYPE_CD`, `FILE`, `REG_USER`).
- Controller `ErpTrnsHistoryController` — `GET /api/v1/settings/erp-transaction-history` (list + counts) and `GET /api/v1/settings/erp-transaction-history/{seqNo}/download` (binary stream).
- DTOs `ErpTrnsHistoryRow`, `ErpTrnsHistoryListResponse`, `ErpTrnsHistoryListRequest` (record + Jakarta Bean Validation).
- Flyway: **none required** — the `erp_trns_hstr` table already lives in the local R&D DB via the standard PROD-table-scope import once Sprint adoption fires (it is in the PROD Active Table List). A new Flyway migration ships only if the schema-parity audit catches a gap.
- ~~View Setting seed~~ — **dropped per OQ-FE-2 (2026-05-11).** Panel does not adopt View Setting; no `SYS_SCR_OPTION_WECMS` rows seeded for this slug.
- `messages.properties` — adds `WBE2014`, `WBE2015`, the new `settings.erp_transaction_history.*` localisation keys.
- Backend design doc: [docs/refactor-wecms/backend/settings/erp-transaction-history.md](../../references/business-rules/settings/erp-transaction-history.md).
- Service mapping: [docs/refactor-wecms/migration/erp-transaction-history-mapping.md](../../references/migration/erp-transaction-history-mapping.md).

### Frontend (`wecms-web`)

- **Rename** `components/settings/transaction-history/` → `components/settings/erp-transaction-history/` so the slug matches the JEX counterpart (per workspace [CLAUDE.md §"Cross-Tree Rules"](../../../../CLAUDE.md): kebab-case slug must match the JEX feature filename).
- New `components/settings/erp-transaction-history/ErpTransactionHistoryPanel.tsx` (rename of the existing `TransactionHistoryPanel.tsx`).
- Wire types `ApiErpTransactionHistoryRow`, `ApiErpTransactionHistoryListResponse` in `lib/types/api.ts` (snake_case wire shape).
- API client `lib/api/erpTransactionHistory.ts` (`fetchErpTransactionHistory`, `getErpTransactionHistoryDownloadUrl`).
- Mapper `mapErpTransactionHistoryRow` in `lib/api/mappers.ts`.
- ~~`lib/viewSettingScreens.ts` registry entry~~ — **dropped per OQ-FE-2 (2026-05-11).** Panel does not adopt View Setting.
- Locale files `locales/{en,ko,vi}.json` — `settings.erp_transaction_history.*` namespace.
- Frontend design doc: [docs/refactor-wecms/frontend/settings/erp-transaction-history.md](../../../../wecms-web/docs/features/settings/transaction-history.md).

### Cross-cutting

- Planning doc: [docs/refactor-wecms/planning/settings/erp-transaction-history-plan.md](../../planning/settings/erp-transaction-history-plan.md).
- Bug reports (if any during Phase 2 / 3): `docs/bug-reports/` with `System: Refactor`.

---

## 9. Risks & open questions

**OQ-1. Upload endpoint scope.** ✅ **Resolved 2026-05-11 (Sokny).** The
JEX `comm_0219_001_c001` action is **not ported**. The legacy JSP appears
broken / unused in production — it requires `ERP_TYPE` / `TRNS_TYPE_CD`
via `entity.validate()` but never sets them, which would throw at runtime.
Every Payment / Transfer / E-Invoice / Account-Inquiry upload feature that
lands later writes its own `ERP_TRNS_HSTR` row from its own service after a
successful file ingest. This feature's backend exposes no upload surface —
the frontend renders the upload chip as disabled (or omits it entirely if
the legacy view JSP never showed one — which it doesn't; the JSP has no
`<input type="file">`). When a future Payment feature lands, it adds an
`ErpTrnsHstrAuditWriter` (or equivalent service-side helper) and writes its
own row inside its own `@Transactional` scope. No shared upload controller.

**OQ-2. `prdtType` source for HAENGSUNG variants.** ✅ **Resolved
2026-05-11 (Sokny).** No auth wire change required. The refactor screen is
product-agnostic — no `prdt_type` on the wire, no `StoredUser.prdtType`,
no per-tab transaction-type filter for W/E, no File-column hide, no
Deleted-tab hide, no Exchange Rate (`15`) gate. The only legacy filter
preserved is the universal Deleted-tab subset (`""`, `02`, `03`, `12`,
`13`, `16`) which is independent of `prdtType` in the legacy code anyway.
Future features that genuinely need per-tenant behaviour will raise
`prdt_type` again — this feature does not.

**OQ-3. NAS root on the refactor side.** ✅ **Resolved 2026-05-11
(Sokny).** Path format: `<storage-root>/<biz_key>/erp-transaction-history/<save_file_nm>`.
Local-dev `storage-root` is **`C:/WECMS PROJECT/File Data`** (forward
slashes — Java `Paths.get()` accepts them on Windows without escaping the
space). Spring config in `application-local.yml`:

```yaml
wecms:
  erp-transaction-history:
    storage-root: "C:/WECMS PROJECT/File Data"
```

Override per developer via env var `WECMS_ERP_TRNS_HSTR_STORAGE_ROOT` when a
different layout is needed (no fail-fast — falls back to the YAML default).

Phase 2 delivery is *download-only* — Spring serves files copied verbatim
from a seeded fixture folder under
`C:/WECMS PROJECT/File Data/<biz_key>/erp-transaction-history/<save_file_nm>`.
The DB seed seeds rows with `FILE_SIZE > 0` only when a matching fixture
file is committed at that path; rows whose fixture is missing keep
`FILE_SIZE = 0` so the frontend hides the download link.

Production NAS layout (`<NAS_ROOT>/<biz_key>/comm_0219_001_c001/<save_file_nm>`)
stays a JEX-only concern — the refactor folder name is the slug
`erp-transaction-history`, never the legacy WSVC id.

**OQ-4. `wb_get_user_nm` parity.** The legacy IDO uses
`WB_GET_USER_NM(REG_ID)` (one-arg). Refactor: the equivalent join needs to
go through `USER_INFM.USER_NM WHERE USER_ID = REG_ID` (with a `BIZ_KEY`
constraint to avoid cross-tenant matches). Use the existing
`UserInfmRepository` + a service-level map cache keyed by `(BIZ_KEY, REG_ID)`
to avoid an N+1. `SYSTEM` / batch rows return `null` and the frontend falls
back to `REG_ID` — exact legacy behaviour.

**OQ-5. Sort key for the File column.** The legacy header `<th data-sort="FILE">`
exists but no JEX IDO column is named `FILE` — sorting by File is undefined
in JEX (likely sorts by raw column name which the IDO does not return, so it
falls back to the default order). Refactor: treat the FILE column as
`SORT_YN='N'` in the seed and remove the sort glyph in the popover — the
legacy ambiguity is not a contract worth preserving. Confirm in MR review.

**OQ-6. `TRNS_TYPE_CD` HAENGSUNG-only types.** ✅ **Resolved 2026-05-11
(Sokny).** All 17 transaction-type codes (including `15` Exchange Rate) are
visible to every tenant on the refactor screen — folded into OQ-2.
Backend continues to accept any 2-char code without filtering.

**OQ-7. Default sort.** ✅ **Resolved 2026-05-11 (Sokny).** Refactor
default sort is **`regDtm DESC, seqNo DESC`** — matches expected legacy
behaviour. Encoded in `ErpTrnsHistoryService.DEFAULT_SORT`. A future
inspection of `SortHelper.getSortDynamic(util, "comm_0219_001_r001")` may
adjust this if the legacy default diverges; if it does, ship the
correction inside a single backend MR with no contract change.

**OQ-8. Soft-delete write path.** The Deleted tab (`ERP_TYPE='D'`) is
populated by *other* features (Scheduled Payment "Cancel", etc.) writing
`ERP_TRNS_HSTR_U001` with `ERP_TYPE='D'`. The refactor ERP Transaction
History feature itself never writes — but Sprint 2+ Payment modules need to
write a soft-delete row. Track this in each Payment feature's spec, not here.

**OQ-9. PageInfo conversion.** Legacy `PageInfo` (1-based page numbers) →
Spring Data `Pageable` (0-based). The refactor mapper subtracts one from
the wire `page` (1 → 0) and adds it back on response (`pageNumber = pageable.getPageNumber() + 1`),
mirroring `FirmBankingHistoryService`.

---

## 10. Changelog

- 2026-05-11 — v0.1 — initial spec (Sokny, AI-assisted). Reverse-engineered
  from JEX source-of-truth files. Scope = read-only + download; upload and
  batch writers deferred. Open questions captured in §9.
