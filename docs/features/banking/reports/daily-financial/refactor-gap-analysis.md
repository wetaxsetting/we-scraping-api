# Daily Financial — Refactor Gap Analysis

Backend evidence: `wecms-api/src/main/java/com/kosign/wecms/domain/banking/{controller,service,dto}`.
Frontend evidence: `wecms-web/components/banking/reports/daily-financial[-hs]/`. Scope authority:
`wecms-api/CLAUDE.md` (PROD Active Table List + Legacy report DB functions). No assumptions.

> **Status update 2026-06 — READY FOR SIT (in-PROD scope).** The P0 read-surface gaps and the
> Transaction History Details popup UI are closed (see **Resolved** below). Remaining items are
> P1 / out-of-scope **non-SIT-blockers**. Full status: [README.md §5](README.md#5-sit-readiness-2026-06)
> + [parity-verification-report.md](parity-verification-report.md).

---

## Resolved (2026-06 — SIT prep)

| Item | Status | Evidence |
|---|---|---|
| **Per-account daily debit/credit + internal aggregates (REC1)** (was *Missing*) | ✅ **RESOLVED** | live-computed from `ACCT_TRNS_HSTR` (+ `ACCT_TRNS_RCV_PAY_INFM` for the internal split) for the as-of date — `ReportService.dailyFinancial` / `computeDailyAggregates` (P0 fix 2026-06-04). |
| **As-of-date semantics + date-aware balances** (was *Incorrect*) | ✅ **RESOLVED** | `asOf` drives the day aggregates + per-account end-of-day balance (`findFirst…TrscDtLessThanEqual…`); `ACCT_MSTR.BAL` fallback only when no history ≤ asOf. |
| **Financial Status calculation** (was *Partial*) | ✅ **RESOLVED** | per-currency current + saving totals from as-of balances; `total = current + saving`; VND/USD/**KRW**. |
| **Transaction History popup UI** | ✅ **RESOLVED — READY FOR SIT** | restyled to the e-Invoice Latest Inquiry History popup (modal layout, bordered table, sticky header). `tsc`/`build` pass. `TransactionHistoryDialog.tsx`. |
| **Pagination styling** | ✅ **RESOLVED** | footer = page-size (left) · page nav (center, when > 1 page) · summary (right); default page size **10**; options **10/20/50/100**; reset to page 1 on size change; dropdown opens upward (no overflow). |
| **Empty-state styling** | ✅ **RESOLVED** | centered row spanning all columns, fixed height (e-Invoice contract) — not floating text. |
| **Time column mapping** | ✅ **RESOLVED (verified, not a defect)** | `ACCT_TRNS_HSTR.TRSC_TM` is empty in source for the affected rows; backend → mapper → UI bind `TRSC_TM` correctly; blank by design (legacy identical). See [README.md §6](README.md#6-time-column-verification-transaction-history-details-popup). |
| **KRW Financial Status coverage** | ✅ **RESOLVED** | KRW current/saving accounts surface a KRW section; `₩` formatting (`lib/currency.ts`). |
| **API data-source / mock fallback** | ✅ **RESOLVED** | `NEXT_PUBLIC_API_BASE_URL` env config fixed; real data verified (`GET …/daily-financial` → 200, VND/USD/KRW). |

---

## Implemented

| Area | Evidence | Notes |
|---|---|---|
| REST endpoint + 4 filters | `ReportController.dailyFinancial` (`:29-36`) | params `asOf`, `currencyScope`, `activeAccountsOnly` (default true), `includeInternalTransfers` (default true) |
| Financial Status totals (REC) | `ReportService.dailyFinancial` (`:139-189`) | per-currency current/savings totals computed from `ACCT_MSTR` (not the legacy summary table) |
| Current / Savings account rows (REC1) | `ReportService.dailyFinancial` (`:156-189`) | balances + account metadata from `ACCT_MSTR`, partitioned by `AcctTypeCd` (current/savings) |
| **Transaction-history popup** (`rept_0015_002`) | `ReportService.dailyFinancialTransactions` (`:230-294`) | **fully wired** to `ACCT_TRNS_HSTR`: chronological sort, day totals (in/out), closing balance, pagination |
| CMC DTO shape | `DailyFinancialCmcReport` | `financial_status[]`, `current_accounts[]` with nested `transactions[]` |
| Txn DTO | `DailyFinancialTransactionsReport` | `tot_cnt`, `tot_in_amt`, `tot_out_amt`, `closing_bal`, `page`, `size`, `rec[]` |
| Reused entities/repos | `AcctMstr`, `AcctTrnsHstr`, `AcctTrnsRcvPayInfm` + repos | Sprint 1 banking schema (V13, V23) |
| Frontend page + popup + types | `DailyFinancialPage.tsx`, `TransactionHistoryDialog.tsx`, `types.ts` | rendered UI; some sections mock-backed (`mockData.ts`) |
| Page mapping / feature docs | `wecms-web/docs/.../page-mapping.md`, both `daily-financial.md` stubs | traceability in place |

## Missing

| Gap | Why it matters | Evidence |
|---|---|---|
| **Inline per-account transactions (REC2_1)** | each account row's inline drilldown ledger | `ReportService.dailyFinancial` returns empty `transactions[]`. **P1 / non-SIT-blocker** — the Transaction History Details **popup** already serves the per-account drilldown (real `ACCT_TRNS_HSTR`); the *inline* list is the deferred enhancement. |
| **Excel export** (server-side provider) | Legacy has a 4,508-line POI export | No `DailyFinancialExportProvider` in `common/export/` yet. **P1 / non-SIT-blocker** — a frontend client-side export is already present on the page. |
| ~~**Per-day debit/credit aggregates on account rows**~~ | REC1 `SND_CNT/AMT`, `RCV_CNT/AMT`, internal variants | ✅ **RESOLVED (2026-06-04)** — now live-computed via a grouped `ACCT_TRNS_HSTR` read for the as-of date, internal split via `ACCT_TRNS_RCV_PAY_INFM`. See **Resolved** above. |
| **Audit logging** | Search/export should write `AUDIT_LOGS_INFM` | Not verified present for daily-financial. **P1 / non-SIT-blocker.** |
| **View Setting seed** | Required before *server-side* Excel export works (`WBE2013` otherwise) | No `web_svc_id = 'daily-financial'` seed found. **P1 / non-SIT-blocker** (couples with the Excel provider). |

## Partial

| Area | What works | What's stubbed/divergent |
|---|---|---|
| ~~Financial Status~~ | ✅ **RESOLVED (2026-06)** | date-aware per-currency current + saving totals (`total = current + saving`), VND/USD/KRW. Source is live-computed `ACCT_TRNS_HSTR`/`ACCT_MSTR` (Strategy B), **not** the materialized summary — verified against real data; edge-case reconciliation vs the nightly aggregate is a P1 follow-up, not a SIT blocker. |
| ~~Account grids~~ | ✅ **RESOLVED (2026-06)** | balances (as-of) + day's debit/credit + internal counts/amounts now populated. |
| HAENGSUNG variant (`daily-financial-hs`) | endpoint + UI exist | `ReportService.dailyFinancialHs` returns a **fixed seed dataset** (`:300-317`); real `rept_0017_001` ledger query deferred (separate feature). |
| ~~Frontend~~ | ✅ **RESOLVED (2026-06)** | real-endpoint wiring confirmed (`GET …/daily-financial` → 200, VND/USD/KRW); popup restyled to e-Invoice pattern; `mockData.ts` is fallback-only. |

## Out of scope (deliberate — not gaps)

| Item | Reason |
|---|---|
| Cash book (`REC3`) / cash-book detail popup (`rept_0015_004`) | `DAILY_FINCL_REPT_DAILY_CASH_SUMR(_DETL)` + `CASH_REPY_MSTR` **not in PROD Active Table List** |
| Receipt/payment-purpose drilldown (`rept_0015_003`) + purpose name on inline txns | `DAILY_FINCL_REPT_DAILY_TRNS_RCV_PAY_HSTR_SUMR` **not in PROD Active Table List** (purpose could be re-derived from in-scope `ACCT_TRNS_RCV_PAY_INFM` if required) |
| Time-deposit / loan panels | already commented out in legacy (Redmine #42129) |
| Nightly summary recompute (`wb_insert_daily_fincl_*`, C002/D002) | batch owns the writes; refactor reads only (CLAUDE.md §"Legacy report DB functions — Sprint 1 stance") |

## Risks

1. **Aggregate parity drift.** Computing financial-status + per-account counts live from
   `ACCT_TRNS_HSTR`/`ACCT_MSTR` may not match the legacy materialized summary exactly (timezone of
   `TRSC_DT`, `INOUT_DV` edge values, internal-transfer usage-code exclusions). *Update 2026-06: the
   read path is implemented + verified against real data (`GET …/daily-financial` → 200, VND/USD/KRW);
   the residual edge-case reconciliation vs the legacy nightly aggregate is a **P1 follow-up, not a
   SIT blocker**.*
2. **Param-name contract split.** Implemented `asOf`/`currencyScope` vs the stub's
   `referenceDate`/`currency`. If the frontend already calls `asOf`, the stub doc is wrong; if not,
   the endpoint is. Reconcile in one MR (full-stack ownership).
3. **Internal-transfer semantics.** Legacy excludes specific usage codes (`4000000008`, `6000000062`,
   `4000000104`, `6000000061`, and `7000000061` for cash). Reproducing the toggle without the
   rcv/pay summary table requires loading those usage codes from `ACCT_TRNS_RCV_PAY_INFM`; otherwise
   the `includeInternalTransfers` flag is cosmetic.
4. **Running balance source.** The inline/popup running balance relies on `ACCT_TRNS_HSTR.BAL` being
   populated and ordered correctly; verify `BAL` is present (the popup uses last-row `BAL` as closing
   balance).
5. **Export not gated.** Shipping the Excel button without the `SYS_SCR_OPTION_WECMS` seed yields
   `WBE2013` at runtime.
6. **HS stub mistaken for done.** `daily-financial-hs` returns deterministic seed rows; do not treat
   it as implemented.

## Recommended actions

Prioritized for the next sprint slice (target: close the in-scope read surface).

1. **OQ-DF-1 — decide read strategy** (PO/lead): live-compute from `ACCT_MSTR`+`ACCT_TRNS_HSTR`
   (Strategy B, no batch dependency, all in-scope) **vs** read the materialized summary tables
   (Strategy A, needs Flyway + batch). Recommend Strategy B. → [migration-guide.md §3](migration-guide.md#3-target-spring-architecture).
2. **Fill inline `transactions[]` (REC2_1).** Reuse the proven `dailyFinancialTransactions` read,
   grouped per account, to populate each `current_accounts[].transactions`. Highest user-visible
   gap.
3. **Populate per-account daily debit/credit aggregates** (`snd_cnt/amt`, `rcv_cnt/amt`) via a
   grouped `ACCT_TRNS_HSTR` query for the date; wire grid footer totals.
4. **Reconcile param names** (`asOf`/`currencyScope`) across endpoint, stub doc, and frontend.
5. **Add `DailyFinancialExportProvider`** + `POST …/daily-financial/export` + `SYS_SCR_OPTION_WECMS`
   seed for `web_svc_id = 'daily-financial'` (mirror `AccountInquiryExportProvider`).
6. **Add audit logging** (`WSVC_ID = 'daily-financial'`) on search + export.
7. **Implement `includeInternalTransfers`** semantics by loading the internal usage codes from
   `ACCT_TRNS_RCV_PAY_INFM` (the only in-scope source) — or document it as cosmetic-only until then.
8. **Validate aggregate parity** against the live `DAILY_FINCL_REPT_DAILY_SUMR_DETL` for ≥3 dates.
9. **Defer / track:** cash book + rcv/pay popups (out of scope), nightly recompute batch (Sprint
   2/3), HAENGSUNG ledger (separate `daily-financial-hs` feature).

> **Open questions to record** under `wecms-api/docs/planning/open-questions/` (per skill rules,
> the human closes these): **OQ-DF-1** read strategy; **OQ-DF-2** param-name contract;
> **OQ-DF-3** whether `includeInternalTransfers` must be functional in v1 given the rcv/pay summary
> table is out of scope.
