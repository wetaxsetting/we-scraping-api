# Daily Cash — Interface Specification

API IDs, request/response structures, the Excel POST contract, and the refactor REST contract.

---

## 1. Read API — `rept_0009_001_r001`

- **Type:** WSVC, `trTp=U`, `login=Y`, audit-logged (`mntLog=Y`). `NORM_USER_ONLY_YN=Y`.
- **Transport:** `jexAjax.plugin("ajax","rept_0009_001_r001")`, suffix `.jct`, **synchronous**.
- **Action:** `rept_0009_001_r001_act.jsp`. **WSVC:** `WSVC.rept_0009_001_r001.xml`.

### Request (IN)
| Field | Len | Required | Meaning |
|---|---|---|---|
| `REQ_DATE` | 8 | ✓ | as-of date `yyyyMMdd` |
| `CCY_CD` | 3 | | `""`=All · `FCY` · `<local>` (`VND`) |
| `INQ_DV` | 4 | | inquiry division — **deprecated**, ignored by action |

### Response (OUT)
```jsonc
{
  "COMMON_HEAD": { "ERROR": false, "CODE": "...", "MESSAGE": "..." },
  "REC":  [ { "CCY_CD":"VND", "TOTL_CASH":"...", "TOTL_NORM_AMT":"...",
              "TOTL_ACCT_AMT":"...", "TOTL_LOAN_AMT":"...", "TOTL_SAV_AMT":"..." }, ... ],
  "REC1": [ { "BIZ_KEY":"...", "LST_COLL_DATE":"...", "REF_DATE":"...", "CCY_CD":"...",
              "ACCT_TYPE_CD":"03", "BANK_CD":"...", "BANK_NM":"...", "ACCT_NO":"...",
              "BAL":"...", "LOAN_NM":"...", "EXPI_DT":"...", "AGMT_AMT":"...",
              "SND_CNT":"...", "SND_AMT":"...", "RCV_CNT":"...", "RCV_AMT":"..." }, ... ],
  "REC2": [ { "BIZ_KEY":"...", "LST_COLL_DATE":"...", "REF_DATE":"...", "CCY_CD":"...",
              "BEF_DATE_BAL":"...", "BAL":"...", "SND_CNT":"...", "SND_AMT":"...",
              "RCV_CNT":"...", "RCV_AMT":"..." }, ... ]
  // plus legacy scalar TOTL_{USD,KHR}_* fields (declared in WSVC; not populated by current action)
}
```
- `REC` = per-currency summary · `REC1` = account detail · `REC2` = cash flow.
- Error path: `COMMON_HEAD.CODE == "REPT_DIF_DATA"` triggers the (dead) `jConfirm` re-aggregate flow.

---

## 2. Last-collection API — `rept_0100_001_r001`

- **Action:** `rept_0100_001_r001_act.jsp`. **WSVC:** `WSVC.rept_0100_001_r001.xml`.

### Request (IN)
| Field | Len | Required | Meaning |
|---|---|---|---|
| `BAT_ID` | 50 | ✓ | report batch id — Daily Cash passes `REPT_0900_001` |

### Response (OUT)
| Field | Len | Meaning |
|---|---|---|
| `LST_COLL_DATE` | 14 | `max(JOB_DATE || JOB_END_TM)` for that `BAT_ID`+`biz_key`, normalised to the user's time zone |

The action switches on `ReptBatId` (`REPT_0200..0900_001`) but all branches run the same
`BAT_CALL_HIS_R002` lookup.

---

## 3. Excel export (legacy) — JSP POST

- **No JSON API.** The TS POSTs a hidden form to a JSP that streams the workbook.
- **Form:** `#excelJsonObjectForm`, `method=post`, `target=rept_0009_001_excel` (hidden iframe).
- **Action JSP:** `rept_0009_001_excel_osmc_vn.jsp` (VN) / `rept_0009_001_excel_osmc.jsp` (non-VN),
  selected by `WABOOKSDynamicDataPool.getCurrentNtnlCd()`.
- **Field:** `excelJsonObject` = `encodeURI(JSON.stringify(payload))`.

### Payload shape (`Drept_0009_001_excelDataJsonOSMC`)
```jsonc
{
  "REQ_DATE": "yyyyMMdd", "CCY_CD": "" | "FCY" | "VND", "INQ_DV": "0",
  "data": { "REC": [...], "REC1": [...], "REC2": [...] },   // the read-API response
  "totalKhr": <number>, "totalUsd": <number>,
  "lastUpdate": "<last-collection text>",
  "noTransaction": "<i18n>", "noAccount": "<i18n>",
  "dictionary": { "CURRENCY":"...", "BANK":"...", "ACCT_NO":"...", "BALANCE":"...",
                  "CURRENT_ACCOUNTS":"...", "SAVINGS_ACCOUNTS":"...", "DAILY_CASH_REPORT":"...",
                  "AS_OF":"...", "TOTAL":"...", "ALL":"...", /* ...full Excel_MultiLanguages_Object */ }
}
```
- **Output:** `application/octet-stream`, `Content-Disposition: attachment; filename=Daily Cash_<yyyyMMddHHmm>.xlsx`.
- **Workbook:** title row → "As of … / Currency: …" → per-currency `● Total <CCY> : …` band →
  Current Accounts section (+totals) → Savings Accounts section (+totals). Cash / Time-Deposit /
  Loan sections are commented out. Amount formats: `#,##0.00` (USD), `#,###` (VND).

