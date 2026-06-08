# Transaction History (VND) — Business Flow (Backend)

How the refactored Spring Boot backend retrieves, filters, maps, confirms, classifies, and exports
bank-account transactions. JEX behaviour at `fnsh_0002_001` is the functional source of truth
([docs/jex/features/transaction-history-vnd/business-flow.md](../../../../../docs/jex/features/transaction-history-vnd/business-flow.md)).

All flows are tenant-scoped via `TenantContext.currentBizKey()` and account-scoped via the
composite `{acctSeqNo}` path parameter.

---

## 1. Retrieval flow (list)

```
GET /api/v1/banking/accounts/{acctSeqNo}/transactions?from&to&type&cnfm_yn&keyword
        ↓ AccountController.listTransactions
        ↓ TransactionService.list(acctSeqNo, from, to, type, cnfm, keyword)
   resolve(acctSeqNo)            → split composite into (BANK_CD, ACCT_NO); validate account in tenant
   fetchRows(resolved, from, to) → AcctTrnsHstrRepository date-range finder (or unbounded when dates null)
   in-memory filters             → type (INOUT_DV) · keyword (RMRK / BANK_REF_NO substring) · cnfm (Y/N/ALL)
   confirmedSeqs(...)            → AcctTrnsRcvPayInfmRepository: which ACCT_SEQ_NOs have a CNFM_YN='Y' row
   rcvPayClassification(...)     → batch-load receipt/pay rows (avoids N+1) for purpose/VAS/contact/chart
   toRow(...) per row             → project deposit/withdraw from INOUT_DV + amount; attach confirm + classify
   totals                         → sum deposit + withdrawal across the filtered set
        ↓
   TransactionListResponse { ROWS[], TOTAL_COUNT, DEPOSIT_TOTAL, WITHDRAW_TOTAL }
```

**Date-range handling.** When `from`/`to` are supplied, the repository uses the
`...TrscDtBetween...OrderByTrscDtDescTrscTmDesc` finder; when absent, the unbounded
`...OrderByTrscDtDescTrscTmDesc` finder. Both return **most-recent-first** (`TRSC_DT DESC, TRSC_TM DESC`),
which is the grid's default order — matching the legacy `ACCT_TRNS_HSTR_R016` default sort.

**Why in-memory filters.** Type / keyword / confirmation are applied in Java after the date-range
fetch. The legacy IDO applied the equivalent predicates in SQL; the refactor keeps the date-range +
account predicate in the DB (the selective ones) and filters the bounded result set in memory. This
is a deliberate parity-preserving simplification, not a gap — the filtered set and totals match the
legacy output.

```
User search (filters)
      ↓
Search conditions (from/to/type/cnfm_yn/keyword)
      ↓
Backend query (date range + account, DB-side)
      ↓
Transaction-history retrieval (most-recent-first)
      ↓
In-memory filter (type / keyword / confirmation)
      ↓
Transaction mapping (deposit/withdraw projection + confirm + classify)
      ↓
Grid display (paged client-side) + totals footer
      ↓
Transaction detail view (on demand)
```

## 2. Amount / balance / status projection (`toRow`)

| Concept | Rule |
|---|---|
| Deposit amount | `INOUT_DV = '1'` (IN) → `DEPOSIT_AMT = TRSC_AMT`; else null |
| Withdrawal amount | `INOUT_DV = '2'` (OUT) → `WITHDRAW_AMT = TRSC_AMT`; else null |
| Balance | `BAL` carried verbatim from `ACCT_TRNS_HSTR.BAL` (running balance after the txn — the bank supplies it) |
| Inout enum | resolved via `InoutDv.fromCode(...)` (`1`→IN, `2`→OUT, `0`→ETC); never literal string compares |
| Confirmation | `CONF_YN = 'Y'` when at least one `ACCT_TRNS_RCV_PAY_INFM` row for the txn has `CNFM_YN='Y'` |
| Confirmed-by / dtm | from the confirmed receipt/pay row's `CNFM_BY_USER_NM` / `CNFM_DTM` |
| Match flag | `LINK_MATCH_YN` carried verbatim (Smart Organizer payment-match state) |

There is **no Java-side running-balance recomputation** — the refactor trusts the bank-supplied
`BAL` exactly as the legacy grid does. (See [refactor-gap-analysis.md](refactor-gap-analysis.md)
"Running balance".)

## 3. Transaction status mapping

The feature carries two orthogonal status axes; neither is a free-form "Success/Failed/Pending" state
(that vocabulary belongs to the Payment-result feature, not account transaction history):

| Axis | DB value | Service value | API value | UI display |
|---|---|---|---|---|
| **Confirmation** | `ACCT_TRNS_RCV_PAY_INFM.CNFM_YN` = `Y` / `N` (or no row) | `confirmed` boolean | `CONF_YN` = `"Y"` / `"N"` | Confirmed / Unconfirmed status tab + per-row Confirm/Undo button; unconfirmed rows tinted yellow |
| **Match** (Smart Organizer) | `ACCT_TRNS_HSTR.LINK_MATCH_YN` = `Y` / `N` | `linkMatchYn` | `LINK_MATCH_YN` = `"Y"` / `"N"` | matched rows show matched-date / matched-by; unmatched expose a Match hand-off |

