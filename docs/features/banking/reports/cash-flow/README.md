# Cash Flow — Legacy Analysis & Refactor-Parity Package (Backend)

> **Scope of this folder.** This package is the *full legacy-to-refactor analysis* of the JEX
> **Cash Flow** report (`cash_0001_001`). Every statement here is backed by reading the actual
> source on disk — the live JEX tree (`WECMS DEV/`), the legacy PL/pgSQL on the JEX DEV database
> (`wecmsvndev`, read-only `postgres` MCP), and the current refactor trees (`wecms-api/`,
> `wecms-web/`). No assumptions; items that could not be confirmed are marked **`Needs Verification`**.
>
> **Created 2026-06-04** during the Cash Flow documentation consolidation. It supersedes the
> one-page Phase-0 stub at [`../cash-flow.md`](legacy-spec.md) (kept as a short pointer) and
> **migrates in** the workspace-root analysis package formerly at
> [`docs/features/cash-flow/`](../../../../../../docs/features/cash-flow/) (now a deprecated
> read-only pointer). The frontend counterpart lives at
> [`wecms-web/docs/features/banking/reports/cash-flow/`](../../../../../../wecms-web/docs/features/banking/reports/cash-flow/).

| Field | Value |
|---|---|
| Feature slug | `cash-flow-report` (runtime id; file/folder named `cash-flow` to match the menu) |
| Module | Banking → Reports |
| Legacy JEX menu | **Cash Flow** (ML `inc_wabook_fnsh_lnb_m013`), WABOOKS **Financial (FNSH)** module |
| Legacy view service | `cash_0001_001` (`login=Y`, `NORM_USER_ONLY_YN=Y`) |
| Legacy read APIs | `cash_0001_001_r001` (cash-flow data), `_r002` (exchange rate), `_r003` (bank list) |
| Legacy calc engine | PL/pgSQL function `get_cashflow_report(...)` on JEX DEV (≈ 90 KB) — **called directly** by the refactor (2026-06-13); the earlier Java reimplementation is retained but off the active path |
| Refactor route | `/banking/reports/cash-flow` (Next.js, `(dashboard)` group) |
| Refactor base URI | `GET /api/v1/banking/reports/cash-flow-report` + `POST .../export` (Spring Boot) |
| Audit `WSVC_ID` | `cash-flow-report` (slug — never `cash_0001_001`) |
| View Setting | Seeded (`web_svc_id='cash-flow-report'`, V124) **only to unlock the Excel export visibility filter** — the report page has no toolbar list-icon popover |
| i18n key prefix | `report.cash_flow.*` (frontend) · `WBE_CASH_FLOW_RATE_REQUIRED` (backend message) |
| Refactor status | **Live path calls `get_cashflow_report` directly (2026-06-13)**; FE/BE aligned. The earlier Java aggregation pipeline is retained but off the active path. (see §4 Executive summary) |

---

## 1. Feature overview

Cash Flow is a **year-at-a-glance, P&L-style cash plan report**. For one company (`BIZ_KEY`), a
year, a base currency and an optional bank-account filter, it renders a hierarchical 12-month-wide
matrix of cash movement grouped under three fixed Level-1 roots:

- **Opening Balance** — carried forward from the prior year's December End Balance (rendered in the table header).
- **ACC-01 REVENUE (수입)**, **ACC-02 EXPENSES (지출)**, **ACC-03 INVESTMENT (투자)** trees — each shown
  Level 1 → Level 2 → Level 3, with a per-root **S. TOTAL** subtotal (ACC-03's subtotal carries the
  display code **NON SALES PROFIT**).
- **SALES PROFIT** (총수입 − 총지출 = Revenue − Expenses) band.
- **End Balance** per month (running balance) — rendered in a sticky footer.
- **Excel export** (server-side Apache POI) and a currency / quarter / bank filter bar.

