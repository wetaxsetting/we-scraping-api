# Internal Transfer Intake — Two Validations Reject a Transfer Legacy Would Record

**Date:** 2026-08-18
**Type:** BUG FIX | PARITY FIX
**Current Work Classification:** BUG
**Status:** READY FOR MANUAL COMMIT
**Repository:** wecms-api
**Issue:** —
**Paired frontend record:** `wecms-web/docs/bugs/internal-transfer-engine-result-parsing-2026-08-18.md`

## Problem

`POST /api/v1/banking/own-account-transfer/scraping-result` carries two validations with no
legacy counterpart. Both are currently masked by a frontend defect (the engine result was
parsed at the wrong level, so `trns_stts` was always the hardcoded string `"99999999"` and
`actual_amt` always equalled `requested_amt`). Fixing the frontend to send the real
engine values un-masks both, and each can reject an intake **after the bank has already moved
the money** — destroying the only record of the transfer.

Minimum change for legacy parity is requested here; nothing else in the intake is touched.

## Legacy vs refactor — the exact difference

Legacy write path: `trns_0002_001_c001_act.jsp` (`valiateInput` + insert), input contract
`WECMS DEV/WECMS_ETC/xml/service/WSVC/WSVC.trns_0002_001_c001.xml`.

### Difference 1 — `trnsStts` `@NotBlank`

| | Legacy | Refactor (before) |
|---|---|---|
| Declared | `<item length="20" name="Transfer Status" id="TRNS_STTS" fullChar="N" />` | `@NotBlank @Size(max = 20) String trnsStts` |
| Required? | **No** — `valiateInput` validates only `OUT_BANK_CODE`, `OUT_ACCT_NO`, `TRANS_TYPE` (conditional), `IN_NAPAS_BANK_CODE` (conditional), `IN_ACCT_NO`, `CCY_CD`, `TRSC_AMT`, `FEE_DFR_SBJ` (conditional). `TRNS_STTS` is never checked. | **Yes** — blank ⇒ HTTP 400, nothing persisted |
| Stored | `NapasRemitTrnsHstr.copyFrom`: `if (data.hasField("TRNS_STTS")) setTrnsStts(...)` — a blank value is stored as-is, an absent one leaves the column NULL | column is `TRNS_STTS VARCHAR(20)`, nullable |
| Frontend initializer | `trns_0001_007.ts:2140` — `TRNS_STTS: ""` | — |

Legacy accepts and records a blank status. The refactor rejects the whole intake.

### Difference 2 — `WBE_OAT_AMOUNT_MISMATCH`