See [database-mapping.md §Status columns](database-mapping.md#5-status--classification-columns).

## 4. Summary flow

```
GET .../transactions/summary?from&to&type&cnfm_yn&keyword
        ↓ TransactionService.summary(...)   — reuses the same fetch + filter path as list()
   TransactionSummaryResponse { totalCount, confirmedCount, unconfirmedCount, depositTotal, withdrawalTotal }
```

The summary endpoint deliberately does **not** apply the confirmation-status filter to its counts —
it returns the pre-status-tab confirmed/unconfirmed split so the frontend can render the All /
Confirmed / Unconfirmed tab counters from one call.

## 5. Confirm / undo-confirm flow

```
PUT    .../transactions/{trnsSeqNo}/confirm   (optional body: contact/purpose/VAS classification)
DELETE .../transactions/{trnsSeqNo}/confirm
```

- **Confirm** (`TransactionService.confirm`): validates the txn exists and is not already confirmed;
  creates or updates the `ACCT_TRNS_RCV_PAY_INFM` row with `CNFM_YN='Y'`; allocates `RCV_PAY_SEQ_NO`
  via per-(BIZ_KEY, ACCT_SEQ_NO) MAX+1 in Java (`nextRcvPaySeqNo`, no legacy DB function); stamps
  `CNFM_BY_USER_ID` / `CNFM_BY_USER_NM` / `CNFM_DTM`; optionally captures contact/purpose/VAS from the
  request body. Returns the refreshed detail. Mirrors `fnsh_0002_001_u001`.
- **Undo** (`TransactionService.undoConfirm`): flips `CNFM_YN` to `'N'` and nulls the confirmation
  metadata, **preserving the receipt/pay row** for audit. Returns `204`-style `ApiResponse<Void>`.

Both are `@Transactional`.

## 6. Classification flows (reporting account · purpose · VAS · contact)

Four PATCH setters + four DELETE clears, each idempotent and `@Transactional`:

| Operation | Endpoint | Validates against | Writes |
|---|---|---|---|
| Reporting account | `PATCH/DELETE .../reporting-account` | `CHART_ACCT_INFM` | `ACCT_TRNS_HSTR.CHART_ACCT_CD` + `CHART_ACCT_SEQ_NO` |
| Purpose | `PATCH/DELETE .../purpose` | `BUSI_USAG_SET_INFM` | `ACCT_TRNS_RCV_PAY_INFM.RCV_PAY_USAG_CD` |
| VAS account | `PATCH/DELETE .../vas-account` | `BUSI_VAS_ACCT_INFM` | `ACCT_TRNS_RCV_PAY_INFM.VAS_ACCT_CD` |
| Contact | `PATCH/DELETE .../contact` | `TRCO_INFM` | `ACCT_TRNS_RCV_PAY_INFM.RCV_PAY_TRCO_KEY` + name snapshot |

When a receipt/pay row does not yet exist, the setter creates one (`ensureRcvPayRow`) before applying
the classification. Picker choices are validated against their source tables (invalid choice →
business error). Each returns the refreshed `TransactionDetailResponse`.

## 7. Export generation flow

```
POST /api/v1/banking/accounts/transactions/export   (body: ExcelExportRequest)
        ↓ AccountController.exportTransactions
        ↓ ExcelExportService (common/export) → TransactionHistoryExportProvider.fetch(request)
   provider reads filters.acctSeqNo + from/to/type/cnfm_yn/keyword
   → TransactionService.list(...)            (export and list share ONE query path)
   → maps each TransactionRow → FIELD_ID-keyed cell map
   → backend-computed subheader: "{n} transaction(s) · Deposit total: … · Withdrawal total: …"
   → ExcelWorkbookWriter (Apache POI SXSSF) streams XLSX
```

- **screenId:** `banking-transaction-history`. Allow-list: `TRSC_ID`, `TRSC_DTM`, `DEPOSIT_AMT`,
  `WITHDRAW_AMT`, `BAL`, `RMRK`, `FILE_NM`, `MTCH_DT`, `MTCH_BY`, `PURPOSE`, `CONTACT_NM`,
  `CONFIRMED_BY`.
- **`TRSC_DTM`** is composed `YYYYMMDD` + `HHmmss` → `YYYY-MM-DD HH:mm:ss`.
- **Amount cells** (`DEPOSIT_AMT` / `WITHDRAW_AMT` / `BAL`) ship raw decimal; POI applies `#,##0.##`.
  The subheader totals route through `AmountFormatter.forDisplay(...)`.
- **Known stub (PARTIAL):** `TRSC_ID`, `FILE_NM`, `MTCH_DT`, `PURPOSE`, `CONTACT_NM` currently emit
  empty strings — the refactor's `TransactionRow` DTO does not yet expose those fields. `MTCH_BY` and
  `CONFIRMED_BY` both map to the confirmed-by name. Tracked in
  [refactor-gap-analysis.md](refactor-gap-analysis.md). The backend still emits the columns (so column
  visibility / order is correct); only their cell values are blank until the DTO is extended.

## 8. Synchronization (sibling feature)

Transaction History **reads** already-synced rows from `ACCT_TRNS_HSTR`; it does **not** trigger a
bank pull. Fetching fresh transactions from the bank (NXiSAS / Coocon smart-gateway) is owned by
**Account Inquiry (VND)** — `BankTransactionSyncService`
(`src/main/java/com/kosign/wecms/domain/banking/nxisas/BankTransactionSyncService.java`), surfaced
through the Account Inquiry "Update / Sync" action and recorded by `SyncHistoryController`. See
[account-inquiry-vnd/business-flow.md](../account-inquiry-vnd/business-flow.md) and memory
`reference_nxisas_gateway_urls`. The boundary is deliberate: sync writes the rows, Transaction
History inquires them.

---

## Transactional summary

| Method | Tx | Notes |
|---|---|---|
| `list` / `summary` / `detail` | `@Transactional(readOnly = true)` | pure reads |
| `confirm` / `undoConfirm` | `@Transactional` | receipt/pay write |
| `update*` / `clear*` (4 setters / 4 clears) | `@Transactional` | classification write; create rcv/pay row if absent |
| export (`ExcelExportService.exportTo`) | **no outer tx** | streaming write does not hold a DB transaction open |
