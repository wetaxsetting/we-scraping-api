# Daily Cash — Database Mapping

> **⚠️ Data-source corrected 2026-06-13 (noted 2026-06-23).** The live read path calls the legacy DB
> functions **`rept_0900_001_rec1..rec4`** directly via `DailyCashReportFunctionRepository`
> (`DailyCashReportService.java:24-75` — the documented set-returning exception, same stance as Cash Flow
> and Daily Financial). The prior **Java live-recompute** (`DailyCashRecomputeRepository` /
> `CurrencyAccumulator`) is **bypassed / no longer wired** and retained only as a cleanup candidate. Any
> "live recompute over `ACCT_MSTR` + `ACCT_TRNS_HSTR`" wording below is the **superseded pre-pivot** record
> (audit history preserved).

Tables, views, functions, and column mappings for `rept_0009_001`. Column lists are sourced from
the IDO XML `<out>` blocks, the JEX entities, and the WSVC sub-rules. DB function bodies are not in
the repo (see Open Questions).

---

## 1. Tables

### `DAILY_CASH_REPT_DAILY_SUMR` — per-currency daily totals
Producer: batch `ReptHelper.call_REPT_0900_001` (upsert from BCS `REC2`+`REC4`). Entity:
`wabooks.entity.DailyCashReptDailySumr`.

| Column | Meaning | Notes |
|---|---|---|
| `BIZ_KEY` | tenant | PK part |
| `REF_DATE` | as-of date (`yyyyMMdd`) | PK part |
| `CCY_CD` | currency | PK part |
| `TOTL_NORM_AMT` | total current/demand deposit balance | from `rec2` |
| `TOTL_SAV_AMT` | total savings balance | from `rec2` |
| `TOTL_ACCT_AMT` | total time-deposit balance | from `rec2` |
| `TOTL_LOAN_AMT` | total loan balance | from `rec2` |
| `TOTL_CASH` | total cash | from `rec4`, merged on `CCY_CD` |
| `LST_COLL_DATE` | last collection datetime | set by batch |

### `DAILY_CASH_REPT_DAILY_SUMR_DETL` — per-account detail  ~~★ refactor read source~~ (superseded 2026-06-04)
Producer: batch (delete-by-FK + insert from BCS `REC1`). Entity:
`wabooks.entity.DailyCashReptDailySumrDetl`. **In the PROD Active Table List.** Refactor entity:
`com.kosign.wecms.domain.banking.entity.DailyCashReptDailySumrDetl` (6-col composite PK).

> **Data-source change 2026-06-13.** The refactor **no longer reads this snapshot table** for the
> Daily Cash report. `DailyCashReportService.search(...)` now **calls the legacy DB functions
> `rept_0900_001_rec1..4` directly** via `DailyCashReportFunctionRepository`. The earlier Java
> live-recompute (legacy `rec1/rec2` parity) via `DailyCashRecomputeRepository.findAccountsAsOf(...)` —
> a native query over **`ACCT_MSTR` + `ACCT_TRNS_HSTR`** (see "Superseded Java-recompute source" below) —
> is **superseded (2026-06-13)**: `DailyCashRecomputeRepository` is `@Deprecated(forRemoval=true)`, off
> the active path. The snapshot table + entity + `V125` migration are retained (valid PROD-active
> artifacts; a future batch port may use them) but are not on the read path. This matches legacy, which
> calls the same `rec1..4` functions live on every read regardless of whether the nightly batch persisted a row.

#### Superseded Java-recompute source (off the active path since 2026-06-13; rec1 replication)

> The table below documents the **superseded** Java recompute (`DailyCashRecomputeRepository`,
> `@Deprecated(forRemoval=true)`). The active path calls `rept_0900_001_rec1..4` directly; `rec1`/`rec2`
> are active, `rec3`/`rec4` (the `CASH_REPY_MSTR` cash source) need data not yet present → `total_cash = 0`.

| Source table | Role in (superseded) recompute | In active scope? |
|---|---|---|
| `ACCT_MSTR` | active accounts + master balance + loan/maturity/agreement; `ACTV_YN='Y'`; loan `01` only if `EXPI_DT ≥ refDate` | ✓ |
| `ACCT_TRNS_HSTR` | as-of balance (latest row `TRSC_DT ≤ refDate`) + same-day debit/credit counts & amounts (`TRSC_DT = refDate`) | ✓ |
| `CASH_REPY_MSTR` (legacy `rec3/rec4` cash source) | **not ported** — out of PROD active-table scope → `total_cash = 0` | ✗ |

