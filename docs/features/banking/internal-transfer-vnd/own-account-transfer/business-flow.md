# Own Account Transfer — Business Flow (Refactor Backend)

End-to-end flow of the refactored Own Account Transfer feature. The **transfer itself never crosses
Spring** — the vendor-signed NXiSAS Coocon engine on the user's Windows machine drives the bank's IB
site directly. Spring's role is **list source accounts → validate → record results → refresh balances →
audit**. Legacy functional source-of-truth: [docs/jex/features/own-account-transfer/business-flow.md](../../../../../../docs/jex/features/own-account-transfer/business-flow.md).

See also: [functional-specification.md](functional-specification.md) · [interface-specification.md](interface-specification.md) · canonical spec [own-account-transfer.md](legacy-spec.md).

---

## 1. Actors & preconditions

- **Tenant ADMIN** (`BUSI_ADMN_INFM` row present) — sees every active VND current/saving account; always sees **Transfer**.
- **Tenant USER** (no admin row) — sees only accounts where `ACCT_AUTH_INFO` grants `V` or `W`; **Transfer** enabled only on `W`-grant rows.
- Scope is **own → own within one `biz_key`, VND-only** (OQ-A, PO 2026-05-18). Two flavours: `SAME_BANK` (same `BANK_CD`) and `NAPAS` (different bank, same tenant, NAPAS rails).
- NXiSAS engine installed on the user's PC for the transfer/balance flows; the page loads without it (engine-status banner + install dialogs handle the gap).

## 2. Architectural boundary

```
Browser (Next.js page + verbatim Coocon bundle)
   │  GET source-accounts / target-accounts            ─────────▶  Spring  (list, validate, audit)
   │  wss → local NXiSAS.exe → bank IB site            (Spring NOT in this path)
   │  POST scraping-result / balance-refresh           ─────────▶  Spring  (persist + audit)
   │  POST nxisas/{decrypt,encrypt,logging,cert}        ─────────▶  Spring  (SEED-CBC + audit tables)
```

## 3. Source-account listing (page load)

1. Page calls `GET …/own-account-transfer/source-accounts?bank_cd=&sortField=&sortDirection=`.
2. `OwnAccountTransferService.listSourceAccounts` (`OwnAccountTransferService.java:173`):
   - Filters `ACCT_MSTR` to `CCY_CD='VND'`, `ACTV_YN='Y'`, `ACCT_TYPE_CD ∈ {03,05}`, bank ∈ tenant allow-list.
   - **Allow-list:** default product (`F`) = Shinhan/IBK/Vietcombank/VietinBank/Woori; HAENGSUNG (`H`) = VietinBank only, with the saving-maturity + `BAL>0` gate (legacy `trns_0001_001_r001_act.jsp:74-77`).
   - Non-admin users are intersected with their `ACCT_AUTH_INFO` `V`/`W` grants; each row gets a `canTransfer` flag (true only with a `W` grant or ADMIN).
   - Batch-loads cert fields (BANK_CERT_INFO) to avoid N+1; computes totals (`tot_bal`, `tot_avi_bal`).
   - Writes a `LIST_SOURCE_ACCOUNTS` audit row.
3. Response → `SourceAccountListResponse { list[], total_count, tot_bal, tot_avi_bal, allowed_bank_cds[] }`.

## 4. Balance refresh ("Update Balance")

1. User triggers a balance refresh; the engine runs a **BALANCE** scrape and the browser POSTs the result to `…/balance-refresh`.
2. `applyBalanceRefresh` (`OwnAccountTransferService.java:293`):
   - Guards: VND-only (else `WBE_OWN_ACCOUNT_TRANSFER_INVALID_CCY`), account active, user holds `W` grant.
   - Always patches `ACCT_BAL_INQ_STTS` / `ACCT_BAL_INQ_MSG` / `ACCT_BAL_LST_INQ_DTM` + `MOD_ID/MOD_DTM`.
   - When the engine status is success (`"00000000"`) and balance changed → sets `CORC_BF_BAL = old BAL`, `BAL = new`, `ACCT_BAL_UPD_DTM = now`; when unchanged → refreshes `REAL_AMT`. Mirrors legacy `trns_0001_001_u001`.
   - Writes a `BALANCE_REFRESH` audit row.
