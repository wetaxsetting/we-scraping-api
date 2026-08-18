# Bank Transaction Sync — Fixed-Deposit Scraping Parity

**System:** Refactor (wecms-api) — no wecms-web change required
**Legacy screen:** `fnsh_0001_001` (Account Inquiry) → nxisas transaction-list scrape popup (`nxisas_tranList`)
**Refactor surface:** [`BankTransactionSyncService`](../../../src/main/java/com/kosign/wecms/domain/banking/nxisas/BankTransactionSyncService.java) (backend job dispatch); FE popup [`TransactionSyncDialog.tsx`](../../../../wecms-web/components/banking/account-inquiry/TransactionSyncDialog.tsx) unchanged
**Date:** 2026-07-16 (gap identified + implemented)
**Status:** ✅ **Implemented + legacy parity** (2026-07-16) — see §4 "Implementation record".

> **Sibling audit:** [bank-transaction-sync-debit-matched-parity-gap.md](bank-transaction-sync-debit-matched-parity-gap.md) covers the auto-match "Debit Matched" row on the same popup. This doc covers the account-type routing (Current/Savings vs Fixed Deposit).

---

## 1. Summary

Legacy scrapes three account types through this popup — **Savings, Current, and Fixed Deposit** — across all currencies (VND / USD / KRW). The same popup + sync endpoint serve **both** the VND account-inquiry page (`fnsh_0001_001`) and the FCY page (`fnsh_0005_001`); the refactor mirrors this — one shared `TransactionSyncDialog` → one `syncTransactions` endpoint → one `BankTransactionSyncService`. FD routing is **currency-agnostic** (keyed on `ACCT_TYPE_CD` + `BANK_CD` only); the account's real `CCY_CD` flows into the job input, so FD works identically for VND and FCY accounts. Fixed-Deposit (`ACCT_TYPE_CD = "02"`) accounts are handled by **dedicated scraping jobs** (`BALANCE_FIXED_DEPOSIT` / `TRANSACTION_FIXED_DEPOSIT`) and are **restricted to a fixed 6-bank whitelist**:

| Account | Legacy jobs dispatched |
|---|---|
| Current / Savings (any connected bank) | `BALANCE` + `TRANSACTION` |
| FD `02` @ **VCB** (`11704004`) | `BALANCE_FIXED_DEPOSIT` + `TRANSACTION_FIXED_DEPOSIT` |
| FD `02` @ **Shinhan** (`11704088`) / **ACB** (`11704011`) / **HD** (`11704101`) / **VietinBank** (`11704001`) / **VPBank** (`11704102`) | `BALANCE_FIXED_DEPOSIT` **only** (no transaction-FD support) |
| FD `02` @ any other bank | falls through to regular `BALANCE` + `TRANSACTION` (legacy else-branch) |

**The gap:** the refactor `BankTransactionSyncService` dispatched `TRANSACTION` + `BALANCE` for **every** account regardless of `ACCT_TYPE_CD`; the gateway `NxisasJob` enum did not even define the FD jobs. A selected FD account would have been sent the wrong Coocon jobs (likely a bank-adapter rejection → `WBE2095` fail-fast on a mixed batch). This audit records the fix.

**Reachability confirmed (2026-07-16).** FD accounts are genuinely selectable, so the routing is exercised end-to-end: `AccountService` admits `ACCT_TYPE_CD "02"` on **both** list scopes — `VND_TYPE_CODES = {02, 03, 05}` and `FCY_TYPE_CODES = {02, 03, 04, 05, 06, 07, 08}`, with concrete-currency narrows (USD/KRW) reusing `FCY_TYPE_CODES` and the "ALL" scope applying no type predicate. Only `01` (Loan) is excluded. See [`AccountService.java:110-121`](../../../src/main/java/com/kosign/wecms/domain/banking/service/AccountService.java) + `resolveFilter`. The display side is already FD-aware and consistent — [`AccountService.resolveLastUpdate`](../../../src/main/java/com/kosign/wecms/domain/banking/service/AccountService.java) shows `ACCT_BAL_LST_INQ_DTM` for balance-only FD banks and `ACCT_HIS_LST_INQ_DT` otherwise (same VCB-vs-others split as the scrape).

---

## 2. Legacy behavior (source of truth)

### 2.1 Job construction — `nxisas_tranList_osmc.js:300-361`

The popup groups selected accounts by `BANK_CERT_SEQ` and, per account, branches on `ACCT_TYPE_CD` + `BANK_CD`:

