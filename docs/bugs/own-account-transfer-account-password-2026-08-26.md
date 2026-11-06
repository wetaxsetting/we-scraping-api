# Own Account Transfer — collect and persist the bank-account password (engine error 80002420)

**Date:** 2026-08-26
**Type:** BUG FIX
**Current Work Classification:** BUG
**Status:** READY FOR MANUAL COMMIT
**Repository:** wecms-api
**Paired frontend record:** [wecms-web/docs/bugs/own-account-transfer-account-password-2026-08-26.md](../../../wecms-web/docs/bugs/own-account-transfer-account-password-2026-08-26.md)
**Evidence (audit — not converted, not copied):** [audits/legacy-parity/own-account-transfer-account-password-source-2026-08-25.md](../audits/legacy-parity/own-account-transfer-account-password-source-2026-08-25.md)

```
Investigation Status:  COMPLETE   (in the audit above)
Approval Status:       APPROVED   — Sonny, 2026-08-26, "fix it to correct with Model A"
Implementation Status: COMPLETE
Test Status:           PASS
```

## Problem

An IBK VN → IBK VN own-account transfer on SIT fails at the bank with vendor code
**`80002420`** — *"An account password was not entered."* The refactor shipped
`acctNumPWD: ""` to the NXiSAS engine because the composer's Account Password input was
rendered `disabled` for IBK and no other source supplied the value.

## Root Cause

Verified in the audit. Legacy assigns `acctNumPWD` unconditionally for IBK
(`trns_0001_007.ts:1943`); it disables the *input* only because it sources the value from
the stored `ACCT_MSTR.ACCT_PWD` instead. The refactor copied the disabling and not the
replacement source, and never migrated the storage half at all — `ACCT_PWD` appeared
exactly once in `src/main`, as a column in `V2__acct_mstr.sql:49`.

## Chosen design — Model A (PO decision, 2026-08-26)

The user types the password on every transfer; the backend **writes** it to
`ACCT_MSTR.ACCT_PWD` / `TRNS_PWD` on a transfer the bank accepted, and **never reads it
back**. Rejected alternatives and the reasoning are in the audit §5 and the conversation
record:

- **Not** prefill-from-DB — that would ship the decrypted bank password to the browser on
  every composer open and make it readable via the eye toggle.
- **Not** a separate Account Management registration screen — dropped at the user's
  request; the inline field carries the whole flow.

Consequence: the refactor is **write-only** on this column pair. No decrypted bank
password ever leaves the server — strictly safer than legacy, which renders every row's
password into a `data-acct` DOM attribute on page load — while `ACCT_MSTR` stays populated
so live JEX keeps working on the same account.

## Implementation

| File | Change |
|---|---|
| [`dto/request/ScrapingResultIntakeRequest.java`](../../src/main/java/com/kosign/wecms/domain/banking/ownaccounttransfer/dto/request/ScrapingResultIntakeRequest.java) | +`acctPwd` / `trnsPwd`, both optional, `@Size(max = 100)` |
| [`repository/AcctMstrRepository.java`](../../src/main/java/com/kosign/wecms/domain/banking/repository/AcctMstrRepository.java) | +`applyTransferPasswords(...)` — native `UPDATE` with `COALESCE`, mirroring `IDO.ACCT_MSTR_U001:55-56` |
| [`service/OwnAccountTransferService.java`](../../src/main/java/com/kosign/wecms/domain/banking/ownaccounttransfer/service/OwnAccountTransferService.java) | +`persistTransferPasswords(...)` / `encryptOrNull(...)`, called from `recordScrapingResult` inside the `engineSuccess` branch |

Three decisions worth keeping:

1. **Legacy pgcrypto cipher, via the existing `BankCertCryptoService`.** Its output is
   byte-identical to the JEX DB function `public.encrypt(txt)` (AES-256-CBC, zero IV,
   key = `sha256('wabooksvn')`, base64). An AES-GCM value here would be undecryptable on
   the legacy side and would silently break IBK / KEB Hana transfers there after cutover.
