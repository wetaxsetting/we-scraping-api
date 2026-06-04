# Daily Financial — Migration Guide

Target: a clean Spring Boot rewrite informed by JEX behaviour — **not** a port. No `JexData`, no
`IDODynamic`, no `_act.jsp` envelope mimicry (per `wecms-api/CLAUDE.md`).

> **Status 2026-06 — the in-PROD read path is implemented via Strategy B and READY FOR SIT.**
> `ReportService.dailyFinancial` live-computes per-day debit/credit + internal aggregates and the
> as-of balance from `ACCT_TRNS_HSTR` (+`ACCT_TRNS_RCV_PAY_INFM`), and the per-currency Financial
> Status (VND/USD/**KRW**). The **Transaction History Details popup** is implemented and UI-aligned to
> the e-Invoice Latest Inquiry History popup (default page size 10 / options 10·20·50·100). Still
> P1 / non-blocking: the **§6 Excel export** server-side provider, the inline REC2_1 grid, and the
> internal-transfer/active-account toggle semantics. Strategy decision (OQ-DF-1) resolved to
> **Strategy B**. See [README.md §5](README.md#5-sit-readiness-2026-06) +
> [refactor-gap-analysis.md](refactor-gap-analysis.md).

---

## 1. Legacy architecture (recap)

```
rept_0015_001_view.jsp + rept_0015_001.ts           (jQuery render, 4 section groups)
        │  POST rept_0015_001_r001 (.jct, sync)
        ▼
rept_0015_001_r001_act.jsp
  1. recompute 5 summary tables on freshness drift (EXCEPT/UNION COUNT → D002/C002)
  2. read REC (R003), REC1 (R001), REC2+REC2_1 (R004 json_agg), REC3 (CASH_SUMR_R003)
        ▼
IDODynamic SQL over DAILY_FINCL_REPT_DAILY_* summary tables
  (materialized from ACCT_MSTR / ACCT_TRNS_HSTR / ACCT_TRNS_RCV_PAY_INFM / CASH_REPY_MSTR
   by wb_insert_daily_fincl_* batch functions)
```

Hard parts: read-time materialization with drift detection; per-account daily debit/credit
aggregation; nested-transaction `json_agg`; correlated running-balance; localized purpose names.

## 2. Scope decisions (already taken; honour them)

| Concern | Decision | Source |
|---|---|---|
| Cash book (`REC3`) | **Out of scope** — `DAILY_FINCL_REPT_DAILY_CASH_SUMR*` + `CASH_REPY_MSTR` not in PROD Active Table List | CLAUDE.md PROD scope |
| Receipt/payment purpose summary | **Out of scope** — `DAILY_FINCL_REPT_DAILY_TRNS_RCV_PAY_HSTR_SUMR` not in list (re-derive from `ACCT_TRNS_RCV_PAY_INFM` if a purpose column is needed) | CLAUDE.md PROD scope |
| Nightly recompute (`wb_insert_*`) | **Not ported to Spring** — batch owns the writes; the refactor reads materialized rows | CLAUDE.md §"Legacy report DB functions" |
| Time-deposit / loan panels | Already commented out in legacy (Redmine #42129) — do not build | `rept_0015_001_view.jsp` |
| HAENGSUNG variant (`rept_0017_001`) | Separate `daily-financial-hs` feature; currently a seed stub | `ReportService.dailyFinancialHs` |

## 3. Target Spring architecture

```
com.kosign.wecms.domain.banking
├── controller/ReportController.java          (exists — daily-financial + /transactions)
├── service/
│   ├── ReportService.java                    (exists — extend dailyFinancial)
│   └── DailyFinancialAssembler.java          (NEW — builds REC1/REC2 rows from summary or live tables)
├── repository/
│   ├── DailyFinclReptDailySumrDetlRepository.java        (NEW — read summary, in-scope)
│   ├── DailyFinclReptDailyAcctTrnsHstrSumrRepository.java(NEW — REC2_1 source, in-scope)
│   ├── AcctMstrRepository.java               (exists)
│   └── AcctTrnsHstrRepository.java           (exists)
├── entity/
│   ├── DailyFinclReptDailySumrDetl.java       (NEW — @Table, column-for-column DEV)
│   └── DailyFinclReptDailyAcctTrnsHstrSumr.java (NEW)
├── dto/
│   ├── DailyFinancialCmcReport.java          (exists — keep REC3 dropped)
│   └── DailyFinancialTransactionsReport.java (exists)
└── export/DailyFinancialExportProvider.java  (NEW — common/export pipeline)
```

### Two viable read strategies

**Strategy A — read the materialized summary (recommended for parity).**
Add Flyway migrations for `DAILY_FINCL_REPT_DAILY_SUMR_DETL` + `DAILY_FINCL_REPT_DAILY_ACCT_TRNS_HSTR_SUMR`
(column-for-column DEV — see §5), seed ≤30 rows for `VN_VNM0004_000001`, map JPA entities, and read
them directly (REC1 = rows; REC = group-by-sum in a JPQL/`@Query`; REC2_1 = the per-account
transaction summary rows assembled into the nested DTO in Java instead of `json_agg`). Depends on the
nightly batch (or a future Spring recompute) to keep the summary fresh.

**Strategy B — compute live from `ACCT_MSTR` + `ACCT_TRNS_HSTR`.**
Skip the summary tables entirely: balances from `ACCT_MSTR`; per-account daily debit/credit
counts+amounts via a grouped query over `ACCT_TRNS_HSTR` for the date; financial-status totals by
account-type CASE-sum. This is what the current `ReportService.dailyFinancial` partially does. No
batch dependency; the running balance per transaction comes straight from `ACCT_TRNS_HSTR.BAL`.

> Recommendation: **Strategy B** for the read path (no batch dependency, all-in-scope tables) — it
> already matches the implemented skeleton and the working `/transactions` popup. Reserve Strategy A
> for if/when exact summary-table parity with the legacy nightly aggregation becomes a requirement.

### Filter → query mapping

| Legacy filter | Spring |
|---|---|
| `biz_key` | `TenantContext.currentBizKey()` (never from request) |
| `REF_DATE` | `asOf` param → date string; default today |
| `CCY_CD` (`""`/`FCY`/code) | `currencyScope`; `FCY` → `ccy <> 'VND'`, code → `ccy = ?`, all → no predicate |
| `ACTV_ACCT_ONLY_YN` | `activeAccountsOnly` → join/exists on `ACCT_MSTR.actv_yn = 'Y'` |
| `INCL_INTL_TRNS_YN` | `includeInternalTransfers` → toggles internal columns + (legacy) usage-code exclusions; for Strategy B, decide whether to expose internal counts at all (depends on whether `ACCT_TRNS_RCV_PAY_INFM` usage codes are loaded) |
| sort local-first | `ORDER BY (ccy <> 'USD'), bank_cd, acct_no` |
| loan maturity | `acct_type_cd <> '01' OR expi_dt >= asOf` |

## 4. DTO design

- Keep `DailyFinancialCmcReport` (financial_status[], current_accounts[] with nested
  `transactions[]`). **Fill `transactions[]`** (the REC2_1 gap) by either (A) reading
  `DAILY_FINCL_REPT_DAILY_ACCT_TRNS_HSTR_SUMR` or (B) the same `ACCT_TRNS_HSTR` query the popup uses,
  grouped per account.
- Reuse `DailyFinancialTransactionsReport` for the popup (already wired).
- Do **not** add a cash/currency-flow block (out of scope).
- All amounts go through `AmountFormatter.wire(...)` (raw decimal) for cells and
  `AmountFormatter.forDisplay(...)` for any composed summary string.
- Wire is lowercase snake_case (global Jackson). Java fields camelCase. No `@JsonProperty("UPPER")`.

## 5. Repository / entity design (if Strategy A)

- Flyway: `V{N}__daily_financial_summary_schema.sql` — `CREATE TABLE IF NOT EXISTS` for the two
  in-scope summary tables, **column-for-column with DEV** (types in
  [database-mapping.md](database-mapping.md)): `BIZ_KEY VARCHAR(20)`, `REF_DATE VARCHAR(8)`,
  `CCY_CD CHAR(3)` (entity `@JdbcTypeCode(SqlTypes.CHAR)`), numeric `(20,6)` amounts, count columns
  `(10,0)`/`(20,6)`. PK = `(BIZ_KEY, REF_DATE, CCY_CD, BANK_CD, ACCT_NO[, ACCT_TYPE_CD])` per DEV.
  Add helper index on `(BIZ_KEY, REF_DATE)`.
- Seed: `V{N+1}__daily_financial_summary_seed.sql` — ≤30 rows for `VN_VNM0004_000001`, real DEV PK
  values, `ON CONFLICT DO NOTHING`.
- **Do not** add `wb_insert_daily_fincl_*` or `rept_0015_001_r001_rec1/rec3` functions to Flyway
  (DB-function policy). If summary freshness is needed, port the aggregation as a Java
  `@Scheduled`/batch service in a later sprint, not a PL/pgSQL function.
- Repository methods: derived finders constrained on `BIZ_KEY` + `REF_DATE` (+ optional currency),
  returning `Sort`-ordered rows; never concatenate sort input into SQL (use the `common/web/`
  whitelist pattern if header-sort is added).

## 6. Excel export

Add `DailyFinancialExportProvider implements ExportRowProvider` (`screenId =
"daily-financial"`) + `POST /api/v1/banking/reports/daily-financial/export`, reusing
`ReportService.dailyFinancial` for rows (the **same** filter path as the screen). Seed a
`SYS_SCR_OPTION_WECMS` row set for `web_svc_id = 'daily-financial'` in the same migration (required —
without it `ExcelExportService` drops every column with `WBE2013`). Mirror
`AccountInquiryExportProvider` / `FirmBankingHistoryExportProvider`. Amounts via the shared
`AmountFormatter` / `ExcelColumnFormat.AMOUNT`. Do not write POI directly.

## 7. Audit & i18n

- Audit: write `AUDIT_LOGS_INFM.WSVC_ID = 'daily-financial'` (slug) on search + export.
- i18n: translate any legacy ML codes to `WBExxxx`/`WBIxxxx` (errors) and frontend i18n keys
  (`reports.daily_financial.*`); resolve account-type / in-out labels via the existing
  `AcctTypeCd` / `InoutDv` enums' `i18nKey()`. Do not reuse JEX ML ids verbatim.

## 8. Reconcile before coding

- Align param names (`asOf`/`currencyScope` vs the stub's `referenceDate`/`currency`) — pick one,
  update both the stub and the frontend in the same MR (full-stack ownership rule).
- Decide Strategy A vs B with the lead (this is **OQ-DF-1**, see
  [refactor-gap-analysis.md](refactor-gap-analysis.md)).