```js
// nxisas_tranList_osmc.js
if ("02" == obj2.ACCT_TYPE_CD && "11704004" == _nxisas_dataObj.bankCd) {
    // VCB — BALANCE_FIXED_DEPOSIT + TRANSACTION_FIXED_DEPOSIT   (:301-321)
} else if ("02" == obj2.ACCT_TYPE_CD && (
    "11704088" == bankCd || "11704011" == bankCd || "11704101" == bankCd ||
    "11704001" == bankCd || "11704102" == bankCd)) {
    // Shinhan / ACB / HD / Vietin / VP — BALANCE_FIXED_DEPOSIT only   (:323-338)
} else {
    // Current / Savings — BALANCE + TRANSACTION   (:340-361)
}
```

The comment at `:331` is explicit: *"BALANCE_FIXED_DEPOSIT only (no TRANSACTION_FIXED_DEPOSIT support)"* for the five balance-only banks.

### 2.2 Account-type codes — `osmc.ui.ts:945-978` (`getAcctType`)

- `01` = Loan (not scraped by this popup)
- **`02` = Fixed / Time Deposit (정기예적금)** ← the "Fixed" type
- `03` = Current VND · `04` = Current USD · `07` = Current KRW
- `05` = Savings VND · `06` = Savings USD · `08` = Savings KRW

Currency (`CCY_CD`) is carried on each job input, so FD and Current/Savings alike support VND / USD / KRW.

### 2.3 Display-status corroboration — `fnsh_0001_001_api_osmc.ts:719-722, 864-868`

The account-list grid routes FD-at-non-VCB rows (`ACCT_TYPE_CD == "02" && BANK_CD != "11704004"`) through a **balance-only** status path (`ACCT_BAL_INQ_STTS` / `ACCT_BAL_LST_INQ_DTM`) because those banks produce no transaction-history timestamp — consistent with them scraping FD balance only. VCB FD uses the regular history path (it scrapes FD transactions too).

### 2.4 FD job payload shape

`BALANCE_FIXED_DEPOSIT` input = `{ acctNum, currency }` (identical to `BALANCE`). `TRANSACTION_FIXED_DEPOSIT` input = `{ acctNum, currency, startDate, endDate }` (identical to `TRANSACTION`). Legacy DTOs: `kr.co.coocon.ibasews.cmo.BalanceFixedDepositInput` / `TransactionFixedDepositInput`. Only the `Job` discriminator differs — the result shapes (`balance[]` / `transaction[]`) match the regular jobs.

---

## 3. Refactor state before the fix (the gap)

| Layer | Before |
|---|---|
| Gateway enum ([`NxisasJob.java`](../../../src/main/java/com/kosign/wecms/domain/banking/nxisas/gateway/dto/NxisasJob.java)) | `BALANCE` + `TRANSACTION` only — no FD jobs (comment: *"only the values the spike consumes are kept"*) |
| Gateway client ([`NxisasGatewayClient.java`](../../../src/main/java/com/kosign/wecms/domain/banking/nxisas/NxisasGatewayClient.java)) | `balance()` + `transaction()` only |
| Service ([`BankTransactionSyncService.java`](../../../src/main/java/com/kosign/wecms/domain/banking/nxisas/BankTransactionSyncService.java)) | `processAccountAsync` unconditionally ran `TRANSACTION` → persist → `BALANCE` → stamp; `ACCT_TYPE_CD` never read; no FD bank whitelist |
| FE ([`TransactionSyncDialog.tsx`](../../../../wecms-web/components/banking/account-inquiry/TransactionSyncDialog.tsx)) | Sends `{ bank_cd, acct_no }` + window only — no account-type awareness (correct: the branch is server-side) |

---

## 4. Implementation record (2026-07-16)

**Backend-only change** — the API/WEB wire contract is unchanged because `BankTransactionSyncService` already loads `AcctMstr` per account, so it knows `ACCT_TYPE_CD` + `BANK_CD` server-side and routes without any new request field.

| File | Change |
|---|---|
| [`NxisasJob.java`](../../../src/main/java/com/kosign/wecms/domain/banking/nxisas/gateway/dto/NxisasJob.java) | Added `BALANCE_FIXED_DEPOSIT` + `TRANSACTION_FIXED_DEPOSIT` (vendor wire values verbatim). |
| [`NxisasGatewayClient.java`](../../../src/main/java/com/kosign/wecms/domain/banking/nxisas/NxisasGatewayClient.java) | Added `balanceFixedDeposit(...)` + `transactionFixedDeposit(...)` — reuse the existing `NxisasBalanceJobInput` / `NxisasTransactionJobInput` + `*JobResult` DTOs (legacy FD payloads are identical in shape). |
| [`BankTransactionSyncService.java`](../../../src/main/java/com/kosign/wecms/domain/banking/nxisas/BankTransactionSyncService.java) | Added `ACCT_TYPE_FIXED_DEPOSIT`, `BANK_VCB`, `FD_BALANCE_ONLY_BANKS`, the `ScrapeMode` enum, and `scrapeModeFor(acct)`. `processAccountAsync` skips the transaction leg for `FD_BALANCE_ONLY`, uses `transactionFixedDeposit` for `FD_WITH_TRANSACTION`, and `fetchBalance(..., fixedDeposit)` routes to `balanceFixedDeposit` for both FD modes. |
| [`BankTransactionSyncServiceFixedDepositTest.java`](../../../src/test/java/com/kosign/wecms/domain/banking/nxisas/BankTransactionSyncServiceFixedDepositTest.java) | **New** — 4 tests: VCB FD (VND) dispatches both FD jobs (never regular); Shinhan FD dispatches balance-only + still succeeds + writes a success audit row; **VCB FD (FCY / USD)** dispatches the FD jobs carrying `currency = "USD"` (proves currency-agnostic VND + FCY parity); a Current (`03`) account at an FD-whitelisted bank still uses the regular jobs (account type is the primary gate). |

