# Daily Financial — P0 Fix Plan

**Scope:** P0 items only from [parity-verification-report.md](parity-verification-report.md) §7.
P1/P2 explicitly out of scope. No UI change unless an API field mismatch forces it.
No REC2 inline grid, no Excel export, no cash/rcv-pay/loan/time-deposit re-introduction.
Scope limited to **Current Accounts + Saving Accounts**. Transaction-history popup preserved.

P0 items being fixed:
- **G-1** — populate the 8 per-account aggregate values (Debit/Credit count+amount + internal variants), currently hard-zero.
- **G-2** — apply the `asOf` date so the report is date-based, not "current `ACCT_MSTR` balances".
- **I-1** — Financial Status computed to match legacy as closely as the in-PROD scope allows.

---

## 1. Strategy decision — live-compute (Strategy B)

**Question (task step 5):** summary-table strategy (A) or live-compute (B)?

**Finding — the documented legacy summary table is NOT available in the refactor DB.**
- `DAILY_FINCL_REPT_DAILY_SUMR_DETL` (legacy REC/REC1 source) has **no Flyway migration and no JPA entity** in `wecms-api`. The only report summary table migrated is the **sibling** `DAILY_CASH_REPT_DAILY_SUMR_DETL` (V125 + `DailyCashReptDailySumrDetl` entity) — that is Daily *Cash*, not Daily *Financial*.
- The recompute functions `wb_insert_daily_fincl_rept_daily_sumr_detl_data` / `…_cash_sumr_data` are **not ported** (CLAUDE.md "Legacy report DB functions — Sprint 1 stance"); the batch owns those writes.
- Therefore Strategy A would require (a) a new Flyway migration to create `DAILY_FINCL_REPT_DAILY_SUMR_DETL`, and (b) seed data — but the table is **batch/request-time materialized** in legacy and is not guaranteed to carry persisted rows on DEV to seed from. That risks a report that cannot be verified against real data.