In JEX almost **all** of the calculation lived in a single PostgreSQL function,
`get_cashflow_report(...)`; the JSP/TypeScript layer was a thin caller + hierarchical renderer. **The
refactor's live path calls `get_cashflow_report` directly** (`CashFlowReportService.search` →
`CashFlowReportRepository`, decision 2026-06-13). The CTE-by-CTE Java reimplementation under
`domain/banking/service/cashflow/*` is **retained but off the active path as of 2026-06-13**. That is
the single most important fact for anyone maintaining this feature; see [business-flow.md](business-flow.md).

---

## 2. Documents in this package

| File | Purpose |
|---|---|
| [README.md](README.md) | This file — identity, index, **executive summary** + completion table. |
| [business-flow.md](business-flow.md) | Request flow, the calculation rules, the legacy `get_cashflow_report` CTE anatomy, and the retained (off-active-path) refactor Java pipeline. |
| [functional-specification.md](functional-specification.md) | Screen behaviour, filters/events, row vocabulary, validation, permissions, ML keys. |
| [database-mapping.md](database-mapping.md) | Source tables / functions, column lists, the legacy → refactor entity/DTO/UI column map, FX, year/quarter handling. |
| [interface-specification.md](interface-specification.md) | Legacy WSVC contract (`_r001/_r002/_r003`), the refactor REST contract (search + export), field-by-field map. |
| [source-code-index.md](source-code-index.md) | Full traceability: every JSP / TS / action / IDO / function / table (legacy) ↔ controller / service / repo / DTO / writer / test (refactor). |
| [migration-guide.md](migration-guide.md) | Legacy architecture → Spring architecture; service-by-service mapping; wire/naming rules; design decisions. |
| [test-scenarios.md](test-scenarios.md) | Functional / parity / edge-case scenarios (backend) + the automated test inventory. |
| [refactor-gap-analysis.md](refactor-gap-analysis.md) | Final parity table (G1–G12 + N-findings), severity-classified — the authoritative open-items list. |

---

## 3. Related documents

- [`../cash-flow.md`](legacy-spec.md) — the one-page refactor feature-spec pointer (superseded in detail by this package; retained).
- [`../daily-cash/`](../daily-cash/) + [`../daily-financial.md`](../daily-financial/README.md) — the sibling Banking → Reports screens; same `<ReportLayout>` / no-pagination pattern.
- [`../README.md`](../README.md) — Banking → Reports group index.
- **Parity audit (primary evidence):** [`../../../../../../docs/features/cash-flow/parity-audit/gap-report.md`](../../../../../../docs/features/cash-flow/parity-audit/gap-report.md) + [`final-confirmation.md`](../../../../../../docs/features/cash-flow/parity-audit/final-confirmation.md) — the source-verified 2026-06-04 audit that closed the Critical quarter-filter gap and confirmed READY FOR SIT/UAT. **Kept in place and cross-linked** (not migrated) — it is the canonical parity-audit artifact.
- **Migrated source package (deprecated):** workspace-root [`docs/features/cash-flow/`](../../../../../../docs/features/cash-flow/) — the original 9-file analysis this folder was built from; now a read-only pointer.
- **DB function classification:** [`../../../../references/database/db-function-classification.md`](../../../../references/database/db-function-classification.md) — `get_cashflow_report` is the HIGH-priority REPORT function the refactor's live path now calls directly (2026-06-13); the earlier Java reimplementation is retained but off the active path.
- Frontend package: [`wecms-web/docs/features/banking/reports/cash-flow/`](../../../../../../wecms-web/docs/features/banking/reports/cash-flow/).
- Backend rules: [`wecms-api/CLAUDE.md`](../../../../../CLAUDE.md) — §"DB Function / Legacy Function Policy", §"Legacy report DB functions — Sprint 1 stance", §"PROD DB Table Scope", §"Excel export — `common/export/`".

---

## 4. Executive summary

### What this feature is

