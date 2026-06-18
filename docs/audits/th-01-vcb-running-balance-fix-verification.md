# TH-01 — Vietcombank Running-Balance Fix · Verification Note

> **Finding closed:** TH-01 (`docs/audits/vcb-11704004-running-balance-parity-audit.md`)
> **Screen:** Current & Savings > Transaction History (VND) · **Bank:** `BANK_CD = '11704004'` (Vietcombank)
> **Legacy source of truth:** `IDO.ACCT_TRNS_HSTR_R016.xml:42-73` (issue #42771)
> **Date:** 2026-06-15 · **Type:** backend change (`wecms-api`) + unit tests
> **Build:** `./gradlew compileJava compileTestJava` clean; targeted tests green.

---

## What changed

Vietcombank's scrape feed leaves `ACCT_TRNS_HSTR.BAL` NULL on ~99% of rows (608/612 on DEV), so the refactor's verbatim `getBal()` read showed blank/0 running balances for every VCB transaction. The fix reproduces the legacy R016 formula:

```
BAL(row) = COALESCE( ATH.BAL, ACCT_MSTR.BAL − Σ signed(later transactions) )
```

- **Later** = `TRSC_DT >` row's date, or same date and **`numeric(ACCT_SEQ_NO) >`** row's (numeric cast, never string).
- **signed** = `+TRSC_AMT` when `INOUT_DV='1'` (deposit), else `−TRSC_AMT`; the running sum always uses `TRSC_AMT`, independent of any stored `BAL`.
- **COALESCE** — a row keeps its stored `ATH.BAL` when present; reconstruction fires only when it is NULL.
- **VCB-only** — gated on `BANK_CD = '11704004'`; every other bank returns an empty map and keeps using `ATH.BAL` directly. The reconstruction reads the **full** account ledger (not the displayed date window), matching the legacy subquery scope, and is scoped to the account's currency (legacy `ATH2.CCY_CD = ATH.CCY_CD`).

Implemented as a pure, unit-testable static kernel (`reconstructVcbBalances`) fed by a per-account ledger fetch (`vcbReconstructedBalances`). Applied on both the list grid and the single-row detail popup. The Transaction History Excel export inherits the fix automatically — `TransactionHistoryExportProvider` reuses `TransactionService.list()`.

## Files changed

| File | Change |
|---|---|
| `src/main/java/com/kosign/wecms/domain/banking/service/TransactionService.java` | Added `VCB_BANK_CD` constant, `vcbReconstructedBalances(Resolved)`, the pure `reconstructVcbBalances(List, BigDecimal)` kernel + `VCB_LEDGER_LATEST_FIRST` comparator + `numericSeq`/`signedAmt` helpers; `list()` computes the reconstruction map and passes the effective balance into `toRow`; `toRow` takes a new `effectiveBal` param and emits it; `detail()` reconstructs for the single row; added `java.util.Comparator` import. |
| `src/test/java/com/kosign/wecms/domain/banking/service/TransactionServiceVcbBalanceTest.java` | **New** — 6 tests (4 required + 2 supporting). |

**No** changes to repositories, entities, DTOs, schema, migrations, config, the Excel export provider, or the frontend. No new DB query was added — the fix reuses the existing `findByBizKeyAndBankCdAndAcctNoOrderByTrscDtDescTrscTmDesc` and `AcctMstrRepository.findByBizKeyAndBankCdAndAcctNo` finders.

## Test results

`./gradlew test --tests TransactionServiceVcbBalanceTest --tests AccountServiceAuthorityTest` → **BUILD SUCCESSFUL**.

| Test class | tests | failures | errors |
|---|---|---|---|
| `TransactionServiceVcbBalanceTest` | 6 | 0 | 0 |
| `AccountServiceAuthorityTest` (regression — shared service / Account Inquiry) | 5 | 0 | 0 |

Required cases:
1. **VCB NULL BAL reconstructs** — `kernel_nullBal_reconstructsFromMasterMinusLaterSignedTxns` + end-to-end `list_vcbAccount_returnsReconstructedBalances` (base 1000; rows resolve to 1000 / 800 / 1100).
2. **VCB stored BAL used verbatim** — `kernel_storedBal_isUsedVerbatim` (stored 12345 not overwritten by 800).
3. **Non-VCB uses ATH.BAL directly** — `list_nonVcbAccount_usesStoredBalDirectly` (master 999999 ignored; rows show stored 777 / 500).
4. **Same-date numeric ordering** — `kernel_sameDate_ordersByNumericSeqNotString` (seq "100" is later than seq "9"; row "9" = 950, proving numeric not string order).
   Supporting: `kernel_withdrawLaterTxn_signedNegative` (withdraw sign handling).

## Confirmation

- **TH-01 is CLOSED.** The VCB running balance is reconstructed exactly per legacy R016 (`COALESCE` + later-signed-sum + numeric seq ordering), on the grid, the detail popup, and (via reuse) the Excel export. Non-VCB banks are byte-for-byte unchanged. Recommend exercising one seeded VCB account in SIT to confirm the rendered column against the live system.
- **AI-01 remains separate and untouched.** Account Inquiry's as-of-date balance lives in a different IDO (`ACCT_MSTR_R004`) and a different service (`AccountService`); none of that code was modified. `AccountServiceAuthorityTest` (5/5) confirms the Account Inquiry read path is unaffected. AI-01 is still a separate open item pending the PO's FCY Option-B confirmation.
- **Smart Organizer** — not touched.
