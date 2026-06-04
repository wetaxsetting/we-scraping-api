# Daily Cash — Functional Specification (Legacy JEX)

Detailed functional rules for `rept_0009_001`. Cites `path:line` against `WECMS DEV/`.

> **Refactor deltas (2026-06-04)** — the refactor follows these legacy rules with these intentional
> differences: data via **live recompute** over `ACCT_MSTR` + `ACCT_TRNS_HSTR` (not the snapshot table);
> account-type mapping fixed (`03/04`=Current, `05/06`=Savings, `02`=Term, `01`=Loan, `07/08` ignored,
> **cash=0**, no `11/12/13/21`); summary block always shown after Search with a **zero VND/USD/KRW**
> fallback; **Cash table removed**; **no checkbox column** on the Current/Savings ledgers. Open items:
> Excel still client-side, last-aggregation timestamp not ported, print parity pending.

---

## 1. Search conditions

| Field | Source / control | Rule |
|---|---|---|
| `REQ_DATE` | `.date` datepicker | **Required.** Format `yyyyMMdd` (`formatDateForSave`). Default = today. Year range `today-10 … today`. VN view renders the input **disabled** → fixed to today (`rept_0009_001_view_osmc.jsp:147`). |
| `CCY_CD` | currency radio `checkCurr` | `""` = All currencies · `"FCY"` = all non-local (`ccy_cd <> 'VND'`) · `"<local>"` = exact (`VND`). |
| `INQ_DV` | `_INQ_DV` internal | `"0"` default; `"1"`/`"2"` only set by the dead `jConfirm` path. **`@deprecated`** — not consumed by the action's `getDynamic0`. |

### Currency dynamic (`getDynamic0`, `rept_0009_001_r001_act.jsp:107-131`)
```sql
and biz_key = ?                       -- always
-- if CCY_CD = 'FCY':   and ccy_cd <> 'VND'
-- else if CCY_CD set:  and ccy_cd = ?     (exact)
-- if CCY_CD blank (All): no currency predicate
```
The same dynamic is applied to all four IDO reads. The REC1 (account detail) read additionally
appends `order by acct_type_cd asc, ccy_cd desc, bank_nm asc, expi_dt asc` (`:66`).

---

## 2. Account-type code semantics (authoritative)

From `wabooks/enums/AcctTypeCd.java:6-15` + `getLabel()`:

| `ACCT_TYPE_CD` | Constant | EN label | KO | Daily Cash section |
|---|---|---|---|---|
| `01` | `LOAN` | (Loan / 대출금) | 대출금 | Loan/Borrowing — *commented out in active view* |
| `02` | `DEPOSIT` | Fixed (Time Deposit) | 정기예적금 | Time Deposits — *commented out* |
| `03` | `CURRENT_KHR` | Current | 입출식예금 | **Current Accounts** |
| `04` | `CURRENT_USD` | Foreign Current | 외화입출식예금 | **Current Accounts** |
| `05` | `SAVINGS_KHR` | Savings | 보통예금 | **Savings Accounts** |
| `06` | `SAVINGS_USD` | Foreign Savings | 외화보통예금 | **Savings Accounts** |
| `07` | `CURRENT_KRW` | Current Accounts | (KRW) | Current (KRW tenants) |
| `08` | `SAVINGS_KRW` | Saving Accounts | (KRW) | Savings (KRW tenants) |

> This table is the **single source of truth** for classification. Both refactor halves diverge
> from it — see [`refactor-gap-analysis.md`](./refactor-gap-analysis.md) gap #1 (CRITICAL).

---

## 3. Grid rendering rules

### Summary (`REC`)
- One row per currency; total = `TOTL_NORM_AMT + TOTL_SAV_AMT + TOTL_CASH + TOTL_ACCT_AMT + TOTL_LOAN_AMT`.
- Local total → `#totalKhr`; USD total → `#totalUsd` (`rept_0009_001_osmc.ts:518-540`).
- Visibility per `checkCurr`: local→KHR only, FCY→USD only, All→both (`:248-506`).
- All-null guard removes the summary line and shows the per-currency "no transaction" message.

