# Daily Cash — Legacy Analysis & Refactor-Parity Package (Backend)

> **Scope of this folder.** This package is the *full legacy-to-refactor analysis* of the JEX
> **Daily Cash** report (`rept_0009_001`). It expands the one-page planning stub at
> [`../daily-cash.md`](../daily-cash.md) (kept in place as the short feature-spec entry). Every
> statement here is backed by actual source-code analysis of the live JEX tree (`WECMS DEV/`), the
> live JEX DEV database (`wecmsvndev`, read-only `postgres` MCP), and the current refactor trees
> (`wecms-api/`, `wecms-web/`). No assumptions.
>
> **Relocated 2026-06-04.** This bundle was consolidated here from the workspace-root
> `docs/features/daily-cash/` tree (now deprecated read-only — see that folder's `README.md`). The
> frontend counterpart lives at
> [`wecms-web/docs/features/banking/reports/daily-cash/`](../../../../../../wecms-web/docs/features/banking/reports/daily-cash/).

| Field | Value |
|---|---|
| Feature slug | `daily-cash-report` |
| Module | Banking → Reports |
| Legacy JEX menu | **Daily Cash** (일일시재보고서 / *Daily Cash Report*) |
| Legacy view service | `rept_0009_001` (view) |
| Legacy read API | `rept_0009_001_r001` (`.jct`, sync) |
| Legacy last-collection API | `rept_0100_001_r001` (`BAT_ID=REPT_0900_001`) — **not ported** |
| Refactor route | `/banking/reports/daily-cash` (Next.js, `(dashboard)` group) |
| Refactor base URI | `/api/v1/banking/reports/daily-cash-report` (Spring Boot) |
| Audit `WSVC_ID` | `daily-cash-report` (slug — never `rept_0009_001`) |
| View Setting | **Not adopted** (report page, no toolbar list-icon) |
| i18n key prefix | `report.daily_cash.*` |
| Refactor status | **Functionally complete (in-scope surface)** — see Executive summary §4 |

---

## 1. Feature overview

Daily Cash is a **point-in-time (as-of-date) cash-position snapshot**. For one company (`BIZ_KEY`) and
one chosen date it renders, grouped by currency:

1. **Per-currency status** — for each currency, a *Current Accounts* total and a *Saving Accounts*
   total (cash / time-deposit / loan totals are computed but their columns are hidden — the legacy VN
   view's Cash table is commented out, Redmine #42096).
2. **Current Accounts** grid — one row per current account (`ACCT_TYPE_CD` `03`/`04`): bank, account
   no, currency, day's debit count/amount, credit count/amount, and balance.
3. **Savings Accounts** grid — same 8-column shape for `ACCT_TYPE_CD` `05`/`06`.
4. **Excel export** + browser print (per-account ledgers, per-currency totals).

It is the shallower sibling of **Daily Financial** (`rept_0015_001` → `daily-financial`): same as-of
snapshot, but Daily Cash has **no per-transaction drilldown** and **no active-only / include-internal
filters**. Intended users: treasury / accounting staff doing daily cash-position monitoring.

**Removed legacy elements (refactor cleanup, 2026-06-04):** the physical-cash **Cash table** (legacy
`rec3/rec4` from `CASH_REPY_MSTR`, already commented out in the live VN view) and the per-row
**checkbox column** are gone. See [refactor-gap-analysis.md](refactor-gap-analysis.md) and the
frontend [ui-specification.md](../../../../../../wecms-web/docs/features/banking/reports/daily-cash/ui-specification.md).

---

## 2. Documents in this package

| File | Purpose |
|---|---|
| [README.md](README.md) | This file — overview, identity, index, **executive summary** + completion table. |
| [business-flow.md](business-flow.md) | User workflow, legacy batch + read orchestration, refactor live-recompute flow, data-flow narrative. |
| [functional-specification.md](functional-specification.md) | Search conditions, the authoritative `ACCT_TYPE_CD` 01–08 table, grid rules, ML keys, validation, permissions. |
| [database-mapping.md](database-mapping.md) | Tables / functions, column lists, legacy read-path mapping, **refactor live-recompute source** + dormant snapshot path. |
| [interface-specification.md](interface-specification.md) | Legacy WSVC contract, Excel POST contract, refactor REST contract (search + export), field map. |
| [source-code-index.md](source-code-index.md) | Full traceability: every JSP / TS / action / IDO / function / table (legacy) ↔ controller / service / repo / DTO / page / component (refactor). |
| [migration-guide.md](migration-guide.md) | Legacy architecture → Spring architecture; service-by-service mapping; wire/naming rules; doc reconciliation. |
| [test-scenarios.md](test-scenarios.md) | Parity scenarios A–G (load, currency filter, account-type classification, grids, Excel/print, validation, permissions). |
| [refactor-gap-analysis.md](refactor-gap-analysis.md) | Implemented / partial / missing / technical debt, prioritized — the authoritative open-items list. |
| [refactor-parity-verification.md](refactor-parity-verification.md) | DB-verified parity check; §1.5 carries the `rept_0900_001_rec2/rec4` function-body extract. |
| [fix-plan.md](fix-plan.md) | Remaining backlog ordered by severity (export single-producer, per-tenant local ccy, dead-code cleanup). |

---

## 3. Related documents

- [`../daily-cash.md`](../daily-cash.md) — the one-page refactor feature spec stub (superseded in detail by this package; retained).
- [`../daily-financial.md`](../daily-financial.md) + [`daily-financial/`](../daily-financial/) — the deeper sibling report; same as-of snapshot pattern.
- [`../cash-flow.md`](../cash-flow.md) — the third Banking → Reports screen.
- [`../README.md`](../README.md) — Banking → Reports group index.
- Frontend package: [`wecms-web/docs/features/banking/reports/daily-cash/`](../../../../../../wecms-web/docs/features/banking/reports/daily-cash/).
- Deprecated origin: [`docs/features/daily-cash/`](../../../../../../docs/features/daily-cash/) — read-only legacy location.
- Cross-cutting: [`docs/references/database/erd/banking-erd.md`](../../../../references/database/erd/banking-erd.md) (`DAILY_CASH_REPT_DAILY_SUMR_DETL`, V125), [`docs/references/database/table-ownership.md`](../../../../references/database/table-ownership.md), [`docs/planning/open-questions/banking-reports-remaining-scope.md`](../../../../planning/open-questions/banking-reports-remaining-scope.md).
- Backend rules: [`wecms-api/CLAUDE.md`](../../../../../CLAUDE.md) — §"Legacy report DB functions", §"PROD DB Table Scope", §"Excel export — `common/export/`".

---

## 4. Executive summary

### What this feature is

A read-only, as-of-date, per-company cash-position snapshot grouped by currency, with two
account-type grids (Current / Savings) and Excel + print export. In JEX it is a single read service
(`rept_0009_001_r001`) that reads four set-returning PL/pgSQL functions
(`rept_0900_001_rec1..4(BIZ_KEY, REQ_DATE, REQ_DATE)`) — `rec1`=per-account detail, `rec2`=per-currency
summary, `rec3`=cash flow, `rec4`=total cash — assembled into `REC` / `REC1` / `REC2`. A nightly batch
(`REPT_0900_001` → `ReptHelper.call_REPT_0900_001`) also persists these into snapshot tables, but the
read functions recompute on every read.

The refactor implements the read path as a **live recompute** over `ACCT_MSTR` + `ACCT_TRNS_HSTR`
(`DailyCashRecomputeRepository.findAccountsAsOf`), with the per-currency summary + flow aggregated in
Java (`CurrencyAccumulator`). It does **not** read the persisted snapshot, so a chosen date returns
accounts even when no nightly batch row exists — matching legacy behavior. Physical cash
(`rec3/rec4` from `CASH_REPY_MSTR`) is out of PROD-active-table scope, so `total_cash = 0`.

### Refactor completion

| Surface | In-scope target | State | Evidence |
|---|---|---|---|
| Search (`as_of_date` + `currency` ALL/FCY/VND) | yes | **Done** | `DailyCashReportController` `GET /daily-cash-report`; `DailyCashCurrencyFilter` |
| Three-tier response (currency summary / accounts / currency flow) | yes | **Done** | `DailyCashReportResponse` (REC/REC1/REC2 parity) |
| Account-type bucketing (`03/04`=Current, `05/06`=Savings, `02`=Term, `01`=Loan) | yes | **Done — DB-verified** | `AcctTypeCd.fromCode` + `CurrencyAccumulator`; `DailyCashReportServiceTest`; parity §1.5 |
| Data source (live recompute over `ACCT_MSTR`+`ACCT_TRNS_HSTR`) | yes | **Done** | `DailyCashRecomputeRepository.findAccountsAsOf` (replaces snapshot read) |
| Current / Savings grids (8 cols, no checkbox) | yes | **Done** | `wecms-web` `DailyCashPage.tsx` `AccountLedger` ×2 |
| Cash table + checkbox removal | yes | **Done** | Redmine #42096 — `DailyCashClient.tsx` / `DailyCashPage.tsx` doc comments |
| Currency handling (`total_cash = 0`, cash source dropped) | scope-reduced | **Done (by scope)** | `CASH_REPY_MSTR` not in PROD active list → `cash=0` |
| Excel / Print export | yes | **Partial** — works, but **frontend-side** (`xlsx-js-style` + print iframe) bypasses the wired backend `/export` single-producer | `wecms-web/lib/dailyCashExport.ts`; backend `DailyCashReportExportProvider` exists but UI does not call it |
| Per-tenant local currency | deferred | **Hardcoded `VND`** (`DailyCashReportService.LOCAL_CCY`) — legacy resolves it dynamically | gap-analysis #6 / OQ-3 |
| Last-collection datetime | out of scope | **Not ported** — `last_collected_date` null on live path; `BAT_CALL_HIS` dropped V31 | gap-analysis #5 |
| Audit log on search + export | yes | **Done** | both write `WSVC_ID = "daily-cash-report"` (slug) |

**Completion estimate (evidence-based):**

- **≈ 100 % of the in-scope read & display surface** — search, three-tier response, DB-verified
  account-type bucketing, live recompute, the two grids, summary, and the Cash-table/checkbox cleanup
  are all implemented and **frontend/backend aligned**. This is the surface the user described as
  "completed", and it is.
- **≈ 90 % counting residual technical debt** — three known items remain (none block the screen):
  1. **Export is a dual path** — the UI generates the workbook client-side instead of calling the
     backend `/export` provider, which violates the single-producer rule (gap #2 / fix-plan #4).
  2. **Local currency is hardcoded `VND`** rather than resolved per tenant (gap #6 / OQ-3).
  3. **Dead code** — orphaned `DailyCashHoldingRow` type, `mockData.ts`, the UPPER_SNAKE
     `fetchDailyCash` legacy fetcher + mappers, and the unconsumed `currency_flow` tier (cleanup).

> **Verdict:** the Daily Cash refactor is **functionally complete and FE/BE-aligned for its in-scope
> surface**; it is **not** a literal "100% parity" with legacy because physical cash, per-tenant local
> currency, and the last-collection datetime are deliberately out of scope or deferred, and the export
> single-producer rule is still pending. See [refactor-gap-analysis.md](refactor-gap-analysis.md) for the
> full parity table and the precise "completed vs remaining" breakdown.

### Migration complexity: **Low–Medium**

- **Lowering factors:** single read service, no transaction drilldown, no recompute-write ownership
  (cash source out of scope), and it reuses already-built `AcctMstr` / `AcctTrnsHstr` entities plus the
  shared export + `BankCdResolver` infrastructure.
- **Raising factors:** faithfully replicating the legacy `rec1` balance-as-of + same-day debit/credit
  aggregation in one native query, and the `rec2` per-type bucketing in Java, had to be DB-verified
  against the live functions (done — parity §1.5) to avoid the earlier fabricated-account-code defect.
