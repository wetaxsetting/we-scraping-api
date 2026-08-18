# Account Inquiry (VND) — AI-01 + AI-06 Implementation Verification

> _**Historical one-off audit.** A point-in-time record of a completed verification / remediation — findings are preserved unchanged. For the feature’s current state see its `features/.../refactor-gap-analysis.md`; for context see the [audits index](../README.md)._

> **Decision:** "Follow legacy behavior first" — implement AI-01 (as-of-date historical balance) and AI-06 (PrdtType H summary-card exclusion) exactly as legacy, instead of carrying undocumented deviations.
> **Screen:** Current & Savings > Account Inquiry (VND) — legacy `fnsh_0001_001`.
> **Date:** 2026-06-15 · **Type:** full-stack change (`wecms-api` + `wecms-web`) + tests + live-DB validation.
> **Supersedes:** the PO_CONFIRMATION_ONLY dispositions in `docs/audits/account-inquiry-vnd-final-decision-audit.md` (AI-01, AI-06) — both now implemented to legacy parity.

---

## AI-01 — As-of-date historical balance (legacy `ACCT_MSTR_R004`)

**Legacy:** the inquiry-date picker sends `START_DATE`; the read IDO `ACCT_MSTR_R004` returns current `ACCT_MSTR.BAL`/`REAL_AMT` for blank/today/future, and for an earlier date reconstructs the **per-row** balance — Vietcombank `11704004`: `ACCT_MSTR.BAL − Σ(signed txns after the date)`; other banks: the last `ACCT_TRNS_HSTR.BAL` with `TRSC_DT ≤ date`. The totals/summary cards (`ACCT_MSTR_R004_CNT`) sum **current** `BAL`/`REAL_AMT` with **no** date logic.

**Implemented (row-only, totals stay current — faithful to legacy):**
- `AcctMstrRepository.findAsOfBalances(bizKey, asOf)` — native query that ports the `ACCT_MSTR_R004` BAL + REAL_AMT `CASE` verbatim (incl. the VCB `11704004` branch, the `cast(acct_seq_no AS NUMERIC)` ordering, and the `REAL_AMT=0 → use BAL logic` rule). The `:asOf >= to_char(CURRENT_DATE,'YYYYMMDD')` guard runs in SQL exactly as legacy, so today/future returns current values.
- `AccountService.list(..., asOfDate, pageable)` — new overload; when `asOfDate` is non-blank it overrides each displayed row's `bal`/`realAmt` from the as-of map (`toRow(..., balOverride, realAmtOverride)`). `aggregateByCcy` (summary cards) is **not** date-scoped — totals stay current, matching `ACCT_MSTR_R004_CNT`.
- `AccountController` — new `asOfDate` request param (`yyyyMMdd`).
- Frontend: the existing inquiry-date `<DateField>` now drives the fetch — `AccountInquiryPage` fires `onAsOfDateChange(iso)`; `AccountInquiryClient` holds `asOfDate` state, converts ISO→`yyyyMMdd`, sends it via `fetchAccounts`, and adds it to the `useApiData` deps. Default (today) → current balances; the default no-date path is unchanged.

**Scope:** wired for the **VND** screen. The FCY twin's `<DateField>` stays popup-local (FCY's OQ-FCY-2 Option-B deviation is unchanged) — the backend supports as-of for FCY too, but the FCY client does not send the param.

## AI-06 — PrdtType H summary-card exclusion (legacy `tot_dynamic0`)

