# Daily Cash Report — Refactor Feature Spec (Backend)

> **This one-pager is the short feature-spec entry. The full legacy-to-refactor analysis package
> lives in [`daily-cash/`](daily-cash/)** — README, business-flow, functional-specification,
> database-mapping, interface-specification, source-code-index, migration-guide, test-scenarios,
> refactor-gap-analysis, refactor-parity-verification, fix-plan. Read the package for anything beyond
> the identity + status snapshot below.
>
> **Updated 2026-06-04.** The earlier draft of this file described an unimplemented design
> (persisted-snapshot read, `accountTypeCd: "11"`, `internalSend*` fields, View Setting seed V123).
> That draft is superseded — the implemented backend does a **live recompute** over `ACCT_MSTR` +
> `ACCT_TRNS_HSTR`, has no internal-transfer fields, and adopts no View Setting. See
> [`daily-cash/database-mapping.md`](daily-cash/database-mapping.md) §5 and
> [`daily-cash/interface-specification.md`](daily-cash/interface-specification.md) for the real contract.

| Field | Value |
|---|---|
| Slug | `daily-cash-report` (runtime id; file renamed to `daily-cash.md` to match the menu) |
| Module | Banking → Reports |
| Status | **Functionally complete (in-scope surface)** — residual technical debt tracked in [`daily-cash/refactor-gap-analysis.md`](daily-cash/refactor-gap-analysis.md) |
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
- Three-tier response: `currency_summary` (REC) → `accounts` (REC1) → `currency_flow` (REC2).
- Live recompute over `ACCT_MSTR` + `ACCT_TRNS_HSTR` (no snapshot dependency).
- DB-verified account-type bucketing (`03/04`=demand, `05/06`=savings, `02`=term, `01`=loan, cash=0).
- Audit log on search and export (`WSVC_ID = "daily-cash-report"`).

### Out of scope / deferred

- Physical cash (`rec3/rec4` from `CASH_REPY_MSTR`) — not in PROD active list → `total_cash = 0`.
- Per-tenant local currency — hardcoded `VND` today (OQ-3).
- Last-collection datetime — `last_collected_date` null on the live path (`BAT_CALL_HIS` dropped V31).
- Per-transaction drilldown — lives in [`daily-financial.md`](daily-financial.md).

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
Full map: [`daily-cash/source-code-index.md`](daily-cash/source-code-index.md).

---

## 3. Frontend

Route `/banking/reports/daily-cash`. Frontend package:
[`wecms-web/docs/features/banking/reports/daily-cash/`](../../../../../wecms-web/docs/features/banking/reports/daily-cash/).
