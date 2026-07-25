# A1-01 — Transaction History "Bank Account Filter" parity audit

- **System:** Refactor (wecms-api + wecms-web)
- **Feature slug:** `account-inquiry` / `transaction-history` (shared account-list query)
- **Date:** 2026-07-24
- **Author:** Claude (AI), reviewed/approved by Mr. Jung (PO)
- **Legacy sources of truth:**
  - Transaction History sidebar — `comm_0141_001_r001_act.jsp` (`getDynamic0` VND / `getDynamic1` FCY) → IDO `ACCT_MSTR_R014`
  - Account Inquiry main table — `fnsh_0001_001_r001` (`FnshHelper.fnsh_0001_001_r001_dynamic0`)
  - Bank Account Management (comparison baseline) — `comm_0183_001_r001`
- **Refactor code:**
  - `AccountService.list(...)` → `AcctMstrRepository.findFiltered / aggregateByCcy / findDistinctBankCds`
  - Both the Transaction History sidebar AND the Account Inquiry main table are served by the **single shared** `GET /api/v1/banking/accounts` path.

---

## 1. Reported issue

> Bank accounts have been configured in **Bank Account Management**. Not all configured
> bank accounts are displayed in the **Bank Account Filter** on the **Transaction History**
> screen.

## 2. Root cause & verdict for A1-01

**A1-01 — Status: WORKING AS DESIGNED (not a bug).**

Bank Account Management (`comm_0183_001` → refactor `SettingsBankAccountService.list`) lists
**every active tenant account** (any type, any currency, no per-user grant). The Transaction
History bank-account filter (and Account Inquiry) intentionally apply extra predicates — the
most decisive being **per-user account authorization** (`ACCT_AUTH_INFO`, `AUTH_TYPE='V'`,
`USE_YN='Y'`). This authorization filter is **identical in legacy and refactor**; a non-admin
user only sees the accounts explicitly granted to them.

Evidence from live JEX DEV (company `VN_VNM0004_000001`): the highlighted "missing" account
SHB 5280 (`700005285280`) is `ACCT_TYPE_CD='03'`, `CCY_CD='VND'`, `ACTV_YN='Y'`,
`HAND_REG_YN='N'` — it passes every structural filter. It is hidden purely because only
**2 of 9** users hold an enabled view grant (`ACCT_AUTH_INFO.USE_YN='Y'`); the other 7 have
`USE_YN='N'`. Legacy `comm_0141_001` hides it for those users too.

**Resolution for A1-01:** grant the affected user View authority for the account under
Settings → Users (account authority), i.e. set `ACCT_AUTH_INFO.USE_YN='Y'`, or use an
admin / already-granted user. **No authorization code change was made** — the per-user
`ACCT_AUTH_INFO` behavior is unchanged and matches legacy exactly.

## 3. Parity divergences found (and the approved dispositions)

The audit surfaced three points where the refactor shared query differed from legacy
`comm_0141_001` (the reported sidebar). All three were dispositioned by the PO on 2026-07-24.

### D1 — `HAND_REG_YN = 'N'` filter — **Status: Parity Fix (applied)**

Legacy `comm_0141_001` `getDynamic0`/`getDynamic1` force `HAND_REG_YN = 'N'` on **both** the
VND and FCY sidebar queries. The refactor `findFiltered` did **not** filter on it, so
hand/manually-registered accounts leaked into the list.

- **Fix:** unconditional `AND a.handRegYn = 'N'` added to `findFiltered`, `aggregateByCcy`
  (totals must match the rows), and `findDistinctBankCds` (bank dropdown must not list a bank
  whose only accounts are now hidden).
- **Scope note (approved refactor behavior change):** because the refactor serves both screens
  from one query, this now also hides hand-registered accounts on the **Account Inquiry** main
  table. Legacy `fnsh_0001_001` did **not** force `HAND_REG_YN`. Per the PO decision, both
  refactor screens follow one consistent rule. See §4.

### D2 — Currency → `ACCT_TYPE_CD` whitelist — **Status: Legacy-compatible enhancement (applied)**

Instead of reverting to the exact legacy FCY whitelist, use the current account model while
preserving the same business intent:

| Scope | Whitelist (refactor, after fix) | Legacy `comm_0141_001` | Notes |
|---|---|---|---|
| **Local currency (VND)** | `02, 03, 05` | `02, 03, 05` | Unchanged — exact legacy parity. |
| **Foreign currency (FCY / concrete non-VND)** | `02, 04, 06, 07, 08` | `02, 04, 06` | Legacy + the newer KRW pair `07/08`. `03`/`05` are **local-currency** types and are **excluded** from FCY. |

`AcctTypeCd` mapping: `02`=DEPOSIT, `03`=CURRENT_KHR, `04`=CURRENT_USD, `05`=SAVINGS_KHR,
`06`=SAVINGS_USD, `07`=CURRENT_KRW, `08`=SAVINGS_KRW, `01`=LOAN (always excluded).

