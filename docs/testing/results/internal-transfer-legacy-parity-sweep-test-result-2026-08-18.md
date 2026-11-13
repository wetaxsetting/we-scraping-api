# Internal Transfer Intake Legacy-Parity Sweep — Test Result (Backend)

**Date:** 2026-08-18
**Type:** TEST RESULT
**Status:** PASS
**Repository:** wecms-api
**Related Record:** [Internal Transfer Intake — Remaining Legacy-Parity Differences (Sweep)](../../bugs/internal-transfer-legacy-parity-sweep-2026-08-18.md)
**Issue:** —

## Scope

Backend half of the 7-item sweep: `FEE_DFR_SBJ` `A`/`B` → `1`/`2` at the DB boundary; the
post-transfer engine-echo channel for `OUT_ACCT_NO` / `IN_ACCT_NO` / `CCY_CD`; and the
`requested_amt` + `WBE_OAT_AMOUNT_MISMATCH` reviews.

## Environment

LOCAL. Pure Mockito unit tests — no Spring context, no database, no network.

## Code / Build Under Test

- Branch: `wecms-ui-fixes`
- Commit: **NOT COMMITTED** — working tree
- Build/artifact: Gradle 8.14.4, Temurin JDK 21, JUnit 5 + Mockito + AssertJ
- API URL/environment: not used

## Automated Tests

| Test / Command | Result | Evidence |
|---|---|---|
| `./gradlew compileTestJava` | **PASS** | clean (one iteration needed to widen the record constructor in the pre-existing `scraping_nonVnd_throwsInvalidCcy` fixture) |
| `./gradlew test --tests "com.kosign.wecms.domain.banking.ownaccounttransfer.*"` | **PASS** | `OwnAccountTransferServiceTest` **32/32**, 0 failures, 0 errors |
| `./gradlew test` (full suite) | **PASS** | **326 test classes, 2748 tests, 0 failures, 0 errors** (aggregated from `build/test-results/test/TEST-*.xml`) |

Suite growth: 2742 → **2748** (+6 new tests for this sweep). All six confirmed present in the
XML report: `scraping_feeBearerA_persistsLegacySenderPaysCode`,
`scraping_feeBearerB_persistsLegacyRecipientPaysCode`,
`feeDfrSbjMapping_isIdempotentAndPassesUnknownsThrough`,
`scraping_engineEcho_persistsBankReturnedAccountsAndCurrency`,
`scraping_engineEchoAbsent_persistsComposedValues`,
`scraping_engineEchoNonVndCurrency_isRecordedNotRejected`.

## Regression Tests

**Did a regression test cover the original defects? YES — both.**

### 1. `FEE_DFR_SBJ` stored the UI vocabulary

- Test cases: `scraping_feeBearerA_persistsLegacySenderPaysCode`,
  `scraping_feeBearerB_persistsLegacyRecipientPaysCode`,
  `feeDfrSbjMapping_isIdempotentAndPassesUnknownsThrough`
- Behavior protected: `fee_dfr_sbj: "A"` on the wire ⇒ `FEE_DFR_SBJ = "1"` in the persisted row;
  `"B"` ⇒ `"2"` — the values `FeeDfrSbjType.SENDER_PAYS` / `RECEIPIENT_PAYS` declare and
  `ValidationHelper.validateFeeDfrSbjType` accepts. Captured with an `ArgumentCaptor` on
  `saveAndFlush`, so the assertion is on the row actually handed to the repository.
- Original failure reproduced: yes — both assertions fail against the pre-change service, which
  passed `req.feeDfrSbj()` straight through (`FEE_DFR_SBJ` would be `"A"` / `"B"`).
- Result: **PASS**

### 2. A bank-normalized account number could reject a completed transfer

- Test cases: `scraping_engineEcho_persistsBankReturnedAccountsAndCurrency`,
  `scraping_engineEchoAbsent_persistsComposedValues`,
  `scraping_engineEchoNonVndCurrency_isRecordedNotRejected`
