# Transaction History (FCY) — Test Scenarios (Backend)

SIT/UAT scenarios for the refactored FCY transaction inquiry. Legend: ✅ verified · ⏳ pending PARTIAL.
Run the DB-backed scenarios against an FCY account in the seed tenant `VN_VNM0004_000001` (or a tenant
holding USD/KRW/JPY/EUR accounts). Cross-ref: [refactor-gap-analysis.md](refactor-gap-analysis.md),
[../../audits/transaction-history-fcy-fix-verification.md](../../../../audits/transaction-history-fcy-fix-verification.md).

## Search

| # | Scenario | Expected | Status |
|---|---|---|---|
| S1 | List by account (no filters) | All rows for the FCY `acctSeqNo`, most-recent-first, in the account's currency only | ✅ |
| S2 | Search by date range (`from`/`to`) | Only `TRSC_DT BETWEEN from AND to` | ✅ |
| S3 | Search by type `DEPOSIT` | Only `INOUT_DV='1'` rows | ✅ |
| S4 | Search by type `WITHDRAW` | Only `INOUT_DV='2'` rows | ✅ |
| S5 | Search by `cnfm_yn=Y` / `N` | Only confirmed / unconfirmed rows | ✅ |
| S6 | Keyword on remark / amount / contact / purpose / VAS / txn-id | Case-insensitive match across all of those (BUG-3) | ✅ |

## Multi-currency (FCY-T01)

| # | Scenario | Expected | Status |
|---|---|---|---|
| M1 | USD account | Rows returned all carry `CCY_CD='USD'`; amounts in USD | ✅ |
| M2 | KRW account | Rows all `CCY_CD='KRW'` | ✅ |
| M3 | JPY account | Rows all `CCY_CD='JPY'` | ✅ |
| M4 | EUR / other FCY account | Rows all the account's own currency | ✅ |
| M5 | Account whose `ACCT_MSTR.CCY_CD` differs from a stray transaction currency | Stray-currency rows excluded by the `fetchRows` currency filter | ✅ |
| M6 | Account with no `ACCT_MSTR` row (currency unresolved) | Currency filter skipped (all rows returned) — edge documented | ⏳ |

## Summary / totals

| # | Scenario | Expected | Status |
|---|---|---|---|
| T1 | Summary counts | `TOTAL_COUNT` = `CONFIRMED_COUNT` + `UNCONFIRMED_COUNT` | ✅ |
| T2 | Deposit/withdraw totals | Σ `TRSC_AMT` split by `INOUT_DV`, FCY-formatted (`AmountFormatter.wire`) | ✅ |

## Detail view

| # | Scenario | Expected | Status |
|---|---|---|---|
| D1 | Open transaction detail | Returns the row incl. `CCY_CD` and un-merged `RMRK2` | ✅ |
| D2 | Detail for unknown `trnsSeqNo` | `WBE_TRANSACTION_NOT_FOUND` | ✅ |
| D3 | Confirmer fields on a confirmed row | `CONFIRMED_BY_ID/NM` + `CONFIRMED_DTM` populated | ✅ |

## Confirm / undo

| # | Scenario | Expected | Status |
|---|---|---|---|
| C1 | Confirm an unconfirmed row | `CNFM_YN='Y'` + confirmer stamped; refreshed detail returned | ✅ |
| C2 | Confirm an already-confirmed row | `WBE_TRANSACTION_ALREADY_CONFIRMED` | ✅ |
| C3 | Undo a confirmed row | `CNFM_YN='N'`, confirmer nulled | ✅ |
| C4 | Undo a non-confirmed row | `WBE_TRANSACTION_NOT_CONFIRMED` | ✅ |

## Classification

| # | Scenario | Expected | Status |
|---|---|---|---|
| L1 | Set reporting account (valid pair) | `CHART_ACCT_CD/SEQ_NO` written; row reflects it | ✅ |
| L2 | Set reporting account (invalid) | `WBE_TRANSACTION_CHART_ACCOUNT_NOT_FOUND` / `…_CODE_MISMATCH` | ✅ |
| L3 | Set/clear purpose | `RCV_PAY_USAG_CD` written/cleared | ✅ |
| L4 | Set/clear VAS account | `VAS_ACCT_CD` written/cleared | ✅ |
| L5 | Set/clear contact | `RCV_PAY_TRCO_KEY` + `RCV_PAY_TRCO_NM` snapshot written/cleared | ✅ |
| L6 | Classify without confirm | `ensureRcvPayRow` creates a `CNFM_YN='N'` row | ✅ |

## Synchronization (sibling — Account Inquiry FCY)

| # | Scenario | Expected | Status |
|---|---|---|---|
| Y1 | Successful sync then list | Newly-scraped FCY rows appear in `ACCT_TRNS_HSTR`; list shows them | ✅ |
| Y2 | Failed sync | Failure audited into `EXT_TRAN_CALL_HIS`; list unaffected | ✅ |
| Y3 | Sync-history read `currency=FCY` | `ccyNot='VND'` filter returns FCY attempts | ✅ |

## Sorting & pagination

| # | Scenario | Expected | Status |
|---|---|---|---|
| P1 | Default order | `TRSC_DT DESC, TRSC_TM DESC` | ✅ |
| P2 | Per-column server sort | Seed-ready, not wired | ⏳ |
| P3 | Client pagination 10/20/50/100 | Full filtered set returned with `TOTAL_COUNT`; frontend pages | ✅ |

## Export

| # | Scenario | Expected | Status |
|---|---|---|---|
| E1 | Export with data | XLSX with the visible View Setting columns; subheader count + totals | ✅ |
| E2 | Export without data | XLSX with header + zero rows; subheader `0 transaction(s)` | ✅ |
| E3 | FCY amount formatting | Data cells `#,##0.##`; subheader totals via `AmountFormatter.forDisplay(..., null)` (no symbol) — confirm acceptable for FCY | ⏳ (UAT sign-off) |
| E4 | Export honours filters + currency | Export rows match the on-screen filtered FCY set | ✅ |

## Permissions

| # | Scenario | Expected | Status |
|---|---|---|---|
| A1 | Authorized non-admin (active View grant) | 200 + rows | ✅ |
| A2 | Unauthorized non-admin (no grant) | **403** `WBE1003` (`AccountAccessGuard`, #29) | ✅ |
| A3 | Admin | Bypasses per-account guard | ✅ |
| A4 | Cross-tenant `acctSeqNo` | No rows (BIZ_KEY scoping) / guard denies | ✅ |
| A5 | Smart Organizer match/unmatch account scope (R11) | Restricted to `BIZ_KEY+BANK_CD+ACCT_NO` | ✅ |

## Error handling

| # | Scenario | Expected | Status |
|---|---|---|---|
| X1 | Malformed `acctSeqNo` (no `_`) | `WBE2002` | ✅ |
| X2 | Empty result | Empty `ROWS`, `TOTAL_COUNT=0` | ✅ |
| X3 | Bank integration failure (sync) | Surfaced by Account Inquiry sync path; TH list unaffected | ✅ |
| X4 | Validation failure on a classify body | `WBE_VALIDATION` | ✅ |
