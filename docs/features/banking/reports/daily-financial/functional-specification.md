# Daily Financial — Functional Specification

Source of truth: `rept_0015_001_view.jsp`, `rept_0015_001.ts`, `rept_0015_001_r001_act.jsp`,
`WSVC.rept_0015_001_r001.xml`, and the live `wecmsvndev` schema. No assumptions.

---

## 1. Search conditions (filter bar)

`rept_0015_001_view.jsp:109-148`, handlers in `rept_0015_001.ts`.

| UI control | View element | TS / param | Sent to read API? | Notes |
|---|---|---|---|---|
| **As of** date | `.calendar-date.datePicker-reqDate` (`view:115`) | `REF_DATE` (`ts:277-278`) | **Yes** | `common.app.formatDateForSave` → `YYYYMMDD`. Default = today. Year range = −10y…now (`ts:271`). |
| **Currency** radio | `name="checkCurr"` (`view:117-129`) | `CCY_CD` (`ts:279`) | **Yes** | Three options: **All** = `""` (empty), **FCY** = `"FCY"`, **local** = `<%=_local_currency%>` (VND on this deployment). |
| **Show account nickname** | `#checkbox_showAccNname` (`view:133`) | `SHOW_ACCT_NICK_NAME` (`ts:235`) | **Excel only** | Default checked. Toggles the nickname column on screen (`ts:191-202`); only sent to the Excel JSP, **not** to `r001`. |
| **Show active accounts only** | `#checkbox_showActiveAcc` (`view:137`) | `ACTV_ACCT_ONLY_YN` (`ts:280`) | **Yes** | Default checked. `Y`/`N`. When `Y`, restricts to accounts where `ACCT_MSTR.actv_yn = 'Y'`. |
| **Include internal transfers** | `#checkbox_inclueInteral` (`view:141`) | `INCL_INTL_TRNS_YN` (`ts:281`) | **Yes** | Default checked. `Y`/`N`. When `N`, internal-transfer rows are excluded from REC2/REC3 via usage-code filters. |

There is **no** company picker (single-tenant per session — `BIZ_KEY` from `loginUser`), no bank
filter, no account filter, no status filter, no date-range (single as-of date only).

### Read API input message (`WSVC.rept_0015_001_r001.xml`)

| Field | Length | Required | Meaning |
|---|---|---|---|
| `REF_DATE` | 9 | yes | As-of date, `YYYYMMDD`. |
| `CCY_CD` | 3 | no | `""` (all) / `FCY` / a currency code (e.g. `VND`, `USD`). |
| `ACTV_ACCT_ONLY_YN` | 1 | yes | `Y`/`N`. |
| `INCL_INTL_TRNS_YN` | 1 | yes | `Y`/`N`. |

---

## 2. Output sections & grid columns

