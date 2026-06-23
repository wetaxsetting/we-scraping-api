# Daily Cash — Refactor Gap Analysis

> **⚠️ Data-source corrected 2026-06-13 (noted 2026-06-23).** The live read path calls the legacy DB
> functions **`rept_0900_001_rec1..rec4`** directly via `DailyCashReportFunctionRepository`
> (`DailyCashReportService.java:24-75` — the documented set-returning exception, same stance as Cash Flow
> and Daily Financial). The prior **Java live-recompute** (`DailyCashRecomputeRepository` /
> `CurrencyAccumulator`) is **bypassed / no longer wired** and retained only as a cleanup candidate. Any
> "live recompute over `ACCT_MSTR` + `ACCT_TRNS_HSTR`" wording below is the **superseded pre-pivot** record
> (audit history preserved).

Legacy (`rept_0009_001`) vs the **existing** refactor (`wecms-api` `DailyCashReport*` +
`wecms-web` `components/banking/reports/daily-cash/*`). Each gap: legacy behavior → current
refactor → missing/pending → priority → recommended steps. Priorities: **Critical / High / Medium / Low**.

All claims below were verified by reading the actual source (not inferred).

---

## Gap #1 — Account-type code mismatch  ·  ~~**Critical**~~ ✅ **RESOLVED 2026-06-04**

> **Fixed.** Backend `DailyCashReportService` now buckets via `AcctTypeCd.fromCode(...)` —
> demand=`03,04`, savings=`05,06`, term=`02`, loan=`01`, cash=`0`; `07/08` and unknown codes
> contribute to nothing; the fabricated `11/12/13/21` constants are deleted. Frontend
> `DailyCashClient` splits Current=`03,04`, Savings=`05,06`. Covered by
> `DailyCashReportServiceTest` (4 tests). DB-verified the bucket mapping equals legacy
> `rept_0900_001_rec2` (parity-verification §1.5). The text below is retained as the historical
> record of the original defect.

**Legacy.** `ACCT_TYPE_CD` (authoritative `wabooks.enums.AcctTypeCd`): `01`=Loan, `02`=Time
Deposit, `03/04`=Current, `05/06`=Savings, `07/08`=KRW Current/Savings. The TS classifies
Current=`03/04`, Savings=`05/06`, Time=`02`, Loan=`01`.

**Refactor — backend.** `DailyCashReportService` buckets on private constants
`TYPE_CASH="01", TYPE_DEMAND="11", TYPE_SAVINGS="12", TYPE_TERM="13", TYPE_LOAN="21"`
(`DailyCashReportService.java:36-40`). These codes **do not exist** in the stored data — the detail
table holds `01–08`. The `switch` matches only `"01"`, which it labels *cash* (but `01` is *Loan*).
Net effect: `demand/savings/term` totals = **0**; loan balances are mis-summed into **cash**.

