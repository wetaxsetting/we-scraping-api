# Internal Transfer Intake — Legacy Parity — Test Result

**Date:** 2026-08-18
**Type:** TEST RESULT
**Status:** PASS
**Repository:** wecms-api
**Related Record:** [Internal Transfer Intake — Two Validations Reject a Transfer Legacy Would Record](../../bugs/internal-transfer-intake-legacy-parity-2026-08-18.md)
**Issue:** —

## Scope

Verification of the two minimum legacy-parity relaxations on
`POST /api/v1/banking/own-account-transfer/scraping-result`:

1. `trnsStts` is optional (`@Size(max = 20)`, `@NotBlank` removed).
2. `requested_amt` ≠ `actual_amt` is logged, not rejected — the row is always written with the
   bank-reported amount.

## Environment

LOCAL. Pure Mockito unit tests — no Spring context, no database, no network.

## Code / Build Under Test

- Branch: `wecms-ui-fixes`
- Commit: **NOT COMMITTED** — working tree
- Build/artifact: Gradle 8.14.4, Temurin JDK 21, JUnit 5 + Mockito
- API URL/environment: not used

## Automated Tests

| Test / Command | Result | Evidence |
|---|---|---|
| `./gradlew compileJava compileTestJava` | **PASS** | clean compile |
| `./gradlew test --tests "…ownaccounttransfer.*" --tests "…transferhistory.*"` | **PASS** | `OwnAccountTransferServiceTest` **26/26**, `OwnAccountTransferControllerTest` **10/10**, `TransferHistoryQueryServiceTest` **15/15**, `TransferHistoryControllerTest` **7/7** — 0 failures, 0 errors |
| `./gradlew test` (full suite) | **PASS** | **326 test classes, 2742 tests, 0 failures, 0 errors** (aggregated from `build/test-results/test/TEST-*.xml`) |

## Regression Tests

**Did a regression test cover the original defect? YES** — for both relaxations.

### 1. Amount drift no longer rejects

- Test case: `scraping_amountMismatch_persistsLegacyParity`
  (replaces the previous `scraping_amountMismatch_throws`)
