# Daily Financial (HS) — Legacy Audit & Parity Plan

**Legacy screen:** `rept_0017_001` ("자금일보" / Daily Financial, HAENGSUNG variant)
**Refactor route:** `/banking/reports/daily-financial-hs`
**Refactor permission cd:** `banking-reports-daily-financial-hs` (product type `H`)
**Audited:** 2026-07-01
**System:** Refactor (backend `wecms-api` + frontend `wecms-web`), against live JEX `WECMS DEV/`.

Related refactor docs: `docs/features/view-setting.md`, `docs/audits/daily-cash-daily-financial-excel-download-audit.md`, `docs/audits/main-financial-pages-sit-readiness-action-plan.md`. Frontend rules: `wecms-web/CLAUDE.md` §7b/§7c/§7e (report pages), §13 (View Setting).

> **Closed by [daily-financial-hs-final-audit.md](daily-financial-hs-final-audit.md) (2026-07-02)** — the final
> closure record: all parity gaps fixed (per-currency TOTAL placement, hidden-column table alignment,
> spacer-row removal) + PO-approved divergences (localized Excel titles EN/VI, KRW inclusion) + validation.

---

## 1. Legacy files & actions

| Concern | Legacy artifact (under `WECMS DEV/`) |
|---|---|
| View JSP | `WECMS/web/view/jex/wabooks/rept/rept_0017_001_view.jsp` |
| Client TS/JS | `WECMS_STATIC/ts-src/js/jex/wabooks/rept/rept_0017_001.ts` (+ compiled `.js`) |
| **Read action** | `.../action/.../rept_0017_001_r001_act.jsp` → IDO `DAILY_FINCL_REPT_R001` |
| **Update action** | `.../action/.../rept_0017_001_u001_act.jsp` → DAO `ReptTrnsHstrDAO.patch` → IDO `REPT_TRNS_HSTR_U000` |
| **Excel action** | `.../view/.../rept_0017_001_r002_excel_view.jsp` (op `rept_0017_001_r002_excel`) |
| WSVC | `WSVC.rept_0017_001[_r001|_u001|_r002_excel].xml` |
| IDO (read) | `IDO.DAILY_FINCL_REPT_R001.xml` — calls PG fn `FN_GET_HAENGSUNG_DAILY_FINANCIAL_REPORT(BIZ_KEY, PERIOD_DATE, ACCT_NO)` |
| IDO (patch) | `IDO.REPT_TRNS_HSTR_U000.xml` — `UPDATE REPT_TRNS_HSTR SET :FIELDS_TO_UPDATE WHERE BIZ_KEY=:BIZ_KEY AND ACCT_SEQ_NO=:ACCT_SEQ_NO` |
| Entity / DAO | `entity/ReptTrnsHstr.java` (+ `ReptTrnsHstrPk.java`), `dao/ReptTrnsHstrDAO.java` |
| Bank list | `cash_0001_001_r003` (active bank accounts) |
| Excel template | `/file/excel/VN/WeCMS_Daily_Financial_Report_{EN|KO}.xlsx` |

---

## 2. Legacy behavior (source of truth)

### 2.1 Page load
- Route `rept_0017_001.act` → default `PERIOD_DATE = today` (YYYYMMDD), bank list loaded via `cash_0001_001_r003` (all boxes checked), then `fn_getData()` runs the initial report.

### 2.2 Search / filter
- Search button → `rept_0017_001_r001` with `{ PERIOD_DATE (YYYYMMDD), ACCT_NO }`.
- `ACCT_NO` is `"ALL"` or a JSON array `[{BANK_CD, ACCT_NO}, …]` from the checked banks.
- Date change and checkbox change update state only; the user must click Search (auto-search is commented out in legacy).

### 2.3 Read query
- `DAILY_FINCL_REPT_R001` calls `FN_GET_HAENGSUNG_DAILY_FINANCIAL_REPORT(BIZ_KEY, PERIOD_DATE, ACCT_NO)`.
- Row types: `HEADER`, `BEFORE_BALANCE`, `TRANSACTION`, `ACCOUNT_TOTAL` (SUB TOTAL), `CURRENCY_TOTAL` (TOTAL).
- Legacy IDO filters `CURRENCY IN ('VND','USD')`; orders VND→USD→KRW, then `ACCOUNT_TYPE_ORDER, BANK_NAME_SORT, ACCOUNT_NUMBER NULLS LAST, TRANSACTION_SEQ`.
- All balances / receipts / payments / subtotals / grand totals are **computed inside the function** — no Java-side calc.

