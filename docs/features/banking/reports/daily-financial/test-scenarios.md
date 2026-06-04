# Daily Financial — Test Scenarios

Derived from the legacy behaviour ([functional-specification.md](functional-specification.md)) and
the refactor contract. Use these for both JEX regression and refactor parity testing.

---

## 1. Normal cases

| # | Scenario | Steps | Expected |
|---|---|---|---|
| N1 | Default search | Open page → click Search (today, All, active-only ON, incl-internal ON) | Financial Status (local+USD), Current & Savings grids, per-bank detail render; "Please click Search" hidden |
| N2 | Past date | Set As-of = a date with known data → Search | Rows reflect that date's balances/transactions; totals match per-currency sums |
| N3 | Local currency only | Currency = local (VND) → Search | Only local block shown; USD block hidden (`ts:332-344`) |
| N4 | Drill to txn history | Click a current-account Bank/AcctNo | `rept_0015_002` popup opens with that account's day transactions (refactor: `/daily-financial/transactions`) |
| N5 | Internal columns | Incl-internal ON → Search | Internal debit/credit columns visible; account header shows internal summary line |
| N6 | Print | Search → Print | Print view excludes title/form/message; shows result tables |
| N7 | Popup UI + default page size | Search → click an account's Bank/Acct No. | Transaction History Details popup opens, e-Invoice styling (bordered table, sticky header); footer shows page-size **10** (options 10/20/50/100) on the left + summary on the right; columns Time·Debit·Credit·Balance·Remarks |
| N8 | Popup page-size change | In popup, change page size to 50 | List re-queries; **resets to page 1**; dropdown opens upward without overflowing the modal |

## 2. Boundary cases

