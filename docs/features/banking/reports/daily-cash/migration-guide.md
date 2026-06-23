# Daily Cash — Migration Guide (JEX → Spring Boot + Next.js)

> **⚠️ Data-source corrected 2026-06-13 (noted 2026-06-23).** The live read path calls the legacy DB
> functions **`rept_0900_001_rec1..rec4`** directly via `DailyCashReportFunctionRepository`
> (`DailyCashReportService.java:24-75` — the documented set-returning exception, same stance as Cash Flow
> and Daily Financial). The prior **Java live-recompute** (`DailyCashRecomputeRepository` /
> `CurrencyAccumulator`) is **bypassed / no longer wired** and retained only as a cleanup candidate. Any
> "live recompute over `ACCT_MSTR` + `ACCT_TRNS_HSTR`" wording below is the **superseded pre-pivot** record
> (audit history preserved).

Refactor guidance with legacy parity as the goal. A refactor implementation **already exists**;
this guide maps legacy → refactor service-by-service and flags what must change for parity. For
the prioritized defect list see [`refactor-gap-analysis.md`](./refactor-gap-analysis.md).

---

## 1. Service-by-service mapping

| Legacy artifact | Refactor counterpart | Status |
|---|---|---|
| View `rept_0009_001` + `rept_0009_001_osmc.ts` | route `/banking/reports/daily-cash` → `DailyCashClient` + `DailyCashPage` | Implemented |
| Read API `rept_0009_001_r001` | `GET /api/v1/banking/reports/daily-cash-report` (`DailyCashReportController.search`) | Implemented |
| Action logic + 4 IDO reads | `DailyCashReportService.search` over `DailyCashRecomputeRepository` (live recompute) | Implemented — **live recompute over `ACCT_MSTR`+`ACCT_TRNS_HSTR`** (2026-06-04) |
| Currency dynamic (`getDynamic0`) | `DailyCashCurrencyFilter` (`parse`/`exact`/`exclude`) | Implemented; `LOCAL_CCY` hardcoded `VND` |
| `REC` summary + `REC4` cash | `CurrencySummary` computed in Java (`CurrencyAccumulator`) | Implemented — mapping fixed (gap #1); cash=0 (`rec4` source out of scope) |
| `REC1` account detail (`rept_0900_001_rec1`) | `AccountRow[]` from `DailyCashRecomputeRepository.findAccountsAsOf` | Implemented — **rec1 replicated in native SQL**; DEV-verified == legacy |
| `REC2` cash flow (`rec3`) | `CurrencyFlow[]` (derived in Java) | Implemented (approximation; `rec3` source `CASH_REPY_MSTR` out of scope; FE doesn't consume) |
| Last-collection `rept_0100_001_r001` + `BAT_CALL_HIS` | — | **Not ported** (`BAT_CALL_HIS` dropped V31); `last_collected_date` now `null` on recompute path |
| Excel JSP (POI) | `DailyCashReportExportProvider` (POI, `/export`) **+** frontend `xlsx-js-style` | Two divergent paths — gap #2 (still open). Provider reuses `service.search` → now also live-recompute. |
| Batch `REPT_0900_001` + `ReptHelper` + BCS | — | **Not ported** — superseded by Java live recompute (no batch dependency for the read path) |
| DB functions `rept_0900_001_rec1/rec2` | `DailyCashRecomputeRepository` native SQL + Java `CurrencyAccumulator` | **Replicated in Java** (no `CREATE FUNCTION`). `rec3/rec4` (cash) **not ported** — source out of scope |
| `wabooks.enums.AcctTypeCd` (01–08) | `common/enums/AcctTypeCd` (01–08, correct) | Mirrored **and now used** by `DailyCashReportService` (gap #1 fixed) |

---

## 2. Backend guidance

1. ~~**Fix account-type buckets (CRITICAL).**~~ ✅ **Done 2026-06-04.** `DailyCashReportService`
   buckets via `common/enums/AcctTypeCd.fromCode(...)`: `03,04`→demand, `05,06`→savings, `02`→term,
   `01`→loan, `07/08` ignored, **cash=0** (no `ACCT_TYPE_CD` is cash). Fabricated `11/12/13/21`
   removed. DB-verified equal to legacy `rec2`.
2. ~~**Decide the canonical data source (HIGH).**~~ ✅ **Decided + implemented 2026-06-04 — live
   recompute (Java/JPQL).** Rather than depend on the batch snapshot, `DailyCashReportService` reads
   `DailyCashRecomputeRepository.findAccountsAsOf(...)` — a native query over `ACCT_MSTR` +
   `ACCT_TRNS_HSTR` replicating legacy `rept_0900_001_rec1` (`wb_get_amt`→inline `ROUND`,
   `wb_get_bank_nm`→`BankCdResolver`); summary stays the Java `SUM(CASE …)` (= legacy `rec2`). No
   new `CREATE FUNCTION` migration (complies with the wecms-api DB-function policy "replicate in
   Java"). Result: a selected date returns accounts even with no snapshot (DEV: 41 accounts for
   2026-06-04 vs snapshot 0). The snapshot table/entity/`V125` are retained but off the read path.
3. **Per-tenant local currency (HIGH).** `LOCAL_CCY = "VND"` is hardcoded; `FCY` must exclude the
   tenant base currency (KHR for Cambodia). Source it from tenant/base-currency settings
   (`wecms-api` OQ-3).
4. **Cash + summary totals.** `DAILY_CASH_REPT_DAILY_SUMR` and `_DAILY_CASH_SUMR_DETL` are out of
   PROD active scope, so computing summary over the detail table is acceptable — but cash holdings
   (`rec4`/`_CASH_SUMR_DETL`) have **no detail-table source**, so `total_cash` will always be 0
   unless a cash source is wired. Decide: (a) accept cash=0 for now and document, or (b) bring the
   cash detail table / function into scope.
5. **Keep `INQ_DV` dropped.** It is deprecated and the dead `REPT_DIF_DATA` re-aggregate path is
   not needed. Document the decision (closes `wecms-api` OQ-2).
6. **Single export producer.** Route the UI through `POST .../export` (POI). See gap #2.

---

## 3. Frontend guidance

1. ~~**Fix current/savings split (CRITICAL).**~~ ✅ **Done 2026-06-04.** `DailyCashClient` splits
   Current=`03/04`, Savings=`05/06` via `isCurrentType`/`isSavingType`; comments corrected.
2. **Remove the client-side workbook (CRITICAL — still open).** Delete `lib/dailyCashExport.ts`'s
   `xlsx-js-style` usage; export through `hooks/useExcelExport` → `POST .../export`
   (`wecms-web` §13b — frontend never carries an XLSX library). Print should go through `lib/print.ts`.
3. **Surface Time-Deposit & Loan + cash.** Legacy keeps them hidden (Redmine #42096/#42129). The
   refactor matches the live VN page: **Cash table removed**, Time-Deposit/Loan sections not rendered,
   and the **summary block is 2-column (Current/Saving)** with the per-currency "Daily Cash Status —
   <CCY> Total" heading. Summary always renders after Search with a **zero VND/USD/KRW fallback**; the
   ledger **checkbox column was removed** (2026-06-04). Keep hidden = parity; revisit only on a PO ask.
4. **Currency scope.** UI exposes All/VND/FCY (matches legacy). USD/KHR exact filters are reachable
   on the backend but not the UI — keep parity unless product asks otherwise.
5. **Search-gated flow** already matches legacy ("click Search to load") — keep.
6. **Last-collection chip.** Legacy shows "Business aggregation last datetime"; refactor has no
   equivalent. Either add a small endpoint (or include it in the response) or drop the chip and document.

---

## 4. Wire/naming rules (refactor)

- Wire JSON: lowercase snake_case (global Jackson). DTO Java fields camelCase.
- Identifiers: slug `daily-cash-report` for audit `WSVC_ID` + View-Setting `WEB_SVC_ID`; never the
  legacy `rept_0009_001` id in code.
- Legacy WSVC ids appear only in JEX file:line citations and migration prose (this doc).

---

## 5. Doc reconciliation

This bundle duplicates content that canonically belongs in three trees:
- **JEX spec** → `docs/jex/features/daily-cash.md` (does not exist yet) — `business-flow.md` +
  `functional-specification.md` + `database-mapping.md` + `interface-specification.md` §1–3 are the source.
- **Refactor backend** → `wecms-api/docs/features/banking/reports/daily-cash.md` (exists) — fold in
  `refactor-gap-analysis.md` backend items + `interface-specification.md` §4.
- **Refactor frontend** → `wecms-web/docs/features/banking/reports/daily-cash.md` (exists) — fold in
  frontend gap items.

Recommended: keep this `docs/features/daily-cash/` set as the consolidated migration workspace, and
when stabilized, port the JEX-only portions into `docs/jex/features/daily-cash.md` and update the
two refactor specs. Confirm placement with Sonny before moving.