### 2.4 Editable fields — per-field auto-save
- Editable **only on `TRANSACTION` rows**: **Receipt Code (`REPT_CD`)**, **Payment Code (`PYMN_CD`)**, **Description EN (`DESC_NM_EN`)**, **Description KO (`DESC_NM_KO`)**, **Memo (`MEMO`)** (legacy setter also supports `SUPP_RMRK`).
- Save fires **on blur OR Enter**, one field at a time: `rept_0017_001_u001` with body `{ ACCT_SEQ_NO, FIELD_ID, FIELD_VAL }`.
- Backend loads the row by PK `(BIZ_KEY, ACCT_SEQ_NO)`, sets the one field, stamps `MOD_ID`/`MOD_DTM`, and patches `REPT_TRNS_HSTR` via `SET :FIELDS_TO_UPDATE WHERE BIZ_KEY=? AND ACCT_SEQ_NO=?`.
- Row not found → error.

### 2.5 Customer / Supplier
- `CUSTOMER_SUPPLIER` (wire `RCPT_NM`) is **read-only display**, produced by the function (from `rcv_pay_trco_nm` / `supp_rmrk`). Not editable, not stored via this screen.

### 2.6 Due Date / Branch
- The function embeds `Due date: <dd.Mon.yyyy>` inside the `BANK_ACCOUNT` header string for **Saving** accounts. Client/Excel split on `"Due date:"` and reformat to `dd Mon yyyy`, rendered red under the account number. HAENGSUNG's accounts are all Current in the seed, so due-date rows are rare there.
- "BRANCH" accent is part of the account header text; not a separate field.

### 2.7 Excel download
- `#download-excel` → `GET /rept_0017_001_r002_excel.act?PERIOD_DATE=..&ACCT_NO=..`.
- Backend re-runs `DAILY_FINCL_REPT_R001` (**fresh DB data — reflects saved edits, not unsaved screen edits**), loads the locale template, writes 12 columns via POI with the same grouping/merge/subtotal layout, streams `WeCMS_Daily_Financial_Report_{EN|KO}.xlsx`.
- 12 columns: Bank Account · Currency · Receipt Code · Payment Code · Description (EN) · Description (KO) · Customer/Supplier · Before Balance · Receipt · Payment · Balance · Memo (Actual Balance on the CURRENCY_TOTAL row).
- Number format: VND/KRW/JPY `#,##0`; others `#,##0.00`.

---

## 3. Refactor state (current)

### 3.1 Backend `wecms-api` — DONE (read only)
- `GET /api/v1/banking/reports/daily-financial-hs?asOf=<yyyyMMdd>&accountFilter=<ALL|JSON>` → `DailyFinancialHsReport`.
- `ReportService.dailyFinancialHs(asOf, accountFilter)` → `DailyFinancialHsReportRepository.fetchReport(bizKey, periodDate, accountFilter)` → `fn_get_haengsung_daily_financial_report(:bizKey,:periodDate,:accountFilter)` (verified in the live refactor DB; 24 output columns, 5 row types, VND/USD/KRW totals; `hand_reg_yn='N'` filter).
- DTO `DailyFinancialHsReport.LedgerRow` — wire `row_type, bank_account, currency, rept_cd, pymn_cd, description_en, description_ko, customer_supplier, before_balance, receipt, payment, balance, balance_company_actual, account_number, account_type_order, acct_seq_no, transaction_date, transaction_time, transaction_seq, memo, bank_cd, bank_name_sort`. Null amounts preserved.
- Unit tests: `ReportServiceDailyFinancialHsTest` (3 cases).
- `REPT_TRNS_HSTR` (PK `(biz_key, acct_seq_no)`) present in the refactor DB with `rept_cd, pymn_cd, desc_nm_en, desc_nm_ko, memo, supp_rmrk` + `reg_id/reg_dtm/mod_id/mod_dtm`. 70 seed rows (2026-05-18..2026-06-24, biz_key `VN_VNM0004_000001`).

### 3.2 Backend gaps vs legacy
- **G1 — No save/update endpoint.** Legacy per-field auto-save (`rept_0017_001_u001`) has no refactor counterpart.
- **G2 — No Excel download endpoint.** HS is explicitly *not* covered by `ReportsDownloadService` (which serves `rept_0900_001` Daily Cash + `rept_0015_001` Daily Financial CMC). No HS template exists in `src/main/resources/templates`.

