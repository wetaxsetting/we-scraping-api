# Daily Cash — Business Flow (Legacy JEX)

End-to-end legacy behavior of the Daily Cash page (`rept_0009_001`). Source-of-truth files cited
inline as `path:line`.

> **Refactor note (2026-06-04).** This doc describes the **legacy** flow. The refactor reproduces it
> with a **live recompute** — `DailyCashReportService` reads `ACCT_MSTR` + `ACCT_TRNS_HSTR` per request
> (legacy `rept_0900_001_rec1/rec2` replicated in Java/SQL), **not** the batch snapshot
> `DAILY_CASH_REPT_DAILY_SUMR_DETL`, so any date returns accounts without depending on the nightly
> batch. Cash (`rec3/rec4` over `CASH_REPY_MSTR`) is out of scope → `total_cash = 0`. UI: summary
> block always shown after Search (zero VND/USD/KRW fallback); Cash table and ledger checkbox column
> removed. See [`refactor-gap-analysis.md`](./refactor-gap-analysis.md) / [`migration-guide.md`](./migration-guide.md).

---

## 0. What the report is

A per-business **snapshot of cash position as of a chosen date**, broken down by currency
(local currency — VND in the VN deployment — and USD / foreign), across account types:
physical cash, current/demand deposits, savings deposits, time deposits, and loans/borrowings.
The currently-live VN page renders only the **Summary**, **Current Accounts**, and **Savings
Accounts** sections; the Cash, Time-Deposit and Loan tables exist in markup but are **commented
out** (`rept_0009_001_view_osmc.jsp` — Cash block lines 297–335; Time-Deposit/Loan lines 430–550),
referencing kosign Redmine issues #42096 / #42129.

---

## 1. Data production (nightly batch) — how the numbers get there

```
Scheduler
  └─ Batch  REPT_0900_001.execute()            batch/.../rept/REPT_0900_001.java
       │   (EnvHelper.isUat() → return early — UAT produces nothing)   :69-71
       ├─ load all NORMAL businesses           BusiInfmDAO.getRows(busi_stts='1')  :83
       └─ for each business:
            ├─ INSERT BAT_CALL_HIS row          JOB_ID='REPT_0900_001', BIZ_KEY,
            │                                   REQ_DATE (default = yesterday), JOB_DATE/STR_TM  :121-146
            ├─ ReptHelper.call_REPT_0900_001()  ReptHelper.java:420-560
            │     ├─ BCS REPT_0900_001.execute() → REC1..REC4
            │     │     REC1 = rept_0900_001_rec1()  account detail
            │     │     REC2 = rept_0900_001_rec2()  per-ccy totals (NORM/SAV/ACCT/LOAN)
            │     │     REC3 = rept_0900_001_rec3()  cash flow
            │     │     REC4 = rept_0900_001_rec4()  total cash
            │     ├─ delete+insert DAILY_CASH_REPT_DAILY_SUMR_DETL       (from REC1)  :449-466
            │     ├─ delete+insert DAILY_CASH_REPT_DAILY_CASH_SUMR_DETL  (from REC3)  :470-490
            │     └─ upsert        DAILY_CASH_REPT_DAILY_SUMR            (REC2 merged with REC4 TOTL_CASH)  :492-532
            └─ UPDATE BAT_CALL_HIS with JOB_END_TM + TOT/SUC/ERR/RSLT  :171-184
```

- `REQ_DATE` defaults to **yesterday** (`DateTimeUtils.backwardDate(1)`) when not supplied
  (`REPT_0900_001.java:133`, `ReptHelper.java:429-431`).
- The batch persists three tables: `DAILY_CASH_REPT_DAILY_SUMR`, `_DETL`, `_DAILY_CASH_SUMR_DETL`.
- **`BAT_CALL_HIS`** records each run; `JOB_DATE || JOB_END_TM` of the latest row is the
  "Business aggregation last datetime" shown on the page.