**Legacy:** `fnsh_0001_001_r001_tot_dynamic0` (redmine #42633) — for an **H** tenant, savings/term-deposit accounts (`05/06/08`) are excluded from the **per-currency summary card** total unless `(EXPI_DT IS NULL OR EXPI_DT ≥ today) AND BAL > 0`. The same clause in the list query `dynamic0` is **commented out** — so it never affects the account rows.

**Implemented (summary-card totals only, rows untouched — faithful to legacy):**
- `AcctMstrRepository.aggregateByCcy(..., excludeMaturedHSavings)` — new predicate: `(:excludeMaturedHSavings = FALSE OR acctTypeCd NOT IN ('05','06','08') OR ((expiDt IS NULL OR to_date(expiDt,'YYYYMMDD') >= CURRENT_DATE) AND bal > 0))`. Applied to the per-currency aggregate **only**.
- `AccountService.list` passes `excludeMaturedHSavings = (PrdtType.fromCode(TenantContext.currentPrdtType()) == H)`. `findFiltered` (the row list) and `findDistinctBankCds` (the dropdown) are **not** changed — account rows and balances are untouched, exactly as the user required.
- Product-gated (not currency-gated), matching legacy's shared `tot_dynamic0` — so it also covers the FCY twin (closes FCY-A04) for an H tenant. No frontend change (totals come from the backend).

---

## Files changed

| File | Change |
|---|---|
| `wecms-api/.../banking/repository/AcctMstrRepository.java` | `aggregateByCcy` + `excludeMaturedHSavings` predicate (AI-06); new `findAsOfBalances` native query (AI-01, R004 port) |
| `wecms-api/.../banking/service/AccountService.java` | `asOfDate` list overloads, `loadAsOfBalances` + `AsOfBalance` record, `toRow` balance-override overload, H-exclusion flag from `PrdtType`; `PrdtType`/`HashMap`/`Map` imports |
| `wecms-api/.../banking/controller/AccountController.java` | `asOfDate` request param |
| `wecms-web/lib/api/banking.ts` | `asOfDate` on `FetchAccountsParams` + wire param |
| `wecms-web/components/shared/AccountInquiryPage.tsx` | `onAsOfDateChange` prop, fired from `<DateField>` |
| `wecms-web/app/(dashboard)/banking/account-inquiry/AccountInquiryClient.tsx` | `asOfDate` state, ISO→`yyyyMMdd`, fetch param + dep, `onAsOfDateChange` wiring |
| `wecms-api/.../test/.../AccountServiceAsOfAndHExclusionTest.java` | **New** — 5 wiring tests |
| `wecms-api/.../test/.../AccountServiceAuthorityTest.java` | Updated `aggregateByCcy` stubs for the new 8th arg |

No schema / migration / config / seed change. Both `wb_get_bank_nm`-style needs were avoided — `findAsOfBalances` is plain SQL over `ACCT_MSTR`/`ACCT_TRNS_HSTR`; the H predicate is JPQL over `ACCT_MSTR`.

## Test results

`./gradlew test` (targeted, `--no-daemon`) → **BUILD SUCCESSFUL**:

| Test class | tests | failures |
|---|---|---|
| `AccountServiceAsOfAndHExclusionTest` (**new**) | 5 | 0 |
| `AccountServiceAuthorityTest` (regression) | 7 | 0 |
| `AccountServiceLookupTest` (regression) | 6 | 0 |
| `BankCdResolverShortNameTest` (regression) | 8 | 0 |
| `TransactionServiceVcbBalanceTest` (regression, TH-01) | 6 | 0 |
| `VasAccountIntegrationTest` (Spring context boot — JPQL parse-validation) | green | 0 |

`wecms-web`: `npx tsc --noEmit` → **exit 0**.

**JPQL validated by context boot:** the new `aggregateByCcy` JPQL (boolean param + `FUNCTION('to_date')` + `CURRENT_DATE`) is parsed by Hibernate at `@SpringBootTest` startup — a malformed query would fail context load; `VasAccountIntegrationTest` passing confirms it parses.

## Live-DB validation (read-only)

- **AI-01 VCB branch** (JEX DEV): for acct `001065042693` (current 17,000,000,000 VND, one later deposit) the as-of formula returns `0` for a date before that deposit; accounts with no later txns return the current balance unchanged. SQL executes correctly against both the JEX DEV and refactor DBs.
- **AI-06 H predicate** (JEX DEV, the HAENGSUNG H tenant): VND summary total `811,401,378,271` without exclusion → `808,333,523,035` with exclusion — a difference of exactly `3,067,855,236`, the one matured VND savings account (`213000212033`, EXPI_DT `20251203`). Rows are unaffected.

## Parity confirmation

- **AI-01 — CLOSED (implemented to legacy parity).** Per-row balances reconstruct as-of-date exactly per `ACCT_MSTR_R004`; summary-card totals stay current per `ACCT_MSTR_R004_CNT`. Default (today) view byte-for-byte unchanged.
- **AI-06 — CLOSED (implemented to legacy parity).** Matured/zero-balance H savings excluded from summary-card totals only (`tot_dynamic0`); account rows and balances untouched, mirroring legacy's commented-out `dynamic0` clause.
- **No undocumented deviation carried forward** — both items now match live legacy behavior rather than a snapshot-only / no-exclusion shortcut.

> Recommended SIT smoke (against live JEX data): VND Account Inquiry with a past inquiry date (rows show as-of balances, cards show current totals); HAENGSUNG (H) VND/FCY summary card excludes the matured savings while the account still lists.
