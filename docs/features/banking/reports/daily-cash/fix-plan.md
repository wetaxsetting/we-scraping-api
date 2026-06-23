# Daily Cash — Fix Plan

**Feature:** Banking > Reports > Daily Cash (`rept_0009_001`) — refactor route `/banking/reports/daily-cash`
**Date:** 2026-06-03 · **OQ-1 resolved 2026-06-04** (DB function bodies read from DEV).
**Source of findings:** [refactor-parity-verification.md](./refactor-parity-verification.md) §1.5
**Status:** **Issues 1 & 2 IMPLEMENTED 2026-06-04** (the two CRITICAL bugs). Issues 3–8 remain plan-only (non-critical, gated on their OQs).

> **2026-06-04 — CRITICAL fixes landed.**
> - **Issue 1 (backend):** `DailyCashReportService.java` — removed fabricated `01/11/12/13/21` constants; bucketing now drives off `AcctTypeCd.fromCode(...)` per the DB-confirmed §1.5 mapping (`03/04`→demand, `05/06`→savings, `02`→term, `01`→loan, `07/08`→none, `cash`→always 0). Added `DailyCashReportServiceTest` (4 tests, all green).
> - **Issue 2 (frontend):** `DailyCashClient.tsx` — Current = `03/04`, Savings = `05/06`; `01/02/07/08` dropped from both tables (legacy parity). `tsc --noEmit` + eslint clean.
> - **Summary UI rework (legacy/Daily-Financial parity, follow-on to Issues 1–2):** `DailyCashPage.tsx` — old Cash holdings table **removed**; summary restyled to a per-currency block `"Daily Cash Status — <CCY> Total : <amount>"` + 2-column Current/Saving table; ordered VND→USD→KRW→others; **always renders after Search** with a zero-row fallback for VND/USD/KRW when the API summary is empty; as-of card restyled to the gray DD-MMM-YYYY band; summary amounts formatted via `formatAmount`. Locale key `report.daily_cash.currency_total` updated (en/ko/vi). Issue 3 partially addressed (summary cells formatted; **detail-table** cells still raw).
> Export (Issue 4) intentionally **not** touched this pass.

Confirmed bugs are ordered by severity. Issues 1–2 are correctness bugs (wrong data). Issues 3–8 are parity divergences mapped to the verification doc's Open Questions — fix after PO sign-off on each OQ.

---

## Issue 1 — Backend buckets accounts on fabricated codes `01/11/12/13/21` 🔴 Critical

**Root cause.** `DailyCashReportService` defines its own account-type constants (`TYPE_CASH="01"`, `TYPE_DEMAND="11"`, `TYPE_SAVINGS="12"`, `TYPE_TERM="13"`, `TYPE_LOAN="21"`) that do not match the legacy/refactor `AcctTypeCd` enum (`01–08`). `DAILY_CASH_REPT_DAILY_SUMR_DETL.ACCT_TYPE_CD` stores `01–08`, so codes `11/12/13/21` never match and `02–08` rows fall through `default`; `01` (legacy LOAN) is mislabeled as cash. Result: every `currency_summary` total and the derived `currency_flow.balance` is wrong/empty.

