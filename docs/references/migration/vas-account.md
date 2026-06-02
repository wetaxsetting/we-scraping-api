# VAS Account — Migration Mapping (JEX → Refactor)

Slug: `vas-account`. JEX module: `comm` (`comm_0217_001` family). PROD active list: `BUSI_VAS_ACCT_INFM` is in scope (`wecms-api/CLAUDE.md` §"PROD Active Table List").

---

## 1. Service-by-service mapping (backend)

| JEX WSVC | Action | Refactor controller method | Refactor URI | Refactor service method |
|---|---|---|---|---|
| `comm_0217_001_r001` | List VAS Accounts (`SRCH_WD` + `STTS` filter) | `VasAccountController.list` | `GET /api/v1/settings/vas-account` | `VasAccountService.list(searchWd, statuses, sortField, sortDirection)` |
| `comm_0217_001_d001` | Soft-delete one row | `VasAccountController.delete` | `DELETE /api/v1/settings/vas-account/{vasAcctSeqNo}` | `VasAccountService.softDelete(vasAcctSeqNo)` |
| `comm_0217_002_r001` | Excel preview / validate | `VasAccountController.excelPreview` | `POST /api/v1/settings/vas-account/excel/preview` (multipart `file`) | `VasAccountExcelService.preview(MultipartFile)` |
| `comm_0217_002_r002` | ~~Single-cell re-validate (inline edit in preview grid)~~ | **Not ported.** | — | **Retired** — the PO call 2026-05-11 dropped inline cell editing from the upload preview (the dialog now only renders parsed rows + per-cell error tooltips; the user fixes their source XLSX and re-uploads). No refactor twin. See audit F2. |
| `comm_0217_002_c001` | Excel save (append / replace) | `VasAccountController.excelImport` | `POST /api/v1/settings/vas-account/excel/import` | `VasAccountExcelService.import_(VasAccountExcelImportRequest)` |
| `comm_0217_003_c001` | Manual create | `VasAccountController.create` | `POST /api/v1/settings/vas-account` | `VasAccountService.create(VasAccountCreateRequest)` |
| `comm_0217_004_r001` | Detail (single record) | `VasAccountController.detail` | `GET /api/v1/settings/vas-account/{vasAcctSeqNo}` | `VasAccountService.detail(vasAcctSeqNo)` |
| `comm_0217_004_u001` | Manual update | `VasAccountController.update` | `PUT /api/v1/settings/vas-account/{vasAcctSeqNo}` | `VasAccountService.update(vasAcctSeqNo, VasAccountUpdateRequest)` |
| — (new) | Excel export | `VasAccountController.export` | `POST /api/v1/settings/vas-account/export` | `ExcelExportService.exportTo(...)` (shared) + `VasAccountExportProvider` (`screenId = "vas-account"`) |
| — (new) | View Setting | (no controller change — reuses `ScreenOptionController`) | `GET / PUT / DELETE /api/v1/screen-options/vas-account/default` + `GET /api/v1/screen-options/vas-account/areas` | unchanged |
| `BUSI_VAS_ACCT_INFM_R002` (legacy combo) | Picker DISP-concat query — out of scope this MR; deferred |  |  |  |

### Field-by-field mapping (list / detail)

| JEX wire (`UPPER_SNAKE`) | Refactor wire (`snake_case`) | Java field | DB column | Notes |
|---|---|---|---|---|
| `VAS_ACCT_SEQ_NO` | `vas_acct_seq_no` | `vasAcctSeqNo` (String on wire) | `vas_acct_seq_no NUMERIC(10,0)` | wire ships as string to dodge JS precision loss |
| `VAS_ACCT_CD` | `vas_acct_cd` | `vasAcctCd` | `vas_acct_cd VARCHAR(15)` | — |
| `VAS_ACCT_NM` | `vas_acct_nm` | `vasAcctNm` | `vas_acct_nm VARCHAR(50)` | — |
| `VAS_ACCT_ENM` | `vas_acct_enm` | `vasAcctEnm` | `vas_acct_enm VARCHAR(50)` | nullable |
| `VAS_ACCT_TYPE` | `vas_acct_type` + `vas_acct_type_key` | `vasAcctType` (String code) | `vas_acct_type CHAR(1)` | wire ships verbatim code + sibling i18n key (`settings.vas_account.type.{dual,credit,debit}`). Mirrors `acct_type_cd` / `acct_type_key` precedent. |
| `STTS` | `stts` + `stts_key` | `stts` (String code) | `stts CHAR(1)` | wire ships verbatim code + sibling i18n key (`settings.vas_account.status.{active,inactive}`). |
| `CONT` | `cont` | `cont` | `cont VARCHAR(200)` | DB wins — see feature spec DN-3 |
| `MOD_ID` | `mod_id` | `modId` | `mod_id VARCHAR(100)` | |
| `MOD_DTM` | `mod_dtm` | `modDtm` | `mod_dtm VARCHAR(14)` | legacy `yyyyMMddHHmmss` text |
| `REG_ID` | `reg_id` | `regId` | `reg_id VARCHAR(100)` | not exposed on list rows; available on detail |
| `REG_DTM` | `reg_dtm` | `regDtm` | `reg_dtm VARCHAR(14)` | same |
| `BIZ_KEY` | — (never on the wire) | `bizKey` | `biz_key VARCHAR(20)` | tenant-scoped; from JWT only |
| `DEL_YN` | — (never on the wire) | `delYn` | `del_yn CHAR(1) DEFAULT 'N'` | soft-delete flag, server-only |

