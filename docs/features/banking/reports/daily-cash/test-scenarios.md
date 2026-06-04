# Daily Cash — Test Scenarios (Parity)

Each scenario states the **legacy behavior** (the parity baseline) and the **expected refactor
behavior**. Use these for refactor verification; ⚠️ flags scenarios that currently **fail** against
the existing refactor (see [`refactor-gap-analysis.md`](./refactor-gap-analysis.md)).

> Legacy reference: `https://dev.wecms.vn/rept_0009_001.act`. Refactor: `/banking/reports/daily-cash`
> + `GET /api/v1/banking/reports/daily-cash-report`. Test tenant: `VN_VNM0004_000001`.

---

## A. Page load & search flow

| # | Scenario | Legacy expected | Refactor expected |
|---|---|---|---|
| A1 | Open page | Filters + "Please click 'Search'" message; report hidden | Same — empty placeholder until Search |
| A2 | Default date | Today; VN input disabled | Today (ISO); date editable via `<DateField>` |
| A3 | Click Search | Report renders for today + All currencies | Fetch fires; sections render |
| A4 | Change currency after search | Re-query | Auto-refetch on scope change |
| A5 | Change date without Search | No refetch | No refetch (matches legacy "click to apply") |
| A6 | Last-collection chip | Shows "영업집계 최종일시 : <datetime>" | ⚠️ No equivalent (not ported) |

## B. Currency filter

| # | Scenario | Legacy expected | Refactor expected |
|---|---|---|---|
| B1 | `CCY_CD=""` (All) | No currency predicate; both KHR/VND + USD shown | `currency=ALL`; `ccyExact=null, ccyExclude=null` |
| B2 | `CCY_CD=VND` (local) | `ccy_cd='VND'`; only local blocks | `currency=VND`; `ccyExact='VND'` |
| B3 | `CCY_CD=FCY` | `ccy_cd <> 'VND'`; only USD blocks | `currency=FCY`; `ccyExclude='VND'` |
| B4 | FCY for a KHR tenant | excludes local (KHR) | ⚠️ excludes `VND` (hardcoded) — wrong for KHR tenant |
| B5 | `currency=USD`/`KHR` exact | reachable only as `FCY`/local in UI | backend supports exact; UI exposes only All/VND/FCY |

## C. Account-type classification ✅ (FIXED 2026-06-04)

| # | Scenario | Legacy expected | Refactor (current) |
|---|---|---|---|
| C1 | Row `ACCT_TYPE_CD='03'` | → **Current Accounts** | ✅ Current (FE split `03/04`); summary → demand |
| C2 | Row `ACCT_TYPE_CD='04'` | → Current Accounts | ✅ Current; summary → demand |
| C3 | Row `ACCT_TYPE_CD='05'/'06'` | → **Savings Accounts** | ✅ Savings (FE split `05/06`); summary → savings |
| C4 | Row `ACCT_TYPE_CD='01'` (loan) | → Loan section (hidden) | ✅ excluded from both tables; summary → loan (NOT cash) |
| C5 | Summary `total_*` per currency | Correct sums per type | ✅ `03,04`→demand, `05,06`→savings, `02`→term, `01`→loan, `07/08` ignored, **cash=0**; DEV-verified == legacy `rec2` |

**Verification:** the source is now the **live recompute** over `ACCT_MSTR` + `ACCT_TRNS_HSTR`
(legacy `rec1` replication), not the snapshot — so seed **`ACCT_MSTR`** (+ optional `ACCT_TRNS_HSTR`
for same-day debit/credit) with one active account per `ACCT_TYPE_CD` 01–06 and assert each lands in
the correct section + summary bucket. Covered at the service layer by `DailyCashReportServiceTest`
(mapping + cash=0 + `11/12/13/21` ignored + empty-source). This is the highest-value parity test.

## D. Grid rendering