**Files to modify**
- `wecms-api/src/main/java/com/kosign/wecms/domain/banking/service/DailyCashReportService.java` (constants `:36-40`, switch `:137-144`).
- (Reuse, don't duplicate) `wecms-api/src/main/java/com/kosign/wecms/common/enums/AcctTypeCd.java` — already has the correct `01–08` mapping + `fromCode`.

**Implementation steps**
1. ~~Resolve OQ-1 first.~~ ✅ **Done 2026-06-04.** `rept_0900_001_rec2` / `rec4` were read from DEV via `pg_get_functiondef` (verification doc §1.5). The mapping below is now DB-confirmed, not inferred — no further investigation needed.
2. Delete the `TYPE_*` string constants. Drive the switch off `AcctTypeCd.fromCode(type)` (enum is the single source of truth — matches the "no hardcoded code literals" enum policy).
3. Map buckets to the **DB-confirmed** mapping below (from `rept_0900_001_rec2`):

   | Bucket (DTO field) | Codes | Status |
   |---|---|---|
   | `loan` (`total_loan`) | `01` | ✅ confirmed (`rec2`: `sum(case when acct_type_cd='01'…)`) |
   | `term` (`total_term_deposit`, 정기예적금) | `02` | ✅ confirmed (`rec2`: `='02'`) |
   | `demand` (`total_demand_deposit`, current) | `03`, `04` | ✅ confirmed (`rec2`: `in ('03','04')`) |
   | `savings` (`total_savings`) | `05`, `06` | ✅ confirmed (`rec2`: `in ('05','06')`) |
   | `cash` (`total_cash`) | **none** | ✅ confirmed: legacy `TOTL_CASH` is sourced from `cash_repy_mstr` via `rec4`→`rec3`, **not** from any `ACCT_TYPE_CD`. `cash_repy_mstr` is **out of the refactor PROD active-table scope** → set `total_cash = 0`. Do **NOT** bucket `01` (loan) into cash. |

   > **Parity refinement (supersedes the earlier draft):** do **NOT** add `07`→demand / `08`→savings. `rec2` sums only `01–06`; `07`/`08` contribute to no legacy summary bucket (and `07` already exists in DEV data — 19 rows — yet is excluded from every total). Including them would over-count vs. legacy. Map exactly `01–06`; let everything else fall through.
4. Keep the `default` no-op (unknown codes — incl. `07`/`08` — contribute nothing) but log at `debug` so future unmapped codes are visible.
5. Re-derive `currency_flow.balance` once buckets are correct (current formula `cash+demand+term+savings+loan` at `:96`). Note `currency_flow` (legacy `REC2`) is itself sourced from `cash_repy_mstr` in legacy (`rec3`) and is out of refactor scope + unconsumed by the FE — consider dropping it rather than computing a non-parity stand-in. Confirm with PO.

**Test scenarios**
- Seed/inspect detail rows for tenant `VN_VNM0004_000001` with codes `01–08` across VND + USD; assert each `currency_summary` bucket equals the sum of the matching codes' `BAL`.
- Unknown code (e.g. `99`) → contributes to no bucket, no exception.
- All-`02` data → `total_term_deposit` populated, others zero (regression guard against the old "everything drops" behavior).
- Compare per-currency totals against the legacy `rept_0009_001_r001` response for the same `REQ_DATE` (parity oracle).

**Risk:** Medium. Logic-local change; the mapping is now DB-confirmed (OQ-1 closed) so no blocker remains. Add/extend `DailyCashReportServiceTest`. The one residual judgement call is `total_cash` / `currency_flow` (both out-of-scope sources) — ship `total_cash = 0` and raise the `currency_flow` drop-vs-keep with the PO.

---

## Issue 2 — Frontend splits Savings on `"03"` (should be `05`/`06`) 🔴 Critical

**Root cause.** `isSavingType` returns `acctTypeCd === "03"`, but legacy `03` = Current Account (VND). Verified legacy split (`rept_0009_001_osmc.ts:622,748`): Current = `03||04`, Savings = `05||06`. The refactor misfiles current-VND accounts into Savings and pushes real savings into Current — an inverted split on the most common accounts.

**Files to modify**
- `wecms-web/components/banking/reports/daily-cash/DailyCashClient.tsx` — `isSavingType` (`:132-134`), its comments (`:77-80,129-131`), `rollupSummary` (`:147`).

**Implementation steps**
1. Replace the discriminator with the legacy-verified sets (`osmc.ts:622,748` — **exactly** `03/04` and `05/06`, no `07/08`):
   ```ts
   const CURRENT_ACCT_TYPES = new Set(["03", "04"]); // demand / current — legacy osmc.ts:622
   const SAVING_ACCT_TYPES  = new Set(["05", "06"]); // passbook / savings — legacy osmc.ts:748
   function isSavingType(c?: string | null) { return SAVING_ACCT_TYPES.has((c ?? "").trim()); }
   function isCurrentType(c?: string | null) { return CURRENT_ACCT_TYPES.has((c ?? "").trim()); }
   ```
   Do **not** add `07/08` — legacy renders neither in either table (and excludes them from summary totals too, §1.5). `07` exists in DEV data and is intentionally dropped by the legacy view.
2. Current section filter → `isCurrentType` (not "everything that isn't savings") so loan/term/`07` rows (`01`/`02`/`07`) don't leak into Current. Legacy parity: the VN view renders only Current (`03/04`) and Savings (`05/06`) — `01`/`02`/`07` rows have **no visible table** and are dropped. Switch the current filter from `!isSavingType` to the explicit `isCurrentType` so the drop is faithful.
3. Fix the misleading comments to state the real mapping.
4. Apply the same sets in `rollupSummary` (`:139-159`).

**Test scenarios**
- Mock `accounts[]` with one row per code `01–07`; assert Current table = `03,04` rows, Savings table = `05,06` rows, `01/02/07` excluded (legacy parity).
- VND current account (`03`) appears under Current, not Savings (the headline regression).
- Empty accounts → both tables show no-rows state.

**Risk:** Low. Pure client filter change, no API contract impact.

---

## Issue 3 — On-screen amounts render unformatted ⚠️ Medium (OQ none; clear bug vs. legacy)

**Root cause.** Ledger/summary cells print raw `String(value)` (`DailyCashClient.tsx:174-201`); legacy formats every cell currency-aware (VND `#,###`, USD `#,##0.00`).

**Files:** `DailyCashPage.tsx` table cell renderers; reuse `lib/currency.ts formatAmount(value, ccy, …)`.
**Steps:** Route every amount/balance/debit/credit cell through `formatAmount` with the row currency; counts through the integer formatter. Do not pre-format in the mapper — format at render.
**Test:** VND row shows `1.234.567`, USD row shows `1,234.56`.
**Risk:** Low.

---

## Issue 4 — Excel export is client-side `xlsx-js-style` (violates §13b) ⚠️ Medium — gated on OQ-8

**Root cause.** `lib/dailyCashExport.ts` builds the workbook in-browser; `wecms-web/CLAUDE.md §13b` mandates the shared `useExcelExport` hook → backend Apache POI (`DailyCashReportExportProvider` already exists, `screenId="daily-cash-report"`).
**Files:** `lib/dailyCashExport.ts`, `DailyCashPage.tsx` (`handleExportExcel`), wire via `hooks/useExcelExport.ts`. Backend provider already present.
**Steps (after OQ-8 sign-off):** Replace client workbook with `useExcelExport({ endpoint: "/api/v1/banking/reports/daily-cash-report/export", screenId, fileName, filters: { asOfDate, currency }, … })`; remove the `xlsx-js-style` import. Confirm the provider's section layout matches legacy (Current + Savings only) and verify the double-audit-write (search + provider) is intended.
**Risk:** Medium — changes export output format; needs a visual diff against legacy workbook + the §1 fix landed first (export reuses `service.search`, so it inherits Issue 1's correctness).

---

## Issue 5 — Remove unwired checkbox column from ledger tables ⚠️ Low — gated on OQ-7

**Root cause.** `AccountLedger` renders header + per-row `<Checkbox>` (`DailyCashPage.tsx:514-516,543-545`) with no state; legacy has no checkboxes on this report.
**Steps:** Remove the checkbox `<col>`/`<th>`/`<td>` (or hide behind a flag) once OQ-7 confirms row-selection isn't a wanted new feature.
**Risk:** Low.

---

## Issue 6 — Currency ordering not VND-first ⚠️ Low — gated on OQ-3

**Root cause.** Recorded against the superseded `findRows` path (`ccyCd ASC`). **As of 2026-06-13** the active path calls `rept_0900_001_rec1..4` directly via `DailyCashReportFunctionRepository`; the rec3 IDO carries the legacy VND-first ORDER BY, so re-verify whether this gap still reproduces before changing anything.
**Steps (after OQ-3):** If the gap persists on the direct-function path, add a VND-first ordering to match legacy REC2.
**Risk:** Low.

---

## Issue 7 — Missing last-aggregation timestamp ⚠️ Low — gated on OQ-2

**Root cause.** Legacy shows "영업집계 최종일시" via `rept_0100_001_r001` (BAT_ID `REPT_0900_001`, reads `BAT_CALL_HIS`); refactor has no equivalent. Note `BAT_CALL_HIS` was dropped in V31 (not in PROD active scope) — porting this needs a scope decision.
**Steps (after OQ-2):** If wanted, add a backend endpoint + FE display. Otherwise document as intentionally dropped.
**Risk:** Low (additive).

---

## Issue 8 — Print uses bespoke path instead of shared `lib/print.ts` ⚠️ Low

**Root cause.** `printDailyCashReport` builds its own iframe/HTML; §6d mandates the shared `printReport`. Also inherits the §1.3 split bug.
**Steps:** Migrate to `printReport({ title, summary, columns, rows })` in a stabilization pass; the §2 fix corrects the row membership.
**Risk:** Low.

---

## Suggested sequencing

1. ~~OQ-1 DB lookup~~ ✅ done (2026-06-04) — Issue 1 unblocked.
2. **Issue 1 (backend codes)** + **Issue 2 (frontend split)** together — same MR, full-stack ownership; restores correct data. Backend: map `01–06` per §1.5, `total_cash=0`. Frontend: split on `03/04` vs `05/06`.
3. **Issue 3 (formatting)** — quick win, same FE MR.
4. PO sign-off on OQ-2/3/4/5/6/7/8, then Issues 4–8 in a follow-up stabilization MR.
5. Update `wecms-web/CLAUDE.md` "Known contract mismatches" Daily Cash note (now stale — the live `/daily-cash-report` path *does* carry `acct_type_cd`).
