# NXiSAS Decrypt Wire Contract + Unconditional Decrypt (OAT)

**Date:** 2026-08-18
**Type:** BUG FIX | PARITY FIX
**Current Work Classification:** BUG
**Status:** READY FOR MANUAL COMMIT
**Repository:** wecms-api
**Issue:** —
**Audit evidence:** `wecms-web/docs/audits/update-balance-balance-required-audit-2026-08-18.md`
**Paired frontend record:** `wecms-web/docs/bugs/nxisas-decrypt-wire-contract-fix-2026-08-18.md`

## Problem

`POST /api/v1/banking/own-account-transfer/nxisas/decrypt` never decrypts anything, for two
independent reasons:

1. **The request field never binds.** `NxisasDecryptRequest`'s record components are UPPER_SNAKE
   (`NXISAS_DATA` / `NXISAS_UID` / `NXISAS_ACTION`) with **no `@JsonProperty`**, so the global
   `PropertyNamingStrategies.SNAKE_CASE` (`JacksonConfig:25`) exposes them on the wire as
   `nxisas_data` / … — while the caller sends the UPPER form. Spring Boot ignores unknown
   properties by default, so all three bind to `null` and the endpoint answers HTTP 200 with
   `{"nxisas_data": null}`. The DTO's own Javadoc claims *"The endpoint accepts the UPPER_SNAKE form
   verbatim"* — nothing implements that.
2. **Decrypt is flag-gated off.** `wecms.oat.seed-decrypt.enabled` defaults `false`
   (`OatSeedDecryptProperties:27-28`) and is set in no profile or env file, so
   `OatNxisasService.decrypt` returns its input unchanged.