2. **The columns are deliberately NOT mapped on the `AcctMstr` entity.**
   `AccountRegistrationService:168` calls `save(existing)` on loaded rows, so a mapped
   field would let an unrelated write path round-trip — or null — the ciphertext. The
   native `UPDATE` is the only writer in the refactor.
3. **Success-only.** Because live JEX consumes `ACCT_PWD` silently
   (`trns_0001_007.ts:675-682` → `:1943`), storing a password the bank rejected would send
   that wrong value from the legacy screen with no chance to correct it — walking the
   bank's lockout ladder `80002421` → `80002422` → `80002423`. The write is also
   **best-effort**: the transfer has already executed at the bank when it runs, so a
   failure is logged and swallowed rather than discarding the record of real money moving.

## Database Impact

**NO DDL, no migration, no new table.** `acct_pwd` / `trns_pwd` already exist —
`db/migration/V2__acct_mstr.sql:49-50`, `varchar(100)`. `ACCT_MSTR` is table #4 in
[`legacy-table-inventory.md`](../references/database/legacy-table-inventory.md).

New write path: `UPDATE ACCT_MSTR SET ACCT_PWD = COALESCE(:acctPwdEnc, ACCT_PWD), …`.
`COALESCE` is deliberate and mirrors legacy — a null parameter leaves the stored value
alone, so a bank needing only the account password cannot blank an existing `TRNS_PWD`.

## API Impact

`POST /api/v1/banking/own-account-transfer/scraping-result` gains two **optional** body
fields, `acct_pwd` / `trns_pwd`. Backward compatible: an older client omitting them
behaves exactly as before. No new endpoint, no response change.

## Verification

### Automated Tests

- Test: `OwnAccountTransferServiceTest` — 6 new cases (`scraping_success_persistsEncryptedAccountPassword`, `…persistsBothPasswordsWhenBothSent`, `…engineFailure_doesNotPersistPassword`, `…passwordlessBank_doesNotTouchAcctMstr`, `…blankPassword_isTreatedAsAbsent`, `…passwordWriteFailure_doesNotFailTheIntake`)
- Command: `./gradlew test --tests "*OwnAccountTransferServiceTest*"`
- Result: **PASS** — 38 tests, 0 failures, 0 errors, 0 skipped

### Regression Review

- Areas checked: `./gradlew compileJava compileTestJava` clean; the 32 pre-existing OAT service tests still pass unchanged; three shared `ScrapingResultIntakeRequest` test factories extended with the two new trailing nulls.
- Result: no behaviour change on any path that sends no password.

### Manual Verification

- Environment: none
- Result: **NOT RUN** — no running backend or NXiSAS engine in this session. The end-to-end IBK transfer (G1/G2 below) is owed on SIT.

Full evidence: [Test Evidence](#test-evidence).

## Test Evidence

Test result: [own-account-transfer-account-password-api-test-result-2026-08-26.md](../testing/results/own-account-transfer-account-password-api-test-result-2026-08-26.md)

```
Final test result:    PASS
Regression coverage:  PASS
```

## Result

Backend collects and persists the password. The `80002420` cause is removed on the API
side; the paired frontend record carries the field that supplies the value.

## Known Limitations / Follow-up

| # | Item | Owner |
|---|---|---|
| 1 | **SIT end-to-end not run.** Register + transfer IBK `076400794420001` → `…0002` and confirm success, then confirm **live JEX can `decrypt()` the value we wrote** — that check is what proves the cipher choice. | Mr. Youvita |
| 2 | `acct_pwd` is NULL for all 54 accounts on `wecmsdevdb`; every user types once, then the column fills as transfers succeed. No backfill is possible — we do not hold the passwords. | — |
| 3 | The same query on **JEX DEV/UAT/PROD** is still unrun (audit B-1). It decides whether live tenants keep working immediately after cutover or must re-enter once. | Sonny / DBA |
| 4 | KEB Hana (`11704080`) — the only bank needing `trns_pwd` — is not in the refactor allow-list, so the transfer-password path is built but inert. | — |
