# Bank Transaction Sync Per-Credential Resolution — Test Result

**Date:** 2026-08-27
**Type:** TEST RESULT
**Status:** PASS
**Repository:** wecms-api
**Related Record:** [Bank Transaction Sync scrapes every account at a bank with one arbitrary credential](../../bugs/bank-transaction-sync-per-credential-resolution-2026-08-27.md)

## Scope

Verifies that `BankTransactionSyncService` resolves the scraping credential from each account's
own `ACCT_MSTR.BANK_CERT_SEQ` and groups the engine login per credential, instead of picking the
lowest `SEQ_NO` registered at the bank and grouping per `BANK_CD`.

No API, wire-contract, or database change is in scope.

## Environment

LOCAL only. Windows 11, Temurin JDK 21, Gradle 8.14.4.
SIT / UAT verification: **NOT RUN** — deployment is manual and has not happened for this change.

## Code / Build Under Test

Branch: current working tree · Commit: **NOT COMMITTED** at time of writing · Build: local
`./gradlew compileJava` + `./gradlew test`.

## Automated Tests

| Test / Command | Result | Evidence |
|---|---|---|
| `./gradlew compileJava` | **PASS** | BUILD SUCCESSFUL, no warnings introduced |
| `./gradlew test --tests "com.kosign.wecms.domain.banking.nxisas.*"` | **PASS** | 12 test classes — 9+8+4+11+2+16+5+4+6+2+3+7 = **77 tests, 0 failures, 0 errors** |
| `./gradlew test --tests "com.kosign.wecms.domain.banking.*"` | **PASS** | BUILD SUCCESSFUL |

## Regression Tests

New file section in
`src/test/java/com/kosign/wecms/domain/banking/nxisas/BankTransactionSyncServiceLoginGroupingTest.java`
(suite grew 7 → 11 declared cases; 5 new methods, one of which replaces coverage the old
bank-only grouping implied).

**Did a regression test cover the original defect? YES.**

- Test file: `BankTransactionSyncServiceLoginGroupingTest.java`
- Test cases:
  - `twoCredentialsSameBank_oneLoginPerCredential_eachWithItsOwnIdAndPassword`
  - `boundAccount_usesItsOwnCredential_notTheLowestSeqAtTheBank`
  - `twoAccountsOnTheSameCredential_stillLoginOnce`
  - `crossBankBinding_isRefused_ratherThanScrapedWithTheWrongBanksLogin`
  - `unboundAccount_fallsBackToTheBankLevelLookup`
- Behavior protected: each account is scraped with the credential it is bound to; one engine
  LOGIN per credential (not per bank); each credential resolves its own module CLASS from its
  `NXISAS_GB_SEQ_NO`; a cross-bank binding is refused rather than followed; an unbound account
  still reaches the documented bank-level fallback.

**Fail-before-fix verified.** Both changed lines were reverted in place
(`resolveCredentialForAccount(acct)` → `loadForScrapingByBank(acct.getBankCd())`, and
`loginGroupKey` back to returning `bankCd` alone) and the suite re-run:

```
BankTransactionSyncServiceLoginGroupingTest > twoAccountsOnTheSameCredential_stillLoginOnce() FAILED
BankTransactionSyncServiceLoginGroupingTest > crossBankBinding_isRefused_ratherThanScrapedWithTheWrongBanksLogin() FAILED
BankTransactionSyncServiceLoginGroupingTest > boundAccount_usesItsOwnCredential_notTheLowestSeqAtTheBank() FAILED
BankTransactionSyncServiceLoginGroupingTest > twoCredentialsSameBank_oneLoginPerCredential_eachWithItsOwnIdAndPassword() FAILED
11 tests completed, 4 failed
```

The 7 pre-existing cases stayed green under the revert, confirming the new tests — not the old
ones — are what pin the fix. Restoring the fix returned 11/11.

`unboundAccount_fallsBackToTheBankLevelLookup` passes both before and after by design: it asserts
the fallback path is *preserved*, so it is a guard against over-correction rather than a
fail-before-fix case.

## Test Scenarios