> **Enum-naming note (verified before coding).** The `CURRENT_KHR` (03) / `SAVINGS_KHR` (05)
> constant names are **historical**: on the live VN dataset these are the local-currency
> current/savings types (the DB currency is VND; the enum label lags the geography). The
> business logic treats them as **local currency** and the DB uses **VND**. The enum was **not
> renamed** — renaming would ripple through every consumer and the DB `ACCT_TYPE_CD` codes are
> the real contract, not the Java constant names. Per PO instruction, the codes are used as-is.

This reverses the earlier refactor build that admitted 03/05 into FCY (to surface foreign
accounts the live JEX DB codes as generic 03/05). **Trade-off accepted by the PO:** a foreign
account mis-coded as generic 03/05 will not appear under FCY.

### D3 — Product Type `H` maturity/balance guard — **Status: Parity Fix (applied)**

Legacy `comm_0141_001` applies, for an `H` (HAENGSUNG) tenant, an extra guard on savings /
term-deposit types (`05`, `06`, `08`) **on the row list**:

```sql
AND (AM.ACCT_TYPE_CD NOT IN ('05','06','08')
     OR ((AM.EXPI_DT IS NULL OR TO_DATE(AM.EXPI_DT,'YYYYMMDD') >= CURRENT_DATE) AND AM.BAL > 0))
```

The refactor already applied this guard to the **totals aggregate** (`aggregateByCcy`,
"AI-06") but **not** to the row list. The fix threads the same `excludeMaturedHSavings` flag
(true only for product type `H`, read from the signed JWT) into `findFiltered` and
`findDistinctBankCds` as well, so rows + totals + bank dropdown all apply the guard
consistently. Non-`H` tenants (incl. null product type) pass `false`; the guard is a no-op.

## 4. Approved refactor behavior change (Account Inquiry ≠ exact legacy `fnsh_0001_001`)

This is recorded explicitly per the PO decision (2026-07-24): applying D1 and the D3 row-level
guard to the **shared** query intentionally changes the **Account Inquiry main table** relative
to legacy `fnsh_0001_001`, which did **not** force `HAND_REG_YN='N'` and applied the H-guard on
totals only.

- **This is NOT exact legacy parity for Account Inquiry.** It is an approved refactor decision:
  in the refactor, the Transaction History sidebar and the Account Inquiry main table follow
  **one consistent shared rule**.
- No separate endpoint or sidebar-specific query was introduced (per PO instruction).
- The per-user `ACCT_AUTH_INFO` authorization behavior is unchanged (A1-01 stays WAD).

## 5. Before / after behavior

Shared account-list query (`GET /api/v1/banking/accounts`, both screens):

| Predicate | Before | After |
|---|---|---|
| `BIZ_KEY` (tenant) | ✅ | ✅ |
| `ACTV_YN = 'Y'` | ✅ | ✅ |
| `HAND_REG_YN = 'N'` | ❌ not applied | ✅ **applied (D1)** — rows, totals, bank dropdown |
| Currency scope (VND `=`, FCY `<>`) | ✅ | ✅ |
| VND `ACCT_TYPE_CD` whitelist | `02,03,05` | `02,03,05` (unchanged) |
| FCY `ACCT_TYPE_CD` whitelist | `02,03,04,05,06,07,08` | `02,04,06,07,08` **(D2)** — drops local 03/05 |
| Per-user `ACCT_AUTH_INFO` view grant (non-admin) | ✅ | ✅ (unchanged) |
| Product-type `H` maturity/balance guard | totals only | **rows + totals + dropdown (D3)** |

Net effects:
- **Transaction History sidebar** — now matches legacy `comm_0141_001` (D1 + D3 + VND
  whitelist), with the D2 FCY enhancement (adds KRW `07/08`).
- **Account Inquiry main table** — now hides hand-registered accounts and (for `H` tenants)
  matured/zero-balance savings rows on the list. Deliberate, approved divergence from legacy
  `fnsh_0001_001` (§4).

## 6. Files changed

