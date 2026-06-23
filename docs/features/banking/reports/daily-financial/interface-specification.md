# Daily Financial — Interface Specification

> **Casing migrated 2026-06-07.** `DailyFinancialCmcReport`,
> `DailyFinancialHsReport`, and `DailyFinancialTransactionsReport` no longer
> carry UPPER_SNAKE `@JsonProperty` — wire JSON is now **lowercase snake_case**
> via the global Jackson SNAKE_CASE strategy. A few fields follow the camelCase
> Java field rather than the legacy token (`balance` not `bal`,
> `acct_nickname` not `acct_nick_nm`, nested-tx `transaction_time` / `amount` /
> `balance_after` / `in_out_div`). UPPER tokens below are historical. See
> [field-casing-parity-audit-2026-06-06.md](../../../../audits/field-casing-parity-audit-2026-06-06.md).

Contracts from `WSVC.rept_0015_001_r001.xml`, the action JSP, and the TS renderer. Refactor contract
from `ReportController` / `DailyFinancialCmcReport` / `DailyFinancialTransactionsReport`.

---

## 1. Legacy request (read API `rept_0015_001_r001`)

Transport: `jexjs.plugin("ajax", 'rept_0015_001_r001')` with `.jct` suffix, **synchronous**
(`rept_0015_001.ts:295-298`). Body keys are `UPPER_SNAKE`.

```jsonc
{
  "REF_DATE": "20260604",          // YYYYMMDD, required
  "CCY_CD": "",                     // "" all | "FCY" | "VND" | "USD" ...
  "ACTV_ACCT_ONLY_YN": "Y",         // Y/N, required
  "INCL_INTL_TRNS_YN": "Y"          // Y/N, required
}
```

## 2. Legacy response

Envelope: JEX `JexData` → `{ COMMON_HEAD: { ERROR, CODE, MESSAGE }, REC, REC1, REC2, REC3 }`.
The four record groups (from `WSVC.rept_0015_001_r001.xml` OUT + `subRule`):

### `REC` — currency totals (per currency row)

| Field | Len | Meaning |
|---|---|---|
| `CCY_CD` | 3 | currency |
| `TOTL_NORM_AMT` | 20 | current-account total |
| `TOTL_SAV_AMT` | 20 | savings total |
| `TOTL_ACCT_AMT` | 20 | time-deposit total |
| `TOTL_LOAN_AMT` | 20 | loan total |
| `TOTL_CASH` | 20 | cash total (subquery to CASH_SUMR) |

### `REC1` — flat per-account rows

`REF_DATE(9)`, `CCY_CD(3)`, `ACCT_TYPE_CD(2)`, `BANK_CD(20)`, `ACCT_NO(100)`, `BAL(20)`,
`LOAN_NM(100)`, `EXPI_DT(8)`, `AGMT_AMT(20)`, `SND_CNT(20)`, `SND_AMT(20)`, `RCV_CNT(20)`,
`RCV_AMT(20)`, `BANK_NM(2000)`, `INTL_RCV_AMT(20)`, `INTL_RCV_CNT(10)`, `INTL_SND_AMT(20)`,
`INTL_SND_CNT(10)`, `ACCT_NICK_NM(50)`.

### `REC2` — account with nested transactions

Account-level: `BANK_CD(20)`, `ACCT_NO(100)`, `CCY_CD(3)`, `ACCT_NICK_NM(50)`, `BANK_NM(2000)`,
`SND_CNT/SND_AMT/RCV_CNT/RCV_AMT(20)`, `INTL_RCV_AMT/INTL_RCV_CNT/INTL_SND_AMT(20)`,
`INTL_SND_CNT(10)`, plus **`REC2_1`** (RECORD, delivered as a JSON string the TS parses —
`ts:778 JSON.parse(val.REC2_1)`).

