# Daily Cash Report — Refactor Feature Spec

| Field | Value |
|---|---|
| Slug | `daily-cash-report` (runtime id; file renamed to `daily-cash.md` to match the menu) |
| Module | Banking → Reports |
| Status | Planning |
| JEX twin | `rept_0009_001` (view) + `rept_0009_001_r001` (read API) |
| Refactor route | `/banking/reports/daily-cash` (Next.js, `(dashboard)` group) |
| Refactor base URI | `/api/v1/banking/reports/daily-cash-report` (Spring Boot) |
| Audit `WSVC_ID` | `daily-cash-report` (slug — never `rept_0009_001`) |
| View Setting `WEB_SVC_ID` | `daily-cash-report` |
| i18n key prefix | `reports.daily_cash.*` |

---

## 1. Summary

Snapshot of every bank account balance and same-day flow on a chosen reference date, grouped by currency and account type. The user picks **As of date** + **Currency** (All / FCY / VND) and clicks **Search**. The report renders:

- Currency-level totals (cash, demand-deposit, term-deposit/savings, loan, sub-balance).
- Per-account rows: bank, account no, balance, today's outflow count/amount (SND), inflow count/amount (RCV), internal-transfer count/amount, and (for loan/deposit accounts) the title and maturity date.
- Currency rollup row (opening balance, debits, credits, closing balance).

### In scope

- Search with `as_of_date` + `currency` filter.
- Three-tier response: currency summary (`REC`) → per-account detail (`REC1`) → currency flow rollup (`REC2`).
- Excel export reusing the shared export pipeline.
- View Setting (column visibility / order) for the per-account grid.
- Audit log on search and export.

### Out of scope