| # | Scenario | Legacy expected | Refactor expected |
|---|---|---|---|
| D1 | Current/Savings columns | Bank, Acct No, CCY, #Debits, Debit, #Credits, Credit, Balance | Same column set (after C-fix) |
| D2 | Amount formatting | `formatAmount` per ccy; null→blank | locale formatter; null→0/blank |
| D3 | Count formatting | floor + comma; `"0.00"`→`0` | numeric coercion |
| D4 | Empty section | "no data" message row | empty state per section |
| D5 | Cash section | hidden (commented out) | ✅ **removed from UI** (2026-06-04) |
| D6 | Time-Deposit / Loan sections | hidden (commented out) | not rendered (parity-OK) |
| D7 | Per-currency total chip | `#totalKhr`/`#totalUsd` = sum of 5 buckets | summary total per currency (`Daily Cash Status — <CCY> Total`) |
| D8 | Checkbox column on ledgers | none | ✅ **none** — removed from Current/Savings tables (2026-06-04) |
| D9 | Summary block after Search | per-currency summary shown | ✅ always shown; **zero VND/USD/KRW fallback** when API summary empty |

## E. Excel / Print

| # | Scenario | Legacy expected | Refactor expected |
|---|---|---|---|
| E1 | Excel download | POI JSP → `Daily Cash_<ts>.xlsx`; Current+Saving sections + totals | ⚠️ client-side `xlsx-js-style` (should be `POST .../export`) |
| E2 | Excel columns | Bank, Acct No, CCY, #Debits, Debit, #Credits, Credit, Balance + per-ccy totals | backend provider = flat ledger (BANK, ACCT_NO, BALANCE, LOAN_NAME, MATURITY_DATE, SEND/RECEIVE) — different set |
| E3 | Excel filename | `Daily Cash_yyyyMMddHHmm.xlsx` | backend supplies filename; frontend builder uses `Daily Cash_<ts>` |
| E4 | Print | clone form, strip chrome, print report | should use `lib/print.ts` |
| E5 | Audit log on export | legacy logs via `mntLog` | backend `/export` writes audit row; ⚠️ client-side export writes none |

## F. Validation & errors

| # | Scenario | Legacy expected | Refactor expected |
|---|---|---|---|
| F1 | Missing date | `validateRequired(REQ_DATE)` error | `WBE_VALIDATION` (missing `asOfDate`) |
| F2 | Bad date format | `validateDate8` error | `@Pattern` → `WBE_VALIDATION` |
| F3 | `REPT_DIF_DATA` | confirm dialog (re-aggregate) | n/a — path removed (document) |
| F4 | No data for date | empty sections + messages | empty detail tables + zero VND/USD/KRW summary fallback |
| F5 | Batch not run for date | legacy recomputes live → may still show data | ✅ **refactor also recomputes live** (ACCT_MSTR+ACCT_TRNS_HSTR) → returns accounts regardless of batch (DEV: 41 accounts for 2026-06-04) |

## G. Permissions / environment

| # | Scenario | Legacy expected | Refactor expected |
|---|---|---|---|
| G1 | Non-logged-in | `login=Y` → blocked | JWT required (401) |
| G2 | WTA/sub-account user | `NORM_USER_ONLY_YN=Y` → blocked | ⚠️ no equivalent gate verified — confirm |
| G3 | Tenant isolation | `biz_key` from session | `bizKey` from `TenantContext` |
| G4 | UAT | batch returns early (no refresh) | n/a (no batch) |

---

## Suggested automated coverage (refactor)
- `DailyCashReportServiceTest` ✅: account-type bucketing per `01–07` (now over the live-recompute
  source); `01`≠cash; `11/12/13/21`/`07/08`/unknown ignored; empty-source → empty result.
- **`DailyCashRecomputeRepository` native query** — not unit-tested (no Testcontainers); validated on
  JEX DEV (same schema): SQL runs + per-type totals equal legacy `rept_0900_001_rec2`. Recommend a
  local integration check (`bootRun` + GET for a no-snapshot date) before sign-off.
- `DailyCashCurrencyFilterTest`: `parse` (null/blank/unknown→ALL), `exact`, `exclude`.
- Controller slice: `asOfDate` pattern validation; `currency` default.
- Frontend: `isCurrentType`/`isSavingType` split (`03/04` vs `05/06`); summary always shown + zero
  VND/USD/KRW fallback; no checkbox column; export still client-side (gap #2).