### Filter / sort mapping (list)

| Legacy filter | Wire field | Backend handling |
|---|---|---|
| `SRCH_WD` | `search_wd` (query param) | ILIKE on `vas_acct_cd` OR `vas_acct_nm` |
| `STTS` | `stts` (query param; repeatable to multi-select) | `IN (...)`; absent → no status filter; legacy sentinel `STTS=N` ("no checkbox checked") returns the empty set — refactor returns the empty set explicitly when both checkboxes are unchecked, no magic `N` value |
| (legacy frontend-only sort) | `sort_field` + `sort_direction` (query params) | `VasAccountSortField` whitelist enum (BANK / NM / TYPE / STTS) — see backend doc |
| (new — refactor adds pagination) | `page` (zero-based, default `0`) + `size` (default `20`, clamped to `100`) (query params) | Mirrors ERP Transaction History. Server returns a `Page<…>` slice plus `total_count` / `page_number` / `page_size` / `total_pages` in the response envelope. Legacy frontend rendered everything in one DOM tree; refactor paginates server-side. |

### Error-code translation

| JEX error | Refactor error | Notes |
|---|---|---|
| `WBE0001 "Invalid {0}"` | reuse `WBE0001` (already in messages) | bean-validation failures emit `WBE_VALIDATION`; explicit business-invalid stays `WBE0001` |
| `WBE0003 "Duplicate {0}"` | reuse `WBE0003` | duplicate `vas_acct_cd` |
| `WBE0404 "{0} Not Found"` | reuse `WBE0404` | unknown `vas_acct_seq_no` (combined with wrong-tenant via `WBE2014` envelope) |
| `WSE0409` (multiple rows) | `WBE9999` | should never happen with PK lookup; refactor logs at `error` if it does |

---

## 2. Page / route mapping (frontend)

| JEX page | JEX URL | Refactor host (Next.js) | Refactor component |
|---|---|---|---|
| `comm_0217_001` — list tab inside Settings popup | `/comm_0217_001.act` (rendered inside `setting-pagepopup-ver2`) | **Settings modal panel** (no standalone route) — mounted inside `<SettingsModal>` via the side-nav | `<VasAccountPanel />` at `components/settings/vas-account/VasAccountPanel.tsx` |
| `comm_0217_002` — Excel Upload popup | `comm_0217_002.act` (layer popup) | shadcn `<Dialog>` opened from the panel (stack level 2) | `<ExcelUploadDialog />` at `components/settings/vas-account/ExcelUploadDialog.tsx` |
| `comm_0217_003` — Add VAS Account popup | `comm_0217_003.act` (layer popup) | shadcn `<Dialog>` (stack level 2) | `<AddVasAccountDialog />` |
| `comm_0217_004` — Edit VAS Account popup | `comm_0217_004.act` (layer popup) | shadcn `<Dialog>` (stack level 2) — same component as Add, conditional-mounted with `mode="edit"` + `key=row.id` | `<AddVasAccountDialog mode="edit" />` |

> **Settings shell decision (resolved 2026-05-11)** — VAS Account
> ships as a **Settings modal panel**, not a top-level Next.js route.
> The earlier `/settings/vas-account` plan was retired in favour of
> the modal-host shape so the feature lives alongside other Settings
> submenus (Bank Account, Approval Line, Chart of Accounts, Contacts,
> …) inside the shared `<SettingsModal>` shell — one common chrome,
> one navigation pattern. Backend / wire contract is unaffected; the
> View Setting registry entry (`SCREEN_ID_TO_ROUTE`) points at the
> modal's host route (`/banking/account-inquiry`) so deep links opening
> the popover still anchor on a valid page. See audit F4.

---

## 3. Data-model deltas

### Schema deltas (relative to DEV `wecmsvndev.busi_vas_acct_infm`)