`REC2_1` element (one per transaction): `REF_DATE(9)`, `BANK_CD(20)`, `ACCT_NO(100)`,
`ACCT_SEQ_NO(20)`, `TRSC_TM(6)`, `TRSC_AMT(20)`, `BAL(20)`, `INOUT_DV(1)`, `RMRK(50)`,
`RCV_PAY_USAG_CD(20)`, `RCV_PAY_USAG_NM(100)`.

### `REC3` — cash rows (one per currency)

`REF_DATE(9)`, `CCY_CD(3)`, `BEF_DATE_BAL(20)`, `BAL(20)`, and the action splits the `OBJECT` JSON
(`act.jsp:523-531`) into `SND_CNT(20)`, `SND_AMT(20)`, `RCV_CNT(20)`, `RCV_AMT(20)`.

---

## 3. Refactor request (Spring)

`GET /api/v1/banking/reports/daily-financial` (`ReportController.java:29-36`).

| Wire param | Maps to legacy | Notes |
|---|---|---|
| `asOf` | `REF_DATE` | optional in controller; service defaults |
| `currencyScope` | `CCY_CD` | enum-style scope (`ALL`/`FCY`/`VND`/`USD`/…) |
| `activeAccountsOnly` | `ACTV_ACCT_ONLY_YN` | boolean, default `true` |
| `includeInternalTransfers` | `INCL_INTL_TRNS_YN` | boolean, default `true` |

> **Naming divergence to reconcile:** the planning stub [`../daily-financial.md`](legacy-spec.md)
> documents `referenceDate` + `currency`; the implemented controller uses `asOf` + `currencyScope`.
> Flag for alignment (see [refactor-gap-analysis.md](refactor-gap-analysis.md)).

Transaction popup: `GET /api/v1/banking/reports/daily-financial/transactions`
(`ReportController.java:43-55`) — params `asOf`, `bankCd`, `acctNo`, `includeInternalTransfers`,
`page` (default 1), `size` (default 15).

## 4. Refactor response (Spring)

Envelope: `{ status, data }` (lowercase snake_case globally — `JacksonConfig`). `DailyFinancialCmcReport`
shape (camelCase Java → snake wire):

```jsonc
{
  "status": { "error": false, "code": "WBI0000", "message": "Success" },
  "data": {
    "as_of": "20260604",
    "currency_scope": "ALL",
    "active_accounts_only": true,
    "include_internal_transfers": true,
    "financial_status": [ { "currency_cd": "VND", "current_total": "0", "savings_total": "0", ... } ],
    "current_accounts": [
      {
        "bank_cd": "...", "bank_nm": "...", "acct_no": "...", "acct_nick_nm": "...",
        "ccy_cd": "VND", "acct_type_cd": "03", "bal": "0",
        "snd_cnt": "0", "snd_amt": "0", "rcv_cnt": "0", "rcv_amt": "0",
        "transactions": []        // ← REC2_1 equivalent: EMPTY skeleton (Sprint 2)
      }
    ]
    // REC3 currency-flow / cash: intentionally absent (out of PROD scope)
  }
}
```

Transaction popup response (`DailyFinancialTransactionsReport`): `TOT_CNT`, `TOT_IN_AMT`,
`TOT_OUT_AMT`, `TOT_BAL`, `PAGE_NO`, `PAGE_PER_CNT`, and `REC[]` of `{ TRSC_TM, CCY_CD, TRSC_AMT, BAL,
INOUT_DV, RMRK }` (`ReportService.dailyFinancialTransactions`). This popup **is** wired to real
`ACCT_TRNS_HSTR` data.