Legacy helper substitutions in the (superseded) native query: `wb_get_amt(amt, ccy)` → inline
`ROUND(amt, ccy='VND' ? 0 : 2)`; `wb_get_bank_nm(bank_cd,'s')` → Java `BankCdResolver`.
Per-type summary aggregation (legacy `rec2`) was in Java (`CurrencyAccumulator`):
`03,04`→demand, `05,06`→savings, `02`→term, `01`→loan, `07/08` ignored, cash=0. **Verified on DEV:
the recompute's per-type totals equalled legacy `rept_0900_001_rec2` for VND/USD/KRW.**

| Column | Type (refactor entity) | Meaning | PK |
|---|---|---|---|
| `BIZ_KEY` | varchar(20) | tenant | ✓ |
| `REF_DATE` | varchar(8) | as-of date | ✓ |
| `ACCT_TYPE_CD` | char(2) | account type `01–08` (see func-spec §2) | ✓ |
| `CCY_CD` | char(3) | currency | ✓ |
| `BANK_CD` | varchar(20) | bank code | ✓ |
| `ACCT_NO` | varchar(100) | account number | ✓ |
| `BAL` | numeric(20,6) NOT NULL | balance | |
| `LOAN_NM` | varchar(100) | deposit/loan product title | |
| `EXPI_DT` | varchar(8) | maturity date | |
| `SND_CNT` | numeric(20,6) NOT NULL | debit (outgoing) count | |
| `SND_AMT` | numeric(20,6) NOT NULL | debit amount | |
| `RCV_CNT` | numeric(20,6) NOT NULL | credit (incoming) count | |
| `RCV_AMT` | numeric(20,6) NOT NULL | credit amount | |
| `AGMT_AMT` | numeric(20,6) | agreement / line-of-credit amount | |
| `LST_COLL_DATE` | varchar(14) | last collection datetime | |
| `BANK_NM` | (IDO output only) | resolved bank name | from function, not a stored column on the detail table |

### `DAILY_CASH_REPT_DAILY_CASH_SUMR_DETL` — cash-flow detail
Producer: batch (from BCS `REC3`). Entity: `wabooks.entity.DailyCashReptDailyCashSumrDetl`.
Columns: `BIZ_KEY`, `REF_DATE`, `CCY_CD`, `BEF_DATE_BAL` (opening balance), `BAL`, `SND_CNT`,
`SND_AMT`, `RCV_CNT`, `RCV_AMT`, `LST_COLL_DATE`.

### `BAT_CALL_HIS` — batch run history (last-collection datetime)
Producer: batch `REPT_0900_001`. Used by `rept_0100_001_r001`.
Columns: `JOB_ID`, `BIZ_KEY`, `JOB_DATE` (`yyyyMMdd`), `JOB_STR_TM`, `JOB_END_TM` (`HHmmss`),
`REQ_DATE`, `RSLT_CD`, `ERR_CONT`, `TOT_CNT`, `SUC_CNT`, `ERR_CNT`.

### `BUSI_INFM` — businesses (tenants)
Iterated by the batch with `busi_stts = NORMAL`. Not read by the report query directly.

---

## 2. Views
None. The report does not use a SQL view — it uses the set-returning functions below.

---

## 3. Functions (set-returning)

All four take `(BIZ_KEY, REQ_DATE, REQ_DATE2)`; the page passes `REQ_DATE2 = REQ_DATE`.

| Function | Called by IDO | Output columns | Feeds |
|---|---|---|---|
| `rept_0900_001_rec1` | `DAILY_CASH_REPT_DAILY_SUMR_R004` | `BIZ_KEY, REF_DATE, CCY_CD, ACCT_TYPE_CD, BANK_CD, BANK_NM, ACCT_NO, BAL, LOAN_NM, EXPI_DT, AGMT_AMT, SND_CNT, SND_AMT, RCV_CNT, RCV_AMT` | `REC1` (account detail) + persisted `_DETL` |
| `rept_0900_001_rec2` | `..._R005` | `BIZ_KEY, REF_DATE, CCY_CD, TOTL_NORM_AMT, TOTL_SAV_AMT, TOTL_ACCT_AMT, TOTL_LOAN_AMT` | `REC` (summary) + persisted `_SUMR` |
| `rept_0900_001_rec3` | `..._R006` | `BIZ_KEY, REF_DATE, CCY_CD, BEF_DATE_BAL, BAL, SND_CNT, SND_AMT, RCV_CNT, RCV_AMT` (`ORDER BY VND-first`) | `REC2` (cash flow) + persisted `_CASH_SUMR_DETL` |
| `rept_0900_001_rec4` | `..._R007` | `BIZ_KEY, REF_DATE, CCY_CD, TOTL_CASH` | `REC.TOTL_CASH` + persisted `_SUMR` |