- Behavior protected: `requested_amt=1000`, `actual_amt=1001` ⇒ the intake **succeeds**,
  `status == "success"`, a row is persisted, and `TRSC_AMT` is captured as **1001** (the
  bank's figure, matching legacy `trns_0001_007.ts:2158`). `SCRAPING_RESULT_OK` is audited.
- Original failure reproduced: yes — the pre-fix code threw
  `BusinessException("WBE_OAT_AMOUNT_MISMATCH")` on exactly this input, which is what the old
  test asserted. The old assertion is now impossible to satisfy, so the replacement test fails
  against the pre-fix service and passes against the fixed one.
- Result: **PASS**

### 2. Blank `trns_stts` is accepted and stored

- Test case: `scraping_blankTrnsStts_persistsAsFailure`
- Behavior protected: `trns_stts=""` ⇒ persisted with `getTrnsStts()` **empty** (never the
  `"99999999"` sentinel), response `status == "failed"`, audited as `SCRAPING_RESULT_REJECT`.
  Matches legacy: `WSVC.trns_0002_001_c001.xml` does not require the field,
  `trns_0002_001_c001_act.jsp#valiateInput` never checks it, and the composer initialises it to
  `""` (`trns_0001_007.ts:2140`).
- Original failure reproduced: this input never reached the service before the fix — the
  `@NotBlank` on the DTO rejected it at the Bean-Validation layer (HTTP 400) before any
  service code ran, so a service-level test could not express it at all. The new test asserts
  the service accepts and correctly classifies blank; the annotation change is what lets the
  request through. This is the honest limit of unit-level coverage for a validation-annotation
  relaxation.
- Result: **PASS**

## API Verification

- Endpoint / method / path: unchanged.
- Request contract: **strictly more permissive** — a payload valid before is still valid.
  `trns_stts` blank/absent now accepted; `requested_amt` ≠ `actual_amt` now accepted.
- Response contract: unchanged shape. `WBE_OAT_AMOUNT_MISMATCH` is no longer emitted (Javadoc
  error list and the API-catalog validate-order line updated to match).
- Every other rejection path is intact and still covered:
  `WBE_OWN_ACCOUNT_TRANSFER_INVALID_CCY` (`scraping_nonVnd_throwsInvalidCcy`),
  `WBE_OAT_SAME_BANK_MISMATCH` (`scraping_sameBankMismatch_throws`),
  `WBE_OAT_SOURCE_NOT_FOUND` (`scraping_sourceNotFound_throws`),
  `WBE_OAT_SOURCE_FORBIDDEN` (`scraping_user_withoutWithdrawAuth_throwsForbidden`),
  `WBE_OAT_DUPLICATE_BANK_TX_ID` (`scraping_duplicateBankTxId_throwsAndDoesNotPersist`).

Result: **PASS**

## UI Verification

Not applicable to this repository. Paired frontend evidence:
`wecms-web/docs/testing/results/internal-transfer-engine-result-parsing-test-result-2026-08-18.md`.

## Database Verification

- Schema change: **none**. No Flyway migration, no manual DBA script, no seed.
  `TRNS_STTS VARCHAR(20)` was already nullable (`NapasRemitTrnsHstr:78`), so a blank or absent
  value needs no DDL.
- Key generation: unchanged — still funnelled through
  `LegacyFunctionRepository.nextNapasRemitSeqNo` → `wb_get_next_val`.
- Value-level effect: `TRNS_STTS` receives legacy-shaped engine codes instead of the invalid
  `'99999999'`; `TRSC_AMT` may now hold the bank's figure where it differs from the composed
  amount, which is exactly what legacy stores.
- Migration safety: **improved** — the refactor stops writing a value that cannot occur in
  legacy data.
- DB executed: **NOT APPLICABLE** (nothing to execute).

Result: **PASS**

## Security / Permission Verification

Both relaxed checks are data-integrity assertions, not security boundaries. Every
security-bearing check is untouched and still covered by passing tests: tenant scoping via
`requireBizKey()`, source ownership + `W`-grant (`userHasWithdrawAuth`), target ownership,
and the ADMIN bypass. The relaxations cannot let a caller write a row for an account they do
not own.

Result: **PASS**

## Performance / Concurrency Verification

No change to query count, transaction boundary, or the two-attempt `seq_no` allocator.
`WBE_OAT_AMOUNT_MISMATCH` previously short-circuited before the ownership lookups; removing
the throw means a drifting payload now runs the same two `AcctMstr` lookups plus one insert as
any normal intake — the ordinary path cost, not a new one.

Result: **PASS**

## Bug 1 re-verification — commit `f6031d9` still fixed

The earlier "A database error occurred" defect (non-unique `NAPAS_BANK_INFO.BANK_CD` →
`NonUniqueResultException` → `WBE_DB` on `GET /target-accounts`) is confirmed still fixed:

- Source: `grep -rn "findByBankCd\b" src/main/java` returns **one hit, inside a Javadoc
  comment** — no live caller of the non-unique derived query remains. Both call sites
  (`OwnAccountTransferService:478`, `OutBankCdNormalizer:85`) use
  `findFirstByBankCdOrderByNapasBankCdAsc`, matching legacy `IDO.ACCT_MSTR_R008`'s
  `ORDER BY NBI.NAPAS_BANK_CD ASC LIMIT 1`.
- Test: `targets_user_filtersByGrant_excludesSourceRow` stubs
  `findFirstByBankCdOrderByNapasBankCdAsc` and **passed** in this run; so did
  `targets_admin_excludesSourceRow`.
- Result: **PASS**

## Existing Failures

None. The backend suite is fully green (2742/2742).

## New Failures

None.

## Not Tested

- Bean-Validation rejection/acceptance of a blank `trns_stts` at the HTTP layer — no
  `@WebMvcTest` case exercises the intake body today (`OwnAccountTransferControllerTest` covers
  the other endpoints). The annotation change is verified by inspection; the service-level
  behaviour for blank is covered by `scraping_blankTrnsStts_persistsAsFailure`. Adding a
  controller-level validation test is a reasonable follow-up.
- Live end-to-end transfer (needs a licensed NXiSAS origin + bank credentials).
- SIT / UAT (deployment is manual and has not been performed).

## Final Result

**PASS**

## Recommendation

READY FOR MANUAL COMMIT — must ship together with the paired `wecms-web` change.

## Working-tree note

`docs/README.md` shows as modified in `git status` but was **not** touched by this change — it
was already modified in the working tree when this work started.
