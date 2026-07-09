# Account Inquiry (VND) — Business Flow (Refactor Backend)

**Screen ID:** `banking-account-inquiry-vnd` · **JEX:** `fnsh_0001_001` · **Hub:** [README.md](README.md)

How each operation behaves in the refactored Spring Boot backend. Field/column details are in
[database-mapping.md](database-mapping.md); the wire contract is in [interface-specification.md](interface-specification.md).

---

## 1. Account retrieval flow (list)

```
GET /api/v1/banking/accounts?currency=VND&bankCd=&keyword=&sortField=&sortDirection=&page=0&size=10
        ↓ AccountController.listAccounts
        ↓ AccountService.list(filter, sortField, sortDirection, page, size)
   1. Resolve currency scope → (ccyEq, ccyNot, acctTypeCdIn)   // VND → CCY_CD='VND', type IN (02,03,05)
   2. authUserId = currentIsAdmin() ? null : currentUserId()   // per-user authority gate
   3. AcctMstrRepository.findFiltered(bizKey, ccyEq, ccyNot, acctTypeCdIn, bankCd, keywordPattern, authUserId, pageable)
   4. loadConnectedBanks(bizKey)                                // BANK_CD set with ≥1 credential in BANK_CERT_INFO
   5. map each AcctMstr → AccountRow (toRow)                    // formatted nickname, bankNm, cert/manual flags
   6. AcctMstrRepository.aggregateByCcy(...)                    // SAME WHERE, unpaged → count + SUM(BAL) + SUM(REAL_AMT)
   7. AcctMstrRepository.findDistinctBankCds(...)              // dropdown options (NOT narrowed by bankCd/keyword)
        ↓
   AccountListResponse { accounts, total_count, balance, available_bal, totals_by_ccy,
                         page_number, page_size, total_pages, banks_available }
```

- **Keyword** matches the *formatted* nickname (`wb_format_acct_nick_nm(acct_nick_nm, bank_cd, acct_no, hand_reg_yn)`),
  the same string shown on screen — including the synthesized `<bank-short> <last4>` for empty raw nicknames.
- **`acct_seq_no`** is synthetic: `BANK_CD + "_" + ACCT_NO` (stable client row key; not a stored column).
- **Totals** never recompute from the page slice — the aggregation query shares the list's WHERE bindings.

---

## 2. Balance inquiry / update-balance flow

The refactor exposes balance refresh through the sync orchestrator (the "Update" button selects accounts
and runs a real-time inquiry) and, for the Own Account Transfer composer, a dedicated `POST .../own-account-transfer/balance-refresh`.

```
User selects accounts → "Update" → POST /api/v1/banking/accounts/transactions-sync
        ↓ BankTransactionSyncService.start(req)
   per account:  LOGIN job → BALANCE job → TRANSACTION job   (via NXiSAS / Coocon smart-gateway)
        ↓
   BALANCE result:
     - ACCT_BAL_INQ_STTS = engine code, ACCT_BAL_INQ_MSG = message
     - on success (00000000 / 42110000): ACCT_BAL_LST_INQ_DTM = now
         · new balance ≠ BAL → CORC_BF_BAL = old BAL; BAL = new; ACCT_BAL_UPD_DTM = now
         · new available ≠ REAL_AMT → REAL_AMT = new
     - on failure: AcctMstrStatusUpdater.markScrapeFailure(...) stamps LST_LGN_STTS / ACCT_BAL_INQ_STTS /
       ACCT_HIS_INQ_STTS + messages in a REQUIRES_NEW tx, and does NOT stamp *_LST_INQ_DTM (legacy parity)
        ↓
   UI re-fetches the list → refreshed balance + last-update + status columns
```

Repository write paths:
- `AcctMstrRepository.applyOwnTransferBalanceRefresh(...)` — conditional UPSERT: always stamps
  `ACCT_BAL_INQ_STTS/MSG` + `ACCT_BAL_LST_INQ_DTM` + `MOD_ID/DTM`; updates `BAL`/`REAL_AMT`/`CORC_BF_BAL`/`ACCT_BAL_UPD_DTM`
  via `COALESCE(:new, existing)` so a status-only refresh leaves balances untouched.
- `AcctMstrRepository.markScrapeFailure(...)` — failure stamp (no datetime), native.

This mirrors JEX `fnsh_0001_001_c001_act.jsp` BALANCE-job handling 1:1 (`ACCT_MSTR_U002`).

---

## 3. Account synchronization flow