The IDOs wrap each as `SELECT <cols> FROM <fn>(:BIZ_KEY,:REQ_DATE,:REQ_DATE2) WHERE 1=1 ??`
(`??` = the `DYNAMIC_0` currency filter).

`BAT_CALL_HIS_R002`: `SELECT max(JOB_DATE || JOB_END_TM) AS LST_COLL_DATE FROM BAT_CALL_HIS WHERE 1=1 ??`
(`?? = and job_id = ? and biz_key = ?`).

---

## 4. Read-path column → output mapping

| WSVC OUT (read API) | Sub-fields | Source IDO / function |
|---|---|---|
| `REC` (RECORD) | `TOTL_CASH, TOTL_NORM_AMT, TOTL_ACCT_AMT, TOTL_LOAN_AMT, TOTL_SAV_AMT, CCY_CD` | `R005`(rec2) + `R007`(rec4) merged on `CCY_CD` |
| `REC1` (RECORD) | `BIZ_KEY, LST_COLL_DATE, REF_DATE, CCY_CD, ACCT_TYPE_CD, BANK_CD, ACCT_NO, BAL, LOAN_NM, EXPI_DT, AGMT_AMT, SND_CNT, SND_AMT, RCV_CNT, RCV_AMT, BANK_NM` | `R004`(rec1) |
| `REC2` (RECORD) | `BIZ_KEY, LST_COLL_DATE, REF_DATE, CCY_CD, BEF_DATE_BAL, BAL, SND_CNT, SND_AMT, RCV_CNT, RCV_AMT` | `R006`(rec3) |
| scalar `TOTL_{USD,KHR}_{CASH,NORM_AMT,ACCT_AMT,LOAN_AMT,SAV_AMT}` | — | declared in WSVC; **not populated** by the current action (TS reads per-row `REC` instead) |

> The scalar `TOTL_*` OUT fields and the old commented-out TS block (`rept_0009_001_osmc.ts:544-614`)
> are legacy remnants — the live TS computes per-currency totals from the `REC` record rows, not
> the scalars. `TOTL_KHR_ACCT_AMT` appears twice in the WSVC (`#1` duplicate) — harmless artifact.

---

## 5. Refactor data access

> **Active read path = direct `rept_0900_001_rec1..4` calls (2026-06-13).** The current `search` path
> does **not** read the persisted snapshot and **does not** use the Java recompute. `DailyCashReportService.search(...)`
> calls the legacy DB functions `rept_0900_001_rec1..4` directly via `DailyCashReportFunctionRepository`
> (the DB functions are the source of truth). `rec1`/`rec2` are active; `rec3`/`rec4` need `CASH_REPY_MSTR`
> data → `total_cash = 0`; `LST_COLL_DATE` is null. The Java recompute described below is **superseded** —
> `DailyCashRecomputeRepository` is `@Deprecated(forRemoval=true)`, off the active path.

**Superseded path (off active path since 2026-06-13) — `DailyCashRecomputeRepository.findAccountsAsOf(bizKey, refDate, ccyExact, ccyExclude)`:**

- Native query, returns `List<DailyCashAccountProjection>` (`ccyCd, acctTypeCd, bankCd, acctNo, bal,
  loanNm, expiDt, agmtAmt, sndCnt, sndAmt, rcvCnt, rcvAmt`). Bank name is **not** projected — resolved
  in Java via `BankCdResolver.resolveBankNm(bankCd)` (from `BANK_INFM`).
- **Balance** = latest `ACCT_TRNS_HSTR` row with `TRSC_DT ≤ refDate` (order `TRSC_DT DESC, CAST(ACCT_SEQ_NO AS NUMERIC) DESC`, `LIMIT 1`), falling back to `ACCT_MSTR.BAL`; rounded `VND→0`, else `2` decimals.
- **Same-day debit/credit** = subquery over `ACCT_TRNS_HSTR WHERE TRSC_DT = refDate`, grouped by
  `(BIZ_KEY, CCY_CD, BANK_CD, ACCT_NO)`: `snd_*` = `INOUT_DV='2'` (outflow/debit), `rcv_*` = `INOUT_DV='1'`
  (inflow/credit); joined only for `ACCT_TYPE_CD IN ('03','04','05','06')`.