### 3.3 Frontend `wecms-web` — STATIC UI ONLY
- `DailyFinancialHsClient` renders `mockData.ts` (`DAILY_FINANCIAL_HS_GROUPS`); **no API call**.
- `fetchDailyFinancialHs({asOf, accountFilter})` and wire types `ApiHsLedgerRow`/`ApiDailyFinancialHsReport` already exist but are **unused**.
- Search button, bank dropdown (static `DAILY_FINANCIAL_HS_BANK_OPTIONS`), Excel button — all **inert**.
- Editable inputs = local `useState` only, **no save**.
- View Setting popover (12-column registry) fully wired client-side.
- Report shell (`ReportLayout`, no pagination) already correct per `wecms-web/CLAUDE.md` §7b/§7e.

### 3.4 FE↔BE contract — aligned
- DTO `descriptionEn`/`descriptionKo` → wire `description_en`/`description_ko` == `ApiHsLedgerRow`. No contract change needed for the read path.

---

## 4. Parity checklist

| # | Item | Legacy | Refactor now | Action |
|---|---|---|---|---|
| P1 | Read via `fn_get_haengsung_daily_financial_report` | ✅ | ✅ backend | none |
| P2 | Default date = today, `ALL` filter | ✅ | ⚠️ FE mock | wire FE |
| P3 | Search → refetch with date + account filter | ✅ | ❌ inert | wire FE |
| P4 | Bank/account multi-select dropdown | ✅ (`cash_0001_001_r003`) | ❌ static | derive from `ALL` report HEADER rows |
| P5 | Grouped rows + SUB TOTAL + TOTAL | ✅ | ⚠️ mock shape | project API rows → `BankGroup[]` |
| P6 | Editable REPT_CD/PYMN_CD/DESC_EN/DESC_KO/MEMO | ✅ auto-save | ❌ local only | **G1** save endpoint + FE blur/Enter |
| P7 | Customer/Supplier read-only | ✅ | ✅ (display) | map field |
| P8 | Due Date / Branch from header string | ✅ | ⚠️ mock | parse `bank_account` |
| P9 | Balances/subtotals from function (no Java calc) | ✅ | ✅ | none |
| P10 | Excel download (saved DB data) | ✅ template POI | ❌ **G2** | build download endpoint + wire FE |
| P11 | Empty state | ✅ | ⚠️ mock | render empty groups |

---

## 5. Implementation plan

### Backend (`wecms-api`)
1. **Save endpoint (G1)** — `PATCH /api/v1/banking/reports/daily-financial-hs/entries/{acctSeqNo}` (or field-batch body), legacy-faithful: writes only `rept_cd | pymn_cd | desc_nm_en | desc_nm_ko | memo` on `REPT_TRNS_HSTR` by PK `(biz_key = TenantContext, acct_seq_no)`, stamps `mod_id/mod_dtm`. New `ReptTrnsHstr` entity + repository (table already in baseline; **no schema change, no key generation** — updates existing rows). Field whitelist enforced server-side. `@menuAuthz.canAccess('banking-reports-daily-financial-hs')`. Row-not-found → `WBExxxx`.
2. **Excel download (G2)** — `GET /api/v1/banking/reports/daily-financial-hs/report?asOf&accountFilter&lang`. Re-runs `ReportService.dailyFinancialHs` (saved DB data), builds the 12-column POI workbook mirroring `rept_0017_001_r002_excel_view.jsp` layout (grouping, merges, SUB TOTAL/TOTAL, number formats). Streams via the shared RFC-5987 binary response. *(Template approach decided with PO — see §6.)*
3. Tests: mapper/service for save (field whitelist, tenant scope, not-found) + download smoke.

### Frontend (`wecms-web`)
4. `DailyFinancialHsClient` → fetch `fetchDailyFinancialHs({asOf, accountFilter})`, project `ApiHsLedgerRow[]` → `BankGroup[]` (row_type discriminator; HEADER opens a group + parses bank name / account-type / due-date / branch; BEFORE_BALANCE + TRANSACTION → entries; ACCOUNT_TOTAL → `subtotal`; CURRENCY_TOTAL → `total` + `closesTotal`). Remove `mockData.ts` usage.
5. Search button → refetch with picked date (→ yyyyMMdd) + `accountFilter`.
6. Bank dropdown options derived from the `ALL` response HEADER rows (bank_cd + account_no + bank name); selection → JSON `[{BANK_CD,ACCT_NO}]`. Remove static `bankOptions.ts` usage.
7. Editable inputs → blur/Enter → `PATCH` save (per-field, matches legacy). Success re-syncs the cell; error → `useDialog` error.
8. Excel button → `downloadDailyFinancialHsReport({asOf, accountFilter, lang})` via `lib/excel.ts`/file download.
9. Keep the current UI design, View Setting, and `ReportLayout` shell unchanged.