> **Transaction History Details popup — UI READY FOR SIT (2026-06).** The frontend
> (`TransactionHistoryDialog.tsx`) is restyled to the **e-Invoice Latest Inquiry History** popup:
> columns **Time · Debit · Credit · Balance · Remarks** (numeric right-aligned; Debit/Credit bound by
> `INOUT_DV` — `"2"`=debit, `"1"`=credit), bordered table + sticky header, centered empty-state,
> footer summary `Total {N} transaction(s) / Total Debit: … Total Credit: … Balance: …`. Pagination:
> **default page size 10**, options **10 / 20 / 50 / 100**, reset to page 1 on size change, dropdown
> opens upward (no overflow). The mapper (`mapDailyFinancialTransactionsResponse`) binds
> `transactionTime ← TRSC_TM` 1:1; when `TRSC_TM` is empty in source the **Time cell is blank by
> design** (verified — not a defect;
> [README.md §6](README.md#6-time-column-verification-transaction-history-details-popup)).

---

## 5. Key SQL (verbatim)

### `DAILY_FINCL_REPT_DAILY_SUMR_DETL_R001` (REC1)

```sql
SELECT T.BIZ_KEY, T.REF_DATE, T.CCY_CD, T.ACCT_TYPE_CD, T.BANK_CD, T.BANK_NM, T.ACCT_NO, T.BAL,
       T.ACCT_NICK_NM, T.LOAN_NM, T.EXPI_DT, T.AGMT_AMT,
       T.SND_CNT, T.SND_AMT, T.RCV_CNT, T.RCV_AMT,
       T.INTL_RCV_AMT, T.INTL_RCV_CNT, T.INTL_SND_AMT, T.INTL_SND_CNT
FROM (
  SELECT BIZ_KEY, REF_DATE, CCY_CD, ACCT_TYPE_CD, BANK_CD, BANK_NM, ACCT_NO, BAL,
         COALESCE(CASE ACCT_NICK_NM WHEN '' THEN NULL ELSE ACCT_NICK_NM END,
                  concat(wb_get_bank_nm(BANK_CD,'s'), ' ', right(ACCT_NO,'4'))) AS ACCT_NICK_NM,
         coalesce(LOAN_NM,'') AS LOAN_NM, coalesce(EXPI_DT,'') AS EXPI_DT, AGMT_AMT,
         SND_CNT, SND_AMT, RCV_CNT, RCV_AMT,
         INTL_RCV_AMT, INTL_RCV_CNT, INTL_SND_AMT, INTL_SND_CNT
  FROM DAILY_FINCL_REPT_DAILY_SUMR_DETL
  WHERE 1=1 ??            -- DYNAMIC_0 (getDynamicTotInfo: biz_key, ref_date, ccy, active subquery, loan maturity)
) T
WHERE 1=1 ??              -- DYNAMIC_1 (getDynamicForSort: ORDER BY ccy_cd<>'USD')
```

### `DAILY_FINCL_REPT_DAILY_SUMR_DETL_R003` (REC currency totals)

```sql
SELECT T.BIZ_KEY, T.REF_DATE, T.CCY_CD, T.TOTL_NORM_AMT, T.TOTL_SAV_AMT, T.TOTL_ACCT_AMT, T.TOTL_LOAN_AMT,
  (SELECT BAL FROM DAILY_FINCL_REPT_DAILY_CASH_SUMR CASH
    WHERE CASH.BIZ_KEY = T.BIZ_KEY AND CASH.REF_DATE = T.REF_DATE AND CASH.CCY_CD = T.CCY_CD) AS TOTL_CASH
FROM (
  SELECT BIZ_KEY, REF_DATE, CCY_CD,
    sum(CASE WHEN ACCT_TYPE_CD IN ('03','04') THEN BAL ELSE 0 END) TOTL_NORM_AMT,
    sum(CASE WHEN ACCT_TYPE_CD IN ('05','06') THEN BAL ELSE 0 END) TOTL_SAV_AMT,
    sum(CASE WHEN ACCT_TYPE_CD = '02' THEN BAL ELSE 0 END) TOTL_ACCT_AMT,
    sum(CASE WHEN ACCT_TYPE_CD = '01' THEN BAL ELSE 0 END) TOTL_LOAN_AMT
  FROM DAILY_FINCL_REPT_DAILY_SUMR_DETL DFRDSD
  WHERE 1=1 ??
  GROUP BY BIZ_KEY, REF_DATE, CCY_CD ORDER BY REF_DATE, CCY_CD
) T
WHERE 1=1 ??
```

### `DAILY_FINCL_REPT_DAILY_SUMR_DETL_R004` (REC2 + nested REC2_1)

The nested-transaction column is a correlated `json_agg(json_build_object(...))` over
`DAILY_FINCL_REPT_DAILY_ACCT_TRNS_HSTR_SUMR`, LEFT-joined to a purpose-name subquery over
`DAILY_FINCL_REPT_DAILY_TRNS_RCV_PAY_HSTR_SUMR` (resolving `wb_get_usag_nm(BIZ_KEY, code, :LANG_CD)`;
split transactions with sentinel usage `99999999999999999999` collapse to "first-name + 1"). Full
body in [source-code-index.md](source-code-index.md). Output column `REC2_1` is the JSON string.

### `DAILY_FINCL_REPT_DAILY_CASH_SUMR_R003` (REC3)

```sql
SELECT REF_DATE, CCY_CD, BEF_DATE_BAL, BAL,
  (SELECT json_build_object(
      'SND_CNT', coalesce(sum(CASE WHEN INOUT_DV='2' THEN 1 ELSE 0 END),0),
      'RCV_CNT', coalesce(sum(CASE WHEN INOUT_DV='1' THEN 1 ELSE 0 END),0),
      'SND_AMT', coalesce(sum(CASE WHEN INOUT_DV='2' THEN TRSC_AMT ELSE 0 END),0),
      'RCV_AMT', coalesce(sum(CASE WHEN INOUT_DV='1' THEN TRSC_AMT ELSE 0 END),0))
   FROM DAILY_FINCL_REPT_DAILY_CASH_SUMR_DETL
   WHERE BIZ_KEY=SUMR.BIZ_KEY AND REF_DATE=SUMR.REF_DATE AND CCY_CD=SUMR.CCY_CD ?? ) AS OBJECT
FROM DAILY_FINCL_REPT_DAILY_CASH_SUMR SUMR WHERE 1=1 ??
```

---

## 6. Grid JSON / client shapes

- `REC2_1` is delivered as a **JSON string** inside each REC2 row; the TS does
  `JSON.parse(val.REC2_1)` (`ts:778`) then splits into debit/credit columns by `INOUT_DV`
  (`'2'` = debit/send, `'1'` = credit/receive — `ts:807,823`).
- Per-row payloads are base64-encoded into `data-json` for popups
  (`common.app.encodeJsonToBase64`, decoded on click — `ts:147,163`).

## 7. Excel export structure (`rept_0015_001_excel.jsp`, 4,508 lines, Apache POI XSSF)

- **Input:** the hidden form posts `excelJsonObject` = `encodeURI(JSON.stringify({ REF_DATE, CCY_CD,
  ACTV_ACCT_ONLY_YN, INCL_INTL_TRNS_YN, SHOW_ACCT_NICK_NAME, DICTIONARY, FINANCIAL_STATUS }))`
  (`ts:250-263`). `DICTIONARY` carries the localized headers; `FINANCIAL_STATUS` is scraped from the
  on-screen totals table.
- **Server:** re-runs the same IDOs (`getRec1` R001, `getRec2` R004, `getRec3` CASH_SUMR_R003,
  `getTotInfo` R003, `getRowsTotalCashInfo` CASH_SUMR_R001 — `excel.jsp:308-399`) and builds an
  `.xlsx` workbook: title row, currency-bucket header (Current / Savings; cash/time-deposit/loan
  columns commented out — `excel.jsp:757-820`), and per-account / per-transaction sheets. Two layout
  variants — with vs without internal-transfer columns (`getHeaderWithInternalTranfer`,
  `createSavingAccountOrCurrentAccoutWithInternalTranfersHeaderTable`).
- **Output:** streamed `application/octet-stream` attachment with UA-aware `Content-Disposition`
  (`excel.jsp:403-433`).
- **Refactor target:** the shared `common/export/` pipeline (a `DailyFinancialExportProvider` +
  `POST /api/v1/banking/reports/daily-financial/export`) — **not yet built** (see gap analysis).