- **Active only** (`ACTV_YN='Y'`); loans (`01`) only when `EXPI_DT ≥ refDate`; currency via the null-safe
  `ccyExact`/`ccyExclude` predicates; `ORDER BY ACCT_TYPE_CD, BANK_CD, ACCT_NO`.
- Legacy helper substitutions: `wb_get_amt(amt, ccy)` → inline `ROUND(...)`; `wb_get_bank_nm` → `BankCdResolver`.
- **Summary + currency-flow are aggregated in Java** (`DailyCashReportService.CurrencyAccumulator`):
  `03,04`→demand, `05,06`→savings, `02`→term, `01`→loan, `07/08` ignored, `cash=0`. Opening balance =
  `closing − rcvAmt + sndAmt`. `DAILY_CASH_REPT_DAILY_SUMR` and `_DAILY_CASH_SUMR_DETL` are **not** in the
  PROD Active Table List, so no summary table is read.
- `lastCollectedDate` is `null` on this path (no snapshot row read); `BAT_CALL_HIS` was dropped in Flyway **V31**.

**Dormant path — `DailyCashReptDailySumrDetlRepository.findRows(...)`:**

- The snapshot entity `DailyCashReptDailySumrDetl` (`@Table(DAILY_CASH_REPT_DAILY_SUMR_DETL)`) and its
  repository still exist and compile, but `DailyCashReportService` **does not inject or call them**. They
  are vestigial relative to the current `search` implementation (the service was migrated off the snapshot
  to live recompute). JPQL kept for reference:
  ```jpql
  SELECT d FROM DailyCashReptDailySumrDetl d
   WHERE d.bizKey = :bizKey AND d.refDate = :refDate
     AND (:ccyExact IS NULL OR d.ccyCd = :ccyExact)
     AND (:ccyExclude IS NULL OR d.ccyCd <> :ccyExclude)
   ORDER BY d.ccyCd ASC, d.acctTypeCd ASC, d.bankCd ASC, d.acctNo ASC
  ```
  A future batch port may revive this read; today it is dead code on the report path.

---

## 6. PROD Active Table List status (refactor scope)

| Table | In active list? | Refactor treatment |
|---|---|---|
| `DAILY_CASH_REPT_DAILY_SUMR_DETL` | ✓ | entity + repository (read) |
| `DAILY_CASH_REPT_DAILY_SUMR` | ✗ | not entity-mapped; summary comes from legacy `rept_0900_001_rec2` (active path) |
| `DAILY_CASH_REPT_DAILY_CASH_SUMR_DETL` | ✗ | not mapped; cash from legacy `rec3/rec4` (data-blocked → 0) |
| `BAT_CALL_HIS` | ✗ (dropped V31) | last-collection lookup not ported |
| `BANK_INFM` | ✓ | bank-name resolution |

---

## 7. Open Questions

1. ~~**`rept_0900_001_rec1..4` bodies — UNVERIFIED.**~~ ✅ **RESOLVED 2026-06-04.** The `rec2`/`rec4`
   bodies were read from DEV via `pg_get_functiondef` and recorded in
   [refactor-parity-verification.md](refactor-parity-verification.md) §1.5: summary buckets `03/04`→NORM,
   `05/06`→SAV, `02`→ACCT, `01`→LOAN; `TOTL_CASH` comes from `cash_repy_mstr` via `rec4→rec3` (out of
   refactor scope → 0); `07/08` excluded from every summary bucket. DEV's `ACCT_TYPE_CD` distribution is
   `01–07` only (no `08`, no fabricated `11/12/13/21`). **Update 2026-06-13:** the active path now calls
   `rept_0900_001_rec1..4` directly (via `DailyCashReportFunctionRepository`), so it uses these function
   bodies as-is rather than the superseded Java `CurrencyAccumulator` + `findAccountsAsOf` recompute (which
   reproduced them and remains the historical record, locked by `DailyCashReportServiceTest`).
2. **Exact column types of `DAILY_CASH_REPT_DAILY_SUMR` / `_CASH_SUMR_DETL`** — taken from JEX
   entities; confirm against DEV `information_schema.columns` if these tables are ever revived in refactor.
3. **Does PROD's batch actually run `REPT_0900_001`?** If the report query recomputes live, the
   persisted `_SUMR`/`_DETL` may be stale/secondary in PROD. Confirm with the PO which is canonical
   (`wecms-api` OQ-1 tracks the same question).