### Validation
- `./gradlew compileJava test` (backend) · `npx tsc --noEmit` + lint (frontend) · manual: default/date/bank/all search, grouped subtotals, editable save round-trip, Excel download, empty state.

---

## 6. Decisions (resolved 2026-07-01) & implementation record

- **D1 — Excel template → CORRECTED 2026-07-01 to TEMPLATE-POPULATE.** The earlier "no HS template ships → build programmatically" premise was wrong: the 3 legacy templates DO exist (`WECMS DEV/WECMS_STATIC/web/file/excel/VN/WeCMS_Daily_Financial_Report_{EN,KO,VI}.xlsx`) and legacy `rept_0017_001_r002_excel_view.jsp` loads them via `WorkbookFactory` (hybrid: template owns title/subtitle/bilingual headers/borders in rows 1–5 + the 996-row pre-bordered data area; data written from row index 5 with programmatic styles/merges). Templates copied verbatim into `wecms-api/src/main/resources/templates/reports/daily-financial-hs/`; `DailyFinancialHsDownloadService` rewritten to load the locale template and populate it — port of the legacy JSP: period date → (row1,col0)+(row2,col11); 12 cols always; bank/ccy vertical merge per account (excl. CURRENCY_TOTAL row); SUB TOTAL merges C–F, TOTAL merges A–F; BEFORE_BALANCE empty desc/counterparty; VND/KRW/JPY `#,##0` else `#,##0.00`; output filename `WeCMS_Daily_Financial_Report_{EN|KO|VI}.xlsx` (== template basename, legacy parity). The prior programmatic generator was replaced entirely.
  - **Merge-overlap fix (2026-07-01):** the function sorts all CURRENCY_TOTAL rows (account_type_order 999990/1/2) to the very END, so a VND+USD+KRW report ends with 3 consecutive CURRENCY_TOTAL rows. The legacy HEADER-delimited grouping absorbed them into the last account's Bank/Currency vertical merge and only stripped ONE (`endRow-1`), so the 2nd/3rd CURRENCY_TOTAL's A–F merge overlapped it → POI `Cannot add merged region … overlaps` (500 / WBE9999 on multi-currency downloads). Fixed by bounding each account's vertical merge to `[first-data-row .. its ACCOUNT_TOTAL]` (computed during the write pass) and excluding CURRENCY_TOTAL rows entirely; dropped the `endRow-1` hack. Regression test `DailyFinancialHsDownloadServiceTest` now uses a VND+USD report with 2 trailing CURRENCY_TOTAL rows.
- **D2 — Save write-path → IMPLEMENTED, per-field auto-save on blur/Enter.** Legacy-faithful write to existing `REPT_TRNS_HSTR` columns by PK; no key gen, no schema change, no migration impact.

Both are migration-safe; no legacy schema/structure change was made.

### Delivered

**Backend (`wecms-api`)**
- `entity/ReptTrnsHstr.java` (+ `ReptTrnsHstrId.java`) — maps PK + the 5 editable columns, audit via `BaseEntity`.
- `repository/ReptTrnsHstrRepository.java` — `findByBizKeyAndAcctSeqNo`.
- `dto/DailyFinancialHsEntryUpdate.java` — partial patch (field whitelist = record shape).
- `service/DailyFinancialHsWriteService.java` — per-field save, tenant-scoped PK, `MOD_ID`/`MOD_DTM` stamp, `WBE_DAILY_FINANCIAL_HS_ENTRY_NOT_FOUND`.
- `service/DailyFinancialHsDownloadService.java` — programmatic POI workbook.
- `controller/ReportController.java` — `PATCH /daily-financial-hs/entries/{acctSeqNo}` + `GET /daily-financial-hs/report`, both `@menuAuthz.canAccess('banking-reports-daily-financial-hs')`.
- `messages.properties` — `WBE_DAILY_FINANCIAL_HS_ENTRY_NOT_FOUND`.
- Tests: `DailyFinancialHsWriteServiceTest` (3) + `DailyFinancialHsDownloadServiceTest` (3). Full suite `./gradlew test` green.