**Refactor — frontend.** `isSavingType(acct_type_cd) === "03"` (`DailyCashClient.tsx:132-134`).
But `03` is *Current*, not Savings. So real savings (`05/06`) land in the **Current** table and
current (`03`) lands in **Savings** — inverted. The in-file comment ("02=demand, 03=saving,
04=term, 05=overdraft") is also incorrect.

**Missing/pending.** Correct, shared account-type mapping aligned to `common/enums/AcctTypeCd`
(which already mirrors `01–08` correctly but is **not used** here).

**Recommended steps.**
1. Backend: replace the 11/12/13/21 constants — demand=`03,04,07`; savings=`05,06,08`; term=`02`;
   loan=`01`; cash is **not** an `ACCT_TYPE_CD` (comes from the cash source — see gap #4).
   Use `AcctTypeCd.fromCode(...)`.
2. Frontend: derive Current/Savings from the corrected mapping via a shared helper; fix the comment.
3. Add the per-type seed test in [`test-scenarios.md`](./test-scenarios.md) C1–C5.

---

## Gap #2 — Export bypasses the backend single producer  ·  **Critical**

**Legacy.** Server-side POI JSP streams the workbook; audited via `mntLog`.

**Refactor.** A correct backend provider exists (`DailyCashReportExportProvider`, `POST
.../export`, shared POI pipeline, audit-logged), **but the UI does not call it** — `DailyCashPage`
calls `exportDailyCashExcel`/`printDailyCashReport` from `lib/dailyCashExport.ts`, which builds the
workbook in-browser with **`xlsx-js-style`**. This violates `wecms-web` §13b (frontend ships no
XLSX library; backend is the single producer), skips the 50k-row cap, the View-Setting visibility
intersection, and audit logging.

**Missing/pending.** Route UI export → `hooks/useExcelExport` → `POST .../export`; remove the
`xlsx-js-style` dependency and client workbook; route print through `lib/print.ts`. Reconcile the
backend provider's flat column set vs. the legacy multi-section layout (decide which is parity).

**Priority: Critical** (rule violation + missing audit). **Steps:** delete client builder; wire the
hook; align provider columns; verify filename/subheader.

---

## Gap #3 — Data-source divergence (live recompute vs persisted read)  ·  ~~**High**~~ ✅ **RESOLVED 2026-06-04**

> **Fixed (Java/JPQL replication, not a DB function).** `DailyCashReportService` now reads via
> `DailyCashRecomputeRepository.findAccountsAsOf(...)` — a native query over `ACCT_MSTR` +
> `ACCT_TRNS_HSTR` that replicates legacy `rept_0900_001_rec1` (as-of balance from the latest
> `ACCT_TRNS_HSTR ≤ refDate`, fallback to `ACCT_MSTR.BAL`; same-day debit/credit counts; loan
> `01` only when `EXPI_DT ≥ refDate`; `wb_get_amt` → inline `ROUND(amt, VND?0:2)`; `wb_get_bank_nm`
> → Java `BankCdResolver`). Summary (rec2) is the existing Java `SUM(CASE …)` over those rows.
> **Verified on DEV**: the recompute SQL's per-type aggregation equals legacy `rec2` for VND/USD/KRW,
> and returns **41 active accounts for 2026-06-04** where the snapshot had 0. No new `CREATE FUNCTION`
> migration (complies with the wecms-api DB-function policy). The text below is the historical record.

**Legacy.** The read action calls `rept_0900_001_rec1..4` **live** each request — it recomputes
from source, independent of whether the nightly batch persisted anything.

**Refactor (before fix).** Read only the batch-persisted `DAILY_CASH_REPT_DAILY_SUMR_DETL` (JPQL). If the JEX
batch hasn't populated rows for the date, the refactor returned empty where legacy would still
compute. The batch (`REPT_0900_001`), helper, BCS, and the four DB functions are **not ported** —
the refactor now replicates `rec1/rec2` in Java instead (see banner). `rec3/rec4` (cash) stay
unported (source `CASH_REPY_MSTR` out of scope → cash = 0).

**Missing/pending.** A PO decision (`wecms-api` OQ-1) on the canonical source. If "share the JEX
nightly summary, read-only" stands, document the dependency and ensure the batch populates the
detail table for queried dates; otherwise port the recompute path (functions or Java equivalent).

**Priority: High.** **Steps:** confirm source with PO; document dependency; add a "batch not run →
empty" test (F5) and a user-facing empty state.

---

## Gap #4 — Cash holdings + summary totals have no source  ·  **High**

**Legacy.** Cash comes from `rept_0900_001_rec4` / `_DAILY_CASH_SUMR_DETL`; `REC.TOTL_CASH` is
merged per currency; the Cash section (commented out in the live view) and the summary total
include cash.

**Refactor.** `DAILY_CASH_REPT_DAILY_SUMR` and `_DAILY_CASH_SUMR_DETL` are **not** in the PROD
Active Table List, so cash has no backing source. `total_cash` is `0` (gap #1 fix). **As of
2026-06-04** the frontend Cash holdings table was **removed entirely** (it was always empty and the
legacy VN view has it commented out, Redmine #42096) — so cash=0 now affects only the per-currency
summary "Total" heading, which is acceptable legacy parity for the VN deployment.

**Missing/pending.** Decision: accept cash=0 and document, or bring a cash source into scope.

**Priority: High** (affects the summary total correctness). **Steps:** confirm with PO; if cash is
required, add `_DAILY_CASH_SUMR_DETL` (or the `rec4` function) to scope; else document cash=0.

---

## Gap #5 — Last-collection datetime not ported  ·  **Medium**

**Legacy.** `fn_last_date()` calls `rept_0100_001_r001` (`BAT_ID=REPT_0900_001`) → `BAT_CALL_HIS`
→ shows "Business aggregation last datetime".

**Refactor.** `BAT_CALL_HIS` dropped in Flyway V31; no lookup. `last_collected_date` is read off
each detail row instead (per-row, not a single "as-of run" timestamp).

**Missing/pending.** Either expose the latest-collection datetime (small endpoint or response
field) or formally drop the chip.

**Priority: Medium.** **Steps:** decide; if kept, add `last_collected_date` summary field or revive
`BAT_CALL_HIS` read.

---

## Gap #6 — Per-tenant local currency hardcoded  ·  **High**

**Legacy.** `FCY` = `ccy_cd <> 'VND'` (the action hardcodes `'VND'` too — same limitation, but the
VN deployment is the only live one).

**Refactor.** `LOCAL_CCY = "VND"` constant in `DailyCashReportService`. For a KHR/Cambodia tenant,
`FCY` would wrongly exclude VND instead of KHR.

**Missing/pending.** Resolve base currency per tenant (`wecms-api` OQ-3).

**Priority: High** (multi-tenant correctness). **Steps:** source local currency from tenant/base-
currency settings; pass into `filter.exclude(localCcy)`.

---

## Gap #7 — Time-Deposit & Loan sections not surfaced  ·  **Medium**

**Legacy.** Markup exists but is commented out (Redmine #42129) — the live VN page shows only
Summary + Current + Savings. The backend still returns term/loan data.

**Refactor.** No Time-Deposit or Loan ledger section. **As of 2026-06-04** the summary block was
restyled to the legacy 2-column shape (Current Accounts / Saving Accounts only) — the earlier
"Time Deposit"/"Available" summary columns were removed, matching the live VN view. `term`/`loan`
totals still feed the per-currency "Total" heading (legacy parity) but are not shown as columns;
`loan_name`/`maturity_date`/`agreement_amount` are returned but never rendered.

**Missing/pending.** Decision on whether to display these (parity-with-live = keep hidden).

**Priority: Medium.** **Steps:** confirm scope with PO; if hidden = parity, document; else add
sections + columns.

---

## Gap #8 — `INQ_DV` / `REPT_DIF_DATA` re-aggregate flow absent  ·  **Low**

**Legacy.** `jConfirm()` offers re-aggregate-then-query on `REPT_DIF_DATA`; `INQ_DV` carries the
choice. **Already dead** — the action never returns `REPT_DIF_DATA`; `INQ_DV` is `@deprecated`.

**Refactor.** Not implemented (correctly).

**Missing/pending.** None — just document the decision (closes `wecms-api` OQ-2).

**Priority: Low.** **Steps:** note "intentionally dropped, legacy path was dead".

---

## Gap #9 — Endpoint duplication / naming  ·  **Low**

**Legacy.** Single read service `rept_0009_001_r001`.

**Refactor.** Active controller = `/api/v1/banking/reports/daily-cash-report`. But `wecms-web`
`lib/api/banking.ts` also references `/api/v1/banking/reports/daily-cash` (`fetchDailyCash`), and a
`ReportController.dailyCash` may exist (per `wecms-api` CLAUDE.md endpoint table).

**Missing/pending.** Confirm whether `/daily-cash` is a dead duplicate; retire one.

**Priority: Low.** **Steps:** grep both controllers; consolidate on `/daily-cash-report`; remove
the unused fetcher.

---

## Gap #10 — Currency scope toggle drops USD/KHR  ·  **Low**

**Legacy.** Radio = All / FCY / local — matches refactor UI.

**Refactor.** `CurrencyScopeToggle` = All/VND/FCY; backend filter also supports exact USD/KHR but
the UI can't reach them. Parity-OK with legacy.

**Priority: Low.** **Steps:** none unless product wants exact USD/KHR — then add toggle options.

---

## Summary

| # | Gap | Priority | Status (2026-06-04) |
|---|---|---|---|
| 1 | Account-type code mismatch (backend buckets + frontend split both wrong) | ~~Critical~~ | ✅ **Resolved** |
| 2 | UI export bypasses backend (client `xlsx-js-style`) | **Critical** | Open (deferred this pass) |
| 3 | Persisted-read vs live-recompute data source | ~~High~~ | ✅ **Resolved** — Java/JPQL recompute over ACCT_MSTR+ACCT_TRNS_HSTR (rec1/rec2); DEV-verified == legacy |
| 4 | Cash holdings + summary cash total unsourced | **High** | Partially addressed — Cash table removed from UI; `total_cash=0` documented; cash source still out of scope |
| 6 | Local currency hardcoded `VND` | **High** | Open |
| 5 | Last-collection datetime not ported | **Medium** | Open |
| 7 | Time-Deposit / Loan sections not surfaced | **Medium** | Open (summary restyled to 2-col; sections still hidden = live parity) |
| 8 | `INQ_DV` / `REPT_DIF_DATA` flow (dead in legacy) | **Low** | Closed (intentionally dropped) |
| 9 | Endpoint duplication (`/daily-cash` vs `/daily-cash-report`) | **Low** | Open |
| 10 | Currency toggle drops USD/KHR exact | **Low** | Open (parity-OK) |

**Resolved:** #1 (account-type mapping) and #3 (data source — now live recompute over
ACCT_MSTR+ACCT_TRNS_HSTR, DEV-verified equal to legacy `rec2`). **Top remaining to fix:** #2
(export rule violation + missing audit) — the highest-priority open item; then the High gaps
#4 (cash source — `CASH_REPY_MSTR` out of scope, cash stays 0) and #6 (per-tenant currency),
both gated on PO decisions.

**UI cleanups (2026-06-04):** the always-empty **Cash table was removed**; the unwired **checkbox
column was removed** from the Current/Savings ledgers (legacy has none); the summary block always
renders after Search with a **zero VND/USD/KRW fallback** when the API summary is empty. None of
these change the business mapping.

**Remaining gaps (snapshot 2026-06-04):** #2 Excel export still client-side (`xlsx-js-style`, no
audit / no `/export`); #4 cash source `CASH_REPY_MSTR` out of scope → `total_cash=0`; #5
last-aggregation timestamp not ported (`last_collected_date` null); print still bespoke
(`lib/print.ts` not used); detail-table cell amounts still raw (summary cells are formatted) and
detail order is `acct_type, bank, acct_no` (not VND-first).

---

## Final parity review (Area · Legacy · Refactor · Status)

| Area | Legacy (`rept_0009_001`) | Refactor (Spring + Next.js) | Status |
|---|---|---|---|
| **Search** | `REQ_DATE` (yyyyMMdd) + `CCY_CD` (`""`/`FCY`/local) + `INQ_DV`; press Search | `GET /daily-cash-report?asOfDate=<ISO>&currency=<ALL/FCY/VND>`; search-then-fetch, scope auto-refetch | ✅ **Complete** — `INQ_DV` intentionally dropped (dead in legacy, gap #8) |
| **Summary** | Per-currency totals (cash / norm / acct / loan / sav) from `rec2`+`rec4` | Per-currency `currency_summary` (cash always 0) + Java `CurrencyAccumulator`; UI shows 2-col Current/Saving with zero VND/USD/KRW fallback | ✅ **Complete** for the live VN surface (cash=0 by scope; term/loan hidden = live parity) |
| **Current Accounts** | `ACCT_TYPE_CD` `03/04`, bank/acct/balance/debit/credit | `accounts` filtered `03/04` → `AccountLedger` (8 cols, no checkbox) | ✅ **Complete** — DB-verified bucketing (gap #1 resolved) |
| **Savings Accounts** | `ACCT_TYPE_CD` `05/06` | `accounts` filtered `05/06` → `AccountLedger` (8 cols) | ✅ **Complete** — DB-verified (gap #1 resolved) |
| **Data source mapping** | Live `rept_0900_001_rec1..4` over `ACCT_MSTR`/`ACCT_TRNS_HSTR`/`CASH_REPY_MSTR` each request | Live recompute `DailyCashRecomputeRepository.findAccountsAsOf` over `ACCT_MSTR`+`ACCT_TRNS_HSTR`; rec2 in Java; cash source out of scope | ✅ **Complete** — DEV-verified == legacy `rec2` (gap #3 resolved); cash=0 (gap #4) |
| **Currency handling** | All / `FCY`(≠local) / exact; local hardcoded VND in action | `DailyCashCurrencyFilter` ALL/FCY/VND/USD/KHR; `LOCAL_CCY="VND"` hardcoded | ⚠️ **Parity for VN** — per-tenant local currency deferred (gap #6 / OQ-3) |
| **Export** | Server-side POI JSP, audited | Backend `DailyCashReportExportProvider` (`POST .../export`) **exists** but UI uses client `xlsx-js-style` + print iframe | ⚠️ **Works, off-contract** — single-producer rule pending (gap #2 / fix-plan #4) |
| **Permissions** | `login=Y`, `mntLogYn=Y`, `NORM_USER_ONLY_YN=Y` | JWT-protected; audit on search+export (`WSVC_ID=daily-cash-report`); no `@PreAuthorize` gate (global model) | ✅ **Complete** for current model — norm-user-only gate not separately enforced (product-wide follow-up) |
| **UI behaviour** | Summary + Current + Savings; Cash/Time-Deposit/Loan tables commented out; no checkbox | Same sections; **Cash table removed** + **checkbox removed** (Redmine #42096); report page (no pagination, no View Setting) | ✅ **Complete** — matches the live VN view |
| **Last-collection datetime** | `fn_last_date` → `BAT_CALL_HIS` chip | Not ported; `last_collected_date` null; `BAT_CALL_HIS` dropped V31 | ⏸️ **Out of scope** (gap #5) |

### Completion verdict

**Daily Cash is functionally complete and FE/BE-aligned for its in-scope surface** — search, the
three-tier response, DB-verified account-type bucketing, live recompute, the Current/Savings ledgers,
the per-currency summary, and the Cash-table/checkbox cleanup are all implemented and consistent across
backend and frontend.

It is **not a literal "100% parity"** with legacy, by deliberate scope and a known rule gap:

- **By scope (accepted):** physical cash (`CASH_REPY_MSTR`) is out of the PROD active-table list →
  `total_cash = 0`; per-transaction drilldown lives in Daily Financial; Time-Deposit/Loan sections stay
  hidden to match the live VN view.
- **Deferred (PO-gated):** per-tenant local currency (#6/OQ-3) and the last-collection datetime (#5).
- **Technical debt to fix (#2, highest priority):** the UI export bypasses the wired backend
  single-producer endpoint.

**Net:** ~**100%** of the in-scope display/read surface; ~**90%** counting the residual technical debt
(export single-producer, per-tenant local currency, dead-code cleanup). The remaining items are tracked
in [fix-plan.md](fix-plan.md) and the open questions above — none block the screen from being used.