`ScrapeMode` routing (mirrors `nxisas_tranList_osmc.js:300-361`):

| `ACCT_TYPE_CD` | `BANK_CD` | `ScrapeMode` | Jobs |
|---|---|---|---|
| `"02"` | `11704004` (VCB) | `FD_WITH_TRANSACTION` | `TRANSACTION_FIXED_DEPOSIT` + `BALANCE_FIXED_DEPOSIT` |
| `"02"` | Shinhan/ACB/HD/Vietin/VP | `FD_BALANCE_ONLY` | `BALANCE_FIXED_DEPOSIT` only |
| `"02"` | any other | `REGULAR` | `TRANSACTION` + `BALANCE` |
| not `"02"` | any | `REGULAR` | `TRANSACTION` + `BALANCE` |

**Tests:** `BankTransactionSyncServiceFixedDepositTest` (4/4 pass) + the pre-existing `BankTransactionSyncService*` suite (async / cancel / login-grouping / vp-profile) all green — existing tests mock `AcctMstr` (so `getAcctTypeCd()` is null → `REGULAR`), confirming no behavioural change for Current/Savings.

**Migration safety:** no DB change (`ACCT_TYPE_CD` was already mapped on `AcctMstr`), no schema/DDL, no new table, no `wb_get_next_val` branch. Pure dispatch logic + two vendor-contract job values.

---

## 5. Known divergence left as-is (out of scope)

The refactor sync is **fail-fast** for every account type — a bank-adapter transaction failure throws `WBE2095` and aborts the whole batch ([`BankTransactionSyncService.java` transaction-leg](../../../src/main/java/com/kosign/wecms/domain/banking/nxisas/BankTransactionSyncService.java)). Legacy instead accumulates per-account success/failure and finalizes with a "Success X / Failed Y" summary. This fail-fast model **predates** this change and applies uniformly to all account types; converting it to per-account accumulation is a separate, cross-cutting item and was **not** touched here. `FD_WITH_TRANSACTION` (VCB) therefore inherits the same fail-fast behavior on a `TRANSACTION_FIXED_DEPOSIT` failure. Raise with the PO if per-account accumulation is required.

---

## 6. References

- Refactor service: [`BankTransactionSyncService.java`](../../../src/main/java/com/kosign/wecms/domain/banking/nxisas/BankTransactionSyncService.java)
- Refactor gateway: [`NxisasGatewayClient.java`](../../../src/main/java/com/kosign/wecms/domain/banking/nxisas/NxisasGatewayClient.java) · [`NxisasJob.java`](../../../src/main/java/com/kosign/wecms/domain/banking/nxisas/gateway/dto/NxisasJob.java)
- Refactor test: [`BankTransactionSyncServiceFixedDepositTest.java`](../../../src/test/java/com/kosign/wecms/domain/banking/nxisas/BankTransactionSyncServiceFixedDepositTest.java)
- JEX business flow: [`docs/jex/features/account-inquiry-vnd/business-flow.md`](../../../../docs/jex/features/account-inquiry-vnd/business-flow.md) §4 "Scraping job construction"
- Legacy job dispatch: `WECMS DEV/WECMS_STATIC/web/js/jex/wabooks/nxisas/nxisas_tranList_osmc.js:300-361`
- Legacy account-type map: `WECMS DEV/WECMS_STATIC/ts-src/js/wabooks/osmc.ui.ts:945-978`
- Legacy display-status branch: `WECMS DEV/WECMS_STATIC/ts-src/js/jex/wabooks/fnsh/fnsh_0001_001_api_osmc.ts:719-722, 864-868`
- Legacy FD job enum: `WECMS DEV/WECMS/src/kr/co/coocon/ibasews/enums/NxisasJob.java:24-25`
- Sibling audit: [bank-transaction-sync-debit-matched-parity-gap.md](bank-transaction-sync-debit-matched-parity-gap.md)
