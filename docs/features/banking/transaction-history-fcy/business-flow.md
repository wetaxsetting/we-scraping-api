# Transaction History (FCY) — Business Flow (Backend)

The *how* of the refactored FCY transaction inquiry — control flow inside `TransactionService` and
the export/sync boundaries. The *what* is in [functional-specification.md](functional-specification.md);
the wire contract in [interface-specification.md](interface-specification.md). All paths relative to
`wecms-api/`. Java package: `com.kosign.wecms.domain.banking`.

> FCY shares the entire flow with the [VND twin](../transaction-history-vnd/business-flow.md). The
> single FCY-specific step is the currency filter in §1 (the `r.ccyCd` predicate). Everything else is
> currency-agnostic.

```
GET /accounts/{acctSeqNo}/transactions?from&to&type&cnfm_yn&keyword
        │
        ▼
resolve(acctSeqNo)  ── split bankCd_acctNo · AccountAccessGuard.assertCanView · recover ACCT_MSTR.CCY_CD
        │
        ▼
fetchRows(resolved, from, to)  ── DB date-range finder  →  IN-MEMORY currency filter (FCY pivot)
        │
        ▼
in-memory filter chain  ── type → classification → keyword → confirm
        │
        ▼
totals (deposit/withdraw)  +  toRow(...) mapping
        │
        ▼
TransactionListResponse(rows, total_count, deposit_total, withdraw_total)
```

---

## 1. Retrieval flow (list)

`TransactionService.list(acctSeqNo, fromDt, toDt, type, cnfmYn, keyword)` (≈ lines 100–159):