| Scenario | Expected | Actual | Result |
|---|---|---|---|
| Vietcom, 2 credentials (seq 1 DigiBiz `NXISAS_GB_SEQ_NO=1`, seq 2 iB@nking `=0`), one account bound to each | 2 LOGINs, each with its own ID + password; classes `DIGIBIZ` + `CORPORATE`; bank-level lookup never called | as expected | PASS |
| Account bound to seq 2 while seq 1 is the lowest at the bank | login carries `…E111` (seq 2), `loadForScraping(1)` never called | as expected | PASS |
| 2 accounts on the same credential | 1 LOGIN, 2 TRANSACTION calls | as expected | PASS |
| Account whose `BANK_CERT_SEQ` points at another bank's credential | session FAILED with `WBE2050`, gateway login never attempted | as expected | PASS |
| Account with `BANK_CERT_SEQ` null | falls back to `loadForScrapingByBank`, session completes | as expected | PASS |
| Pre-existing: 2 accounts same bank, single credential | still 1 LOGIN | unchanged | PASS |
| Pre-existing: 2 banks × 2 accounts | 1 LOGIN per bank group | unchanged | PASS |
| Pre-existing: OTP pause → resume drains the group without re-login | unchanged | unchanged | PASS |

## API Verification

NOT APPLICABLE — no endpoint, DTO, or wire field changed. `TransactionsSyncRequest.AccountKey`
still carries only `bank_cd` + `acct_no`; the binding is read server-side.

## UI Verification

NOT RUN — no frontend change, and no browser environment was used.

## Database Verification

NOT APPLICABLE for schema — `Database Impact: NONE`.

Read-only evidence queries were run against the shared dev DB (`wecmsdevdb`) to establish the
defect and to size the fallback risk:

| Query | Finding |
|---|---|
| `bank_cert_info` + bound-account count for `VN_VNM0004_000001` | Vietcom `11704004` has seq 1 (`nxisas_gb_seq_no=1`, `…S07`, 0 accounts) and seq 2 (`=0`, `…E111`, 3 accounts) — the lowest `SEQ_NO` is the wrong one |
| `acct_mstr` grouped by `hand_reg_yn` / `bank_cert_seq IS NULL` | 37 bound, **17 unbound** with `hand_reg_yn='N'` |
| unbound rows grouped by `bank_cd` | 12 banks, **none Vietcom**, all single-sub-type — so the retained bank-level fallback is not ambiguous on current data |

## Security / Permission Verification

No auth, permission, or tenant-scope surface changed. The change is strictly *more* restrictive:
a credential belonging to another bank is now refused (`WBE2050`) instead of being forwarded to
the engine. Tenant scoping is unchanged — `loadForScraping` remains tenant-checked via
`loadOrThrow`.

## Performance / Concurrency Verification

The login-success path adds one `acctRepo.findByBizKeyAndBankCdAndAcctNo` lookup per LOGIN (not
per account) to rebuild the group key from the wire `AccountKey`. Negligible, and the same
repository method is already called once per account at :520.

Login count changes only for a tenant with multiple credentials at one bank: N credentials → N
logins. That is the legacy behaviour (`nxisas_tranList_osmc.js:265`), not a regression.

## Existing Failures

None. The whole `domain.banking` package was green before and after.

The 17 failures observed on the first run of this change were **caused by the change** and are
fixed, not carried forward: Mockito defaults a `Long` getter to `0L` rather than `null`, so four
suites' unbound `AcctMstr` stubs reported `BANK_CERT_SEQ = 0` and diverted into the by-seq lookup
with no stub behind it. Those stubs now return `null` explicitly, and the service guards a null
credential with `WBE2050`.

## New Failures

None.

## Not Tested

- Live NXiSAS engine round-trip — the Coocon gateway is unreachable from local R&D.
- The two-login path against real Vietcom credentials — needs SIT plus an account actually bound
  to the DigiBiz credential (today SIT has none).
- Browser / visual verification — no frontend change.

## Final Result

**PASS**

## Recommendation

**READY FOR MANUAL COMMIT.** SIT verification of the two-credential path should follow the
deploy, using a Vietcom account registered under each credential.