The view declares six section blocks; **Cash, Time Deposit, and Loan section markup is commented
out** (Redmine #42129 — `view:249-284, 391-514`). Live sections below.

### 2.1 Financial Status — totals (`REC` → `fn_finacialStatus`)

Two blocks, local currency (`view:170-208`) and USD (`view:210-247`), each with a per-type total
row. Columns are driven by ML codes; the populated cells are:

| Cell (CSS class) | Source field (REC) | Meaning |
|---|---|---|
| `.totalLocCurrentAmt` / `.totalUsdCurrentAmt` | `TOTL_NORM_AMT` | Current-account total |
| `.totalLocSaveAmt` / `.totalUsdSaveAmt` | `TOTL_SAV_AMT` | Savings total |
| `.totalLocCashAmt` / `.totalUsdCashAmt` (hidden) | `TOTL_CASH` | Cash total |
| `.totalLocAcctAmt` / `.totalUsdAcctAmt` (hidden) | `TOTL_ACCT_AMT` | Time-deposit total |
| `.totalLocLoanAmt` / `.totalUsdLoanAmt` (hidden) | `TOTL_LOAN_AMT` | Loan total |
| `.localTotal` / `.usdTotal` | sum of the five | Grand total per currency |

`fn_finacialStatus` (`ts:401-449`) sums each `TOTL_*` field, splitting USD vs everything-else by
`CCY_CD`.

### 2.2 Current Accounts grid (`REC1` where `ACCT_TYPE_CD ∈ {03,04}` → `fn_currentAccounts(…, "CURRENT")`)

`view:286-336`, render `ts:451-678`. Columns:

| Col ID (ML) | Header | Source (REC1 row) | Notes |
|---|---|---|---|
| `n006` | Bank | `BANK_NM` | clickable → txn-history popup |
| `n007` | Account No. | `ACCT_NO` | clickable |
| (nickname) | Account Nickname | `ACCT_NICK_NM` | shown only when *Show account nickname* on |
| `n008` | Currency | `CCY_CD` | |
| `n009` | No. of Debits | `SND_CNT` | `Math.trunc` |
| `n010` | Debit | `SND_AMT` | `_osmc.ui.formatAmount` |
| `n011`/`n012` | No. of Internal Debits / Internal Debit | `INTL_SND_CNT` / `INTL_SND_AMT` | shown only when *Include internal transfers* on |
| `n013` | No. of Credits | `RCV_CNT` | |
| `n014` | Credit | `RCV_AMT` | |
| `n015`/`n016` | No. of Internal Credits / Internal Credit | `INTL_RCV_CNT` / `INTL_RCV_AMT` | internal columns |
| `n017` | Balance | `BAL` | |

A footer total row per currency (local + USD) sums each column (`ts:566-665`).

### 2.3 Savings Accounts grid (`REC1` where `ACCT_TYPE_CD ∈ {05,06}` → `fn_currentAccounts(…, "SAVING")`)

`view:338-389` — identical column set to Current Accounts (ML codes `n019`–`n030`).

### 2.4 Per-bank transaction detail (`REC2` → `fn_accBank`, `ts:743-924`)

For each account in `REC2`, a header line (`Bank AcctNo (CCY) [nickname]` + internal-transfer
summary) and a two-sided **Debit | Credit** table built from the nested `REC2_1` JSON array.
Per transaction (`REC2_1` element): Time (`TRSC_TM`), Debit/Credit amount (`TRSC_AMT` split by
`INOUT_DV`), running Balance (`BAL`), Remarks (`RMRK`), Purpose/Account (`RCV_PAY_USAG_NM`).
The amount link is clickable → receipt/payment-confirmation popup when `RCV_PAY_USAG_NM` is present.

> **Refactor status (2026-06 — READY FOR SIT).** The **inline** per-bank detail is deferred (P1,
> non-blocker); the per-account drilldown is served by the **Transaction History Details popup**
> (`rept_0015_002` equivalent, `GET …/daily-financial/transactions`), now UI-aligned to the e-Invoice
> Latest Inquiry History popup: columns **Time · Debit · Credit · Balance · Remarks** (numeric
> right-aligned), centered empty-state, footer summary, **default page size 10 / options 10·20·50·100**.
> The Purpose/Account column is out of scope (its summary table is not in the PROD Active Table List).
> **Time column** renders blank by design when `TRSC_TM` is empty in source (verified — not a defect;
> [README.md §6](README.md#6-time-column-verification-transaction-history-details-popup)).

### 2.5 Cash book row (`REC3` → `fn_cash`, `ts:697-719`)

Type (Cash), Currency (`CCY_CD`), Opening Balance (`BEF_DATE_BAL`), No. of Debits (`SND_CNT`),
Debit (`SND_AMT`), No. of Credits (`RCV_CNT`), Credit (`RCV_AMT`), Balance (`BAL`, clickable →
cash-book detail popup). *(`#cash` tbody — section header markup commented out, but the row renderer
is live.)*

---

## 3. Buttons & actions

| Button | View | Handler | Behaviour |
|---|---|---|---|
| **Search** | `.btn_search` (`view:144`) | `ts:189-210` | Reveals sections, calls `fn_listData` (sync `r001`). |
| **Excel** | `.icon-svg.excel` (`view:153`) | `ts:229-265` | Serializes filters + dictionary + scraped financial-status table into `#excelJsonObject`, posts the hidden form to `rept_0015_001_excel.jsp` (target = hidden iframe). |
| **Print** | `.icon-svg.print` (`view:154`) | `ts:212-227` | Clones the result area (minus title/form/message) and calls `_v2common.app.callPrintThis`. |
| Row drilldowns | `.view-details` / `.viewConfirmation` / `.viewCashBookTrnsDetail` | `ts:146-167` | Open `rept_0015_002` / `_003` / `_004` layer popups. |

---

## 4. Validation rules

Backend (`rept_0015_001_r001_act.jsp:41-49`, `ValidationHelper`):

- `REF_DATE` — required.
- `ACTV_ACCT_ONLY_YN` — required **and** must be `Y`/`N` (`validateYesOrNo`).
- `INCL_INTL_TRNS_YN` — required **and** must be `Y`/`N`.
- `CCY_CD` — not validated (optional; empty = all).

Frontend performs no explicit pre-submit validation beyond the date picker default; checkbox values
are normalized to `Y`/`N` on click (`ts:169-187`).

---

## 5. Business rules

(Authoritative detail in [database-mapping.md](database-mapping.md) and the SQL quoted in
[interface-specification.md](interface-specification.md).)

1. **Tenant isolation.** Every query filters `biz_key = loginUser.bizKey` (`act.jsp:285-286, 336-337`).
2. **As-of single date.** `ref_date = REF_DATE`; `FROM_DT = TO_DT = REF_DATE` for the recompute
   freshness checks (`act.jsp:57-58`, etc.).
3. **Freshness recompute (read-time materialization).** Before reading, each summary table is
   compared to its source via `EXCEPT`/`UNION` (R002 `COUNT`). A non-zero count means the day's
   source data changed since last materialization → the day is deleted (D002) and re-inserted
   (C002). This makes the report self-healing without waiting for the nightly batch
   (`act.jsp:53-205`).
4. **Account-type partitioning** (`AcctTypeCd` enum): `01` Loan, `02` (Time) Deposit, `03`
   Current-local, `04` Current-USD, `05` Savings-local, `06` Savings-USD. The financial-status
   totals bucket by these codes (`SUMR_DETL_R003` CASE expressions).
5. **Currency filtering.** `CCY_CD = "FCY"` → `ccy_cd <> 'VND'`; a specific code → `ccy_cd = ?`;
   empty → no currency predicate (`act.jsp:296-305`). REC (currency totals via R003) additionally
   forces `ccy_cd <> 'VND'` in its inner `getDynamic0` (`act.jsp:342`) — local vs USD split is by
   currency downstream.
6. **Active-accounts filter.** `ACTV_ACCT_ONLY_YN = Y` adds a subquery `(bank_cd, acct_no) IN
   (SELECT … FROM acct_mstr WHERE biz_key = ? AND actv_yn = 'Y')` (`act.jsp:308-321`).
7. **Loan maturity guard.** Loan rows are included only when `expi_dt >= ref_date`
   (`act.jsp:288-291`) — matured loans drop off.
8. **Internal-transfer handling.** When `INCL_INTL_TRNS_YN = N`:
   - REC2 excludes account-sequences whose `RCV_PAY_USAG_CD ∈ {4000000008, 6000000062,
     4000000104, 6000000061}` (`act.jsp:258-271`).
   - REC3 (cash) excludes `use_usag_cd = 7000000061` (`act.jsp:368-371`).
   When `Y`, the internal debit/credit columns are also shown on screen (`ts:388-395`).
9. **Sorting.** Local currency first, USD last: `ORDER BY (ccy_cd <> 'USD') → 1 else 2`
   (`act.jsp:239-243`).
10. **Account nickname fallback.** When the stored nickname is blank, the summary computes
    `bank-short-name + last-4-of-account` (`SUMR_DETL_R001` SQL:
    `COALESCE(NULLIF(ACCT_NICK_NM,''), concat(wb_get_bank_nm(BANK_CD,'s'),' ',right(ACCT_NO,4)))`).
11. **Purpose-name resolution.** REC2_1 transaction rows resolve the receipt/payment purpose name
    via `wb_get_usag_nm(biz_key, code, LANG_CD)`; split transactions with sentinel usage
    `99999999999999999999` aggregate to "first-detail-name + 1" (`SUMR_DETL_R004` SQL).
12. **Running balance (cash).** The cash detail re-derives a running balance by summing prior +
    same-day-up-to-seq `CASE INOUT_DV WHEN '1' THEN +amt ELSE -amt END` (`CASH_SUMR_DETL_R002/C002`).
13. **Amount/number formatting** is client-side via `_osmc.ui.formatAmount(value, ccyCd)`; counts via
    `Math.trunc`. USD formats to 2 decimals, others to whole units in the Excel path
    (`rept_0015_001_excel.jsp:586-597`).