**Decision: Strategy B — live-compute from in-scope operational tables.** This is the path the gap analysis already recommends (OQ-DF-1, [refactor-gap-analysis.md](refactor-gap-analysis.md) "recommended actions" #1) and the only one verifiable against existing seed data. All three source tables are in the PROD Active Table List **and** already migrated + seeded locally:

| Table | Migration / seed | Role in this fix |
|---|---|---|
| `ACCT_MSTR` | V13/V23, seed V22 | active-account set + account metadata + balance fallback |
| `ACCT_TRNS_HSTR` | V13/V23, seed V22/V29 | per-day debit/credit aggregates + as-of end-of-day balance |
| `ACCT_TRNS_RCV_PAY_INFM` | V13/V21/V23, seed V22 | internal-transfer classification (usage codes) |

No schema change, no new migration, no batch dependency.

---

## 2. Computation (per as-of date, per tenant)

`asOf` (`yyyyMMdd`) defaults to today when blank (legacy requires `REF_DATE`; the frontend always sends it).

**Internal usage codes** (verbatim from legacy `rept_0015_001_r001_act.jsp:264-267` `getDynamic0ForRec2`):
`4000000008`, `6000000062`, `4000000104`, `6000000061`.

**Per-account daily aggregates (G-1):**
1. Load the day's transactions in one query: `ACCT_TRNS_HSTR WHERE biz_key = :biz AND trsc_dt = :asOf`.
2. Build the internal set: `ACCT_TRNS_RCV_PAY_INFM WHERE biz_key = :biz AND acct_seq_no IN (<day acct_seq_nos>) AND rcv_pay_usag_cd IN (<4 codes>)` → set of internal `acct_seq_no`.
3. For each day transaction, classify by `INOUT_DV` (`2` = debit/out, `1` = credit/in — [database-mapping.md §2.2](database-mapping.md)) and internal membership. `SND`/`RCV` and `INTL_SND`/`INTL_RCV` are **disjoint** (legacy footer math `INTL_SND_CNT + SND_CNT` proves SND excludes internal — [rept_0015_001.ts:897-909](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/rept/rept_0015_001.ts#L897-L909)):
   - `INOUT_DV='2'` + not internal → `snd_cnt++`, `snd_amt += amt`
   - `INOUT_DV='2'` + internal → `intl_snd_cnt++`, `intl_snd_amt += amt`
   - `INOUT_DV='1'` + not internal → `rcv_cnt++`, `rcv_amt += amt`
   - `INOUT_DV='1'` + internal → `intl_rcv_cnt++`, `intl_rcv_amt += amt`

**As-of end-of-day balance (G-2):** per output account, the `BAL` of the latest `ACCT_TRNS_HSTR` row with `trsc_dt <= :asOf` (order `trsc_dt desc, trsc_tm desc, acct_seq_no desc`). Falls back to `ACCT_MSTR.BAL` when the account has no history on/before `asOf` (documented limitation — no opening balance source in scope). This replaces the previous unconditional "current `ACCT_MSTR.bal`".

**Financial Status (I-1):** per currency, `current_total = Σ as-of-balance` over current-type accounts, `savings_total = Σ as-of-balance` over savings-type accounts, `total = current_total + savings_total`. In-scope total is current+saving only (cash/time-deposit/loan are out of scope and not rendered by legacy either), so `total` no longer over-counts non-current/saving types as the prior code did.

Account-type partition unchanged (current `{03,04,07}`, saving `{05,06,08}` — the I-2 superset question is P2, untouched).

---

## 3. Explicit non-goals (kept as P1/P2 per instructions)

- **G-4 (P1):** `includeInternalTransfers=false` does NOT exclude internal transactions from the report. The internal columns are now *populated*; the toggle remains visibility-only on the frontend. (The internal usage-code load is used here only to split the columns correctly, which G-1 requires.)
- **G-6 (P1):** `activeAccountsOnly=false` still does not widen to inactive accounts (active-only finder retained).
- **I-6 (P1):** Excel stays client-side; no backend provider added.
- **G-3 (P1):** no inline REC2 transaction grid.
- **I-2 (P2):** current/saving partition superset unchanged.
- DTO/wire shape unchanged → **no frontend change required** (the frontend already reads `NUM_DEBITS`/`DEBIT`/`NUM_INTERNAL_DEBITS`/… and renders them; they were simply zero before).

---

## 4. Files to change

- `repository/AcctTrnsHstrRepository.java` — add `findByBizKeyAndTrscDt(...)` + `findFirstByBizKeyAndBankCdAndAcctNoAndTrscDtLessThanEqualOrderBy…`.
- `repository/AcctTrnsRcvPayInfmRepository.java` — add `findByBizKeyAndAcctSeqNoInAndRcvPayUsagCdIn(...)`.
- `service/ReportService.java` — rewrite `dailyFinancial(...)` (aggregates + as-of date + status). `dailyFinancialTransactions(...)` (popup) untouched.
- `test/.../service/ReportServiceDailyFinancialTest.java` — new Mockito unit tests.

---

## 5. Verification

- `./gradlew test` (Mockito unit tests prove the aggregation/classification/as-of-balance/status logic).
- ~~**Live-data verification against the running local Postgres is the remaining step and cannot be completed in this environment**~~ (the `postgres_dev_server_refactor` MCP returned an empty schema *at the time this plan was written*; no local Postgres was running then).

> **Resolved 2026-06 (supersedes the struck-through line above).** The live-data step was
> subsequently completed: with `NEXT_PUBLIC_API_BASE_URL` pointed at the running backend, an
> authenticated `GET /api/v1/banking/reports/daily-financial` returned **HTTP 200 with real
> VND/USD/KRW data**. The P0 read surface is verified end-to-end and **Daily Financial is marked
> READY FOR SIT (in-PROD scope)**. Evidence + the env-config root cause are in
> [runtime-data-source-check.md §7](runtime-data-source-check.md) and
> [parity-verification-report.md §9/§14](parity-verification-report.md); the package status is
> [README.md §5](README.md#5-sit-readiness-2026-06). This plan document is retained as the P0 work
> record — the pre-verification caveat above is preserved struck-through as the audit trail.
