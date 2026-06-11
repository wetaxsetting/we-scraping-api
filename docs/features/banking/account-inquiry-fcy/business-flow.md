# Account Inquiry (FCY) — Business Flow (Backend)

> The *how*. Retrieval, currency filtering, balance/last-update calculation, account-sync,
> transaction-sync, and bank-integration flows. Companion: [functional-specification.md](functional-specification.md).

## 1. FCY account retrieval flow

```
GET /api/v1/banking/accounts?currency=FCY&bankCd&keyword&sortField&sortDirection&page&size
   ↓ AccountController.listAccounts  (@PreAuthorize canAccessAccountInquiry → banking-fcy-update-inquire)
   ↓ AccountService.list(AccountFilter, sortField, sortDirection, Pageable)
       1. resolveFilter(filter)          → ResolvedFilter {ccyEq, ccyNot, acctTypeCdIn, bankCd, keywordPattern}
       2. buildSort(...) + DEFAULT_SORT  → Spring Data Sort
       3. AcctMstrRepository.findFiltered(bizKey, ...resolved..., authUserId, pageable)  → Page<AcctMstr>
       4. AcctMstrRepository.aggregateByCcy(bizKey, ...resolved..., authUserId)           → List<CcyAggregate>
       5. loadConnectedBanks(bizKey)     → Set<BANK_CD> with a live BANK_CERT_INFO credential
       6. toRow(acctMstr, connectedBanks) per row  → AccountRow  (resolveLastUpdate applied here)
       7. findDistinctBankCds(...)        → banks_available dropdown options
   ↓ AccountListResponse { accounts[], total_count, balance, available_bal, totals_by_ccy[],
                           page_number, page_size, total_pages, banks_available[] }
```

### Currency filtering logic (`resolveFilter`, `AccountService.java:444-474`)

```java
switch (currency.toUpperCase()) {
  case "FCY" -> { ccyNot = "VND"; acctTypeCdIn = FCY_TYPE_CODES; }   // all non-VND  (FCY-A01)
  case "VND" -> { ccyEq  = "VND"; acctTypeCdIn = VND_TYPE_CODES; }
  case "USD" -> { ccyEq  = "USD"; acctTypeCdIn = USD_TYPE_CODES; }
  case "KRW" -> { ccyEq  = "KRW"; acctTypeCdIn = KRW_TYPE_CODES; }
  default    ->   ccyEq  = currency;                                 // EUR/JPY/… no type restriction
}
```

`FCY_TYPE_CODES = {02, 04, 06, 07, 08}`. The FCY page's "All Currency" option sends `currency=FCY`; a
narrow sends the concrete CCY. The same query path serves both — there is no separate FCY endpoint.

## 2. Balance inquiry / last-update calculation

Balances are read from the **live snapshot** on `ACCT_MSTR` (`BAL`, `REAL_AMT`) — the refactor runs no
historical / as-of-date query on the list path. `toRow` projects each entity to an `AccountRow`,
formatting amounts per the row's own `CCY_CD`.

> **Note (OQ-FCY-2):** legacy `fnsh_0005_001` *does* reconstruct as-of-date balances from `ACCT_TRNS_HSTR`
> (`IDO.ACCT_MSTR_R004.xml:90-217`, incl. Vietcombank `11704004`). The refactor's snapshot-only behaviour
> is a **PO Approved Deviation (Option B, 2026-06-06)**; full parity (Option A) is deferred. The earlier
> "FALSE POSITIVE" label was incorrect and is retracted — see [refactor-gap-analysis.md](refactor-gap-analysis.md).

**Last-update source** (`resolveLastUpdate`, FCY-A02):

```java
boolean fixedDepositOnly = "02".equals(a.getAcctTypeCd()) && !"11704004".equals(a.getBankCd());
return fixedDepositOnly ? a.getAcctBalLstInqDtm()       // FD-only banks (no tx history)
                        : a.getAcctHisLstInqDt();        // online accounts
```

The real sync stamps `ACCT_HIS_LST_INQ_DT` (`BankTransactionSyncService.processOneAccount:555`), so the
column moves after an Update.

## 3. Account synchronization / Update Balance flow