3. Response → `BalanceRefreshResponse { bank_cd, acct_no, bal, ccy_cd, acct_bal_lst_inq_dtm }`.

## 5. Transfer composition & execution

1. User clicks **Transfer** on a row → composer dialog opens.
2. Composer lazy-loads target accounts: `GET …/target-accounts?source_bank_cd=&source_acct_no=` → `listTargetAccounts` (`OwnAccountTransferService.java:463`) returns the same own-account universe minus the source row, resolving NAPAS codes for cross-bank routing.
3. For a NAPAS (cross-bank) transfer the composer validates the recipient bank: `GET …/napas-bank-info/{napas_bank_cd}` → `lookupNapasBankInfo` returns NAPAS display names + the internal `bank_cd`.
4. The browser drives the engine: `LOGIN → (2nd-factor: OTP/QnA/Confirm) → TRANSFER[_ML|_INTERNAL] → LOGOUT`. Engine `Output.Result` is SEED-CBC encrypted; the browser round-trips it through `…/nxisas/decrypt` (gated by `wecms.oat.seed-decrypt.enabled`).
5. On completion the browser POSTs `…/scraping-result` with an `idempotency_key`. `recordScrapingResult` (`OwnAccountTransferService.java:568`) runs a 7-step validation then persists one `NAPAS_REMIT_TRNS_HSTR` row:
   1. **Idempotency** — replay returns the cached row with `already_recorded=true`.
   2. **Currency** — must be VND.
   3. **Transfer-type shape** — `SAME_BANK` needs `target_bank_cd`; `NAPAS` needs `target_napas_bank_cd`.
   4. **Amount** — `requested_amt == actual_amt` exactly (VND, no tolerance).
   5. **Source ownership** — active VND row, user `W`-grant.
   6. **Target shape** — active VND row; NAPAS code resolved to internal `bank_cd`.
   7. **Persist** — `SEQ_NO` allocated via `allocateNextSeqNo` (2-attempt retry on PK race), `TRNS_STLM_YN='N'`.
   - Writes `SCRAPING_RESULT_OK` (engine success + persisted) or `SCRAPING_RESULT_REJECT` (validation fail or engine failure).
6. Response → `ScrapingResultResponse { seq_no, idempotency_key, bank_tx_id, recorded_amt, trns_stts, status, already_recorded }`.

## 6. Status mapping

| Concept | Source | Values |
|---|---|---|
| Engine transfer status | `NAPAS_REMIT_TRNS_HSTR.TRNS_STTS` | `"00000000"` = success; any other code = failed |
| Collapsed status (wire) | `ScrapingResultResponse.status` | `"success"` / `"failed"` |
| Balance-inquiry status | `ACCT_MSTR.ACCT_BAL_INQ_STTS` | `"00000000"` = success; other = failed |
| Settlement flag | `NAPAS_REMIT_TRNS_HSTR.TRNS_STLM_YN` | `"N"` on insert; settlement batch flips to `"Y"` |

There is **no in-app approval state machine** for own-account transfers (Pending/Approved/Rejected/Processing). The bank's own confirmation (the engine's 2nd-factor + the bank IB's review) is the only review; the refactor records the final engine outcome only. See [refactor-gap-analysis.md](refactor-gap-analysis.md) for how this maps to the generic "approval/status" review areas.

## 7. Audit / external-transaction-call history

Every engine run is logged via `…/nxisas/logging` → `OatNxisasService.recordLogging` (`OatNxisasService.java:181`):
- One `NXSIAS_MODULE_TRAN_LOG_HIS` row per terminal job (LOGIN/BALANCE/TRANSFER/OTP/QNA/CAPTCHA/ACCT_LIST).
- `EXT_TRAN_CALL_HIS` rows for Firm-Banking-History scraping categories only (`BALANCE_INQUIRY`, `CARD_INQUIRY`, …). **TRANSFER jobs are NOT written to `EXT_TRAN_CALL_HIS`** — they live in `NAPAS_REMIT_TRNS_HSTR` (B10, 2026-06-04). This restores the "Scraping Balance" visibility in Firm Banking History.

See [database-mapping.md §external-transaction-call-history](database-mapping.md) for the column-level detail.