Consequence: the browser proceeds with SEED-CBC ciphertext in `Output.Result`, so Own Account
Transfer can read neither `Result.balance[0]` (Update Balance) nor `Result.transfer[0]`
(a successful transfer's bank-echoed values).

The same casing defect disables two sibling endpoints — see `## Scope` items 3–4.

## Current Behavior

- `decrypt` / `encrypt`: input binds `null` → returns `null` → 200 `{"nxisas_data": null}`.
- `logging`: `NxisasLoggingRequest(String JSON_STR)` binds `null` → `recordLogging` hits its
  `req.JSON_STR() == null` guard and returns → **no `NXSIAS_MODULE_TRAN_LOG_HIS` row is ever
  written** from this screen.
- `SeedCbcDecryptService.walkAndDecrypt` (`:99-140`) is **already correct** — it recurses to any
  depth and handles the refactor's `orgObj → result[] → Output` shape. Only the plumbing is broken.

## Expected Behavior

Legacy `nxisas_v2.js:1684-1704` posts `NXISAS_DATA` / `NXISAS_UID` / `NXISAS_ACTION` and reads
`rData.NXISAS_DATA` back — UPPER because JEX has **no naming strategy**; those are literal WSVC
field ids. Legacy also decrypts **unconditionally by default**: the guard at `:2353` fires when
`nonDecrypt` is `undefined`, and the decrypt runs *before* the success/fail dispatch at `:2361` /
`:2389`.

So: the wire names must match whatever the caller actually sends, and the decrypt must always run.

## Scope

1. **`NxisasDecryptRequest` / `NxisasDecryptResponse`** — align the wire contract with the caller
   (`decryptNxisasResult` in `wecms-web`, which this MR changes to lowercase in lockstep).
2. **`OatNxisasService.decrypt` / `.encrypt`** — run the cipher unconditionally, matching legacy and
   the e-Invoice precedent (`EInvoiceScrapeDecryptService`, unconditional since 2026-07-14).
3. **`NxisasLoggingRequest.JSON_STR`** — bind the key the **vendor bundle** actually posts.
4. **`NxisasCertAcctListResponse`** — same UPPER_SNAKE component pattern; review and align.
5. Contract tests pinning the wire key names on every DTO touched.

## Out of Scope

- `SeedCbcDecryptService` itself (already correct).
- e-Invoice / GDT decrypt paths (already correct).
- Any schema, migration, or seed change.

## Initial Investigation

Complete — see the audit F1–F10. Additional verification performed for this implementation is
recorded under `## Implementation` (who controls each caller's key casing, and whether the
flag/properties class has other consumers).

## Root Cause

Legacy's literal WSVC field ids were copied into a framework that renames every property, with no
`@JsonProperty` to pin them — and the decrypt was additionally left behind a default-off flag built
on the premise that "VND payloads mostly arrive plaintext", which the SIT capture disproves for
both Vietcom and Shinhan.

## Implementation

Verification performed first (it decided the *shape* of the fix, not just the content):

- **`wecms-web/public/nxisas/js/nxisas_v2.js` exists** — the refactor ships its own copy of the vendor
  bundle, and that copy calls `_v2nxisas.nxisas_decrypt(res)` at `:2329` and `:2357`.
- **The JEX adapter short-circuits that call.** `wecms-web/lib/nxisas/jexjs-adapter.ts:428` intercepts
  `nxisas_decrypt` / `nxisas_encrypt` and returns a synchronous client-side pass-through, because the
  bundle invokes them with `async: false` and reads the return value on the same tick — a network
  round-trip is impossible there. So the bundle decrypt **never reaches Spring**, and this endpoint's
  only caller is the refactor's own `decryptNxisasResult`. That is what makes a lowercase rename safe.
- **The logging and cert-list endpoints are the opposite case** — those *are* reached by the bundle
  (the adapter posts the body verbatim), so their keys are vendor-controlled: `JSON_STR`
  (`nxisas_v2.js:2310`) and `SEQ_NO` (`:1630`). They must be *pinned*, not renamed.

Implemented:

1. **`NxisasDecryptRequest` / `NxisasDecryptResponse`** → camelCase components
   (`nxisasData` / `nxisasUid` / `nxisasAction`), so the global `SNAKE_CASE` strategy produces
   `nxisas_data` / `nxisas_uid` / `nxisas_action`. The Javadoc claim that the endpoint "accepts the
   UPPER_SNAKE form verbatim" — which nothing implemented — was deleted and replaced with the actual
   contract plus the reason it is lowercase here and UPPER on the sibling endpoints.
2. **`OatNxisasService.decrypt` / `.encrypt`** → unconditional. Both flag guards removed, along with
   the `seedProps` field and its import.
3. **`OatSeedDecryptProperties` and `OwnAccountTransferConfig` deleted.** With the guards gone the flag
   had no consumer, and a live-looking `wecms.oat.seed-decrypt.enabled` property that silently does
   nothing is worse than no property — someone would set it expecting an effect. Its references were
   removed from `application.yml`, all four `.env.*.example` files, the `SeedCbcDecryptService` Javadoc,
   and the controller Javadoc. `OwnAccountTransferConfig` existed only to enable that one properties
   class, so it went with it.
4. **`NxisasLoggingRequest.JSON_STR`** → `@JsonProperty("JSON_STR")`, with a note not to normalise it
   to camelCase (that would re-break the bundle post).
5. **`NxisasCertAcctListResponse`** → `@JsonProperty` on all five components.
6. **`NxisasCertAcctListRequest`** → `@JsonProperty("SEQ_NO")`. **Added to scope during
   implementation**: the same defect class, and worse — the component was `seqNo` *with* `@NotBlank`, so
   every bundle call bound null and was rejected outright as `WBE_VALIDATION` rather than failing
   silently.

`SeedCbcDecryptService` itself was not touched (its walker was already correct) beyond the Javadoc
paragraph that described the now-deleted flag.

## Files Changed

| File | Change |
|---|---|
| `dto/request/NxisasDecryptRequest.java` | components → camelCase; false Javadoc claim removed |
| `dto/response/NxisasDecryptResponse.java` | component → camelCase |
| `dto/request/NxisasLoggingRequest.java` | `@JsonProperty("JSON_STR")` added |
| `dto/request/NxisasCertAcctListRequest.java` | `@JsonProperty("SEQ_NO")` added |
| `dto/response/NxisasCertAcctListResponse.java` | `@JsonProperty` added on all 5 components |
| `service/OatNxisasService.java` | decrypt/encrypt unconditional; flag field + import removed |
| `config/OatSeedDecryptProperties.java` | **deleted** |
| `config/OwnAccountTransferConfig.java` | **deleted** |
| `controller/OwnAccountTransferController.java` | Javadoc on the two endpoints rewritten |
| `common/nxisas/SeedCbcDecryptService.java` | Javadoc — both callers now unconditional |
| `resources/application.yml` | flag line removed from the property inventory |
| `.env.{local,prod,sit,uat}.example` | Own Account Transfer flag block removed (4 files) |
| `test/.../dto/NxisasWireContractTest.java` | **new** — 5 wire-key contract tests |
| `test/.../service/OatNxisasServiceSeedDecryptTest.java` | **new** — 3 unconditional-decrypt tests |
| `test/.../service/OatNxisasServiceSeedFlagTest.java` | **deleted** — asserted the defect |
| `test/.../service/OatNxisasServiceExtTranLogTest.java` | constructor arg + import for the deleted properties class |

## API Impact

**The `/nxisas/decrypt` and `/nxisas/encrypt` wire contract changes** — request and response key names
move from `NXISAS_DATA` / `NXISAS_UID` / `NXISAS_ACTION` to `nxisas_data` / `nxisas_uid` /
`nxisas_action`. Both are internal endpoints with a single caller (the `decryptNxisasResult` function in
`wecms-web`), which changes in the same MR. No other endpoint path, method, envelope, error code,
authorization, or tenant scope changes.

`/nxisas/logging` and `/nxisas/cert-acct-list` keep their UPPER_SNAKE keys — those are now *enforced*
rather than merely asserted in prose, so from the caller perspective they start working instead of
changing shape.

Behavioural change: both decrypt endpoints now transform their payload instead of echoing it.

## UI Impact

None directly. Downstream: Update Balance can reach `Result.balance[0]`, and a successful transfer can
reach `Result.transfer[0]` — see the paired frontend record.

## Database Impact

**NONE — no schema change. No migration. No seed. No SQL artifact.** Values written change only in
content: `ACCT_MSTR.BAL` / `REAL_AMT` will start receiving real refreshed balances,
`NAPAS_REMIT_TRNS_HSTR` will start receiving bank-echoed account/currency/fee/memo values instead of
composed fallbacks, and `NXSIAS_MODULE_TRAN_LOG_HIS` will start receiving the per-job audit rows it was
always meant to. All three write into already-supported columns.

## Integration Impact

The Coocon/NXiSAS engine contract is unchanged. The internal `/nxisas/*` wire contract changes (key
names); both ends ship in the same MR.

## Verification

Summary — full detail in the test-result record linked below.

### Automated Tests

- Test: `./gradlew test --tests "com.kosign.wecms.domain.banking.ownaccounttransfer.*"`
- Result: **PASS** — 52 tests, 0 failures
- Test: `./gradlew test` (full suite)
- Result: **PASS** — 2754 tests, 0 failures/errors, 327 classes

### Regression Review

- Areas checked: every consumer of the two deleted config classes (service, the
  `@EnableConfigurationProperties` registration, 2 tests, `application.yml`, 4 env examples, 2 Javadoc
  sites); both other `/nxisas/*` DTO pairs; the second `SeedCbcDecryptService` consumer (e-Invoice),
  which was already unconditional and is unaffected.
- Result: full backend suite green, so no other module depended on the flag or on the old key names.

### Manual Verification

- Environment: LOCAL
- Steps: compile + targeted + full test runs; red-check of the contract tests by reintroducing the
  defects (3 of 5 failed, then restored to green)
- Result: **PASS** for what is testable here. Live SIT verification is **NOT RUN** — it needs a deploy
  plus a licensed NXiSAS engine origin.

## Test Evidence

Test result:
[NXiSAS Decrypt Wire Contract + Unconditional Decrypt — Test Result](../testing/results/nxisas-decrypt-wire-contract-test-result-2026-08-18.md)

Final test result:    PASS
Regression coverage:  PASS (5 contract tests, red-checked)

## Result

All five approved items implemented on the backend side, plus the `SEQ_NO` binding found during the
work. The root cause — legacy literal WSVC field ids copied into a framework that renames every
property, with no `@JsonProperty` to pin them, failing silently because Spring Boot ignores unknown
properties — is fixed at every one of the four affected DTO pairs, and is now covered by tests that
fail if it recurs.

The default-off decrypt flag is gone, so the cipher runs on the path legacy always ran it on.

## Known Limitations / Follow-up

1. **Not verified on SIT.** The original symptom was observed on `wecms.kosign.dev`; confirming the fix
   there needs a manual deploy. Until then the fix is verified by contract test only.
2. **`getCertInfo(seq)` remains structurally broken.** The `SEQ_NO` binding now works, but the bundle
   requests that call with `async: false` and reads the return value on the same tick, which the adapter
   cannot honour — so it still returns null. Documented on `NxisasCertAcctListRequest`. It is a fallback
   path (the composer pre-populates cert fields), so nothing regresses; a real fix would need a bundle
   edit and is a separate decision.
3. **`CERT_INFO_DETL_CON` still ships a decrypted plaintext password.** Pre-existing, unchanged by this
   MR, and now *reachable* where before it was garbled — worth a separate look at whether the bundle
   genuinely needs the plaintext on the wire.
4. **No live-engine test coverage exists anywhere for this path**, on either side. Every test here is a
   contract or unit test; the first genuine end-to-end proof will be the SIT run.
5. **The user-visible SIT symptom is raised client-side, not by this API.** `WBE6021`
   ("Balance is required.") exists only in the frontend locale files; it is absent from
   `messages*.properties` and from every Java source, so no backend validation rejected the balance
   refresh — the frontend's legacy-parity gate refused to POST because the balance was unreachable in
   the still-encrypted payload. That gate is correct and unchanged. This backend fix removes the cause
   (the payload is now decrypted); the symptom disappears only once both sides are deployed together.