- Real-time refresh (report runs against the daily summary tables — server-side recompute is JEX's job, not ours).
- Drill-down to per-transaction detail (lives in Daily Financial Report — see [daily-financial.md](daily-financial.md)).
- Multi-day range (single as-of date only — matches JEX).

---

## 2. JEX source-of-truth citations

> Read-only. Functional spec, not code to copy.

### View / Action JSP

- `WECMS DEV/WECMS/web/view/jex/wabooks/rept/rept_0009_001_view_osmc.jsp:129` — title + form (as-of date input, currency radio All/FCY/VND).
- `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/rept/rept_0009_001_r001_act.jsp:60-105` — orchestrates four reads (`getRowsForRec1..4`) and stitches them into REC/REC1/REC2.
- `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/rept/rept_0009_001_r001_act.jsp:115-124` — currency filter logic: empty → all; `FCY` → not VND; else exact match.

### WSVC contract

- `WECMS DEV/WECMS_ETC/xml/service/WSVC/WSVC.rept_0009_001_r001.xml:32-92`
  - IN: `REQ_DATE` (8, YYYYMMDD), `CCY_CD` (3, optional — `""` / `FCY` / `VND` / `USD` / `KHR`), `INQ_DV` (4, optional inquiry division).
  - OUT: `REC` (currency totals), `REC1` (per-account list), `REC2` (currency flow rollup), plus scalar totals per currency (USD_CASH, USD_NORM_AMT, USD_ACCT_AMT, USD_LOAN_AMT, USD_SAV_AMT, KHR_* counterparts).

### IDO queries / DAOs

- `WECMS DEV/WECMS_ETC/xml/service/IDO/IDO.DAILY_CASH_REPT_DAILY_SUMR_R003.xml` .. `R007.xml` — five read IDOs against the summary tables.
- `WECMS DEV/WECMS/src/wabooks/dao/DailyCashReptDailySumrDAO.java`
- `WECMS DEV/WECMS/src/wabooks/dao/DailyCashReptDailySumrDetlDAO.java`
- `WECMS DEV/WECMS/src/wabooks/dao/DailyCashReptDailyCashSumrDetlDAO.java`

### Frontend TS

- `WECMS DEV/WECMS_STATIC/ts-src/js/jex/wabooks/rept/rept_0009_001_osmc.ts:41-72` — i18n dictionary (CURRENCY, OPENING_BALANCE, DEBITS, CREDITS, BALANCE, BANK, ACCOUNT_NO, LOAN_TITLE, MATURITY_DATE, DEPOSIT_TITLE, SAVINGS_ACCOUNTS, TIME_DEPOSITS, LOAN_BORROWING). Lines 1–40 are type/interface declarations.

### DB tables

- `DAILY_CASH_REPT_DAILY_SUMR` — currency-level daily summary.
- `DAILY_CASH_REPT_DAILY_SUMR_DETL` — per-account daily flow detail.
- `DAILY_CASH_REPT_DAILY_CASH_SUMR_DETL` — cash-account daily detail.
- Joined to `ACCT_MSTR`, `BANK_INFM` for nick names / bank names / account types.

---

## 3. REST endpoint

### `GET /api/v1/banking/reports/daily-cash-report`

> Implementation note (2026-05-14): the original spec proposed `/api/v1/reports/daily-cash` but the actual codebase nests every banking-domain endpoint under `/api/v1/banking/...` per `wecms-api/CLAUDE.md`. The endpoint also collides with the legacy stub `GET /api/v1/banking/reports/daily-cash` (kept in place until consumers migrate), so the refactor slug appears in the path as `/daily-cash-report`. Wire shape is lowercase snake_case via the global Jackson naming strategy — the JSON example below shows the camelCase Java DTO field names; on the wire they serialise as `as_of_date`, `currency_summary`, `total_cash`, etc.

| Param | Type | Required | Notes |
|---|---|---|---|
| `asOfDate` | `LocalDate` (ISO `yyyy-MM-dd`) | yes | Maps to JEX `REQ_DATE` (formatted as `yyyyMMdd` at the IDO layer). |
| `currency` | enum `ALL` / `FCY` / `VND` / `USD` / `KHR` | no, default `ALL` | `ALL` → no filter; `FCY` → all currencies except local (VND for VN tenant); else exact match. |

`biz_key` is taken from the session, never the wire.

### Response shape

```json
{
  "asOfDate": "2026-05-14",
  "currency": "ALL",
  "currencySummary": [
    {
      "currencyCd": "VND",
      "totalCash": 0,
      "totalDemandDeposit": 0,
      "totalTermDeposit": 0,
      "totalLoan": 0,
      "totalSavings": 0
    }
  ],
  "accounts": [
    {
      "bizKey": "...",
      "lastCollectedDate": "2026-05-14",
      "refDate": "2026-05-14",
      "currencyCd": "VND",
      "accountTypeCd": "11",
      "bankCd": "11704011",
      "bankNm": "Vietcombank",
      "accountNo": "0011000xxxx",
      "balance": 0,
      "loanName": null,
      "maturityDate": null,
      "agreementAmount": null,
      "sendCount": 0,
      "sendAmount": 0,
      "receiveCount": 0,
      "receiveAmount": 0,
      "internalSendCount": 0,
      "internalSendAmount": 0,
      "internalReceiveCount": 0,
      "internalReceiveAmount": 0
    }
  ],
  "currencyFlow": [
    {
      "currencyCd": "VND",
      "beforeDateBalance": 0,
      "balance": 0,
      "sendCount": 0,
      "sendAmount": 0,
      "receiveCount": 0,
      "receiveAmount": 0
    }
  ]
}
```

### Errors

- `400 WBE_VALIDATION` — missing/invalid `asOfDate`, unknown `currency` enum.
- `403 WBE0401` — no permission on the report screen.

---

## 4. Frontend page

`wecms-web/app/(dashboard)/banking/reports/daily-cash/page.tsx` (the route group is `(dashboard)`, not `(authenticated)`).

**Implemented 2026-05-14 (initial slice):** `DailyCashClient` calls `fetchDailyCashReport({ asOfDate: today, currency: "ALL" })` on mount and maps the three-tier wire shape into the existing `DailyCashPage` props. Search-button-driven refetch, currency-scope toggle, ReportLayout adoption (§7b), and the empty-state "Please click the 'Search' button" gate are deferred to a follow-up MR — the i18n keys (`report.daily_cash.empty_message`) are already in `locales/{en,ko,vi}.json` from the existing static UI.

- Filter bar: `<DatePicker label="As of" name="asOfDate" />` (typography token `text-input`) + `<RadioGroup label="Currency" options=[All, FCY, VND] />`. Search button on the right.
- Initial state: empty grid with placeholder "Please click the 'Search' button." (`text-helper`).
- On search: call `useDailyCashReport({ asOfDate, currency })` (React Query) → render three sections:
  1. Currency summary cards (one per currency in `currencySummary`) — KPI tile typography `text-3xl font-semibold`.
  2. Account grid (DataGrid component, columns drive by View Setting).
  3. Currency rollup footer.
- Export: button calls `/api/v1/reports/daily-cash/export.xlsx` with the same params.

i18n keys live in `messages/{en,ko,vi}/reports/daily_cash.json` with the dictionary mirrored from `rept_0009_001_osmc.ts`.

---

## 5. Backend skeleton (as implemented 2026-05-14)

Lives under `com.kosign.wecms.domain.banking.*` (not `com.wecms.reports.dailycash` — the refactor's actual package layout is `com.kosign.wecms.domain.<module>.<feature>`):

```
wecms-api/src/main/java/com/kosign/wecms/domain/banking/
├── controller/DailyCashReportController.java       // GET + POST /export
├── service/
│   ├── DailyCashReportService.java                 // tx + audit
│   └── DailyCashCurrencyFilter.java                // enum ALL/FCY/VND/USD/KHR
├── repository/DailyCashReptDailySumrDetlRepository.java
├── entity/DailyCashReptDailySumrDetl.java          // legacy DAILY_CASH_REPT_DAILY_SUMR_DETL
├── dto/DailyCashReportResponse.java                // currencySummary + accounts + currencyFlow
└── export/DailyCashReportExportProvider.java       // POST /export via ExcelExportService
```

`DAILY_CASH_REPT_DAILY_SUMR` and `DAILY_CASH_REPT_DAILY_CASH_SUMR_DETL` (referenced in §2 of this spec) are **not** in the PROD Active Table List, so the refactor does not create entities for them. Currency-level summary + flow rollup are computed by aggregating over `DAILY_CASH_REPT_DAILY_SUMR_DETL` in `DailyCashReportService` instead.

Audit emission: every successful search and every export call writes `AUDIT_LOGS_INFM` rows via `AuditLogService.append` with:
- `wsvc_id = "daily-cash-report"` (slug, not the legacy WSVC id)
- `ref_no = "DAILY_CASH_REPT_DAILY_SUMR_DETL"` (entity name)
- `ref_no1 = <ref_date>` (yyyyMMdd)
- `ref_no2 = <currency filter enum name>`
- `detail_key = audit.daily_cash_report.{search,export}.detail`

Locale fan-out (EN/KO/VI/KM) is owned by the shared `AuditLogService`.

View Setting seed: `V123__daily_cash_report_view_setting_seed.sql` registers 9 columns (BANK · ACCT_NO · BALANCE · LOAN_NAME · MATURITY_DATE · SEND_COUNT · SEND_AMOUNT · RECEIVE_COUNT · RECEIVE_AMOUNT) under `web_svc_id='daily-cash-report'` / `dym_area_id='default'`. All rows ship `SORT_YN='N'` — table-header sort is intentionally not exposed for the snapshot report.

### `POST /api/v1/banking/reports/daily-cash-report/export`

XLSX export via the shared `ExcelExportService` (`screenId='daily-cash-report'`). Body is the standard `ExcelExportRequest` envelope: `filters.asOfDate` + `filters.currency` mirror the GET params; `columns[]` honours the user's live View Setting selection minus hidden columns; the backend recomputes the count subheader.

---

## 6. DB / migration

Read-only feature — no schema changes. Queries hit existing `DAILY_CASH_REPT_*` tables already populated by the JEX nightly batch. If the refactor later owns the batch, that's a separate feature (`daily-cash-batch`).

---

## 7. Open questions

- Does the refactor share the JEX nightly summary job, or do we recompute on the fly? **Assumed: share, read-only.**
- `INQ_DV` parameter in WSVC is not referenced in the JSP UI — confirm with Sonny whether it's dead or used by an external caller.
- Tenants with non-VND local currency (Cambodia: KHR) — the `FCY` filter must read the tenant's base currency, not hardcode VND.