1. **`resolve(acctSeqNo)`** (≈ 675–698) — the FCY pivot:
   - Split `acctSeqNo` on the first `_` into `bankCd` / `acctNo` (missing `_` → `WBE2002`).
   - `bizKey` from `TenantContext.currentBizKey()`.
   - **`accountAccessGuard.assertCanView(bizKey, bankCd, acctNo)`** (#29) — admin bypass else active
     View grant, 403 `WBE1003` on denial.
   - **Recover the currency:**
     `acctRepo.findByBizKeyAndBankCdAndAcctNo(...).map(AcctMstr::getCcyCd).orElse(null)`.
   - Returns `record Resolved(bizKey, bankCd, acctNo, ccyCd)`.
2. **`fetchRows(resolved, fromDt, toDt)`** (≈ 700–714):
   - No date range → `txnRepo.findByBizKeyAndBankCdAndAcctNoOrderByTrscDtDescTrscTmDesc(...)`.
   - With range → `...AndTrscDtBetweenOrderByTrscDtDescTrscTmDesc(...)`.
   - **FCY currency filter (#5 — CLOSED):**
     `if (r.ccyCd != null && !r.ccyCd.isBlank()) rows = rows.stream().filter(h -> r.ccyCd.equals(h.getCcyCd())).toList();`
     — restricts the rows to the FCY account's own currency. Skipped when no `ACCT_MSTR` row exists.
3. **In-memory filter chain** — `matchesTypeFilter` → build classification map → `matchesKeyword` →
   `matchesCnfmFilter` (order matters: classification is built before keyword so keyword can hit
   contact/purpose/VAS).
4. **Totals** — sum `TRSC_AMT` over the filtered set keyed by `InoutDv.IN/OUT`.
5. **`toRow(...)`** maps each `AcctTrnsHstr` + confirmed rcv_pay + classification + match info to
   `TransactionRow`.
6. Return `TransactionListResponse(mapped, mapped.size(), AmountFormatter.wire(depositTotal), AmountFormatter.wire(withdrawTotal))`.

> **Single-currency-per-account assumption.** A `bankCd_acctNo` is assumed single-currency, so the
> `ACCT_MSTR.CCY_CD`-derived filter is sufficient. If an account legitimately spans currencies the
> route cannot disambiguate (the audit's one open FCY edge — a future `ccyCd` route segment). For all
> production FCY accounts this assumption holds.

---

## 2. Amount / balance / status projection (`toRow`)

`toRow(...)` (≈ 893–927):

- Per-transaction amount = `TRSC_AMT`, placed in `DEPOSIT_AMT` (when `INOUT_DV='1'`) or `WITHDRAW_AMT`
  (when `INOUT_DV='2'`); the other is `"0"`. All amounts wire-formatted via `AmountFormatter.wire`
  (2 dp HALF_UP, trailing zeros stripped).
- `BAL` passed through verbatim — **no running-balance recomputation** in the refactor (the legacy
  Vietcombank `11704004` recompute is intentionally absent — see [refactor-gap-analysis.md](refactor-gap-analysis.md)).
- `CONF_YN` from the confirmed-rows set; **`CONFIRMED_BY` / `CONFIRMED_DTM`** populated from the
  confirmed rcv_pay row (BUG-1 — CLOSED).
- `LINK_MATCH_YN`, `BANK_TRNS_SHORT_ID`, `PAY_ORG_FILE_NM`, `MATCHED_AT`, `MATCHED_BY` from the match
  info (BUG-2 — CLOSED).
- Classification fields (`CHART_ACCT_CD`, `CHART_ACCT_SEQ_NO`, `USE_USAG_CD`, `VAS_ACCT_CD`,
  `RCV_PAY_TRCO_KEY`, `RCV_PAY_TRCO_NM`) from the classification map.

Amounts are emitted in the account's foreign currency (the FCY value is not converted to VND).

---

## 3. Transaction status mapping

"Confirmed" is **not** a column on `ACCT_TRNS_HSTR` — it is the presence of a sibling
`ACCT_TRNS_RCV_PAY_INFM` row with `CNFM_YN='Y'`. `confirmedRowsBySeq(...)` (≈ 767–775) batch-loads
`rcvPayRepo.findByBizKeyAndAcctSeqNoIn(...)` for the page and retains `CNFM_YN='Y'` rows, mapping
`acctSeqNo` → confirmer id/name/dtm.

---

## 4. Summary flow

`summary(...)` (≈ 165–199) runs the same resolve → fetchRows (incl. currency filter) → filter chain,
then returns `TransactionSummaryResponse(total, confirmed, total-confirmed, depositTotal, withdrawTotal)`.

---

## 5. Confirm / undo-confirm flow

- `confirm(...)` (≈ 248–299): rejects if already confirmed (`WBE_TRANSACTION_ALREADY_CONFIRMED`);
  reuses the existing rcv_pay row or creates a fresh one (`RCV_PAY_SEQ_NO` = Java MAX+1 via
  `nextRcvPaySeqNo`); stamps `CNFM_YN='Y'` + confirmer fields; returns refreshed `detail(...)`.
- `undoConfirm(...)` (≈ 302–320): flips `CNFM_YN='N'`, nulls confirmer fields; throws
  `WBE_TRANSACTION_NOT_CONFIRMED` if none.

---

## 6. Classification flows (reporting account · purpose · VAS · contact)

Each setter validates against its master table then writes to `ACCT_TRNS_HSTR` (reporting account) or
the rcv_pay row (purpose / VAS / contact), all `@Transactional`:

| Flow | Validates against | Writes |
|---|---|---|
| `updateReportingAccount` / `clearReportingAccount` | `CHART_ACCT_INFM` (`STTS='A'`, `DEL_YN='N'`) | `CHART_ACCT_CD` + `CHART_ACCT_SEQ_NO` on `ACCT_TRNS_HSTR` |
| `updatePurpose` / `clearPurpose` | `BUSI_USAG_SET_INFM` (`USE_YN='Y'`) | `RCV_PAY_USAG_CD` on rcv_pay |
| `updateVasAccount` / `clearVasAccount` | `BUSI_VAS_ACCT_INFM` (`STTS='A'`, `DEL_YN='N'`) | `VAS_ACCT_CD` on rcv_pay |
| `updateContact` / `clearContact` | `TRCO_INFM` (`TRCO_STTS != 'C'`) | `RCV_PAY_TRCO_KEY` + `RCV_PAY_TRCO_NM` snapshot on rcv_pay |

`ensureRcvPayRow(...)` (≈ 631–652) returns an existing rcv_pay row or creates a fresh `CNFM_YN='N'`
one, so purpose/VAS/contact pickers work independent of Confirm.

---

## 7. Export generation flow

`POST /accounts/transactions/export` → `ExcelExportService.exportTo(response, request)` →
`TransactionHistoryExportProvider.fetch(...)`:

1. Reads `acctSeqNo` (mandatory), `from`/`to`/`type`/`cnfm_yn`/`keyword` from the request `filters`.
2. Calls `transactionService.list(...)` — reusing the same query path (currency filter + #29 guard +
   all in-memory filters).
3. Maps each `TransactionRow` via `toFieldMap` (`TRSC_DTM` = `composeDtm(trscDt, trscTm)`, `MTCH_DT` =
   `datePart(matchedAt)`, amounts raw).
4. Backend-computed subheader: `"<n> transaction(s) · Deposit total: <…> · Withdrawal total: <…>"`.
5. `ExcelExportService` intersects columns with the persisted View Setting + the allow-list, caps at
   50k rows, streams XLSX.

---

## 8. Synchronization (sibling feature)

Transaction History **reads** already-synced rows from `ACCT_TRNS_HSTR`. Pulling fresh transactions
from the bank is owned by **Account Inquiry (FCY)** via `BankTransactionSyncService` (the
`POST /accounts/transactions-sync` family + `BankScrapingController` against the Coocon / NXiSAS smart
gateway). Sync writes new `AcctTrnsHstr` rows (dedup by `BANK_TRNS_FULL_ID`) and audits each attempt
into `EXT_TRAN_CALL_HIS` (`SVC_ID = WEB_SVC_ID = "REAL_TIME_ONLINE_INQUIRY"`) via `SyncHistoryWriter`
(`@Transactional(REQUIRES_NEW)`). The sync-history read endpoint
(`GET /api/v1/banking/sync-history?currency=FCY`) translates `currency=FCY` to `ccyNot="VND"`. None
of this is invoked by the Transaction History list path. Boundary detail:
[account-inquiry-fcy/business-flow.md](../account-inquiry-fcy/business-flow.md).

---

## Transactional summary

| Method | `@Transactional` | Notes |
|---|---|---|
| `list` / `summary` / `detail` | read-only | currency filter + `AccountAccessGuard` in `resolve()` |
| `confirm` / `undoConfirm` | yes | rcv_pay write |
| `updateReportingAccount` / `clear…` (×4 classification pairs) | yes | master-table validation + write |
| Export | none on orchestrator | `ExcelExportService.exportTo` deliberately runs without an outer tx (streaming write) |
| Sync (sibling) | yes; audit row `REQUIRES_NEW` | owned by `BankTransactionSyncService` / `SyncHistoryWriter` |
