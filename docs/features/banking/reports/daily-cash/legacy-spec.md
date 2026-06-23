# Daily Cash Report — Refactor Feature Spec (Backend)

> **⚠️ Data-source corrected 2026-06-13 (noted 2026-06-23).** The live read path calls the legacy DB
> functions **`rept_0900_001_rec1..rec4`** directly via `DailyCashReportFunctionRepository`
> (`DailyCashReportService.java:24-75` — the documented set-returning exception, same stance as Cash Flow
> and Daily Financial). The prior **Java live-recompute** (`DailyCashRecomputeRepository` /
> `CurrencyAccumulator`) is **bypassed / no longer wired** and retained only as a cleanup candidate. Any
> "live recompute over `ACCT_MSTR` + `ACCT_TRNS_HSTR`" wording below is the **superseded pre-pivot** record
> (audit history preserved).

> **This one-pager is the short feature-spec entry. The full legacy-to-refactor analysis package
> lives in [`daily-cash/`]()** — README, business-flow, functional-specification,
> database-mapping, interface-specification, source-code-index, migration-guide, test-scenarios,
> refactor-gap-analysis, refactor-parity-verification, fix-plan. Read the package for anything beyond
> the identity + status snapshot below.
>
> **Updated 2026-06-14.** The backend now calls the **legacy DB functions `rept_0900_001_rec1..4` directly**
> (the Java live-recompute path — `DailyCashRecomputeRepository` + `CurrencyAccumulator` — is **bypassed**,
> retained only as cleanup candidates). `rec1` → account details and `rec2` → currency summary are active;
> `rec3` → currency flow / opening balance and `rec4` → total cash read `CASH_REPY_MSTR`, which is empty in
> the R&D DB, so cash stays 0 until that data lands. The currency filter is applied **outside** the functions
> (`ALL` / exact `ccy_cd=?` / `FCY` = `ccy_cd<>'VND'`); `req_date2 = req_date`. See
> [daily-cash-db-function-alignment-decision.md](../../../../../../docs/audits/daily-cash-db-function-alignment-decision.md).
>
> **Prior note (2026-06-04, superseded):** the implemented backend was a **live recompute** over `ACCT_MSTR` +
> `ACCT_TRNS_HSTR`. That was correct before the 2026-06-14 switch to the DB functions.

| Field | Value |
|---|---|
| Slug | `daily-cash-report` (runtime id; file renamed to `daily-cash.md` to match the menu) |
| Module | Banking → Reports |
| Status | **Calls `rept_0900_001_rec1..4` directly (2026-06-14).** rec1/rec2 active; rec3/rec4 need `CASH_REPY_MSTR` data. Live HTTP/JPA smoke recommended. Residual technical debt (export rule, dead snapshot repo) in [`daily-cash/refactor-gap-analysis.md`](refactor-gap-analysis.md). |
| JEX twin | `rept_0009_001` (view) + `rept_0009_001_r001` (read API) |
| Refactor route | `/banking/reports/daily-cash` (Next.js, `(dashboard)` group) |
| Refactor base URI | `/api/v1/banking/reports/daily-cash-report` (Spring Boot) |
| Audit `WSVC_ID` | `daily-cash-report` (slug — never `rept_0009_001`) |
| View Setting | **Not adopted** (report page — no toolbar list-icon) |
| i18n key prefix | `report.daily_cash.*` |

---

## 1. Summary

Point-in-time, per-company cash-position snapshot for one chosen date, grouped by currency. The user
picks **As-of date** + **Currency** (All / FCY / VND) and clicks **Search**. The report renders a
three-tier response — per-currency summary, per-account detail (split Current `03/04` vs Savings
`05/06` on the frontend), and a per-currency flow rollup — plus Excel + print export.

### In scope (implemented)

- Search `GET /api/v1/banking/reports/daily-cash-report?asOfDate=&currency=`.
- Three-tier response: `currency_summary` (rec2) → `accounts` (rec1) → `currency_flow` (rec3).
- **Direct calls to `rept_0900_001_rec1..4`** via `DailyCashReportFunctionRepository` (DB functions are the source of truth; no Java recompute).
- Account-type bucketing is performed inside `rec2` (`03/04`=demand, `05/06`=savings, `02`=term, `01`=loan); `total_cash` from `rec4`.
- Audit log on search and export (`WSVC_ID = "daily-cash-report"`).

### Out of scope / deferred

- Physical cash (`rec3/rec4` from `CASH_REPY_MSTR`) — the calls are **wired**, but `CASH_REPY_MSTR` is empty in the R&D DB (and off the PROD active list) → `total_cash = 0` and empty currency flow until the data/scope lands.
- Per-tenant local currency — hardcoded `VND` today (OQ-3).
- Last-collection datetime — `last_collected_date` null on the live path (`BAT_CALL_HIS` dropped V31).
- Per-transaction drilldown — lives in [`daily-financial.md`](../daily-financial/README.md).

### Known technical debt

- **Export single-producer** — the UI generates the workbook client-side (`xlsx-js-style`) and does not
  call the wired backend `/export` + `DailyCashReportExportProvider`. See gap #2 / fix-plan #4.

---

## 2. REST endpoints (as implemented)

- `GET  /api/v1/banking/reports/daily-cash-report` — `asOfDate` (ISO `yyyy-MM-dd`, required) + `currency`
  (`ALL`/`FCY`/`VND`/`USD`/`KHR`, optional, default `ALL`). Returns `ApiResponse<DailyCashReportResponse>`.
- `POST /api/v1/banking/reports/daily-cash-report/export` — shared `ExcelExportRequest`; streams XLSX via
  `DailyCashReportExportProvider` (`screenId = daily-cash-report`).

Backend code: `com.kosign.wecms.domain.banking` — `controller/DailyCashReportController`,
`service/{DailyCashReportService, DailyCashCurrencyFilter}`,
`repository/{DailyCashRecomputeRepository, DailyCashAccountProjection, DailyCashReptDailySumrDetlRepository}`,
`dto/DailyCashReportResponse`, `entity/DailyCashReptDailySumrDetl`, `export/DailyCashReportExportProvider`.
Full map: [`daily-cash/source-code-index.md`](source-code-index.md).

---

## 3. Frontend

Route `/banking/reports/daily-cash`. Frontend package:
[`wecms-web/docs/features/banking/reports/daily-cash/`](../../../../../../wecms-web/docs/features/banking/reports/daily-cash/).