---

## 4. Refactor REST contract (Spring Boot)

> **Data source (2026-06-04):** the request params and response shape below are **unchanged**, but
> the backend now builds the response by **live recompute** over `ACCT_MSTR` + `ACCT_TRNS_HSTR`
> (`DailyCashRecomputeRepository.findAccountsAsOf` = legacy `rept_0900_001_rec1`) + Java summary
> (`rec2`), **not** from the batch snapshot `DAILY_CASH_REPT_DAILY_SUMR_DETL`. Behavioural effect: a
> selected date returns accounts even when no snapshot exists (e.g. 2026-06-04). `total_cash` is
> always `0` (legacy `rec3/rec4` source `CASH_REPY_MSTR` is out of refactor scope);
> `last_collected_date` is `null` on this path. The frontend summary block always renders after
> Search (zero **VND/USD/KRW** fallback when summary is empty); detail tables have no checkbox column.

### Search — `GET /api/v1/banking/reports/daily-cash-report`
> Controller `DailyCashReportController` maps `/api/v1/banking/reports/daily-cash-report`.
> Note: `wecms-web`'s older `lib/api/banking.ts fetchDailyCash()` references
> `/api/v1/banking/reports/daily-cash`; the active client `fetchDailyCashReport()` calls
> `/daily-cash-report`. **Open Question** — confirm whether the `/daily-cash` route is a dead
> duplicate (`ReportController.dailyCash`) and retire it.

**Query params**
| Param | Required | Rule |
|---|---|---|
| `asOfDate` | ✓ | ISO `yyyy-MM-dd` (`@Pattern`); converted to `yyyyMMdd` `REF_DATE` |
| `currency` | | `ALL` / `VND` / `FCY` / `USD` / `KHR`; null/blank → `ALL`; unknown → `ALL` |

`biz_key` is taken from `TenantContext` (JWT), never the wire.

**Response (`DailyCashReportResponse`, wire = lowercase snake_case)**
```jsonc
{
  "status": { "error": false, "code": "WBI0000", "message": "Success" },
  "data": {
    "as_of_date": "2026-06-03",
    "currency": "ALL",
    "currency_summary": [
      { "currency_cd":"VND", "total_cash":"0", "total_demand_deposit":"...",
        "total_term_deposit":"...", "total_loan":"...", "total_savings":"..." }
    ],
    "accounts": [
      { "biz_key":"...", "last_collected_date":"...", "ref_date":"20260603",
        "currency_cd":"VND", "acct_type_cd":"03", "bank_cd":"...", "bank_nm":"...",
        "acct_no":"...", "balance":"...", "loan_name":null, "maturity_date":null,
        "agreement_amount":null, "send_count":"...", "send_amount":"...",
        "receive_count":"...", "receive_amount":"..." }
    ],
    "currency_flow": [
      { "currency_cd":"VND", "before_date_balance":"...", "balance":"...",
        "send_count":"...", "send_amount":"...", "receive_count":"...", "receive_amount":"..." }
    ]
  }
}
```

**Field mapping (legacy → refactor)**
| Legacy | Refactor |
|---|---|
| `REC.TOTL_CASH/NORM/SAV/ACCT/LOAN_AMT` | `currency_summary.total_cash/demand_deposit/savings/term_deposit/loan` |
| `REC1.*` | `accounts[*]` (snake_case rename; `LOAN_NM→loan_name`, `EXPI_DT→maturity_date`, `AGMT_AMT→agreement_amount`, `SND_*→send_*`, `RCV_*→receive_*`) |
| `REC2.*` (cash flow) | `currency_flow[*]` (`BEF_DATE_BAL→before_date_balance`) |
| `LST_COLL_DATE` | `accounts[*].last_collected_date` (per-row; no `BAT_CALL_HIS` lookup) |
| `INQ_DV` | dropped |

### Export — `POST /api/v1/banking/reports/daily-cash-report/export`
- Body: shared `ExcelExportRequest` (`screenId=daily-cash-report`, `dymAreaId`, `fileName`,
  `filters`, `sort`, `columns`).
- Provider `DailyCashReportExportProvider`: allowed FIELD_IDs `BANK, ACCT_NO, BALANCE, LOAN_NAME,
  MATURITY_DATE, SEND_COUNT, SEND_AMOUNT, RECEIVE_COUNT, RECEIVE_AMOUNT`. Reuses
  `DailyCashReportService.search`. Streams XLSX via shared POI pipeline.
- ⚠️ **The Next.js UI does not call this endpoint** — it builds the workbook client-side with
  `xlsx-js-style` (`lib/dailyCashExport.ts`). See [`refactor-gap-analysis.md`](./refactor-gap-analysis.md) gap #2.

---

## 5. Error / status codes

| Layer | Code | Meaning |
|---|---|---|
| Legacy | `REPT_DIF_DATA` | summary differs/missing → re-aggregate confirm (dead path) |
| Legacy | `WBC1105` | confirm-dialog message id |
| Refactor | `WBE_VALIDATION` | bad `asOfDate` (pattern) |
| Refactor | `WBE2010–2013` | export config / row-cap / no-columns (shared export pipeline) |
| Both | `WBI0000` | success |