| Operation | Column | Action | Reason |
|---|---|---|---|
| ADD column | (none) | Schema is taken column-for-column from DEV (Legacy Schema Parity Rule). | |
| RENAME column | (none) | Legacy names stay verbatim — `vas_acct_seq_no`, `vas_acct_cd`, etc. | |
| DROP column | (none) | DEV columns are all in PROD use; nothing dropped. | |
| ADD index | `IX_BUSI_VAS_ACCT_INFM_BIZ_CD_DEL` on `(biz_key, vas_acct_cd, del_yn)` | new performance index | Refactor list / create dedup check hits this index on every request; legacy DEV has only the PK. Allowed by `wecms-api/CLAUDE.md` §"Extra performance indexes are allowed". |
| ADD index | `IX_BUSI_VAS_ACCT_INFM_BIZ_DEL_STTS` on `(biz_key, del_yn, stts)` | new performance index | Speeds up the Active+Inactive list filter. |

DEV column count for `busi_vas_acct_infm`: **13**. Local Flyway migration must match.

### Flyway migration plan

| Migration | Purpose | Idempotent |
|---|---|---|
| `V{N}__vas_account_schema.sql` | `CREATE TABLE IF NOT EXISTS busi_vas_acct_infm (...)` column-for-column with DEV. PK `(biz_key, vas_acct_seq_no)`. Two new indexes (see above). | yes |
| `V{N+1}__vas_account_wb_get_next_val_branch.sql` | Add the `WHEN table = 'busi_vas_acct_infm' AND field = 'vas_acct_seq_no'` branch to local `wb_get_next_val(...)` — DEV has the branch already; the refactor preserves it as a temporary compatibility bridge during R&D **OR** the create path implements the generator in a Java service (preferred — per `wecms-api/CLAUDE.md` "DB Function / Legacy Function Policy"). **Decision:** Java-service generator. This migration is omitted; instead see the backend spec for `VasAcctSeqNoGenerator`. | n/a (not shipped) |
| `V{N+1}__vas_account_seed.sql` | Demo seed — top 30 rows for `biz_key = 'VN_VNM0004_000001'` ordered by `reg_dtm DESC`, idempotent via `INSERT … ON CONFLICT DO NOTHING`. Only `del_yn = 'N'` rows. | yes |
| `V{N+2}__vas_account_view_setting_seed.sql` | Seed `SYS_SCR_OPTION_WECMS` with the 5 columns (`VAS_ACCT_CD`, `VAS_ACCT_NM`, `VAS_ACCT_TYPE`, `STTS`, `ETC`) for `web_svc_id = 'vas-account'` + `dym_area_id = 'default'`. Idempotent. | yes |

Final numeric `N` assigned at MR time (next free Vxx slot — V75+ as of 2026-05-11).

### Data migration

Per the Data Migration Rule (max 30 rows / tenant, `BIZ_KEY = 'VN_VNM0004_000001'`):

- Migrate **only `del_yn = 'N'` rows** (live data) to keep the local DB lightweight.
- Top 30 rows by `reg_dtm DESC`.
- Preserve real DEV `vas_acct_seq_no` values in the seed (so existing local references — if any — line up). Seed migration sets `ON CONFLICT DO NOTHING`.
- Demo data only — never run against UAT / PROD. The legacy DBA script handles those environments.

---

## 4. Legacy artifacts retired / preserved

| Artifact | Status |
|---|---|
| `BusiVasAcctInfm` Java entity (legacy `wabooks.entity`) | preserved in JEX tree — read-only reference |
| `BusiVasAcctInfmDAO` (legacy) | preserved in JEX tree |
| `IDO.BUSI_VAS_ACCT_INFM_*.xml` (R001 / R002 / C001 / U001 / U002 / D001) | preserved in JEX tree |
| `IDO.BUSI_VAS_ACCT_INFM_D001` (physical delete) | **functionally retired** — refactor never hard-deletes |
| `comm_0217_001_view.jsp` / `_act.jsp` family | preserved in JEX tree |
| Legacy ML codes (`comm_0217_001_m001` … `_j005`) | retired in refactor — new keys live under `settings.vas_account.*` |
| Legacy `dictionary_*.properties` entries for VAS terms | preserved in JEX tree |
| Legacy `SeqHelper.getNextVal(...)` for `vas_acct_seq_no` | retired in refactor (Java-side generator) |

---

## 5. Migration risk / rollback

- **Reversibility:** every refactor migration is additive (`IF NOT EXISTS`, `ON CONFLICT DO NOTHING`). The local DB can be rolled back to pre-V{N} state by truncating only the seeded rows; no schema destruction is required.
- **Production blast radius:** zero. Refactor is local-only R&D — never deploys to DEV / UAT / PROD. The live system continues running the JEX `comm_0217_001` family unchanged.
- **Coexistence:** none required. Refactor and JEX do not share a runtime database during R&D.
