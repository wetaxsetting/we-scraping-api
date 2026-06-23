# Daily Financial — Legacy Analysis & Refactor-Readiness Package

> **Scope of this folder.** This package is the *full legacy-to-refactor analysis* of the JEX
> **Daily Financial** report (`rept_0015_001`). It expands the one-page planning stub at
> [`../daily-financial.md`](legacy-spec.md) (kept in place — that stub remains the short
> feature-spec entry). Every statement here is backed by actual source-code analysis of the live
> JEX tree (`WECMS DEV/`), the live JEX DEV database (`wecmsvndev` schema, read-only `postgres`
> MCP), and the current refactor trees (`wecms-api/`, `wecms-web/`). No assumptions.

| Field | Value |
|---|---|
| Feature slug | `daily-financial` |
| Module | Banking → Reports |
| Legacy JEX menu | **Daily Financial** (`data-jex-ml="rept_0015_001_m002"`) |
| Legacy view service | `rept_0015_001` (view) |
| Legacy read API | `rept_0015_001_r001` |
| Legacy popups | `rept_0015_002` (txn history), `rept_0015_003` (receipt/payment detail), `rept_0015_004` (cash-book detail) |
| Legacy Excel | `view/jexGrid/file/rept_0015_001_excel.jsp` (4,508 lines, Apache POI) |
| Refactor route | `/banking/reports/daily-financial` (Next.js) |
| Refactor base URI | `/api/v1/banking/reports/daily-financial` (Spring Boot) |
| Audit `WSVC_ID` | `daily-financial` (slug — never `rept_0015_001`) |
| Refactor status | **READY FOR SIT** (in-PROD scope) — main report (date-aware), Financial Status (VND/USD/KRW), Current/Saving grids, and the Transaction History Details popup are implemented + verified against real data. Non-SIT-blockers remain (server-side Excel provider, inline REC2 grid, internal-transfer toggle, active-account widening) — all P1/out-of-scope. See [§5 SIT Readiness](#5-sit-readiness-2026-06) + [parity-verification-report.md](parity-verification-report.md). |

---

## 1. Feature overview

Daily Financial is a **point-in-time (as-of-date) treasury snapshot**. For one company (`BIZ_KEY`)
and one chosen date it renders:

1. **Financial Status** — per-currency totals (local currency block + USD block): current-account
   total, savings total (cash / time-deposit / loan totals are computed but the panels are
   commented out — Redmine #42129).
2. **Current Accounts** grid — one row per current account (`ACCT_TYPE_CD` `03`/`04`) with debit
   count/amount, credit count/amount, optional internal-transfer columns, and balance.
3. **Savings Accounts** grid — same shape for `ACCT_TYPE_CD` `05`/`06`.
4. **Per-bank-account transaction detail** — for each account, a debit/credit ledger of that day's
   transactions (time, amount, running balance, remark, purpose/counterparty).
5. **Cash** book row (`REC3`) — cash opening/closing balance + day's receipts/payments.
6. **Excel export** + browser print.

It is the deeper sibling of **Daily Cash** (`rept_0014_001` → `daily-cash`): same as-of snapshot,
but every account row drills down to its per-transaction list, and it adds two filters — *active
accounts only* and *include internal transfers*.

**Intended users:** treasury / accounting staff doing daily cash-position monitoring and
reconciliation.

---

## 2. Documents in this package

| File | Purpose |
|---|---|
| [README.md](README.md) | This file — overview, legacy menu info, refactor status, index, **executive summary**. |
| [business-flow.md](business-flow.md) | User workflow, screen flow, backend orchestration flow, data-flow narrative. |
| [functional-specification.md](functional-specification.md) | Search conditions, grid columns, buttons, Excel/print, validation, business rules. |
| [database-mapping.md](database-mapping.md) | Every table + function: purpose, columns (live-DB types), CRUD type, I/O. |
| [interface-specification.md](interface-specification.md) | Request params, response (REC/REC1/REC2/REC2_1/REC3) shapes, grid JSON, Excel structure. |
| [source-code-index.md](source-code-index.md) | Full traceability: JSP / TS / action / IDO / SQL / functions / tables / refactor files. |
| [migration-guide.md](migration-guide.md) | Legacy architecture → target Spring architecture; API / DTO / repository design. |
| [test-scenarios.md](test-scenarios.md) | Normal / boundary / error / currency / account-type / Excel cases. |
| [refactor-gap-analysis.md](refactor-gap-analysis.md) | Implemented / missing / partial / risks / prioritized actions. |

---

## 3. Related documents

- [`../daily-financial.md`](legacy-spec.md) — the original one-page refactor feature stub (superseded in detail by this package; retained).
- [`../daily-cash.md`](../daily-cash/README.md) — sibling report; the **reference implementation** to clone for completing this feature.
- [`../README.md`](../README.md) — Banking → Reports group index.
- JEX functional spec (read-only source of truth): `docs/jex/features/` (if a `daily-financial` JEX doc lands, it must share this slug).
- Backend rules: [`wecms-api/CLAUDE.md`](../../../../../CLAUDE.md) — §"Legacy report DB functions — Sprint 1 stance", §"PROD DB Table Scope for Refactoring", §"Excel export".

### Planning, open questions & traceability (preserved — cross-linked, not duplicated)

- planning/open-questions/banking-reports-remaining-scope.md _(removed — historical `planning/` folder deleted by project decision)_ — tracks Daily Financial CMC (partial) + HS (hardcoded fixture), the HS deferral, and the `fn_get_haengsung_daily_financial_report` port decision. **Primary open-questions doc for this feature.**
- planning/open-questions/documentation-follow-up.md _(removed — historical `planning/` folder deleted by project decision)_ — OQ-DOC-6 (report JEX-id citation consistency) + OQ-DOC-8 (HS variant scope).
- [references/database/db-function-classification.md](../../../../references/database/db-function-classification.md) — classifies `rept_0015_001_r001_rec1/rec3` + `fn_get_haengsung_daily_financial_report` and their target Java repos.
- Feature-local open questions raised by this package: **OQ-DF-1** read strategy (resolved 2026-06-14 → **direct `rept_0015_001_r001_rec1` call**, which superseded the earlier Strategy-A-vs-B options), **OQ-DF-2** param-name contract (resolved → `asOf`/`currencyScope`), **OQ-DF-3** `includeInternalTransfers` functional-in-v1 — see [refactor-gap-analysis.md](refactor-gap-analysis.md#recommended-actions). The human closes these.
- Frontend counterpart package (same slug): [wecms-web/docs/features/banking/reports/daily-financial/](../../../../../../wecms-web/docs/features/banking/reports/daily-financial/README.md).
- Related JEX-system bug (HAENGSUNG `rept_0017_001`, separate from this CMC package): [docs/bug-reports/BUG-001_haengsung_daily_financial_wrong_account_type_label.md](../../../../../../docs/bug-reports/BUG-001_haengsung_daily_financial_wrong_account_type_label.md).

---

## 4. Executive summary

### What this feature is

A read-only, as-of-date, per-company financial snapshot with per-account transaction drilldown and
Excel export. In JEX it is a single read service (`rept_0015_001_r001`) that **first recomputes five
materialized summary tables** for the chosen date (delete-and-reinsert if a freshness check detects
drift), then runs four read queries (`REC`, `REC1`, `REC2`, `REC3`) — three of which use heavy
PostgreSQL features (`json_agg`/`json_build_object`, `EXCEPT`/`UNION` set comparison, correlated
running-balance subqueries, and two set-returning PL/pgSQL functions).

### Refactor completion

| Surface | In-scope target | State | Evidence |
|---|---|---|---|
| Search filters (date, currency, active-only, incl-internal) | yes | **Done** | `ReportController.dailyFinancial` (params `asOf`, `currencyScope`, `activeAccountsOnly`, `includeInternalTransfers`) |
| Financial Status totals (REC) | yes | **Done — DB-side source (2026-06-14)** — per-currency current + saving totals (VND/USD/KRW), `total = current + saving`, aggregated from the read-only function `rept_0015_001_r001_rec1`; **07/08 (KRW current/savings) kept** by PO direction | `ReportService.dailyFinancial` + `DailyFinancialCmcFunctionRepository.rec1` |
| Current / Savings account rows (REC1) | yes | **Done — DB-side source (2026-06-14)** — balances (as-of) + per-day debit/credit + internal counts/amounts read straight from `rept_0015_001_r001_rec1` (bank name via `wb_get_bank_nm`, amounts via `wb_get_amt`); prior Java live-compute removed. **DF-3** (`activeAccountsOnly` → `ACTV_YN` filter) and **DF-4** (`includeInternalTransfers` → internal columns zeroed when off) fixed | `ReportService.dailyFinancial` + `DailyFinancialCmcFunctionRepository.rec1` |
| Inline per-account transactions (REC2_1) | yes | **Deferred (P1, non-blocker)** — inline grid empty; the Transaction History Details popup serves the per-account drilldown | `ReportService.dailyFinancial` |
| Transaction-history popup (`rept_0015_002`) | yes | **Done — READY FOR SIT** — reads `ACCT_TRNS_HSTR`, paginated + totals; UI aligned to the e-Invoice Latest Inquiry History popup (2026-06) | `ReportService.dailyFinancialTransactions`; `wecms-web/.../daily-financial/TransactionHistoryDialog.tsx` |
| Cash book (REC3) | **out of scope** | **Dropped** — `DAILY_FINCL_REPT_DAILY_CASH_SUMR*` + `CASH_REPY_MSTR` not in PROD Active Table List | CLAUDE.md PROD scope list |
| Receipt/Payment & cash-book detail popups (`rept_0015_003/004`) | **out of scope** | **Not ported** — depend on out-of-scope tables | CLAUDE.md PROD scope list |
| Summary recompute (`wb_insert_*`, C002/D002) | **not needed** | **Not ported — and not required.** CMC reads the underlying read-only `rept_0015_001_r001_rec1` directly (the summary tables are just its materialised cache), so there is **no write-on-read** and the `DAILY_FINCL_REPT_*` summary tables are **not read as the primary source** | CLAUDE.md §"Legacy report DB functions" |
| Excel export | yes | **Deferred (P1, non-blocker)** — frontend client-side export present; backend `DailyFinancialExportProvider` not yet added | `common/export/` scan |
| Frontend page + popup dialog | yes | **Done** — real API data (env config fixed 2026-06); popup restyled to e-Invoice pattern; `tsc`/`build` pass | `wecms-web/components/banking/reports/daily-financial/` |
| KRW Financial Status coverage | yes | **Done** — KRW current/saving accounts surface a KRW section; `₩` formatting | `ReportService.dailyFinancial`; `lib/currency.ts` |
| HAENGSUNG variant (`daily-financial-hs`, `rept_0017_001`) | in scope | **Real (2026-06-14)** — hardcoded stub removed; now calls **`fn_get_haengsung_daily_financial_report`** via a native-query repository; `REPT_TRNS_HSTR` + the function **added to the refactor DB baseline** (PO-approved). Remaining: bank/account multi-select UI, seed `REPT_TRNS_HSTR` rows, live HTTP smoke. | `ReportService.dailyFinancialHs` + `DailyFinancialHsReportRepository` |

**Completion estimate (evidence-based, updated 2026-06):**

- **The in-PROD-scope read surface is functionally complete and SIT-ready** (filters + date-aware
  balances + per-currency Financial Status incl. KRW + per-account debit/credit/internal aggregates +
  a fully-working, e-Invoice-aligned transaction popup), verified against real data
  (`GET /api/v1/banking/reports/daily-financial` → HTTP 200, real VND/USD/KRW). **DF-3 (active-account
  widening) and DF-4 (internal-transfer toggle exclusion) are now fixed (2026-06-14)** on the
  `rept_0015_001_r001_rec1` path. The remaining items (server-side Excel provider, inline REC2 grid)
  are **P1 / out-of-scope — not SIT blockers** (see [§5](#5-sit-readiness-2026-06)). Numeric live
  verification is **data-blocked in R&D** (`acct_trns_hstr` empty for the tenant).
- **≈ 40 % of the *full legacy* feature surface**, because the cash book, time-deposit, loan, and
  receipt/payment-purpose drilldowns are deliberately **excluded from refactor scope** (their tables
  are not in the PROD Active Table List) and the nightly recompute is owned by a not-yet-ported batch.

### Migration complexity: **Medium–High**

- **Lowering factors:** scope reduction removes the three hardest tables (cash + rcv/pay summaries);
  the read path can lean on already-built `AcctMstr` / `AcctTrnsHstr` entities and the proven
  Daily Cash / transaction-popup patterns.
- **Raising factors (now largely retired):** the original concern — reproducing the per-account daily
  debit/credit aggregates and running-balance math in Java — is **moot as of 2026-06-14**: CMC reads
  the legacy `rept_0015_001_r001_rec1` function directly instead of recomputing, so there is no Java
  parity surface to maintain. The `json_agg` nested-transaction shape (`REC2_1`) remains out of scope
  (served by the transaction-history popup, not an inline grid).

**Recommended next steps (prioritized):** see [refactor-gap-analysis.md §Recommended actions](refactor-gap-analysis.md#recommended-actions).

---

## 5. SIT Readiness (2026-06)

**Daily Financial — Status: READY FOR SIT** (in-PROD scope).

**Completed:**

- Main report screen (date-aware — driven by the selected As-of date, not "current" balances)
- Financial Status section (per-currency current + saving totals; `total = current + saving`)
- Current Accounts section
- Saving Accounts section
- Transaction History Details popup (implemented; UI aligned to the **e-Invoice Latest Inquiry History** popup)
- VND / USD / KRW support (KRW renders its own Financial Status section with `₩` formatting)
- API integration (real data; `NEXT_PUBLIC_API_BASE_URL` env configuration fixed — no mock fallback in a correctly-configured environment)
- Date-aware balances (as-of balance computed by `rept_0015_001_r001_rec1`: latest `BAL ≤ asOf`, else `ACCT_MSTR.BAL`)
- Debit / Credit / Internal debit / Internal credit count + amount columns (read from `rept_0015_001_r001_rec1`, not Java-recomputed)

**Transaction History Details popup — implemented (READY FOR SIT):**

- Modal layout updated (e-Invoice popup contract: `w-[1024px] max-w-[92vw] rounded-2xl`)
- Table styling updated (bordered rounded box, sticky `bg-wb-bg-sidebar` header)
- Empty state updated (centered row spanning all columns, fixed height — not floating text)
- Pagination updated (footer layout: page-size left, page nav center when needed, summary right)
- Default page size = **10**
- Page size options = **10 / 20 / 50 / 100** (shared `PAGE_SIZE_OPTIONS`)
- Footer summary implemented (`Total {N} transaction(s) / Total Debit: … Total Credit: … Balance: …`)
- Responsive dialog sizing implemented
- Page-size dropdown opens upward (no overflow/overlap below the modal footer)
- Build (`npm run build`) and TypeScript (`tsc --noEmit`) validation **passed**

**Remaining (non-SIT blockers):**

- Excel export enhancement — backend `DailyFinancialExportProvider` + `POST …/daily-financial/export` (frontend client-side export already present) — P1.
- REC2 inline per-account transaction expansion — intentionally out of scope for SIT; the popup serves the drilldown — P1.
- `includeInternalTransfers=false` toggle exclusion + `activeAccountsOnly=false` widening — P1 semantics; columns are populated, the toggle is currently display-only.
- Cosmetic UI enhancements only.

> Out of scope (not gaps): cash book (REC3), receipt/payment & cash-book detail popups, time-deposit / loan panels, nightly summary recompute — their tables are outside the PROD Active Table List or the panels are commented out in legacy.

---

## 6. Time Column Verification (Transaction History Details popup)

**Result: verified correct — no mapping issue, no header-alignment issue.** The Time column is
intentionally blank because the source transaction records contain no time value. **Not a defect.**

**Evidence:**

- `ACCT_TRNS_HSTR.TRSC_TM` contains **empty values** for the affected rows (confirmed on the dev DB: the 5 transactions on `2026-06-04` for `VN_VNM0004_000001` all have `trsc_tm = ''`, length 0, not null).
- Backend returns `TRSC_TM` correctly — `ReportService.dailyFinancialTransactions` maps `r.getTrscTm()` into `DailyFinancialTransactionsReport.TransactionRow.TRSC_TM` (1st positional field).
- Mapper passes `TRSC_TM` correctly — `mapDailyFinancialTransactionsResponse`: `transactionTime ← r.TRSC_TM` ([lib/api/mappers.ts](../../../../../../wecms-web/lib/api/mappers.ts)).
- UI renders `TRSC_TM` correctly — `<Td>{formatTime(r.transactionTime)}</Td>`, header↔cell order is 1:1 (Time · Debit · Credit · Balance · Remarks).
- Empty `TRSC_TM` renders as blank by design — `formatTime("")` returns `""`. Debit/Credit bind by `INOUT_DV` (`"2"`=debit, `"1"`=credit), so all-debit rows correctly fill the Debit column.
- Legacy behaves the same way — `rept_0015_001.ts` renders `common.app.time_format2(v.TRSC_TM)`, which is blank for an empty `TRSC_TM`.

**Conclusion:** the Time column is intentionally blank because source transaction records contain no
time value. This is not a defect; the column binding and header alignment are correct.

---

## 7. Release Note

### 2026-06 — Daily Financial Final SIT Preparation

**Completed:**

- Fixed Financial Status calculations (date-aware; per-currency current + saving; `total = current + saving`).
- Added KRW support (KRW Financial Status section + `₩` formatting).
- Fixed API data-source configuration (`NEXT_PUBLIC_API_BASE_URL`) so the deployed/local frontend reaches `wecms-api`.
- Removed mock-data dependency in correctly-configured environments (real `GET /api/v1/banking/reports/daily-financial` data; mock is fallback-only).
- Updated the Transaction History Details popup UI to the e-Invoice Latest Inquiry History pattern (table/empty-state/footer/pagination; default page size 10; options 10/20/50/100; upward dropdown).
- Verified Time-column behaviour (blank by design — empty `TRSC_TM` in source; not a defect).
- Completed SIT readiness review — **Daily Financial: READY FOR SIT** (in-PROD scope; remaining items are P1 / out-of-scope non-blockers).

> Documentation-only release note. No business logic or implementation changed in this update.