```
Sync Request (selected accounts) → BankTransactionSyncService.start
        ↓
   resolve credential for each BANK_CD (BANK_CERT_INFO) → WBE2050 if none
   resolve NxisasBankConfig for the bank          → WBE2093 if none
        ↓
   gateway LOGIN → may return a challenge:
        OTP_REQUIRED / CAPTCHA_REQUIRED / QNA_REQUIRED
        → in-flight batch persisted on NxisasScrapeSession (mode=SYNC), sessionId returned
        → resume: POST .../transactions-sync/{sessionId}/{otp|captcha|qna}
        ↓
   on LOGIN success: LST_LGN_STTS / LST_LGN_STTS_MSG / LST_LGN_SUC_DTM updated
   BALANCE + TRANSACTION jobs run → ACCT_MSTR + ACCT_TRNS_HSTR updated, EXT_TRAN_CALL_HIS appended
        ↓
   TransactionsSyncResponse { status: COMPLETED, accounts_synced, debit_count, credit_count,
                              credit_total, debit_total, last_update }
```

Error codes: `WBE2050` (no credential), `WBE2093` (no gateway config), `WBE2060` (session expired),
`WBE9001` (wrong resume verb), `WBE2095` (bank/gateway rejection — raw code echoed in args),
`WBE_BANK_ACCOUNT_NOT_FOUND` (account not in tenant).

---

## 4. Transaction synchronization flow

Same orchestrator as §3 — the TRANSACTION job inserts/updates `ACCT_TRNS_HSTR` rows for the window and
stamps `ACCT_HIS_INQ_STTS` / `ACCT_HIS_INQ_MSG` / `ACCT_HIS_LST_INQ_DT`. Synced debit transactions are
eligible for auto-match to ERP purchase orders downstream (legacy `BUSI_MATCH_LOG` path — out of this
screen's scope but triggered by the same sync). Each external call is logged to `EXT_TRAN_CALL_HIS`
(`SVC_ID = REAL_TIME_ONLINE_INQUIRY`), which is what the **Sync History** dialog reads back via
`GET /api/v1/banking/sync-history`.

---

## 5. Last-update display calculation

The "Last Update" cell is driven by the inquiry status columns, not a single timestamp. The display
**source is `ACCT_BAL_LST_INQ_DTM`** (stamped only on a successful balance inquiry).

| Condition | Display |
|---|---|
| Login + balance + history all success (`00000000` / history also `42110000`) | Datetime from `ACCT_BAL_LST_INQ_DTM` (humanized). If > 72 h old → stale warning glyph + tooltip. |
| Balance failed (`ACCT_BAL_INQ_STTS ∉ success`) | `ACCT_BAL_INQ_MSG` (error text) |
| Transaction history failed (`ACCT_HIS_INQ_STTS ∉ success`) | `ACCT_HIS_INQ_MSG` (error text) |
| Never inquired (status null) / manual account | "No update information" / blank |

Success codes: `00000000` (standard) and `42110000` (history: "no new transactions in window" — a
terminal success, not an error). The backend ships the raw `acct_bal_inq_stts` + `acct_bal_upd_dtm`
(sourced from `ACCT_BAL_LST_INQ_DTM`, HR-1) on the wire; the frontend renders the Success/Failed/datetime
decision. See [database-mapping.md §Status mapping](database-mapping.md#status-mapping).

---

## 6. Status calculation

`acct_bal_inq_stts` is shipped verbatim from `ACCT_MSTR.ACCT_BAL_INQ_STTS`. The frontend maps it to a
boolean `error` (`"ERR"`/`"ERROR"` or any non-success code → error) and renders Success vs. a red Failed
label. The backend does **not** collapse the code to a boolean — it ships the raw code so the UI keeps
the legacy success-code set (`00000000`, `42110000`) configurable. On a failed scrape the balance is
**not** zeroed — the last known `BAL`/`REAL_AMT` stays and the row is flagged.

---

## 7. Bank integration flow (end-to-end)

```
User Request (Update / Sync)
        ↓
POST /api/v1/banking/accounts/transactions-sync        (AccountController → AccountService.syncTransactions → BankTransactionSyncService)
        ↓
NXiSAS local engine ↔ Coocon smart-gateway             (dev-sgw…:8443 DEV/SIT · sgw.webcashvietnam.com UAT/PROD)
        ↓ LOGIN → BALANCE → TRANSACTION jobs per account
Bank response (balance / available / transactions, or OTP/CAPTCHA/QnA challenge)
        ↓
Database update: ACCT_MSTR (balance + status + last-inq dtm), ACCT_TRNS_HSTR (rows), EXT_TRAN_CALL_HIS (call log)
        ↓
UI refresh: list re-fetch → updated balance, available, last-update, status columns; Sync History reads EXT_TRAN_CALL_HIS
```

The Coocon smart-gateway is shared with the FX-rate path — configured only via `wecms.coocon.smart-api-*`
(no separate `wecms.nxisas.*`). See memory `reference_nxisas_gateway_urls` and the
[NXiSAS / Coocon spike](../../../../references/audits/nxisas-vcb-spike.md).
