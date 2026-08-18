# Cash Flow — Migration Guide (JEX → Spring Boot + Next.js)


> ⚠️ **Flyway numbering in this document is PRE-REBASELINE (note added 2026-08-17).**
> Bare `V<n>` references below were written against the migration set as it stood **before** the
> [2026-06-09 rebaseline](../../../../references/database/flyway-migration-baseline.md), which renumbered every migration to a
> one-table-per-migration scheme and dropped all seed / helper-index / view migrations. **Treat every
> bare `V<n>` here as HISTORICAL** — it does not identify the same migration today. To find the current
> migration for a table, run `ls src/main/resources/db/migration/ | grep <table>` or see
> [flyway-migration-baseline.md](../../../../references/database/flyway-migration-baseline.md) §5.

Maps the legacy artifacts to the completed Spring Boot / Next.js refactor and records the design
decisions. Feeds [refactor-gap-analysis.md](refactor-gap-analysis.md).

> **Identifier naming** — the refactor uses the kebab-case slug **`cash-flow-report`**, never the legacy
> `cash_0001_001`. Legacy ids survive only as source-of-truth citations and migration prose.

---

## 1. Artifact mapping

| Legacy | Refactor target | Status |
|---|---|---|
| `cash_0001_001.act` / `_view.jsp` | Next.js route `/banking/reports/cash-flow` (`page.tsx` → `CashFlowClient` → `CashFlowPage`) | ✅ done |
| `cash_0001_001.ts` (UI + render + ExcelJS) | `CashFlowClient.tsx` + `CashFlowPage.tsx` + `BankFilterPopover.tsx` | ✅ done |
| `cash_0001_001_r001` + IDO `CASH_FLOW_R001` + fn `get_cashflow_report` | `CashFlowReportController.search()` → `CashFlowReportService.search()` (+ `cashflow/*` helpers) | ✅ done (**Java reimpl** — function not called) |
| client ExcelJS export | `CashFlowExcelWriter` (server POI) via `POST .../export` | ✅ done (moved server-side) |
| `cash_0001_001_r002` (FX rate) | `GET /api/v1/banking/exchange-rates` (feeds rate prefill) | ✅ exists; selection logic differs — **N3** |
| `cash_0001_001_r003` (bank list) | `GET /api/v1/banking/accounts` (feeds `BankFilterPopover`) | ✅ exists; Haengsung hide-closed not ported — **N4** |
| View column visibility | `V124` seed (`SYS_SCR_OPTION_WECMS`, `web_svc_id='cash-flow-report'`) | ✅ done (export visibility gate only) |

---

## 2. Architecture translation

| Legacy concern | Legacy mechanism | Refactor mechanism |
|---|---|---|
| Calc engine | one ≈ 90 KB PL/pgSQL function (12 CTEs) | Java service + 8 `cashflow/*` helpers; **no DB function** |
| Account-level helper | `wb_get_chart_acct_level_text` | `ChartOfAccountsHierarchy` (parent-walk) |
| Approval helper | `wb_get_appr_stts` | `ApprovalStatusResolver` → `appr_type_cd='01'` (provably equal — N0-A) |
| FX | inline `ROUND` CASE in the function | `FxConverter` (single `setScale/divide`, `HALF_UP`) |
| Wire envelope | `COMMON_HEAD` + `REC` (UPPER_SNAKE) | `{ status, data }`, lowercase snake_case (Jackson global) |
| Error codes | JEX ML codes | `WBE_CASH_FLOW_RATE_REQUIRED` / `WBE_VALIDATION` |
| Auth | `_r001 login=N` | JWT-required (`SecurityConfig`) — D1 |
| Export | client ExcelJS | server POI `CashFlowExcelWriter` |

See [business-flow.md §5](business-flow.md) for the per-step Java pipeline.

---

## 3. Design decision — reimplement vs. reuse the function

| Option | Pros | Cons |
|---|---|---|
| **A. Reimplement in Java** *(chosen)* | DB-portable, testable, debuggable, no PL/pgSQL drift; aligns with the api-side "no new legacy DB helpers" policy; enables the D2 validation | Java math must equal the function byte-for-byte → parity risk |
| B. Call `get_cashflow_report` natively | guaranteed parity, one source of truth | couples to Postgres + `wecmsvndev`; the function is unmaintained; harder to unit-test; violates the DB-function policy |

Because Option A was chosen, **regression parity tests are mandatory** — and exist
([test-scenarios.md](test-scenarios.md)). The two Critical parity risks were *proven equivalent* in the
2026-06-04 audit: the approval gate reduces to `appr_type_cd='01'` (N0-A), and FX is single-round
HALF_UP matching Postgres `ROUND` (N0-B).

---

## 4. Deliberate divergences from legacy

| # | Legacy | Refactor | Reason |
|---|---|---|---|
| **D1** | `_r001` `login=N` (anonymous) | JWT-required | public access to a tenant-scoped report is a data leak (Sonny override 2026-05-13) |
| **D2** | `_act.jsp` requires both `USD_RATE_AMT` and `KRW_RATE_AMT` always | rate required only when base ≠ that currency | legacy validation is over-strict / confusing; refactor's conditional check is clearer |
| **D3** | (legacy emitted title-case `Sales Profit` + `NON SALES PROFIT` as a subtotal) | refactor preserves the verbatim values: `Sales Profit` row + `Level 1 Subtotal` with `NON SALES PROFIT` display code | wire-level parity for the FE classifier |
| **D4** | function consumes `wb_get_appr_stts` + `wb_get_chart_acct_level_text` | Java `ApprovalStatusResolver` + `ChartOfAccountsHierarchy` | no new legacy PL/pgSQL helpers (api policy) |
| **D5** | Korean/English concatenated `display_name` (`수입 REVENUE`) | preserved verbatim | FE renders the string as-is |
| **D6** | hardcoded INVESTMENT code list | preserved as `INVESTMENT_BALANCE_CONTRIBUTING_CODES` | production carve-out parity (G2) |

---

## 5. Migration checklist

- [x] Backend reimplements `get_cashflow_report` in Java (`CashFlowReportService` + `cashflow/*`).
- [x] Frontend wired to the live endpoint, no mock fallback (`mockData.ts` is dead code).
- [x] Server quarter filtering removed — full Jan–Dec always; quarter = client column-visibility (PO 2026-06-04, **G5/D-1 RESOLVED**, guarded by `CashFlowReportQuarterParityTest`).
- [x] Approval-gate parity proven (`appr_type_cd='01'`, **N0-A**).
- [x] FX single-round HALF_UP parity proven (**N0-B**, `FxConverterTest`).
- [x] Excel export moved server-side (`CashFlowExcelWriter`); NON SALES PROFIT blue+green duplicate + centered END BALANCE matched (parity audit §8b).
- [ ] **Parity-test against `get_cashflow_report` output** for several tenants/years/currencies (SIT/UAT — the unit tests cover the math; a live-vs-Java diff harness is the SIT recommendation).
- [ ] **Confirm INVESTMENT codes** (`2001026/2001027/2002003/2002002`) on UAT/PROD (**G2**, `Needs Verification`).
- [ ] **Reconcile Excel number format** per-currency vs generic `#,##0.##` (**G7**, PO call).
- [ ] **FX-rate weekday/`SEQ_NO=2` preference** — port or accept "latest seq" (**N3**, PO call).
- [ ] **Haengsung hide-closed bank accounts** in the bank multi-select before any Haengsung release (**N4**).