```
POST /api/v1/banking/accounts/transactions-sync   { accounts:[{bank_cd,acct_no}], from_dt?, to_dt?, auto }
   ↓ AccountController.syncTransactions
   ↓ AccountService.syncTransactions → BankTransactionSyncService.start(req)
       resolve credentials (BANK_CERT_INFO) → NxisasGatewayClient (Coocon smart-gateway)
       JOBs: LOGIN → TRANSACTION → BALANCE
       on challenge → return status OTP_REQUIRED | QNA_REQUIRED | CAPTCHA_REQUIRED + sessionId
       on success   → processOneAccount: INSERT ACCT_TRNS_HSTR (dedup on BANK_TRNS_FULL_ID SHA-256)
                      + refreshBalance writes ACCT_MSTR.BAL/REAL_AMT + stamps ACCT_HIS_LST_INQ_DT
   ↓ TransactionsSyncResponse { session_id, status, accounts_synced, debit_count, credit_count,
                                credit_total, debit_total, last_update, ... }
```

**Challenge resume** (carry the `session_id` back):

```
POST /api/v1/banking/accounts/transactions-sync/{sessionId}/qna      { question, hidden_value, answer }
POST /api/v1/banking/accounts/transactions-sync/{sessionId}/otp      { value }
POST /api/v1/banking/accounts/transactions-sync/{sessionId}/captcha  { value }
```

Session state is held by `NxisasScrapeSession` / `NxisasSessionRegistry` between the challenge and the
resume call.

**Window defaults:** `from`/`to` default to `now-30d .. now` with a 92-day cap
(`BankTransactionSyncService.java:138-163`); the request may override.

**Error codes** (subset): `WBE2050` no credential for bank · `WBE_BANK_ACCOUNT_NOT_FOUND` account not in
tenant · `WBE2093` bank lacks NxisasBankConfig · `WBE2060` session not found/expired · `WBE9001` wrong
resume verb · `WBE2095` bank/gateway rejection. Coocon success code `00000000`.

### R&D scope note (FCY-A03)

The server-side Coocon path is fully coded, but workspace R&D rules forbid live bank integration
locally, so the scrape may not actually run in the local env. The flow + dialog are testable with the
backend reachable; live-bank validation is **scope-deferred**. Document expected local behaviour for the
tester (Mr. Youvita): scrape likely unavailable locally — exercise the UI/flow path.

## 4. Transaction synchronization & history retrieval

- **Sync** writes transactions into `ACCT_TRNS_HSTR` (idempotent on `BANK_TRNS_FULL_ID`), respecting
  `LOCKED_YN='Y'` rows (re-scrape does not overwrite `rmrk`/`rmrk2`/match flags).
- **History retrieval** (balance popup + drill-down): `GET /api/v1/banking/accounts/{acctSeqNo}/transactions`
  with `from`/`to`/`type`/`cnfm_yn`/`keyword`. On the FCY screen the balance-click popup fetches the
  clicked account's transactions for `from=to=selectedDate` and offers a "View Details" deep-link to the
  per-account FCY Transaction History (legacy `fnsh_0006_001`).

## 5. Status calculation

Per-row inquiry status flows verbatim from `ACCT_MSTR` columns set by the last scrape:
`ACCT_BAL_INQ_STTS` / `ACCT_BAL_INQ_MSG` (balance), `ACCT_HIS_INQ_STTS` / `ACCT_HIS_INQ_MSG` (history),
`LST_LGN_STTS` / `LST_LGN_STTS_MSG` (login). On scrape failure `markScrapeFailure` (REQUIRES_NEW
transaction) stamps these so the failure survives the outer rollback and the row keeps its error state.

## 6. Bank integration flow (summary)

```
User clicks Update on selected FCY rows
      ↓ POST /accounts/transactions-sync
      ↓ BankTransactionSyncService → NxisasGatewayClient (RestClient)
      ↓ Coocon smart-gateway   (dev-sgw…:8443 DEV/SIT · sgw.webcashvietnam.com UAT/PROD)
      ↓ LOGIN / TRANSACTION / BALANCE jobs   (+ OTP/QNA/CAPTCHA challenge → resume endpoints)
      ↓ persist ACCT_TRNS_HSTR + ACCT_MSTR balance/status + ACCT_HIS_LST_INQ_DT
      ↓ audit into NXSIAS_MODULE_TRAN_LOG_HIS
      ↓ response counts/totals → frontend refetches the list → grid + Last Update refresh
```

Topology note (FCY-A03): legacy scraped via the **client-side NXiSAS engine** bridging to Coocon; the
refactor scrapes **server-side** via a real `RestClient` to the same Coocon backend. Same backend,
different topology — by design.
