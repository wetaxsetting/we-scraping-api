# NXiSAS Decrypt Wire Contract + Unconditional Decrypt — Test Result

**Date:** 2026-08-18
**Type:** TEST RESULT
**Status:** PASS
**Repository:** wecms-api
**Related Record:** [nxisas-decrypt-wire-contract-fix-2026-08-18.md](../../bugs/nxisas-decrypt-wire-contract-fix-2026-08-18.md)
**Issue:** —

## Scope

Backend half of the `/nxisas/*` wire-contract fix:

1. `NxisasDecryptRequest` / `NxisasDecryptResponse` renamed to camelCase components
   (wire `nxisas_data` / `nxisas_uid` / `nxisas_action`).
2. `OatNxisasService.decrypt` / `.encrypt` made unconditional; the
   `wecms.oat.seed-decrypt.enabled` flag and its two config classes deleted.
3. `NxisasLoggingRequest.JSON_STR` pinned with `@JsonProperty`.
4. `NxisasCertAcctListResponse` (5 fields) and `NxisasCertAcctListRequest.SEQ_NO` pinned
   with `@JsonProperty`.

## Environment

LOCAL only. No SIT/UAT/PROD execution — the change is not deployed.

## Code / Build Under Test

- Branch: current working tree
- Commit: **NOT COMMITTED** (work stops at `READY FOR MANUAL COMMIT`)
- Build: `./gradlew test` (Gradle, Temurin JDK 21)
- API URL/environment: none — unit/contract tests only, no running server

## Automated Tests

| Test / Command | Result | Evidence |
|---|---|---|
| `./gradlew compileJava` | PASS | no output (clean) |
| `./gradlew test --tests "com.kosign.wecms.domain.banking.ownaccounttransfer.*"` | PASS | 52 tests, 0 failures — `OwnAccountTransferControllerTest` 10 · `NxisasWireContractTest` 5 · `OatNxisasServiceExtTranLogTest` 2 · `OatNxisasServiceSeedDecryptTest` 3 · `OwnAccountTransferServiceTest` 32 |
| `./gradlew test` (full suite) | PASS | **2754 tests, 0 failures/errors across 327 classes** (tallied from `build/test-results/test/*.xml`) |

### New tests added

| Class | Tests | Pins |
|---|---|---|
| `dto/NxisasWireContractTest` | 5 | JSON key names on all four DTOs, using a mapper built from the real `JacksonConfig.snakeCaseCustomizer()` rather than a hand-rolled `SNAKE_CASE` mapper |
| `service/OatNxisasServiceSeedDecryptTest` | 3 | decrypt/encrypt run with no flag; decrypt applies on `ErrorCode 00000000`; encrypt→decrypt round-trip |

### Tests changed / removed

| Class | Change |
|---|---|
| `OatNxisasServiceSeedFlagTest` | **Deleted.** Its `flagOff_returnsCiphertextUnchanged` case asserted the exact defect being fixed — that a BALANCE payload comes back as untouched ciphertext. Replaced by `OatNxisasServiceSeedDecryptTest`. |
| `OatNxisasServiceExtTranLogTest` | Constructor arg + import for the deleted `OatSeedDecryptProperties` removed. No assertion changed. |

## Regression Tests

**Did a regression test cover the original defect? YES.**

- Test file: `src/test/java/com/kosign/wecms/domain/banking/ownaccounttransfer/dto/NxisasWireContractTest.java`
- Test cases: `decryptRequest_bindsLowercaseSnakeCase`, `decryptResponse_emitsLowercaseSnakeCase`,
  `loggingRequest_bindsUpperSnakeFromBundle`, `certAcctListRequest_bindsUpperSnakeFromBundle`,
  `certAcctListResponse_emitsUpperSnakeForBundle`
- Behaviour protected: the wire key names each `/nxisas/*` endpoint binds and emits.

**Red-check performed** (the defects were reintroduced and the tests were confirmed to fail):

The first attempt reintroduced the defect by renaming record components back to UPPER_SNAKE, which
cascaded into `cannot find symbol` compile errors in callers rather than a clean assertion failure —
that is not a valid red-check, so the tree was restored and the check redone surgically, mutating
only the **wire names** while leaving accessor names intact:

| Reintroduced defect | Result |
|---|---|
| `@JsonProperty("NXISAS_DATA")` on the decrypt request components | `decrypt request binds the lowercase keys the frontend sends` **FAILED** |
| `@JsonProperty("NXISAS_DATA")` on the decrypt response | `decrypt response emits nxisas_data, the key the frontend reads` **FAILED** |
| `@JsonProperty("JSON_STR")` removed from the logging request | `logging request binds JSON_STR verbatim` **FAILED** |

3 of 5 failed; the 2 cert-list cases correctly still passed (those DTOs were not mutated). The fix was
then restored and the package re-run: `BUILD SUCCESSFUL`.

## API Verification

- `POST /nxisas/decrypt` and `/nxisas/encrypt` now bind `nxisas_data` / `nxisas_uid` /
  `nxisas_action` and emit `nxisas_data` — verified by contract test, not by a live call.
- `POST /nxisas/logging` binds `JSON_STR`; `POST /nxisas/cert-acct-list` binds `SEQ_NO` and emits the
  five `CERT_INFO_*` / `NXISAS_GB_SEQ_NO` keys — verified by contract test.
- Envelope, error codes, `@menuAuthz` gating, tenant scope: **unchanged** by this MR.
- **Live HTTP verification: NOT RUN.** No running backend + licensed NXiSAS engine was available.

## Database Verification

NOT APPLICABLE — no schema, migration, seed, or SQL artifact. Values written to
`ACCT_MSTR.BAL` / `REAL_AMT`, `NAPAS_REMIT_TRNS_HSTR`, and `NXSIAS_MODULE_TRAN_LOG_HIS` change in
content only (real bank values instead of composed fallbacks / no row at all), into
already-supported columns.

## Security / Permission Verification

Reviewed, narrow scope:

- `requireBizKey()` still guards both `decrypt` and `encrypt` — tenant binding unchanged.
- `NxisasCertAcctListResponse.CERT_INFO_DETL_CON` remains a decrypted plaintext password on the wire.
  This MR did **not** change what is exposed or who may reach it; it only fixes the key names. The
  pre-existing exposure is documented on the DTO and gated by tenant + W-grant.
- No secret, key, or credential added to code, config, docs, or logs. The new frontend diagnostics log
  the decrypted **engine payload** (balance / transfer figures), matching what legacy already logs to
  the browser console — no cipher key, UID, or action value is logged.

## Existing Failures

None. The full backend suite is green (2754/2754).

## New Failures

None.

## Not Tested

- **Live SIT verification** — NOT RUN. Requires a deploy plus a licensed NXiSAS engine origin; both are
  manual steps outside this change.
- **`getCertInfo(seq)` end-to-end** — the `SEQ_NO` binding is fixed, but the bundle requests that call
  with `async: false` and reads the return value on the same tick, which the adapter cannot honour.
  Documented as a residual limitation on `NxisasCertAcctListRequest`; not exercised here.

## Final Result

PASS

## Recommendation

READY FOR MANUAL COMMIT — paired with the frontend change, which must ship in the same MR (the wire
contract changes on both sides simultaneously).