**Frontend (`wecms-web`)**
- `lib/api/banking.ts` — `updateDailyFinancialHsEntry`, `downloadDailyFinancialHsReport`.
- `daily-financial-hs/projectRows.ts` — `projectHsRows` (row_type → BankGroup) + `deriveBankOptions`.
- `DailyFinancialHsClient.tsx` — real fetch, projection, Search (searchKey), mount ALL fetch for the dropdown, save + download handlers, error dialogs.
- `DailyFinancialHsPage.tsx` — wired Search / Excel / bank dropdown / per-field save-on-blur-Enter (skips non-persistable BEFORE_BALANCE rows). UI design unchanged.
- **View Setting DB-backed (2026-07-01):** the popover now loads/saves via the shared `useViewSetting(SCREEN_IDS.BANKING_REPORTS_DAILY_FINANCIAL_HS, DEFAULT_AREA_ID)` hook + `/api/v1/screen-options` (was client-side `useState`). Legacy `sys_scr_option_wecms` `rept_0017_001`/`table1` (12 cols) → refactor slug `banking-reports-daily-financial-hs`/`default` (legacy underscore id rejected by the backend screenId `@Pattern`; legacy FIELD_IDs mapped to the refactor registry ids). Seed = manual DBA script `db/migration/V249__sys_scr_option_daily_financial_hs.sql` (12 cols, `sort_yn='N'`, widths 125/78/170/170/200/200/200/135/135/135/135/285, `ON CONFLICT DO NOTHING`). Static `DEFAULT_VIEW_SETTING_ITEMS` kept as first-paint/unseeded fallback; DB is source of truth once seeded. No backend Java change (screen-option API is screen-agnostic). **Excel unchanged** — legacy `rept_0017_001_r002_excel_view.jsp` has no column-visibility logic; the download always emits all 12 columns regardless of View Setting (confirmed), matching `DailyFinancialHsDownloadService`.
- **SUB TOTAL / TOTAL row rendering (2026-07-01, FE):** an interim attempt made the label cell `sticky left-0` with an opaque background to keep it visible on horizontal scroll — but a **wide-colSpan sticky cell pins to the viewport-left and its opaque background overlays the numeric cells that scroll under it**, so the Before Balance / Receipt / Payment / Balance / Memo values were covered/truncated (e.g. "3.000.000.000" showing as ")0"). **Reverted:** `SummaryCells` renders the "SUB TOTAL"/"TOTAL" label as a plain right-aligned colSpan cell immediately left of the numeric columns (a sibling, never an overlay), so all totals render in their correct columns and are never covered/shifted when scrolled. Field mapping (verified correct): CURRENCY_TOTAL → `beforeBalance`/`receipt`/`payment`/`balance` (from `toTotals`) + Memo = `balance_company_actual`; each mapped in `columns.ts` via the per-column `total` accessor. View Setting untouched. (Keeping the label always-visible on deep scroll would need proper frozen leading columns — cumulative-offset sticky block so numbers scroll to its right, not under it — a larger optional enhancement.)
- **BEFORE_BALANCE render parity (fix 2026-07-01):** legacy `rept_0017_001.ts fnGetDataRow` creates the Receipt/Payment/Description inputs ONLY for `isTransaction`; a BEFORE_BALANCE row renders empty code/description cells (no input boxes, no "Before Balance"/"전일잔고" label) — just the Before Balance amount + SUB TOTAL. The refactor was rendering every row as editable. Fixed: `LedgerEntry.editable` (TRANSACTION only); `projectRows.toEntry` blanks code/description for non-transaction rows; `EntryCell` renders `<InputCell>` only when `editable`, else a plain (blank) cell. No-transaction accounts now show header + before-balance amount + SUB TOTAL only. FE-only (API contract already correct — BEFORE_BALANCE carries `acct_seq_no=""`).
- `types.ts` — `LedgerEntry.acctSeqNo`. Deleted `mockData.ts` + `bankOptions.ts`.
- `tsc --noEmit` + eslint on changed files green.

### Remaining
- Live HTTP + browser smoke (bootRun + `npm run dev`): default/date/bank/all search, subtotals, editable save round-trip, Excel open, empty state — run in the local envs.
- Optional doc cleanup: the `wecms-web/CLAUDE.md` API table still calls HS "Stub backend + frontend mock" (now stale); update in a docs pass.
