# Daily Financial (HS) — Legacy Spec & Refactor Comparison

> Legacy `rept_0017_001` behaviour (source of truth, read-only `WECMS DEV/` tree) side-by-side
> with the refactor. Deep legacy semantics were captured in
> [audits/daily-financial-hs-legacy-audit.md](../../../../audits/legacy-parity/daily-financial-hs-legacy-audit.md) §2;
> this document is the package-local comparison.

---

## 1. Legacy artifacts

| Concern | Legacy artifact (under `WECMS DEV/`) |
|---|---|
| View JSP | `WECMS/web/view/jex/wabooks/rept/rept_0017_001_view.jsp` — title h1 (ML `rept_0017_001_v001`, :134), View-Setting icon (:139), Excel button (:143-160), As-of date `#TO_DT` (:166-179), bank multi-select (:181-221), Search (:223), table anchor `dymAreaId="table1"` (:230-238) |
| Client TS | `WECMS_STATIC/ts-src/js/jex/wabooks/rept/rept_0017_001.ts` — `fn_getData`, `fn_renderTableRows`, `fnGetDataRow`, `fn_saveData` |
| Read action | `WECMS/web/WEB-INF/action/jex/wabooks/rept/rept_0017_001_r001_act.jsp` — validates `PERIOD_DATE` + `ACCT_NO` (:19-23), IDO call (:25-31), `output.put("REC1", rows)` (:68) |
| Save action | `rept_0017_001_u001_act.jsp` — validates `ACCT_SEQ_NO` + `FIELD_ID` (:26-29); FIELD_ID if-chain + `ReptTrnsHstrDAO.patch` (:66-99); missing row → `WBE0404` |
| Excel action | `rept_0017_001_r002_excel_view.jsp` — template load (:53-68), locale date formats (:72-107), 12 column widths (:141-149) |
| WSVC | `WSVC.rept_0017_001.xml` (view; `trTp=U`, `mntLogYn=Y`) · `WSVC.rept_0017_001_r001.xml` (in: `PERIOD_DATE`, `ACCT_NO`; out: `REC1`, 22 fields) · `WSVC.rept_0017_001_u001.xml` (in: `ACCT_SEQ_NO`, `FIELD_ID`, `FIELD_VAL`; empty out) · `WSVC.rept_0017_001_r002_excel.xml` |
| Read IDO | `IDO.DAILY_FINCL_REPT_R001.xml` — SELECT over `FN_GET_HAENGSUNG_DAILY_FINANCIAL_REPORT(:BIZ_KEY, :PERIOD_DATE, :ACCT_NO)` with `WHERE ROW_TYPE IN (5 types) AND CURRENCY IN ('VND','USD')`, ORDER BY currency-first (:24-64) |
| Patch IDO | `IDO.REPT_TRNS_HSTR_U000.xml` — `UPDATE REPT_TRNS_HSTR SET :FIELDS_TO_UPDATE WHERE BIZ_KEY=? AND ACCT_SEQ_NO=?` |
| Entity / DAO | `src/wabooks/entity/ReptTrnsHstr.java` (370 lines; sanitizing setters, `fieldsToUpdateMap` PATCH pattern) · `ReptTrnsHstrPk.java` · `dao/ReptTrnsHstrDAO.java` (`findByPk` :28-33, `patch` :35-51) |
| Bank list | `cash_0001_001_r003` (active bank accounts for the dropdown) |
| Excel templates | `WECMS_STATIC/web/file/excel/VN/WeCMS_Daily_Financial_Report_{EN,KO,VI}.xlsx` — **byte-identical across the three languages** (SHA-256-verified in the final audit); A1 title `일 일 자 금 수 지 현 황` in every language; the JSP never writes A1 |
| ML codes | `rept_0017_001_v001`–`v006` (title, Excel button, "As of", "All Banks" ×2, "Search") |

Legacy menu registration lives in the Jex Studio menu tables (HAENGSUNG product tree) — *exact
legacy menu row: not verified from implementation* (the refactor equivalent is the `MENU_ITEM`
seed where the H tree carries `banking-reports-daily-financial-hs`).

---

## 2. Request flow — legacy vs refactor

| Step | Legacy | Refactor |
|---|---|---|
| Page load | `rept_0017_001.act` → TS defaults `PERIOD_DATE=today`, loads bank list via `cash_0001_001_r003` (all checked), runs `fn_getData()` | Route `/banking/reports/daily-financial-hs` → `DailyFinancialHsClient` fetches on mount (`asOf=today`, `accountFilter=ALL`); bank options derived from a mount-only `ALL` fetch |
| Search | POST `rept_0017_001_r001.jct` `{PERIOD_DATE, ACCT_NO}` (button-only; auto-search commented out) | `GET /api/v1/banking/reports/daily-financial-hs?asOf&accountFilter` (button-only; `searchKey` bump) |
| Per-field save | POST `rept_0017_001_u001` `{ACCT_SEQ_NO, FIELD_ID, FIELD_VAL}` on blur/Enter | `PATCH …/entries/{acctSeqNo}` with a partial snake_case body on blur/Enter |
| Excel | `GET rept_0017_001_r002_excel.act?PERIOD_DATE&ACCT_NO` | `GET …/daily-financial-hs/report?asOf&accountFilter&lang` |

## 3. SQL / computation