| # | Scenario | Expected |
|---|---|---|
| B1 | Date with **no** accounts/transactions | Empty grids, zero totals; no JS error (renderers handle empty arrays) |
| B2 | Account with many transactions in one day | Popup paginates (refactor `size`=15 default); running balance chronological |
| B3 | Account with blank nickname | Nickname falls back to `bank-short + last4` (R001 `COALESCE`/`wb_get_bank_nm`) |
| B4 | Loan maturing exactly on As-of (`expi_dt = ref_date`) | Loan included (guard is `>=`) |
| B5 | Loan matured before As-of | Loan excluded |
| B6 | Split transaction (sentinel usage `99999999999999999999`) | Purpose name shows "first-detail-name + 1" |
| B7 | As-of = future date | Empty result (no data) — no error |
| B8 | Export at MAX rows | Refactor: ≤ 50,000 rows; beyond → `WBE2011` |
| B9 | Transaction with empty `TRSC_TM` | Popup **Time** cell renders blank by design (source has no time); debit/credit still bind by `INOUT_DV`; not a defect ([README §6](README.md#6-time-column-verification-transaction-history-details-popup)) |

## 3. Error cases

| # | Input | Expected (legacy → refactor) |
|---|---|---|
| E1 | Missing `REF_DATE` | Validation error (`validateRequired`) → refactor `WBE_VALIDATION` |
| E2 | `ACTV_ACCT_ONLY_YN` not Y/N | `validateYesOrNo` error → `WBE_VALIDATION` |
| E3 | `INCL_INTL_TRNS_YN` not Y/N | `validateYesOrNo` error → `WBE_VALIDATION` |
| E4 | No session / expired token | Legacy redirect; refactor `401` via `JwtAuthenticationEntryPoint` |
| E5 | Tenant mismatch attempt (request tries other `biz_key`) | Ignored — `BIZ_KEY` from `TenantContext`/session only; never honored from request |
| E6 | Concurrent recompute (legacy only) | Legacy transaction may serialize; refactor read path has no recompute |

## 4. Currency cases

| # | `CCY_CD` | Expected predicate / display |
|---|---|---|
| C1 | `""` (All) | No currency predicate; both local + USD blocks shown; REC has rows per currency |
| C2 | `FCY` | `ccy_cd <> 'VND'`; USD block shown, local hidden |
| C3 | local (`VND`) | `ccy_cd = 'VND'`; local block only |
| C4 | `USD` | `ccy_cd = 'USD'`; USD block; amounts formatted to 2 decimals |
| C5 | Mixed-currency company | Local-first ordering (`ccy_cd <> 'USD'` sort key); per-currency totals separated |

## 5. Account-type cases

`ACCT_TYPE_CD`: 01 loan, 02 time-deposit, 03 current-local, 04 current-USD, 05 savings-local,
06 savings-USD.

| # | Scenario | Expected |
|---|---|---|
| A1 | Current accounts only (03/04) | Rows in Current grid; financial-status `TOTL_NORM_AMT` = sum of balances |
| A2 | Savings accounts (05/06) | Rows in Savings grid; `TOTL_SAV_AMT` populated |
| A3 | Time deposit (02) | `TOTL_ACCT_AMT` computed, but the Time-Deposit panel is hidden (commented out) |
| A4 | Loan (01) | `TOTL_LOAN_AMT` computed, panel hidden; subject to maturity guard |
| A5 | Active-only filter | Accounts where `ACCT_MSTR.actv_yn = 'N'` excluded when `ACTV_ACCT_ONLY_YN = Y` |
| A6 | Internal-transfer exclusion | When `INCL_INTL_TRNS_YN = N`, account-sequences with internal usage codes (4000000008/6000000062/4000000104/6000000061) drop from per-bank detail |

## 6. Excel export cases

| # | Scenario | Expected |
|---|---|---|
| X1 | Export with nickname ON | Nickname column present in account sheets |
| X2 | Export with nickname OFF | Nickname column omitted |
| X3 | Export with internal-transfer ON | Wider layout (internal debit/credit columns) — `createSavingAccountOrCurrentAccoutWithInternalTranfersHeaderTable` |
| X4 | Export with internal-transfer OFF | Narrow layout |
| X5 | USD amounts | `#,##0.00` format; local amounts `#,##0` |
| X6 | Filename | Legacy: UA-aware `Content-Disposition`; refactor: RFC 5987 `filename*=UTF-8''…xlsx` |
| X7 | (refactor) export before View Setting seed exists | `WBE2013` (no exportable columns) — seed `SYS_SCR_OPTION_WECMS` first |

## 7. Refactor parity checks (current state — 2026-06, READY FOR SIT)

| # | Check | Current result |
|---|---|---|
| P1 | `GET /daily-financial` returns date-aware financial_status + current_accounts | ✅ as-of balances + per-day debit/credit + internal aggregates; per-currency status incl. **KRW**; verified `HTTP 200` real data |
| P2 | `current_accounts[].transactions` (inline REC2_1) populated | ❌ deferred (P1, **non-blocker**) — the Transaction History Details **popup** serves the drilldown |
| P3 | `GET /daily-financial/transactions` returns real rows + popup UI aligned to e-Invoice | ✅ from `ACCT_TRNS_HSTR`, paginated, totals; popup restyled (default page size 10 / options 10·20·50·100) — **READY FOR SIT** |
| P4 | Cash / currency-flow block present | ❌ intentionally absent (out of scope) |
| P5 | Excel export | ⚠️ frontend client-side export present; backend `DailyFinancialExportProvider` pending (P1, non-blocker) |
| P6 | Param names | `asOf` / `currencyScope` (frontend + backend agree); stub-doc reconciliation is cosmetic |
| P7 | Audit row on search | verify `AUDIT_LOGS_INFM.WSVC_ID = 'daily-financial'` (P1) |
| P8 | Popup empty-state / footer / pagination match e-Invoice | ✅ centered empty row (colSpan, fixed height), footer summary, upward page-size dropdown (no overflow) |
| P9 | Popup **Time** column when `TRSC_TM` empty | ✅ blank by design — verified not a defect ([README §6](README.md#6-time-column-verification-transaction-history-details-popup)); all-debit rows fill Debit column correctly |
| P10 | KRW Financial Status section under Currency = All | ✅ KRW current/saving accounts surface a KRW section with `₩` formatting |