- Behavior protected: the bank's `DDA…` form and a non-VND currency are **persisted verbatim**
  while the two `AcctMstr` ownership lookups still receive the **composed** keys — asserted
  explicitly with `verify(acctMstrRepo).findByBizKeyAndBankCdAndAcctNo(TENANT, SHINHAN, "0001")`.
  Blank / absent echo falls back to the composed values (legacy's failure branch).
- Original failure reproduced: the defect was *latent* — before the change there was no channel
  for the bank's values at all, so the only way to store them would have been to overwrite the
  validated fields, which would have made ownership validation fail on a `DDA…` number. These
  tests pin that the two roles stay separated, which is the guarantee that prevents it.
- Result: **PASS**

## API Verification

- Endpoint / method / path: unchanged.
- Request contract: **additive** — three new optional fields
  (`engine_out_acct_no`, `engine_in_acct_no`, `engine_ccy_cd`), each `@Size`-only. A payload valid
  before is still valid.
- `fee_dfr_sbj` wire vocabulary **deliberately unchanged** (`A`/`B`/`S`) — conversion is internal,
  so item 2 needed no contract change (exactly what the task asked for).
- Response contract: unchanged. No new error code; no error code removed beyond the already-retired
  `WBE_OAT_AMOUNT_MISMATCH`.
- Every other rejection path intact and still covered: `WBE_OWN_ACCOUNT_TRANSFER_INVALID_CCY`,
  `WBE_OAT_SAME_BANK_MISMATCH`, `WBE_OAT_SOURCE_NOT_FOUND`, `WBE_OAT_SOURCE_FORBIDDEN`,
  `WBE_OAT_DUPLICATE_BANK_TX_ID`.

Result: **PASS**

## UI Verification

Not applicable to this repository. Paired frontend evidence:
`wecms-web/docs/testing/results/internal-transfer-legacy-parity-sweep-test-result-2026-08-18.md`.

## Database Verification

```
[ ] correct artifact location            — N/A, no DB artifact produced
[x] no schema change                     — no ALTER, no new column, no migration, no seed
[x] no .sql file added                   — confirmed
[x] key generation untouched             — wb_get_next_val funnel unchanged
[x] stored VALUES change, documented     — FEE_DFR_SBJ → 1/2; OUT/IN_ACCT_NO + CCY_CD may now
                                           carry the bank-echoed value
[x] pre-existing drift detection SQL      — supplied read-only in the record's ## Database Impact
[x] nothing executed                     — no DB call was made from this session
```

`ACCT_BAL_INQ_STTS`: no backend change. The parity fix is client-side because **legacy's gate is
also client-side** (`trns_0001_007.ts:1717-1733` returns before the AJAX; the action JSP at
`trns_0001_001_u001_act.jsp:93-105` would accept a failure by design). `applyBalanceRefresh`'s
failure branch is retained to mirror that JSP.

Migration safety: **improved** — both affected columns now carry values that can occur in legacy
data.

Result: **PASS** · DB executed: **NOT APPLICABLE**

## Security / Permission Verification

The engine-echo channel was designed so that validation and persistence never share a field. The
`engine_*` values are write-only: not used in any repository lookup, predicate, or authorization
decision. `scraping_engineEcho_persistsBankReturnedAccountsAndCurrency` asserts this directly by
verifying the ownership lookups received the composed account numbers. Tenant scoping
(`requireBizKey`), the source `W`-grant check, target ownership, and the ADMIN bypass are all
unchanged and still covered by passing tests. `toLegacyFeeDfrSbj` is a pure total function with no
I/O.

Result: **PASS**

## Performance / Concurrency Verification

No change to query count, transaction boundary, or the two-attempt `seq_no` allocator.
`firstNonBlank` and `toLegacyFeeDfrSbj` are in-memory string operations on the existing insert path.

Result: **PASS**

## Bug 1 re-verification — commit `f6031d9` still fixed

- Source: `grep -rn "findByBankCd\b" src/main/java` → **one hit, inside a Javadoc comment**. Both
  live call sites use `findFirstByBankCdOrderByNapasBankCdAsc`, matching legacy
  `IDO.ACCT_MSTR_R008`'s `ORDER BY NBI.NAPAS_BANK_CD ASC LIMIT 1`.
- Tests: `targets_user_filtersByGrant_excludesSourceRow` (stubs that method) and
  `targets_admin_excludesSourceRow` both **passed** in this run.
- Result: **PASS**

## Transfer History `99999999` filter re-verification

`TransferHistoryQueryServiceTest` **15/15 PASS**, including
`list_failedSentinelPassesStatusNeqOnly`. `STATUS_FAILED_SENTINEL = "99999999"` remains in
`TransferHistoryQueryService:94` — the one place legacy uses the value (a UI/query sentinel,
`trns_0003_001_view.jsp:158` → `trns_0003_001_r001_act.jsp:73-81`). Untouched by this sweep.

Result: **PASS**

## Existing Failures

None. The backend suite is fully green (2748/2748).

## New Failures

None.

## Not Tested

- Bean-Validation acceptance of the three new optional fields at the HTTP layer — no
  `@WebMvcTest` case exercises the intake body today (`OwnAccountTransferControllerTest` covers the
  other endpoints). The annotations are `@Size`-only and verified by inspection; the service-level
  behaviour is covered by the six new tests. A controller-level validation test is a reasonable
  follow-up.
- Live end-to-end transfer (licensed NXiSAS origin + bank credentials required).
- SIT / UAT — **SIT NOT VERIFIED**; deployment is manual and has not been performed.

## Final Result

**PASS**

## Recommendation

READY FOR MANUAL COMMIT — must ship together with the paired `wecms-web` change.

## Working-tree note

`docs/README.md` shows as modified in `git status` but was **not** touched by this change — it was
already modified in the working tree when this work started.