Identical engine: both systems call the same set-returning function and do **zero** app-side
calculation. The refactor query mirrors the legacy IDO's ORDER BY verbatim (currency-first key
included — see [database-mapping.md §4](database-mapping.md#4-the-refactor-read-query-verbatim)).
Differences are the dropped `CURRENCY IN ('VND','USD')` filter (KRW inclusion — intentional, §6)
and the absent `ROW_TYPE IN (…)` guard (redundant — the function emits only those five types).

## 4. Save path

| Aspect | Legacy | Refactor | Parity |
|---|---|---|---|
| Editable fields | `MEMO`, `REPT_CD`, `PYMN_CD`, `DESC_NM_EN`, `DESC_NM_KO` (u001 if-chain; the entity also has a `SUPP_RMRK` setter but u001 never dispatches it) | same 5 fields (`DailyFinancialHsEntryUpdate`) | ✅ |
| Granularity | one field per call | partial DTO (frontend sends one field per blur/Enter) | ✅ behavioural |
| Sanitization | `StringUtils.sanitize()` (NBSP strip) in entity setters | `sanitize()` port in `DailyFinancialHsWriteService` | ✅ |
| Audit stamp | `MOD_USER` + `MOD_DTM` (UTC) | `MOD_ID` + `MOD_DTM` (BaseEntity) | ✅ |
| Missing row | `WbRollbackException WBE0404` | `BusinessException WBE_DAILY_FINANCIAL_HS_ENTRY_NOT_FOUND` | ✅ (new refactor code id — JEX ML codes are never reused verbatim) |
| Update statement | IDO `REPT_TRNS_HSTR_U000` `SET :FIELDS_TO_UPDATE WHERE PK` | Hibernate dirty-checked `UPDATE … WHERE PK` | ✅ |

## 5. Excel

| Aspect | Legacy | Refactor | Parity |
|---|---|---|---|
| Mechanism | template-populate (POI `WorkbookFactory` over `/file/excel/VN/…{LANG}.xlsx`) | template-populate (classpath copy of the same templates) | ✅ |
| Data source | re-runs `DAILY_FINCL_REPT_R001` (saved DB data) | re-runs `ReportService.dailyFinancialHs` | ✅ |
| Columns | 12, always (no View-Setting filtering) | 12, always | ✅ |
| Widths / merges / row styling | 30/10/20/20/25/25/30/18/18/18/18/30; bank+ccy vertical merge; SUB TOTAL C:F cyan; TOTAL A:F grey | same, with the merge bound corrected to `[first data row .. ACCOUNT_TOTAL]` (legacy's `endRow-1` hack overlapped on multi-currency output) | ✅ (refactor is the robust form) |
| Number formats | `#,##0` VND/KRW/JPY else `#,##0.00` | same | ✅ |
| Date slots | (row1,col0) + (row2,col11); EN `M/d/yyyy`, KO `yyyy년 M월 d일` | A2 + L3; same formats (+ VI = EN format) | ✅ |
| A1 title | template-owned; Korean in ALL languages (templates byte-identical) | template-owned; **EN/VI localized** (§6) | ⚠ intentional divergence |
| Filename | `WeCMS_Daily_Financial_Report_{EN\|KO}.xlsx` | `WeCMS_Daily_Financial_Report_{EN\|KO\|VI}.xlsx` | ✅ |

## 6. Intentional differences

All three are recorded (with rationale + pinning tests) in the final audit §6 — **do not "fix"
them back in a future parity pass**:

1. **Excel A1 title localized per language (PO/business request, 2026-07-02).** EN
   `Daily Financial Report` · KO unchanged · VI `Báo cáo tài chính hằng ngày`. Only
   `xl/sharedStrings.xml` differs from legacy (per-zip-entry CRC verified); pinned by
   `templateTitleAndDateSlots_perLocale`. Live JEX templates untouched.
2. **KRW included.** Legacy filtered `CURRENCY IN ('VND','USD')`; the refactor covers the
   tenant's full account set ("one TOTAL for VND, USD, KRW depending on returned data",
   2026-07-02 requirement). Documented in the repository Javadoc.
3. **Summary-row cell structure.** Row count/order/rowSpan geometry match legacy 1:1, but SUB
   TOTAL / TOTAL rows render one `<td>` per visible column instead of legacy's colSpan merges —
   deliberately robust under View Setting hide/reorder. Visual output equivalent.

Secondary (mechanical) divergences: bank dropdown options derived from the `ALL` report's HEADER
rows instead of `cash_0001_001_r003` (equivalent data, one fewer legacy service); View Setting
rows re-keyed `rept_0017_001`/`table1` → `banking-reports-daily-financial-hs`/`default` with the
FIELD_ID remap (V249 — the backend screenId `@Pattern` rejects underscore ids); refactor save DTO
accepts multi-field bodies (frontend keeps one-field-per-save).

## 7. Preserved behaviour (the parity core)

- Eager initial load (today + ALL); Search-only refetch; date/filter changes are inert until
  Search.
- Five row types rendered in DB order; SUB TOTAL blue per account; TOTAL green closing each
  currency section; no spacer rows; blank (never "0") for null amounts.
- Only TRANSACTION rows editable; the exact 5-field set; save on blur/Enter; saved-data-only
  Excel.
- Customer/Supplier read-only (function-derived); `Due date:` / `BRANCH` text parsed out of the
  `BANK_ACCOUNT` header string.
- No pagination, no sorting, no audit-log writes, no exchange-rate logic.