A read-only, per-company, 12-month cash-plan matrix with three hierarchical category trees, an
Opening/End running balance, a Sales-Profit band, and Excel export — computed on-the-fly from
transaction history (there is no persisted cash-flow summary table). In JEX it is one read service
(`cash_0001_001_r001`) that calls the PL/pgSQL function `get_cashflow_report(biz_key, year, quarter,
base_currency, usd_rate, krw_rate, bank_filters)`, which unions four transaction sources, applies an
approval gate, converts currency at the user-supplied flat rate, walks the Chart-of-Accounts
hierarchy, and emits hierarchy-aware rows.

The refactor's live read path **calls `get_cashflow_report` directly** (decision 2026-06-13):
`CashFlowReportService.search(...)` delegates to `CashFlowReportRepository` (`CashFlowReportRepository.java:67`),
and the DB helpers `wb_get_chart_acct_level` + `wb_get_chart_acct_level_text` were added to the refactor DB.
The earlier Java pipeline — `PaymentDataAggregator` (the 4-source union + approval gate), `FxConverter`
(per-currency rounding), `ChartOfAccountsHierarchy` (L1→L2→L3 walk, replacing `wb_get_chart_acct_level_text`),
`BalanceCalculator` (Opening / End / Sales-Profit + the hardcoded INVESTMENT carve-out), and
`CashFlowRowAssembler` (the ordered row stream) — is **retained but off the active path as of 2026-06-13**;
the live path calls `get_cashflow_report` directly.

### Refactor completion

> **Live path note (2026-06-13):** the table below describes the **retained Java pipeline**, which is now **off the active path** — the live path calls `get_cashflow_report` directly via `CashFlowReportRepository`. The rows are kept as the historical parity record of the (superseded) Java reimplementation.

| Surface | In-scope target | State | Evidence |
|---|---|---|---|
| Search (`year` + `quarter` + `baseCurrency` + `usdRate`/`krwRate` + `bankFilters`) | yes | **Done** | `CashFlowReportController` `GET /cash-flow-report`; `CashFlowReportService.validate` |
| Hierarchical rows (Opening / L1 / L2 / L3 / Subtotal / Sales Profit / End) | yes | **Done** | `CashFlowRowAssembler.assemble` — verbatim legacy `row_type` vocabulary |
| Four source aggregations (`ACCT_TRNS_HSTR`+`ACCT_TRNS_RCV_PAY_INFM`, `CARD_TRNS_HSTR`, `OVRS_PYMN_MSTR`, `SCDUL_PYMN_SUMR`+`_DETL`) | yes | **Done** | `PaymentDataAggregator.collect` + 4 repository `findCashFlowSourceRows` queries |
| Approval gate (non-bank sources `appr_type_cd='01'`; bank `cnfm_yn='Y'`) | yes | **Done — provably equal to legacy** | `ApprovalStatusResolver` + `ExptResolMstrRepository.findCompletedApprSeqNos`; gap-report §4 N0-A |
| Currency conversion (VND/USD/KRW, per-currency rounding scales) | yes | **Done — single-round HALF_UP, matches Postgres `ROUND`** | `FxConverter`; gap-report §4 N0-B; `FxConverterTest` (9 permutations) |
| Opening / End / Sales Profit + prior-year carry-forward | yes | **Done** | `BalanceCalculator`; `BalanceCalculatorTest` (golden 2025 sample) |
| INVESTMENT carve-out (`{2001026,2001027,2002003,2002002}`, `cal_rule`-signed) | yes | **Done — identical set** | `BalanceCalculator.INVESTMENT_BALANCE_CONTRIBUTING_CODES` |
| Quarter handling (full Jan–Dec always; quarter = client column-visibility only) | yes | **Done — legacy parity (PO 2026-06-04)** | `CashFlowReportQuarterParityTest`; gap-report §4 G5 |
| Excel export (grouped/merged layout, special-row bands) | yes | **Done** | `CashFlowExcelWriter` (dedicated POI XSSF writer) via `POST .../export` |
| NON SALES PROFIT (blue subtotal + green band, intentional legacy duplicate) | yes | **Done (FE)** | frontend `groupReport`; gap-report §8b Defect A |
| Auth (legacy `_r001 login=N` → JWT-required) | yes | **Done — deliberate divergence D1** | `SecurityConfig` `anyRequest().authenticated()` |

