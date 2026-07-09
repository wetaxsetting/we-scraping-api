# Daily Financial Report — Refactor Feature Spec (one-page stub)

> **Superseded in detail — see the full package.** This one-page stub is retained as the short
> feature-spec entry, but the **authoritative, implementation-accurate documentation** for Daily
> Financial lives in the package at [`daily-financial/`](README.md). When the stub
> and the package disagree, **the package wins** (it is reconciled against the actual code as of the
> 2026-06-04 P0 fix). Two drifts the stub originally carried are corrected below and noted here for
> traceability:
> 1. **Slug** — the canonical slug is **`daily-financial`** (route-based), not `daily-financial-report`. Audit `WSVC_ID` and View Setting `WEB_SVC_ID` (when seeded) both use `daily-financial`.
> 2. **Read strategy + params** — the implemented endpoint uses **`asOf` / `currencyScope`** (not `referenceDate` / `currency`) and **calls the legacy function `rept_0015_001_r001_rec1` directly** (via `DailyFinancialCmcFunctionRepository`, 2026-06-14); it does **not** read materialized summary tables (out of the PROD Active Table List) and no longer Java-recomputes ("Strategy B", superseded). See [README.md](README.md) + [migration-guide.md §3](migration-guide.md#3-target-spring-architecture).

| Field | Value |
|---|---|
| Slug | `daily-financial` (route-based; file is `daily-financial.md` to match the menu) |
| Module | Banking → Reports |
| Status | **Implemented — READY FOR SIT (in-PROD scope)** · full status: [daily-financial/README.md §5](README.md#5-sit-readiness-2026-06) |
| JEX twin | `rept_0015_001` (view) + `rept_0015_001_r001` (read API) |
| Refactor route | `/banking/reports/daily-financial` (Next.js) |
| Refactor base URI | `/api/v1/banking/reports/daily-financial` (Spring Boot) |
| Audit `WSVC_ID` | `daily-financial` (slug — never `rept_0015_001`) |
| View Setting `WEB_SVC_ID` | `daily-financial` (not yet seeded — couples with the server-side Excel provider, P1) |
| i18n key prefix | frontend `report.daily_financial.*` |

---

## 1. Summary

A deeper version of the Daily Cash Report: same as-of snapshot, but every account row can be expanded to its **per-transaction list** for that day (time, amount, running balance, in/out flag, remark, purpose code). Adds two filters not present on Daily Cash:

- **Active accounts only** (Y/N) — hide closed/inactive accounts.
- **Include internal transfers** (Y/N) — show own-account-transfer rows separately or roll them up.

Used by treasury / accounting for daily reconciliation; this is also the screen that drives the Excel report `WeCMS_Daily_Financial_Report_*.xlsx`.

### In scope (as implemented — see the package for the authoritative state)

- Search with **`asOf`** + **`currencyScope`** + `activeAccountsOnly` + `includeInternalTransfers` (the implemented param names; the stub's earlier `referenceDate`/`currency` is corrected).
- Financial Status per-currency totals (`REC`) + Current/Savings account rows (`REC1`) — read from the legacy function **`rept_0015_001_r001_rec1`** (which itself projects over `ACCT_MSTR`/`ACCT_TRNS_HSTR`); the prior Java live-compute ("Strategy B") was superseded 2026-06-14.
- Transaction-history drill-down **popup** (`rept_0015_002` → `GET …/daily-financial/transactions`).

### Out of scope / deferred (not gaps — see [refactor-gap-analysis.md](refactor-gap-analysis.md))

- **Materialized summary tables + nightly recompute** (`wb_insert_daily_fincl_*`, C002/D002) — *not consumed*; the refactor calls the read-only `rept_0015_001_r001_rec1` function directly, and the batch owns the writes. (The original stub's "consumes the materialized summary tables" line is corrected — those tables are not in the PROD Active Table List.)
- **Inline per-account transactions (REC2_1)** — empty in v1; the popup serves the drilldown (P1).
- **Server-side Excel provider** — frontend ships a client-side export; no backend `DailyFinancialExportProvider` yet (P1).
- **Cash book (REC3) + receipt/payment & cash-book detail popups (`rept_0015_003/004`)** + time-deposit/loan panels — out of PROD scope / commented out in legacy.
- Edit/annotate transactions (read-only report); multi-day range.

---

## 2. JEX source-of-truth citations

### View / Action JSP

- `WECMS DEV/WECMS/web/view/jex/wabooks/rept/rept_0015_001_view.jsp:101` — title, form (as-of date, currency, two checkboxes), grid placeholder.
- `WECMS DEV/WECMS/web/view/jex/wabooks/rept/rept_0015_001_view.jsp:115` — `REF_DATE` input.
- `WECMS DEV/WECMS/web/view/jex/wabooks/rept/rept_0015_001_view.jsp:117-129` — currency radio group.
- `WECMS DEV/WECMS/web/view/jex/wabooks/rept/rept_0015_001_view.jsp:137,140` — `ACTV_ACCT_ONLY_YN` + `INCL_INTL_TRNS_YN` checkboxes.
- `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/rept/rept_0015_001_r001_act.jsp:1-100` — orchestration; also triggers `DAILY_FINCL_REPT_DAILY_SUMR_DETL` and `DAILY_FINCL_REPT_DAILY_CASH_SUMR` update/recompute paths before reading.

### WSVC contract

- `WECMS DEV/WECMS_ETC/xml/service/WSVC/WSVC.rept_0015_001_r001.xml:32-114`
  - IN: `REF_DATE` (9, YYYYMMDD), `CCY_CD` (3, optional), `ACTV_ACCT_ONLY_YN` (1, Y/N), `INCL_INTL_TRNS_YN` (1, Y/N).
  - OUT:
    - `REC` — currency totals (TOTL_CASH, TOTL_NORM_AMT, TOTL_ACCT_AMT, TOTL_LOAN_AMT, TOTL_SAV_AMT, CCY_CD).
    - `REC1` — per-account flat (REF_DATE, CCY_CD, ACCT_TYPE_CD, BANK_CD, ACCT_NO, BAL, LOAN_NM, EXPI_DT, AGMT_AMT, SND/RCV CNT/AMT, INTL_RCV/SND, ACCT_NICK_NM, BANK_NM).
    - `REC2` — account with nested `REC2_1` transaction list.
    - `REC2_1` — transactions: ACCT_NO, ACCT_SEQ_NO, TRSC_TM, TRSC_AMT, BAL, INOUT_DV, RMRK, RCV_PAY_USAG_CD, RCV_PAY_USAG_NM.
    - `REC3` — currency rollup (BEF_DATE_BAL, BAL, SND_CNT/AMT, RCV_CNT/AMT).

### IDO queries

- `WECMS DEV/WECMS_ETC/xml/service/IDO/IDO.DAILY_FINCL_REPT_DAILY_ACCT_TRNS_HSTR_SUMR_R001.xml` .. `R004.xml`
- `WECMS DEV/WECMS_ETC/xml/service/IDO/IDO.DAILY_FINCL_REPT_DAILY_CASH_SUMR_*.xml` (C002, D002, R001..R004)
- `WECMS DEV/WECMS_ETC/xml/service/IDO/IDO.DAILY_FINCL_REPT_DAILY_SUMR_DETL_*.xml`

### Frontend TS

- `WECMS DEV/WECMS_STATIC/ts-src/js/jex/wabooks/rept/rept_0015_001.ts` — search handler, expand/collapse for transactions, Excel export.

### DB tables

- `DAILY_FINCL_REPT_DAILY_ACCT_TRNS_HSTR_SUMR` — daily per-account transaction summary.
- `DAILY_FINCL_REPT_DAILY_SUMR_DETL` — daily summary detail.
- `DAILY_FINCL_REPT_DAILY_CASH_SUMR` — daily cash summary.
- Joined to `ACCT_MSTR`, `BANK_INFM`, `ACCT_TRNS_HSTR` (for the `REC2_1` transaction rows), `ACCT_TRNS_RCV_PAY_INFM` (for purpose codes).

---

## 3. REST endpoint

### `GET /api/v1/banking/reports/daily-financial`

> **Implemented param names** (corrected from the stub's original `referenceDate`/`currency`). See [interface-specification.md §3](interface-specification.md) for the authoritative contract.

| Param | Type | Required | Notes |
|---|---|---|---|
| `asOf` | `String` (`yyyyMMdd`) | no, defaults to today | Maps to legacy `REF_DATE`. Drives the day aggregates + as-of balance. |
| `currencyScope` | `ALL` / `FCY` / `VND` | no, default `ALL` | `VND` = exactly VND; `FCY` = every currency ≠ VND. |
| `activeAccountsOnly` | `boolean` | no, default `true` | Maps to `ACTV_ACCT_ONLY_YN`. **`false` not yet honored** (active-only finder retained — P1/G-6). |
| `includeInternalTransfers` | `boolean` | no, default `true` | Maps to `INCL_INTL_TRNS_YN`. Splits internal columns; **exclusion-from-totals is display-only** in v1 (P1/G-4). |

`biz_key` from the JWT (`TenantContext.currentBizKey()`), never from the request.

### Response shape

```json
{
  "referenceDate": "2026-05-14",
  "currency": "ALL",
  "activeAccountsOnly": true,
  "includeInternalTransfers": true,
  "currencySummary": [ { "currencyCd": "VND", "totalCash": 0, "totalDemandDeposit": 0, "totalTermDeposit": 0, "totalLoan": 0, "totalSavings": 0 } ],
  "accounts": [
    {
      "bankCd": "11704011",
      "bankNm": "Vietcombank",
      "accountNo": "0011000xxxx",
      "accountNickNm": "Operating VND",
      "currencyCd": "VND",
      "accountTypeCd": "11",
      "balance": 0,
      "sendCount": 0, "sendAmount": 0,
      "receiveCount": 0, "receiveAmount": 0,
      "internalSendCount": 0, "internalSendAmount": 0,
      "internalReceiveCount": 0, "internalReceiveAmount": 0,
      "loanName": null, "maturityDate": null, "agreementAmount": null,
      "transactions": [
        {
          "accountSeqNo": 1,
          "transactionTime": "09:14:22",
          "amount": 1500000,
          "balanceAfter": 32500000,
          "inOutDiv": "IN",
          "remark": "Customer payment",
          "rcvPayUsageCd": "01",
          "rcvPayUsageNm": "Sales"
        }
      ]
    }
  ],
  "currencyFlow": [
    { "currencyCd": "VND", "beforeDateBalance": 0, "balance": 0, "sendCount": 0, "sendAmount": 0, "receiveCount": 0, "receiveAmount": 0 }
  ]
}
```

Note: `transactions` may be served inline (small days) or lazy-loaded per account via `GET /api/v1/banking/reports/daily-financial/accounts/{bankCd}/{accountNo}/transactions?referenceDate=...` to keep the initial payload bounded — TBD by perf testing.

### Errors

- `400 WBE_VALIDATION` — bad date / enum.
- `403 WBE0401` — no permission.
- `409 WBE_BUSY` — concurrent recompute in progress (if the refactor owns the recompute path).

---

## 4. Frontend page

`wecms-web/app/(authenticated)/banking/reports/daily-financial/page.tsx`

- Filter bar: date + currency radio + two checkboxes.
- Grid: collapsible rows. Top level = accounts; click to expand the transaction sub-grid. Use the typography tokens `text-table-header` / `text-table-cell`.
- Footer: currency flow rollup.
- Export: `/api/v1/banking/reports/daily-financial/export.xlsx`, template = `WeCMS_Daily_Financial_Report_<lang>.xlsx`.

i18n: `messages/{en,ko,vi}/reports/daily_financial.json`.

---

## 5. Backend skeleton

```
wecms-api/src/main/java/com/wecms/reports/dailyfinancial/
├── DailyFinancialReportController.java
├── DailyFinancialReportService.java
├── DailyFinancialReportRepository.java
├── DailyFinancialRecomputeService.java   // optional — wraps the legacy C002/D002 update calls
└── dto/
    ├── DailyFinancialReportRequest.java
    ├── DailyFinancialReportResponse.java
    ├── AccountWithTransactionsDto.java
    └── TransactionRowDto.java
```

Audit: `wsvc_id='daily-financial-report'` on search and export.

---

## 6. Open questions

- Does the refactor recompute summaries (`DAILY_FINCL_REPT_DAILY_CASH_SUMR_C002 / D002`) or just read? **Default: read-only; batch owns the writes.** Needs PO call.
- Performance: for accounts with > 500 transactions/day, lazy-load vs inline? Decide after measuring on the Haengsung dataset.
- `RCV_PAY_USAG_CD` — confirm the code list comes from `comm_0218_001` (Receipt/Payment Purpose) so we can reuse that lookup hook.