| File | Change |
|---|---|
| `src/main/java/com/kosign/wecms/domain/banking/repository/AcctMstrRepository.java` | `findFiltered`: `+ HAND_REG_YN='N'` (D1) `+ :excludeMaturedHSavings` guard (D3) `+ boolean param`. `aggregateByCcy`: `+ HAND_REG_YN='N'`. `findDistinctBankCds`: `+ HAND_REG_YN='N'` `+ :excludeMaturedHSavings` guard `+ boolean param`. Javadoc updated. |
| `src/main/java/com/kosign/wecms/domain/banking/service/AccountService.java` | `FCY_TYPE_CODES` → `02,04,06,07,08` (D2, removed 03/05). Whitelist + `resolveFilter` Javadoc rewritten for D2. `excludeMaturedHSavings` flag computed once pre-`findFiltered` and passed to `findFiltered` + `aggregateByCcy` + `findDistinctBankCds` (D3). |
| `src/test/java/com/kosign/wecms/domain/banking/service/AccountServiceAsOfAndHExclusionTest.java` | New mock arities; new tests: D3 row-level H flag (H→true, F→false), D3 bank-dropdown flag, D2 VND whitelist `02,03,05`, D2 FCY whitelist `02,04,06,07,08` (excludes 03/05). |
| `src/test/java/com/kosign/wecms/domain/banking/service/AccountServiceAuthorityTest.java` | Mock/verify arities updated for the new boolean param. FCY tests rewritten to assert the D2 whitelist (`02,04,06,07,08`, no 03/05) on FCY/KRW/USD; VND stays `02,03,05`. Existing `ACCT_AUTH_INFO` authority tests unchanged (A1-01 authorization behavior preserved). |

## 7. Verification

- `./gradlew compileJava` — **PASS** (exit 0).
- `AccountServiceAsOfAndHExclusionTest` + `AccountServiceAuthorityTest` — **PASS** (both classes green).
  - D1 (`HAND_REG_YN='N'`) is a static JPQL WHERE predicate (no parameter) enforced in
    `findFiltered` / `aggregateByCcy` / `findDistinctBankCds`; the fixtures use `HAND_REG_YN='N'`
    and the clause is covered by inspection (a `@DataJpaTest` runtime assertion is out of the
    project's current Mockito-only test pattern).
  - D3 verified: `excludeMaturedHSavings` is passed `true` for `H` and `false` for `F`/null to
    the row list and bank dropdown (previously aggregate-only).
  - D2 verified: VND → `02,03,05`; FCY/KRW/USD → `02,04,06,07,08` (never 03/05); LOAN 01 never.
  - Per-user `ACCT_AUTH_INFO` authorization tests still pass — A1-01 behavior unchanged.
- Full `./gradlew test` is currently blocked by an **unrelated** pre-existing compile break in
  `VasAccountServiceTest.java` (a `VasAccountService` constructor gained a 4th arg in other
  in-progress work; that test was not updated). Not part of this change and not modified here.

## 8. Frontend coordination

The §3 filter/type/guard changes are fully backend-side; the wire contract (`AccountRow`,
`/api/v1/banking/accounts`) is unchanged. The sidebar (`useTransactionHistoryAccounts`) and the
Account Inquiry table both consume the same endpoint and receive the now-consistently-filtered
list.

### D4 — Sidebar truncated to 10 accounts — **Status: Frontend parity fix (applied)**

Follow-up finding (2026-07-24): the Transaction History **sidebar** account list showed only
the first 10 accounts (e.g. 10 of 33 VND). Legacy `comm_0141_001` (IDO `ACCT_MSTR_R014`)
returns **all** accounts with no pagination and renders them in a short fixed-height scroll box.

- **Root cause:** the sidebar hook `useTransactionHistoryAccounts(scope)` called
  `fetchAccounts(scope)` with no `size`, so the paginated `/api/v1/banking/accounts` endpoint
  fell back to `AccountService.DEFAULT_PAGE_SIZE = 10`. Accounts 11..N never reached the client,
  so scrolling could not reveal them.
- **Fix (frontend-only, PO-approved 2026-07-24):**
  `components/banking/transaction-history/accounts.ts` now calls
  `fetchAccounts({ currency: scope, size: 100 })` (`SIDEBAR_MAX_ACCOUNTS` = backend
  `MAX_PAGE_SIZE`). The sidebar's existing `custom-scrollbar-y` container then scrolls through
  all accounts, matching legacy. No backend/contract change.
- **Cap caveat:** a tenant with >100 accounts in one currency scope would still truncate; a true
  unpaged mode was deferred (not a real case today — the reported tenant has 33). The main
  Account Inquiry **table** pagination (`10 of 33` + page buttons) is a separate, intentional
  refactor feature and is unchanged.
- **Presentation (D4b, 2026-07-24):** the account sub-list is now capped to a **~5-row
  fixed-height scroll box** (`--h-wb-sidebar-acct-list = 180px`, `custom-scrollbar-y`) instead of
  expanding inline within the overall sidebar scroll — matching legacy
  `inc_wabook_fnsh_lnb.jsp`. `max-h` (not a fixed height) means ≤5 accounts render at natural
  height with no scrollbar; the 6th+ triggers the nested scroll. Applied only to
  `accountList` groups (Transaction History VND/FCY) in the shared `Sidebar`, not to generic
  nested menus.
- **Verify:** `npx tsc --noEmit` in `wecms-web` — PASS (exit 0).

Files: `wecms-web/components/banking/transaction-history/accounts.ts`,
`wecms-web/components/layout/Sidebar.tsx`, `wecms-web/app/globals.css`
(`--h-wb-sidebar-acct-list` token).
