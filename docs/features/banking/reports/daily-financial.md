# Daily Financial Report — Refactor Feature Spec

| Field | Value |
|---|---|
| Slug | `daily-financial-report` (runtime id; file renamed to `daily-financial.md` to match the menu) |
| Module | Banking → Reports |
| Status | Planning |
| JEX twin | `rept_0015_001` (view) + `rept_0015_001_r001` (read API) |
| Refactor route | `/banking/reports/daily-financial` (Next.js) |
| Refactor base URI | `/api/v1/reports/daily-financial` (Spring Boot) |
| Audit `WSVC_ID` | `daily-financial-report` (slug — never `rept_0015_001`) |
| View Setting `WEB_SVC_ID` | `daily-financial-report` |
| i18n key prefix | `reports.daily_financial.*` |

---

## 1. Summary

A deeper version of the Daily Cash Report: same as-of snapshot, but every account row can be expanded to its **per-transaction list** for that day (time, amount, running balance, in/out flag, remark, purpose code). Adds two filters not present on Daily Cash:

- **Active accounts only** (Y/N) — hide closed/inactive accounts.
- **Include internal transfers** (Y/N) — show own-account-transfer rows separately or roll them up.

Used by treasury / accounting for daily reconciliation; this is also the screen that drives the Excel report `WeCMS_Daily_Financial_Report_*.xlsx`.

### In scope

- Search with `referenceDate` + `currency` + `activeAccountsOnly` + `includeInternalTransfers`.
- Four-tier response: currency summary (`REC`), account list (`REC1`), account with nested transactions (`REC2` → `REC2_1`), currency flow rollup (`REC3`).
- Excel export bound to the existing template.
- View Setting on the account grid and the transaction sub-grid.
- Audit log on search and export.

### Out of scope

- Owning the nightly summary recompute (`d001`/`c002` operations the JEX action triggers) — the refactor consumes the materialized summary tables; the recompute job migrates as a separate feature.
- Edit/annotate transactions (read-only report).
- Multi-day range.

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

### `GET /api/v1/reports/daily-financial`

| Param | Type | Required | Notes |
|---|---|---|---|
| `referenceDate` | `LocalDate` | yes | Maps to `REF_DATE` (YYYYMMDD). |
| `currency` | enum `ALL` / `FCY` / `VND` / `USD` / `KHR` | no, default `ALL` | Same semantics as Daily Cash. |
| `activeAccountsOnly` | `boolean` | no, default `true` | Maps to `ACTV_ACCT_ONLY_YN`. |
| `includeInternalTransfers` | `boolean` | no, default `true` | Maps to `INCL_INTL_TRNS_YN`. |

`biz_key` from session.

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

Note: `transactions` may be served inline (small days) or lazy-loaded per account via `GET /api/v1/reports/daily-financial/accounts/{bankCd}/{accountNo}/transactions?referenceDate=...` to keep the initial payload bounded — TBD by perf testing.

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
- Export: `/api/v1/reports/daily-financial/export.xlsx`, template = `WeCMS_Daily_Financial_Report_<lang>.xlsx`.

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
