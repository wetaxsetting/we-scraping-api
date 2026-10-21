# Own Account Transfer — account-password persistence (API) — Test Result

**Date:** 2026-08-26
**Type:** TEST RESULT
**Status:** PASS
**Repository:** wecms-api
**Related Record:** [../../bugs/own-account-transfer-account-password-2026-08-26.md](../../bugs/own-account-transfer-account-password-2026-08-26.md)

## Scope

Backend half of the `80002420` fix: `ScrapingResultIntakeRequest.acctPwd` / `trnsPwd`,
`AcctMstrRepository.applyTransferPasswords(...)`, and
`OwnAccountTransferService.persistTransferPasswords(...)` — the success-only write of the
bank-account password to `ACCT_MSTR.ACCT_PWD` / `TRNS_PWD`.

## Environment

LOCAL only — JVM unit tests (Mockito, no Spring context, no database).
SIT / UAT: **NOT RUN.**

## Code / Build Under Test

- Branch: working tree
- Commit: **NOT COMMITTED** — work stops at `READY FOR MANUAL COMMIT`
- Toolchain: Temurin JDK 21, Gradle 8.14.4
- API URL / environment: n/a (no context booted)

## Automated Tests

| Test / Command | Result | Evidence |
|---|---|---|
| `./gradlew compileJava` | PASS | clean |
| `./gradlew compileJava compileTestJava` | PASS | clean (one pre-existing unchecked-operations note in `WithdrawalAccountListServiceTest`) |
| `./gradlew test --tests "*OwnAccountTransferServiceTest*"` | **PASS** | `tests=38 skipped=0 failures=0 errors=0` |

## Regression Tests

Six new cases in `OwnAccountTransferServiceTest`. The defect is a *missing write*, so the
regression cover pins both the write and — more importantly — the conditions under which
it must **not** happen.

| Test case | Behaviour protected |
|---|---|
| `scraping_success_persistsEncryptedAccountPassword` | On success, the password is encrypted via `BankCertCryptoService` and written; **ciphertext, never plaintext** |
| `scraping_success_persistsBothPasswordsWhenBothSent` | Both columns write independently when both are supplied |
| `scraping_engineFailure_doesNotPersistPassword` | 🔴 On `80002421` nothing is encrypted or written — a rejected password must never reach the column live JEX reads silently |
| `scraping_passwordlessBank_doesNotTouchAcctMstr` | Vietcom / Vietin send nothing ⇒ no repository round-trip |
| `scraping_blankPassword_isTreatedAsAbsent` | `"   "` normalises to null so `COALESCE` leaves the stored ciphertext alone — blank is "not supplied", not "clear it" |
| `scraping_passwordWriteFailure_doesNotFailTheIntake` | A DB failure on the convenience write does not discard the record of an already-executed transfer |

**Did a regression test cover the original defect? YES** — `scraping_success_persistsEncryptedAccountPassword`
covers the write that was entirely absent, and the frontend record's
`bank-profiles.acctPassword.test.ts` covers the `acctNumPWD: ""` payload that produced
`80002420`.

## Database Verification

**Dependency only — NOT RUN.** The tests mock `AcctMstrRepository`; no SQL executed, no
database reachable in this session.

Owed on SIT, and one of these is the real proof of the design:

- `UPDATE … SET ACCT_PWD = COALESCE(…)` matches exactly one row for `(biz_key, bank_cd, acct_no)`.
- 🔴 **Live JEX `decrypt(ACCT_PWD)` returns the plaintext we wrote.** This is what
  validates the pgcrypto cipher choice; an AES-GCM value would break the legacy side
  silently after cutover.
- No DDL was required — `acct_pwd` / `trns_pwd` already exist (`V2__acct_mstr.sql:49-50`).

## Security / Permission Verification

- Reviewed: the write is scoped by `bizKey` from `TenantContext` plus the existing source-ownership re-validation already performed earlier in `recordScrapingResult`; the two new fields are never logged (the log line records only *whether* each password was present), never audited, and never echoed in a response.
- Result: **PASS** (source review). No new authorization surface — the endpoint's existing tenant + W-grant gate is unchanged.

## Not Tested

| Check | Why |
|---|---|
| Manual / SIT end-to-end | No running backend or NXiSAS engine in this session |
| Live-JEX `decrypt()` round-trip | No JEX DB access |
| Spring context / `ddl-auto: validate` boot | Unit tests use Mockito, no context |

## Existing Failures

None in this suite. The three shared `ScrapingResultIntakeRequest` test factories needed
two extra trailing nulls for the widened record — a mechanical compile fix, not a
behaviour change.

## New Failures

None.

## Final Result

**PASS**

## Recommendation

**READY FOR MANUAL COMMIT** — then SIT verification per the follow-up table in the
implementation record. Not `COMPLETE`: the end-to-end and the live-JEX decrypt round-trip
have not been performed.