**Completion estimate (evidence-based):**

- **≈ 100 % of the in-scope read & display surface** — search, the seven-row hierarchy, the four
  source aggregations, the approval gate, FX conversion, Opening/End/Sales-Profit math, the
  INVESTMENT carve-out, quarter-as-column-visibility, and Excel export are all implemented and
  **frontend/backend aligned**. This is the surface the user described as "completed", and it is.
  The 2026-06-04 parity audit concluded **READY FOR SIT/UAT**.
- **Residual non-blocking items (PO calls, presentation/product-gating only):**
  1. **Excel number format (G7)** — refactor uses generic `#,##0.##`; legacy used per-currency
     `[$-vi-VN]#,##0` (VND/KRW, no decimals) and `[$-de-DE]#,##0.00` (USD). Cell values unaffected.
  2. **FX-rate weekday / `SEQ_NO=2` preference (N3)** — refactor `exchange-rates` endpoint selects the
     latest seq per currency; legacy `_r002` preferred the latest weekday rate and the closing seq.
  3. **Haengsung hide-closed-accounts (N4)** — the legacy `_r003` matured/zero-balance-savings filter
     is not yet in the refactor bank list (consistent with Haengsung product-gates deferred in R&D).
  4. **`Grand Total` dead code (G8)** — a `row_type` the function never emits; carried into the DTO
     doc / Excel grouper / FE classifier as inert dead handling. Harmless; remove for clarity.
  5. **INVESTMENT codes data-existence (G2)** — `{2001026,2001027,2002003,2002002}` are absent in DEV
     tenant 000001 (0 of its chart rows), so ACC-03 contributes 0 to balances there. Parity-neutral
     (both sides use the same set); confirm the codes on UAT/PROD before any Haengsung-facing release.

> **Verdict:** the Cash Flow refactor is **functionally complete and FE/BE-aligned for its in-scope
> surface, with legacy parity verified and regression-guarded** (`CashFlowReportQuarterParityTest`,
> `BalanceCalculatorTest`, `FxConverterTest`). The remaining differences are non-blocking PO calls —
> presentation-only (Excel format) or product-gating (FX selection, Haengsung filter) — tracked in
> [refactor-gap-analysis.md](refactor-gap-analysis.md) and
> [the parity audit §7](../../../../../../docs/features/cash-flow/parity-audit/gap-report.md).

### Migration complexity: **High** (highest of the three Banking reports)

- **Raising factors:** the legacy logic is a ≈ 90 KB PL/pgSQL function (12 CTEs, 4 unioned sources, an
  approval-status helper, a chart-level helper, prior-year recompute, a hardcoded INVESTMENT carve-out,
  per-currency rounding). The earlier approach reimplemented it in Java byte-for-byte (Option A), which
  carried real parity risk — hence the parity-test suite. **As of 2026-06-13 the live path calls
  `get_cashflow_report` directly**, sidestepping that risk; the Java pipeline is retained but off the active path.
- **Lowering factors:** the report is read-only (no writes, no batch, no triggers), and the refactor
  reuses already-built entities (`AcctTrnsHstr`, `OvrsPymnMstr`, `ScdulPymnSumr(_Detl)`, `ExptResolMstr`,
  `ChartAcctInfm`) plus the shared export infrastructure. The two Critical parity risks (approval gate,
  FX rounding) were *proven equivalent* in the 2026-06-04 audit.

---

## 5. Status legend used throughout

- **Confirmed** — verified against source/DEV/refactor code on disk.
- **Needs Verification** — inferred or not present in DEV data; must be checked on UAT/PROD or with the PO.