| | Legacy | Refactor (before) |
|---|---|---|
| Amount fields on the wire | **one** — `TRSC_AMT` | **two** — `requested_amt` + `actual_amt` |
| What is stored | on success `Result.transfer[0].remitAmt` (the bank's figure); on failure the entered amount (`trns_0001_007.ts:2158, 2169`) | `actual_amt` |
| Validation | `validateAmountOnlyPositive(TRSC_AMT)` — positive number, nothing else | `if (!Objects.equals(req.requestedAmt(), req.actualAmt())) rejectAndAudit(...)` (`OwnAccountTransferService.java:581-585`) |
| Concept of "requested vs actual" | **does not exist** | invented by the refactor |

Legacy cannot reject on a mismatch because it has nothing to compare against — it simply
records what the bank returned. The refactor throws `WBE_OAT_AMOUNT_MISMATCH`, so a transfer
the bank completed for a slightly different figure leaves **no history row at all**.

## Expected Behavior (legacy parity)

1. A blank / absent `trns_stts` is accepted and stored, as legacy does.
2. An `actual_amt` that differs from `requested_amt` is **logged, not rejected** — the row is
   always written, carrying the bank's figure. Preserving the record of a transfer that may
   already have moved money outranks the invariant.

## Scope

`ScrapingResultIntakeRequest.trnsStts` annotation; the amount-tolerance branch in
`OwnAccountTransferService.recordScrapingResult`; the two affected tests; the endpoint's
Javadoc + the API-catalog line that documents the old behaviour.

## Out of Scope

Every other validation in the intake keeps its current behaviour, deliberately — these are
refactor-added safety checks whose rejection cannot lose a *completed* transfer, or which
guard tenant isolation:

- CCY guard (`WBE_OWN_ACCOUNT_TRANSFER_INVALID_CCY`) — OQ-A VND-only scope.
- Transfer-type shape (`WBE_OAT_TARGET_NAPAS_REQUIRED` / `..._TARGET_BANK_REQUIRED` /
  `..._SAME_BANK_MISMATCH`).
- Source + target ownership / active / VND (`WBE_OAT_SOURCE_NOT_FOUND`,
  `WBE_OAT_SOURCE_FORBIDDEN`, `WBE_OAT_TARGET_NOT_FOUND`, `WBE_OAT_TARGET_NAPAS_UNKNOWN`,
  `WBE_OAT_TARGET_BANK_NAPAS_MISMATCH`) — tenant isolation, no legacy equivalent but
  security-bearing.
- Duplicate settlement-id guard (`WBE_OAT_DUPLICATE_BANK_TX_ID`) — this one **does** mirror
  legacy (`trns_0002_001_c001_act.jsp:94-99` → `ValidationHelper.validateTrnsStlmId`), and it
  stays inert from this screen because legacy maps no engine field to `TRS_ID` (the frontend
  keeps `bank_tx_id` empty).

No schema change. No new endpoint. No key-generation change (`wb_get_next_val` funnel
untouched).

## Root Cause

Two refactor-invented validations that legacy does not have, on a write path whose whole
purpose is to record an already-executed bank transaction.

## Implementation

1. `ScrapingResultIntakeRequest.java` — `@NotBlank @Size(max = 20) String trnsStts`
   → `@Size(max = 20) String trnsStts`, with the legacy contract documented on the field.
2. `OwnAccountTransferService.java` — the amount-tolerance branch keeps its `log.warn` and
   drops the `rejectAndAudit(...)` call; comment records the legacy citation and why.
3. Javadoc on `recordScrapingResult` + `OwnAccountTransferController.recordScrapingResult`
   error list updated (`WBE_OAT_AMOUNT_MISMATCH` no longer emitted).
4. Tests: `scraping_amountMismatch_throws` → `scraping_amountMismatch_persistsLegacyParity`;
   new `scraping_blankTrnsStts_persistsAsFailure`.

`messages.properties` keeps the `WBE_OAT_AMOUNT_MISMATCH` entry — unreferenced message keys
are harmless and removing it would be an unrelated change.

## Files Changed

- `src/main/java/com/kosign/wecms/domain/banking/ownaccounttransfer/dto/request/ScrapingResultIntakeRequest.java`
- `src/main/java/com/kosign/wecms/domain/banking/ownaccounttransfer/service/OwnAccountTransferService.java`
- `src/main/java/com/kosign/wecms/domain/banking/ownaccounttransfer/controller/OwnAccountTransferController.java` (Javadoc only)
- `src/test/java/com/kosign/wecms/domain/banking/ownaccounttransfer/service/OwnAccountTransferServiceTest.java`
- `docs/architecture/api-catalog/banking-submodules.md` (documented validate order)

## API Impact

`POST /api/v1/banking/own-account-transfer/scraping-result` becomes **more permissive**:
a blank `trns_stts` and a `requested_amt` ≠ `actual_amt` pair are now accepted. No response
shape change; `WBE_OAT_AMOUNT_MISMATCH` is no longer returned. No existing client breaks —
a payload that was valid before is still valid.

## UI Impact

None directly. Paired frontend change is in the frontend record.

## Database Impact

No schema change, no migration, no seed, no SQL script. Column values change only:
`NAPAS_REMIT_TRNS_HSTR.TRNS_STTS` receives legacy-shaped engine codes instead of the
invalid `'99999999'`, and may be blank (legacy-compatible, column is nullable).
`TRSC_AMT` may now differ from the composed amount, which is exactly what legacy stores.

Migration safety: **improved** — the refactor stops writing a value that cannot appear in
legacy data.

## Integration Impact

None — Spring is strictly post-scrape here; the engine runs in the browser.

## Verification

See `## Test Evidence`.

### Automated Tests

- Test: `OwnAccountTransferServiceTest` (26 tests, incl. 2 changed/new for this fix); full suite 2742 tests
- Result: PASS

### Regression Review

- Areas checked: the `f6031d9` NAPAS `BANK_CD` duplicate fix
  (`findFirstByBankCdOrderByNapasBankCdAsc`, covered by
  `targets_user_filtersByGrant_excludesSourceRow`); every other intake rejection path;
  `TransferHistoryQueryServiceTest` (the `99999999` failed-filter sentinel).
- Result: PASS — see the test-result record.

### Manual Verification

- Environment: LOCAL
- Steps: not performed — an end-to-end transfer needs a licensed NXiSAS origin and live bank
  credentials.
- Result: NOT RUN

## Result

The intake now records every post-scrape result the browser reports, as legacy does. A blank
`trns_stts` is stored instead of 400'ing; an amount that differs from the composed figure is logged
and stored instead of throwing `WBE_OAT_AMOUNT_MISMATCH`. No transfer the bank executed can be
dropped by these two checks any more.

Code + tests + docs complete and verified locally (backend suite 2742/2742, 0 failures; 2
changed/new OAT tests). **READY FOR MANUAL COMMIT** — nothing committed, pushed, merged, or
deployed; no SQL prepared or executed. Must ship together with the paired `wecms-web` change.

## Known Limitations / Follow-up

1. `requested_amt` is now advisory on this endpoint (kept on the wire, logged when it
   disagrees). Legacy has no such field at all; removing it would be a contract change beyond
   this fix.
2. `FEE_DFR_SBJ` vocabulary divergence (refactor `A`/`B` vs legacy `1`/`2`, `FeeDfrSbjType`)
   is a **separate, pre-existing** finding on the same insert. Not changed here — it is a
   wire-contract change on both sides and needs PO approval.

## Test Evidence

Test result:
[Internal Transfer Intake Legacy Parity — Test Result](../testing/results/internal-transfer-intake-legacy-parity-test-result-2026-08-18.md)

Final test result:    PASS
Regression coverage:  PASS
