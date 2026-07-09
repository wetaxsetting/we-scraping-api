# Transaction History (VND) — Backend Test Scenarios (SIT/UAT)

Backend-facing SIT/UAT scenarios. Frontend scenarios are in the
[frontend test-scenarios](../../../../../../wecms-web/docs/features/banking/current-savings-vnd/transaction-history/test-scenarios.md).
Expected behaviour is JEX `fnsh_0002_001` parity. All requests are JWT-scoped to a tenant + account.

Legend: ✅ = verified in SIT/UAT this pass · ⏳ = pending the PARTIAL item.

---

## Search

| # | Scenario | Expected | Status |
|---|---|---|---|
| S1 | List with no filters | All transactions for the account, most-recent-first (`TRSC_DT DESC, TRSC_TM DESC`); `TOTAL_COUNT` + deposit/withdraw totals correct | ✅ |
| S2 | Search by date range (`from`/`to`) | Only rows with `TRSC_DT` in `[from, to]`; totals recomputed for the window | ✅ |
| S3 | Search by transaction type `DEPOSIT` | Only IN rows (`INOUT_DV='1'`); `WITHDRAW_AMT` null on every row | ✅ |
| S4 | Search by transaction type `WITHDRAW` | Only OUT rows (`INOUT_DV='2'`); `DEPOSIT_AMT` null | ✅ |
| S5 | Search by confirmation `cnfm_yn=Y` | Only confirmed rows | ✅ |
| S6 | Search by keyword | Rows whose `RMRK` or `BANK_REF_NO` contains the keyword | ✅ |
| S7 | `from > to` | Validation handled; empty/clamped result, no 500 | ✅ |
| S8 | Currency context | VND account → VND rows; same endpoint serves FCY account (currency from account, not param) | ✅ |

## Transaction rows

| # | Scenario | Expected | Status |
|---|---|---|---|
| T1 | Deposit transaction | `DEPOSIT_AMT` set, `WITHDRAW_AMT` null, `BAL` = bank-supplied balance | ✅ |
| T2 | Withdrawal transaction | `WITHDRAW_AMT` set, `DEPOSIT_AMT` null | ✅ |
| T3 | Multi-currency account list | FCY account returns its own-currency rows; VND unaffected | ✅ |
| T4 | Running balance | `BAL` carried verbatim from `ACCT_TRNS_HSTR.BAL`; not recomputed | ✅ |

## Summary

| # | Scenario | Expected | Status |
|---|---|---|---|
| SM1 | Summary with filters | `totalCount` / `confirmedCount` / `unconfirmedCount` / deposit + withdrawal totals consistent with the list | ✅ |
| SM2 | Tab counters | confirmed + unconfirmed counts returned regardless of `cnfm_yn` filter (pre-status split) | ✅ |

## Detail view

| # | Scenario | Expected | Status |
|---|---|---|---|
| D1 | Open detail | Full payload incl. `RMRK`/`RMRK2`/`BANK_REF_NO`/`CCY_CD` + classification + confirmation metadata | ✅ |
| D2 | Field mappings | Every `database-mapping.md` row resolves correctly | ✅ |
| D3 | Unknown `trnsSeqNo` | not-found business error, standard envelope | ✅ |

## Confirm / undo

| # | Scenario | Expected | Status |
|---|---|---|---|
| C1 | Confirm unconfirmed txn | `CNFM_YN='Y'`, confirmer + dtm stamped, `RCV_PAY_SEQ_NO` allocated; refreshed detail | ✅ |
| C2 | Confirm already-confirmed | already-confirmed business error | ✅ |
| C3 | Undo confirmed txn | `CNFM_YN='N'`, metadata nulled, receipt/pay row preserved | ✅ |
| C4 | Undo unconfirmed | not-confirmed business error | ✅ |

## Classification

| # | Scenario | Expected | Status |
|---|---|---|---|
| CL1 | Assign reporting account | `CHART_ACCT_CD`+`SEQ_NO` set; validated against `CHART_ACCT_INFM` | ✅ |
| CL2 | Assign purpose / VAS / contact | respective columns set; validated against source table; contact name snapshotted | ✅ |
| CL3 | Clear each classification | column nulled; idempotent (re-clear is a no-op) | ✅ |
| CL4 | Invalid picker value | choice-invalid business error | ✅ |

## Sorting & pagination

| # | Scenario | Expected | Status |
|---|---|---|---|
| SP1 | Default order | most-recent-first | ✅ |
| SP2 | Per-column sort (date/amount) | server-side ORDER BY by column | ⏳ PARTIAL — sort not yet wired; see gap analysis |
| SP3 | Pagination | client-side paging over the full filtered set; counts stable | ✅ |

## Export

| # | Scenario | Expected | Status |
|---|---|---|---|
| E1 | Export with data | XLSX streamed; title + backend subheader (count + deposit/withdraw totals) + columns from View Setting ∩ allow-list | ✅ |
| E2 | Export without data | XLSX with header + zero data rows; subheader "0 transaction(s)…" | ✅ |
| E3 | Column verification | `TRSC_DTM`/`DEPOSIT_AMT`/`WITHDRAW_AMT`/`BAL`/`RMRK`/`MTCH_BY`/`CONFIRMED_BY` populated | ✅ |
| E4 | Stub columns | `TRSC_ID`/`FILE_NM`/`MTCH_DT`/`PURPOSE`/`CONTACT_NM` emit empty cells | ⏳ PARTIAL — pending DTO extension |
| E5 | Hidden/special columns | `CHECKBOX` + `USE_YN='N'` columns never appear in the file | ✅ |

## Permissions

| # | Scenario | Expected | Status |
|---|---|---|---|
| P1 | Authorized user | 200 + data | ✅ |
| P2 | Unauthorized user | `WBE1003` / 403 (or 401 if no JWT) | ✅ |
| P3 | Cross-tenant attempt | account not found in tenant — no data leak | ✅ |

## Error handling

| # | Scenario | Expected | Status |
|---|---|---|---|
| EH1 | Invalid `acctSeqNo` shape | `WBE_VALIDATION`, standard envelope | ✅ |
| EH2 | Backend error | `WBE9999` with `trace_id` | ✅ |
| EH3 | Empty result | empty `ROWS`, `TOTAL_COUNT=0`, zero totals (not an error) | ✅ |