> **Important nuance:** the *read* path (below) does **not** read these persisted tables — it
> calls the same `rept_0900_001_rec1..4` functions **live** at request time. The persisted tables
> are produced by the batch but the report query recomputes. (The refactor inverts this — it reads
> the persisted detail table. See [`refactor-gap-analysis.md`](./refactor-gap-analysis.md) gap #3.)

---

## 2. Page load

`rept_0009_001_osmc.ts` `onload()` (line 112) runs three steps:

1. **`loadDate()`** (`:154`) — initialise the datepicker (`.date`), year range
   `today-10 … today`, **default = today**. In the VN view the input is rendered
   **`disabled`** (`rept_0009_001_view_osmc.jsp:147`), so the date is effectively fixed to today
   on the VN deployment. Selecting a date resets `_INQ_DV = "0"`.
2. **`fn_last_date()`** (`:918`) — calls `rept_0100_001_r001` with `BAT_ID='REPT_0900_001'`, then
   shows `"영업집계 최종일시 : <formatted LST_COLL_DATE>"` (Business aggregation last datetime),
   or "없음"/none if absent.
3. **`event()`** (`:117`) — bind click handlers (search / excel / print) and hide `.section-cash`.

Initial visible state: the report area is hidden; a centred message **"Please click the 'Search'
button."** (`rept_0009_001_v303`) is shown.

---

## 3. Search / filter

Controls (`rept_0009_001_view_osmc.jsp:136-174`):

| Control | DOM | Values |
|---|---|---|
| As-of date | `.date` datepicker | single date (VN: disabled → today) |
| Currency radio | `input[name=checkCurr]` | **All** (`value=""`, label `rept_0009_001_v005`) · **FCY** (`value="FCY"`) · **local** (`value=<%=_local_currency%>` → `VND`) |
| Search button | `.btn_search_tb` | triggers fetch |

On **Search** (`event()` `:119-126`): hide the prompt message, reveal the report containers, then
call `fn_listData({ TO_EXCEL:false })`.

### `fn_listData()` (`:204`)
1. Read `radioValue` (currency), `reqDate` (`formatDateForSave`), set the "as of" + "currency"
   display chips.
2. Build request `{ REQ_DATE, CCY_CD: radioValue, INQ_DV: _INQ_DV }`.
3. `jexAjax('rept_0009_001_r001')`, suffix `.jct`, **synchronous** (`async:false`), progress bar.
4. On response:
   - If `jexError`: if `CODE == "REPT_DIF_DATA"` → `jConfirm()` (re-aggregate confirm flow,
     see §6); else show the error message and stop.
   - Else: stash the payload for Excel, then render Summary, Current/Savings/Cash sections.
5. After the main render, call `fn_regularDeposit()`, `fn_savingAmt()`, `fn_loan()`, `fn_cash()`,
   then destroy the progress bar.

---

## 4. Grid / table rendering

`fn_listData()` consumes the response records (`REC`, `REC1`, `REC2`):

### Summary (`REC`, per currency)
For each `REC` row, compute the per-currency total
`TOTL_NORM_AMT + TOTL_SAV_AMT + TOTL_CASH + TOTL_ACCT_AMT + TOTL_LOAN_AMT` and write `#totalKhr`
(local) or `#totalUsd` (`:518-540`). Currency-visibility logic:

| `checkCurr` | Local (KHR/VND) blocks | USD blocks |
|---|---|---|
| local (`VND`) | show | hide |
| `FCY` | hide | show |
| All (`""`) | show | show |

Empty-data guards toggle the "no transaction" message rows (`.messageKHR` / `.messageUSD`).

### Account detail tables (`REC1`) — split by `ACCT_TYPE_CD`
Each handler iterates `REC1` and emits `<tr>` rows into a section table:

| Section | Handler | `ACCT_TYPE_CD` filter | Columns rendered |
|---|---|---|---|
| **Current Accounts** (`.deposit_withdraw`) | inline in `fn_listData` `:621-694` | `03`, `04` | Bank · Acct No · CCY · #Debits · Debit · #Credits · Credit · Balance |
| **Savings Accounts** (`.SavingAmount`) | `fn_savingAmt` `:744` | `05`, `06` | Bank · Acct No · CCY · #Debits · Debit · #Credits · Credit · Balance |
| Time Deposits (`.regular_Deposit`) | `fn_regularDeposit` `:703` | `02` | Bank · Acct No · Title · Maturity · CCY · Balance — **table commented out in view** |
| Loan / Borrowing (`.LOAN`) | `fn_loan` `:791` | `01` | Bank · Acct No · Title · Maturity · CCY · Line-of-credit · Balance — **commented out** |

Per-section footer total rows (`#TOTL_DEPOSIT_CCY_CD`/`_USD`, `#TOTL_TIME_DEP_*`, `#TOTL_LOAN_*`)
are filled from the summed balances; the empty-row message (`.showMessage*`) is toggled when the
section has no rows.

### Cash flow (`REC2`)
`fn_cash()` (`:832`) renders the Cash table from `REC2`: Type(현금) · CCY · Opening Balance
(`BEF_DATE_BAL`) · #Debits · Debit · #Credits · Credit · Balance. **Cash table is commented out in
the active view**, so this currently renders into a hidden node.

All amounts are formatted via `_osmc.ui.formatAmount(value, ccyCd)`; counts via `fn_formatcnt`
(floor + comma). Null amounts render blank.

---

## 5. Button events

| Event | Binding | Behavior |
|---|---|---|
| **Search** | `.btn_search_tb` click `:119` | reveal report, `fn_listData({TO_EXCEL:false})` |
| **Excel** | `.excel-down` click `:129` | `download_excel(excelDataJson)` → POST JSON to the Excel JSP in hidden iframe |
| **Print** | `.page-print` click `:134` | clone `.form-wrap`, strip toolbar/title/form/message, `_v2common.app.callPrintThis(...)` (report mode) |

### Excel (`download_excel` `:895`)
1. Attach `lastUpdate` (last-collection text), `noTransaction`, `noAccount` strings + the ML
   `dictionary` to the payload.
2. `fn_formatExcel()` strips `LST_COLL_DATE` from `REC1`/`REC2` rows.
3. `encodeURI(JSON.stringify(data))` → `#excelJsonObject`, submit `#excelJsonObjectForm`.
4. Form target = hidden iframe `rept_0009_001_excel`; action = `rept_0009_001_excel_osmc_vn.jsp`
   (VN) or `rept_0009_001_excel_osmc.jsp` (non-VN) — chosen in the view by
   `WABOOKSDynamicDataPool.getCurrentNtnlCd()` (`rept_0009_001_view_osmc.jsp:87-95`).
5. The Excel JSP (`rept_0009_001_excel_osmc_vn.jsp`) parses the JSON, builds an Apache POI `XSSF`
   workbook (title, "As of … / Currency: …", per-currency Total band, Current + Saving sections
   with totals) and streams it as `attachment; filename=Daily Cash_<yyyyMMddHHmm>.xlsx`.
   The Cash / Time-Deposit / Loan sections are commented out there too — only Current + Saving are emitted.

---

## 6. Popup / detail navigation

- **No row drill-down / detail popup.** Daily Cash is a flat snapshot. Per-transaction history
  lives in the separate **Daily Financial** report.
- **One confirm dialog — `jConfirm()`** (`:164`): only fires when the read API returns
  `CODE == "REPT_DIF_DATA"`. Shows message `WBC1105` with buttons "집계실행후조회"
  (re-aggregate then query, sets `INQ_DV='1'`) / "집계없이조회" (query without re-aggregating,
  `INQ_DV='2'`), then re-runs `fn_listData`.
  - **Status: effectively dead in the current code.** `rept_0009_001_r001_act.jsp` never returns
    `REPT_DIF_DATA`, and `INQ_DV` is annotated `@deprecated` in the type
    (`rept_0009_001_r001.d.ts:4-5`) and is ignored by the action's `getDynamic0`. Treated as a
    legacy artifact — see [`functional-specification.md`](./functional-specification.md) §"Edge cases".

---

## 7. Data flow summary (read path)

```
Browser (rept_0009_001_osmc.ts)
  ├─ rept_0100_001_r001  ──► IDO BAT_CALL_HIS_R002 ──► BAT_CALL_HIS  (last-collection datetime)
  └─ rept_0009_001_r001  ──► rept_0009_001_r001_act.jsp
         getDynamic0 (biz_key + currency filter)
         ├─ getRowsForRec1 → IDO R004 → fn rept_0900_001_rec1()  ──► REC1 (account detail)
         ├─ getRowsForRec2 → IDO R005 → fn rept_0900_001_rec2()  ─┐ per-ccy totals
         ├─ getRowsForRec4 → IDO R007 → fn rept_0900_001_rec4()  ─┴► REC (merged TOTL_CASH on CCY_CD)
         └─ getRowsForRec3 → IDO R006 → fn rept_0900_001_rec3()  ──► REC2 (cash flow)
```

`REQ_DATE2` is passed equal to `REQ_DATE` (`rept_0009_001_r001_act.jsp:170-171`) — the functions
take a date range but the page always queries a single day.