### Current Accounts (`REC1`, `ACCT_TYPE_CD` ∈ {`03`,`04`})  (`:621-694`)
Columns: Bank (`BANK_NM`) · Account No (`ACCT_NO`) · Currency (`CCY_CD`) · No. of Debits
(`trunc(SND_CNT)`) · Debit (`SND_AMT`) · No. of Credits (`trunc(RCV_CNT)`) · Credit (`RCV_AMT`) ·
Balance (`BAL`). Footer totals: `#TOTL_DEPOSIT_CCY_CD` (sum of VND balances) and
`#TOTL_DEPOSIT_USD` (sum of USD balances).

### Savings Accounts (`REC1`, `ACCT_TYPE_CD` ∈ {`05`,`06`})  (`fn_savingAmt :744`)
Same 8 columns as Current. Empty → `.showMessageSaveAmt`.

### Time Deposits (`REC1`, `02`) / Loan (`REC1`, `01`)  — markup commented out
`fn_regularDeposit` / `fn_loan` still build rows into `.regular_Deposit` / `.LOAN`, but the
container tables are commented out in `rept_0009_001_view_osmc.jsp` (per Redmine #42129), so they
do not display. Time-deposit columns: Bank · Acct No · Deposit Title (`LOAN_NM`) · Maturity
(`EXPI_DT`) · CCY · Balance. Loan columns add Line-of-Credit (`AGMT_AMT`).

### Cash (`REC2`)  — markup commented out
`fn_cash` (`:832`) builds: Type(현금) · CCY · Opening Balance (`BEF_DATE_BAL`) · #Debits · Debit ·
#Credits · Credit · Balance. Container commented out (Redmine #42096).

### Formatting rules
- Amounts: `_osmc.ui.formatAmount(parseFloat(v), ccyCd)`; `null` → blank string.
- Counts: `fn_formatcnt` → `floor(value)` then comma; blank → `'0'`; `"0.00"`/`"0.000000"` → `'0'`.
- Bank/account/loan names: `common.app.null2void` (null → "").
- Dates: `common.app.formatDateForView` (maturity), `formatDateTimeForView` (last-collection).

---

## 4. Multilingual labels / message keys

ML type = **Web Service View** (main page). JSP labels use `data-jex-ml="rept_0009_001_v###"`;
TS labels use `rept_0009_001.ML.get("rept_0009_001_j###", fallback)`. The `ML_ID → DIC_ID` map is
in `WECMS_SYNC/.../ML_COM/rept_0009_001_view.json`.

### JSP view labels (selected — from `rept_0009_001_view.json` + view)
| ML ID | DIC | Meaning (from usage) |
|---|---|---|
| `rept_0009_001_v367` | — | **Daily Cash** (page title `H1`) |
| `rept_0009_001_v005` | COM_DIC_00000043 | All (currency radio) |
| `rept_0009_001_v071` | COM_DIC_00000328 | As-of / Date label |
| `rept_0009_001_v072` | COM_DIC_00000084 | Currency label |
| `rept_0009_001_v306` | — | Search |
| `rept_0009_001_v303` | — | "Please click the 'Search' button." |
| `rept_0009_001_v307` | — | Daily Cash (inquiry-page title) |
| `rept_0009_001_v068` / `_v069` | COM_DIC_00000330 / _00000084 | As-of / Currency chip labels |
| `rept_0009_001_v008` / `_v014` | DIC_00001874 | Total (KHR / USD) |
| `rept_0009_001_v036` / `_v037` | DIC_00001882 / DIC_00001875 | Current Accounts / Savings Accounts section titles |
| `rept_0009_001_v038..v044` | various | Savings table headers (Bank, Acct No, CCY, #Debits, Debit, #Credits, Credit, Balance) |
| `rept_0009_001_v075` / `_v076` | COM_DIC_00000295 / _00000294 | Total (local) / Total (USD) footer |
| `rept_0009_001_f005..f011` | — | per-section "no data" messages |

### TS dictionary keys (`rept_0009_001_osmc.ts:44-69`, used for the Excel workbook)
`j011` CURRENCY · `j012` Opening Balance · `j013` No. of Debits · `j014` Debit · `j015` No. of
Credits · `j016` Credit · `j017` Balance · `j018` Bank · `j019` Account No · `j020` Loan Title ·
`j021` Maturity Date · `j022` Line of Credit · `j023` Deposit Title · `j024` Cash · `j025` Current
Accounts · `j026` Savings Accounts · `j027` Time Deposits · `j028` Loan/Borrowing · `j029` Daily
Cash Report · `j030` As of · `j031` Total · `j032` All · `j033` Currency Code · `j034` Currency.
Also `j002` 현금/Cash, `j003` 알림/Alert, `j007`/`j008` confirm buttons, `j009` 영업집계 최종일시,
`j010` 없음/None.

> **Open Question — exact per-language text.** Only English fallbacks (from the `.ts`) and Korean
> JSP source strings are verifiable in the repo. The EN/KO/KH/VI values behind each `DIC_ID` live
> in the Jex Studio dictionary DB and were not extracted this pass.

---

## 5. Validation rules

Backend (`rept_0009_001_r001_act.jsp:52-57`):
- `ValidationHelper.validateRequired(REQ_DATE)` — required.
- `ValidationHelper.validateDate8(REQ_DATE)` — must be an 8-digit date.

`rept_0100_001_r001_act.jsp:31-35`:
- `BAT_ID` required; `validateReptBatchId(BAT_ID)` — must be a known `ReptBatId`.

Frontend: no explicit client validation beyond the datepicker; relies on backend.

---

## 6. Edge cases

| Case | Behavior |
|---|---|
| No data for the date | Per-currency "no transaction" message rows shown; summary line removed; section "no data" rows shown. |
| Batch not run for the date (read path) | Legacy *recomputes live* via the `rept_0900_001_rec*` functions, so it can still return data even without a persisted batch row. (Contrast refactor — reads persisted detail only.) |
| `REPT_DIF_DATA` returned | `jConfirm()` offers re-aggregate / query-as-is; sets `INQ_DV`. **Currently unreachable** — action never returns this code; `INQ_DV` deprecated. |
| UAT environment | Batch `REPT_0900_001` returns immediately (`EnvHelper.isUat()`), so UAT never refreshes the persisted summary tables. |
| FCY currency | `ccy_cd <> 'VND'` (the local currency literal is hardcoded `'VND'` in the action dynamic). |
| Synchronous fetch | `fn_listData` uses `async:false`; a slow/failed call blocks the UI until complete. |
| `null` vs `"0.00"` counts | `fn_formatcnt` / inline guards normalise `"0.00"`/`"0.000000"` → `0`; `null` → `''`/`0` depending on field. |
| `TOTL_KHR_ACCT_AMT#1` duplicate field | The WSVC OUT declares `TOTL_KHR_ACCT_AMT` twice (`#1`) — a legacy schema artifact; not read by the TS. |
| `System.out.println` debug | `rept_0009_001_r001_act.jsp:180-211` prints REC assembly to stdout (legacy debug left in). |

---

## 7. Permissions / service config

From `WSVC.rept_0009_001_r001.xml`:
- `login = Y` (auth required), `trTp = U`, `mntLog = Y` (audit-logged), `actUseYn = Y`.
- `ext: NORM_USER_ONLY_YN = Y`, `WTA_USER_ALLOW_YN = N` → **normal users only** (no WTA/sub-account-only users).
- Service-time window unrestricted (`tmUseYn = N`, 000000–240000).
