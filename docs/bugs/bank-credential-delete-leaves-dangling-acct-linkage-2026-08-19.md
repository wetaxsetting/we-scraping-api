# Bank Credential Delete Leaves a Dangling `ACCT_MSTR.BANK_CERT_SEQ`

**Date:** 2026-08-19
**Type:** BUG FIX
**Current Work Classification:** BUG
**Investigation Status:** COMPLETE
**Approval Status:** APPROVED — Sonny, in-session 2026-08-19. The coexistence impact below was raised first and the decision was reaffirmed ("It's the custom case in refactor WeCMS. Please fix it to correct." + explicit selection of the backend fix)
**Implementation Status:** COMPLETE
**Test Status:** PASS
**Status:** READY FOR MANUAL COMMIT
**Repository:** wecms-api
**Issue:** —
**Paired frontend record:** `wecms-web/docs/bugs/own-account-transfer-credential-gate-per-account-parity-2026-08-19.md` (per-account gate, already implemented)
**Audit evidence:** `wecms-web/docs/audits/own-account-transfer-shinhan-transfer-button-disabled-audit-2026-08-19.md`

## Problem

Deleting a bank credential removes the `BANK_CERT_INFO` row but leaves every linked account's
`ACCT_MSTR.BANK_CERT_SEQ` pointing at the deleted `SEQ_NO`. The pointer dangles for good: nothing in
the codebase ever clears it (`grep -rn "setBankCertSeq(null)"` → no hits).

Reported symptom (SIT/LOCAL, tenant `VN_VNM0004_000001`, Shinhan VN `11704088`): all four Shinhan
accounts show a disabled **Transfer** button on Own Account Transfer with *"Bank login credentials
were deleted. Please register Login Credentials again."* — while `bank_cert_info` **does** hold a
Shinhan row. The accounts carry `BANK_CERT_SEQ = 1`; the tenant's `seq_no` values run 2…18 with
Shinhan at **18**. Seq 1 was deleted, and re-registering the credential produced a new seq without
re-linking the accounts.

## Current Behavior

**There are TWO delete paths, and the user-facing one is the second.** The initial write-up of this
record named only `BankCredentialService`; a call-site sweep during implementation
(`grep -rn "bankRepo.delete|bankCertInfoRepo.delete"`) found both, and both had the same defect:

| Endpoint | Service | Consumer |
|---|---|---|
| `DELETE /api/v1/login-credentials/{type}/{seqNo}` | `LoginCredentialsService.delete` (`:238-240`) | **Settings → Login Credentials — the reported flow** |
| `DELETE /api/v1/banking/bank-credentials/{seqNo}` | `BankCredentialService.delete` (`:77-79`) | the banking-tree CRUD surface |

```java
// BankCredentialService:77-79
public void delete(BigDecimal seqNo) {
    repository.delete(loadOrThrow(seqNo));      // BANK_CERT_INFO only
}

// LoginCredentialsService:238-240 (the path the Settings UI calls)
BankCertInfo row = bankRepo.findByBizKeyAndSeqNo(bizKey, seqNo)
        .orElseThrow(() -> new BusinessException("WBE2090"));
bankRepo.delete(row);                          // BANK_CERT_INFO only
```

`ACCT_MSTR` is never touched, so:

- `OwnAccountTransferService:209` resolves `certBySeq.get(BigDecimal.valueOf(bankCertSeq))` → miss →
  all five cert fields project as null → the frontend gate disables the row.
- `SEQ_NO` is **tenant-global** (this tenant has one sequence shared by 17 different banks), so a
  dangling pointer is also a latent **wrong-bank** hazard: neither legacy's IDO
  `BANK_CERT_INFO_R006` nor `loadBankCerts` filters `BANK_CD`, so if a number a stale pointer holds
  is ever occupied by another bank's credential, that bank's login ID + password would be projected
  onto these accounts and the engine would attempt a login with the wrong bank's credentials — a
  guaranteed authentication failure, feeding the bank's 3-strike lockout.

## Expected Behavior

1. Deleting a credential clears the linkage it owned: every `ACCT_MSTR` row in the same tenant whose
   `BANK_CERT_SEQ` equals the deleted `SEQ_NO` is set to `NULL` (accounts themselves are **kept** —
   they live in Bank Account Management). `NULL` then honestly means "not registered against a
   credential", and re-registering + picking accounts re-links them.
2. Cert resolution never crosses banks: a credential whose `BANK_CD` differs from the account's is
   treated as no-cert.

## Legacy JEX comparison — and why this diverges deliberately

| | Legacy | Refactor (after this change) |
|---|---|---|
| Delete credential | `comm_0218_001_d001_act.jsp` → `BankCertInfoDAO.deleteByPk(biz_key, seq_no)`. `ACCT_MSTR` untouched → dangling pointer | Also clears `ACCT_MSTR.BANK_CERT_SEQ` for that seq |
| Cert resolution | `nxisas_acctList_r003` → IDO `BANK_CERT_INFO_R006`, keyed `BIZ_KEY + SEQ_NO`, **no `BANK_CD`** | Same key **plus** a `BANK_CD` equality guard |
| Missing/stale seq at transfer time | `nxisas_v2.js:1626-1646` `getCertInfo` has no guard → `loginID`/`loginPWD` `undefined` are sent to the engine, i.e. a bank login with empty credentials | Pre-flight blocks before the engine is invoked (already shipped) |

**This is a deliberate divergence from legacy, approved for this change.** Legacy leaves the pointer
dangling and then attempts an empty-credential login; both are unsafe. The refactor already guards
the login, and this change makes the stored data match what the guard reports.

**Coexistence impact — stated plainly.** Per `project_uat_prod_live_jex_db_target` the refactor runs
against the **live JEX DB in every environment**, so `ACCT_MSTR.BANK_CERT_SEQ` becoming `NULL` is
visible to the live JEX screens too. Effect there: legacy's own picker/transfer screens will see the
account as "no credential registered" — which is the truth after a delete, and is strictly better
than legacy's current behaviour of sending empty credentials to the bank. No column is added,
dropped or re-typed; no legacy row is deleted.

## Scope

- `AcctMstrRepository` — one bulk-update query (`clearBankCertSeq`).
- `LoginCredentialsService.delete(...)` — clear the linkage inside the same transaction. **This is
  the path the Settings UI calls**; it was added to scope during implementation, after the call-site
  sweep in `## Current Behavior` showed the record's original scope would have left the reported
  flow unfixed.
- `BankCredentialService.delete(...)` — same clear, on the banking-tree endpoint.
- `OwnAccountTransferService` — `BANK_CD` guard on the resolved cert.
- **Re-link regression coverage** — `AccountRegistrationServiceCredentialRelinkTest`. Added to scope
  after implementation, when a review of the recovery path (raised by Sonny: *"how to link
  BANK_CERT_SEQ with table ACCT_MSTR?"*) found the re-link implemented on both sides but **untested**.
  Clearing the pointer makes that write the only route back to a working Transfer button, so it must
  be guarded. No production code changed for this — `AccountRegistrationService` is untouched.

## Out of Scope

- **Back-filling the tenant's already-dangling rows** (the reported Shinhan accounts). This change is
  forward-looking; existing rows are cured by re-running Settings → Login Credentials → the bank →
  Select Bank Accounts, which rewrites `BANK_CERT_SEQ`. A one-off DBA script is possible under
  `docs/sql/2026/` but is **not** written here — it is a data change against the live JEX DB and
  needs its own decision.
- The frontend gate — already fixed in `wecms-web` (per-account credential gate, shipped separately).
- `loadBankCerts`'s tenant-wide batch load and its `BigDecimal` scale comparison — left as-is per
  `feedback_preserve_existing_code`; see `## Known Limitations / Follow-up` item 3.
- Card Transactions' own `setBankCertSeq` writers (`CardTransactionsService`,
  `CardTransactionSyncService`) — they resolve their own seq per sync and are unaffected.

## Root Cause

**Verified.** Both delete paths were implemented as a single-row entity delete, mirroring legacy's
`deleteByPk`, with no consideration of the inbound foreign reference from `ACCT_MSTR`. There is no
DB-level FK (the legacy schema carries none), so nothing forced the question — no compile error, no
constraint violation, no failing test.

The `LoginCredentialsService` comment block at the delete even reasons explicitly about **not**
cascade-deleting the account *rows* (correct, and deliberately reverted from an earlier over-eager
cascade) — but it never distinguished the *rows* from the *pointer*. Preserving the rows is right;
preserving a pointer to a row that no longer exists is not, and the two were conflated.

The defect stayed invisible because a delete followed by a **re-register** is what surfaces it: the
re-register mints a **new** `SEQ_NO` (per-tenant `MAX+1` via `wb_get_next_val`) and never re-links
the old accounts, so the credential is visibly present in the table while every account still points
at the deleted seq. A delete with no re-register just looks like "no credential", which is the truth.

## Implementation

### 1. One bulk update, in `AcctMstrRepository`

```java
@Modifying(clearAutomatically = true, flushAutomatically = true)
@Query("""
       update AcctMstr a
          set a.bankCertSeq = null,
              a.modId       = :userId,
              a.modDtm      = :nowDtm
        where a.bizKey      = :bizKey
          and a.bankCertSeq = :bankCertSeq
       """)
int clearBankCertSeq(String bizKey, Long bankCertSeq, String userId, String nowDtm);
```

Scoped by `BIZ_KEY + BANK_CERT_SEQ`, which is exact: `BANK_CERT_INFO`'s PK is `(BIZ_KEY, SEQ_NO)`, so
one seq identifies one credential inside a tenant. Bulk JPQL bypasses `@PreUpdate`, so `MOD_ID` /
`MOD_DTM` are stamped explicitly using the same `yyyyMMddHHmmss` UTC convention as
`AccountRegistrationService`. The method returns the affected-row count so each caller can log it.

### 2. Both delete paths call it, in their own transaction

`LoginCredentialsService.delete` (the Settings path) and `BankCredentialService.delete` (the
banking-tree path) each delete the `BANK_CERT_INFO` row and then clear the linkage **inside the
existing `@Transactional` boundary** — so a failure cannot leave the pointer behind. The accounts
themselves are untouched; only `BANK_CERT_SEQ` goes to `NULL`. Re-registering through the Account
Picker re-links them (`AccountRegistrationService:164/195`).

The GDT branch of `LoginCredentialsService.delete` is deliberately unchanged — `GDT_CERT_INFO` owns no
`ACCT_MSTR` linkage.

### 3. The re-link back — verified, and now regression-guarded

Clearing the pointer is only safe if something writes it back. Verified in both trees — **both write it
on both branches**, so this is legacy parity, not a refactor invention:

| | New account | Existing account |
|---|---|---|
| Legacy `fnsh_0101_001_c001_act.jsp` | `:257` `setBankCertSeq(...)` → `create` `:265` | `:321` `setBankCertSeq(...)` → `patch` `:359` |
| Refactor `AccountRegistrationService` | `:195` `row.setBankCertSeq(...)` → `saveAll` | `:164` `existing.setBankCertSeq(...)` → `save` |

The flow that restores it:

```
Settings → Login Credentials → [+ Add]  (or Registered → Add Login)
  1. pick bank            2. Login ID + Password
  3. NXiSAS scrape        → engine returns the bank's account list
                            (NO credential row written yet — held in browser memory)
  4. Select Bank Accounts → user ticks accounts
  5. SAVE → POST /api/v1/login-credentials/bank/register   ONE transaction:
       a) INSERT BANK_CERT_INFO  → NEW SEQ_NO (wb_get_next_val, per-tenant MAX+1)
       b) per ticked account: exists? UPDATE BANK_CERT_SEQ = <new seq>, ACTV_YN = 'Y'
                              new?    INSERT      BANK_CERT_SEQ = <new seq>
       c) fan out ACCT_AUTH_INFO
     → Transfer re-enables
```

Two properties make the NULL safe rather than a trap:

- **`createBankWithAccounts` rejects an empty account list *before* creating the credential**
  (`WBE_BANK_ACCOUNT_REGISTER_EMPTY`). A `BANK_CERT_INFO` row therefore cannot exist without at least
  one linked account, so "re-registered but nothing re-linked" is unreachable from the Settings UI.
- **The picker does not filter out already-registered accounts.** `applyScrapeResult` maps the engine's
  full account list straight into the dialog (`(res.accounts ?? []).map(mapScrapedToBankRow)`), and
  `SelectBankAccountsDialog` has no already-registered exclusion or disable. Every scraped account is
  offered and tickable — which is what lets an existing row be re-selected and re-pointed.

**Coverage gap found and closed.** The re-link was implemented but had **no test anywhere**: the only
registration test (`AccountRegistrationServicePrnSeqAndWhitelistTest`) covers `PRN_SEQ` allocation and
the `ACCT_AUTH_INFO` fan-out, and nothing asserted `BANK_CERT_SEQ` on the registration path. That
mattered *because of this change* — the consequence of losing the re-link got strictly worse:

| | Before this fix | After this fix |
|---|---|---|
| Accounts after delete | keep a stale seq (broken) | `NULL` (honest) |
| Accounts after re-register, **if the re-link were lost** | keep the stale seq — still broken, no change | **stay `NULL` forever — Transfer could never be re-enabled from the UI** |

`AccountRegistrationServiceCredentialRelinkTest` (5 cases) now pins it: NULL → new seq (the recovery
path), stale seq → overwritten (the documented cure for the reported rows), reactivation + `REG_*`
preservation, the INSERT branch, and all-accounts-not-just-the-first.

### 4. `BANK_CD` guard on cert resolution

`OwnAccountTransferService` gained `resolveCertForAccount(a, certBySeq)`, replacing the inline
`certBySeq.get(...)` at the single resolution site. A resolved cert whose `BANK_CD` differs from the
account's is logged at `WARN` and treated as **no cert** — the row projects null cert fields and the
frontend gate disables Transfer with the existing register-credentials message, exactly as an
unlinked account does. `loadBankCerts` itself is unchanged (it still batch-loads by tenant), so the
guard adds no query.

## Files Changed

| File | Change |
|---|---|
| [src/main/java/com/kosign/wecms/domain/banking/repository/AcctMstrRepository.java](../../src/main/java/com/kosign/wecms/domain/banking/repository/AcctMstrRepository.java) | **new** `clearBankCertSeq` bulk-update query (+39) |
| [src/main/java/com/kosign/wecms/domain/setting/logincredentials/service/LoginCredentialsService.java](../../src/main/java/com/kosign/wecms/domain/setting/logincredentials/service/LoginCredentialsService.java) | bank-branch delete clears the linkage; `@Slf4j` added for the unlinked-count log (+20) |
| [src/main/java/com/kosign/wecms/domain/banking/bankcredentials/service/BankCredentialService.java](../../src/main/java/com/kosign/wecms/domain/banking/bankcredentials/service/BankCredentialService.java) | same clear; `AcctMstrRepository` + `@Slf4j` added (+42) |
| [src/main/java/com/kosign/wecms/domain/banking/ownaccounttransfer/service/OwnAccountTransferService.java](../../src/main/java/com/kosign/wecms/domain/banking/ownaccounttransfer/service/OwnAccountTransferService.java) | `resolveCertForAccount` + `BANK_CD` guard (+40) |
| [src/test/java/com/kosign/wecms/domain/banking/repository/ClearBankCertSeqQueryContractTest.java](../../src/test/java/com/kosign/wecms/domain/banking/repository/ClearBankCertSeqQueryContractTest.java) | **new** — 6 query-contract cases |
| [src/test/java/com/kosign/wecms/domain/banking/bankcredentials/service/BankCredentialServiceDeleteLinkageTest.java](../../src/test/java/com/kosign/wecms/domain/banking/bankcredentials/service/BankCredentialServiceDeleteLinkageTest.java) | **new** — 4 cases |
| [src/test/java/com/kosign/wecms/domain/banking/ownaccounttransfer/service/OwnAccountTransferServiceCertBankGuardTest.java](../../src/test/java/com/kosign/wecms/domain/banking/ownaccounttransfer/service/OwnAccountTransferServiceCertBankGuardTest.java) | **new** — 4 cases |
| [src/test/java/com/kosign/wecms/domain/setting/logincredentials/service/LoginCredentialsServiceTest.java](../../src/test/java/com/kosign/wecms/domain/setting/logincredentials/service/LoginCredentialsServiceTest.java) | +2 cases (bank clears linkage, GDT does not) |
| [src/test/java/com/kosign/wecms/domain/banking/service/AccountRegistrationServiceCredentialRelinkTest.java](../../src/test/java/com/kosign/wecms/domain/banking/service/AccountRegistrationServiceCredentialRelinkTest.java) | **new** — 5 cases guarding the re-link (no production code change) |

`git diff --stat -- src/`: **5 tracked files changed, 180 insertions(+), 4 deletions(-)** — plus 4 new
(untracked) test files. Production code is 4 files (repository + 2 delete services + the OAT guard);
`AccountRegistrationService` is **unmodified** — its re-link only gained tests.
Documentation touched in the same pass: this record, the test-result record,
and five permanent docs (both features' `test-scenarios.md`, login-credentials `business-flow.md` +
`functional-specification.md`, own-account-transfer `business-flow.md`).

## API Impact

**None — no contract change on either endpoint.** `DELETE /api/v1/login-credentials/{type}/{seqNo}`
and `DELETE /api/v1/banking/bank-credentials/{seqNo}` keep their request shapes, their `ApiResponse`
envelopes, and their error codes (`WBE2090` / `WBE2050`). What widens is the **side effect**: the
accounts that pointed at the deleted credential are unlinked in the same transaction.

`GET /api/v1/banking/own-account-transfer/source-accounts` also keeps its contract — the five cert
fields (`bank_cert_seq`, `cert_info_con`, `cert_info_detl_con`, `cert_info_detl_con2`,
`nxisas_gb_seq_no`) already ship. Only *when* they project as null changes, and the frontend already
handles null (that is the state the per-account gate reads).

## Database Impact

**Data-behaviour change, no schema change.** `UPDATE ACCT_MSTR SET BANK_CERT_SEQ = NULL, MOD_ID, MOD_DTM
WHERE BIZ_KEY = ? AND BANK_CERT_SEQ = ?`, issued in the same transaction as the credential delete.
No DDL, no Flyway migration, no `.sql` artifact required for the code path itself. See
`## Out of Scope` for the (unwritten) back-fill.

## Integration Impact

**Strictly fewer bad bank logins**, which is the point — each failed authentication feeds the bank's
3-strike lockout, and a lockout costs a password-reset request to the PO team in Vietnam.

- A stale pointer can no longer resolve a **foreign bank's** credential, so the NXiSAS engine is never
  handed bank B's login for a bank A account.
- After a delete, the accounts read as un-credentialed, so the frontend gate blocks the composer before
  the engine is invoked at all (the pre-flight `hasSavedBankCert` guard is unchanged and still the
  last line).
- No change to the Coocon / NXiSAS request contract, the scraping flow, or the dispatch path.

## Verification

### Automated Tests

- Test: `./gradlew test --tests "…ClearBankCertSeqQueryContractTest" --tests "…bankcredentials.service.*" --tests "…ownaccounttransfer.*"`
- Result: **PASS** — 9 classes, **71 executed, 71 passed, 0 failed**
- Test: `./gradlew test --tests "…logincredentials.service.LoginCredentialsServiceTest"`
- Result: **PASS** — **12 executed, 12 passed, 0 failed** (10 pre-existing + the 2 added here)
- Test: `./gradlew test --tests "…banking.service.AccountRegistrationService*"`
- Result: **PASS** — **8 executed, 8 passed, 0 failed** (5 new re-link + 3 pre-existing)
- Test: `./gradlew test` (full suite — justified: `AcctMstrRepository` is shared across Banking,
  Payment, and the reports)
- Result: **PASS** — 331 classes, **2775 executed, 2755 passed, 20 skipped, 0 failed, 0 errors**
- Test: `./gradlew compileJava` / `compileTestJava`
- Result: **PASS** — no new warnings (one pre-existing unchecked-operations note in
  `WithdrawalAccountListServiceTest`)

### Regression Test

```
Test file:          src/test/java/com/kosign/wecms/domain/setting/logincredentials/service/LoginCredentialsServiceTest.java
Test case:          delete(bank): clears ACCT_MSTR.BANK_CERT_SEQ for the deleted seq (no dangling pointer)
Behavior protected: deleting a bank credential must never leave an ACCT_MSTR row pointing at the
                    removed SEQ_NO — that dangling pointer is what disabled Transfer on Own Account
                    Transfer while the credential was visibly present in the table
```

**Fail-before / pass-after — executed**, on both delete paths and on the guard:

| Reverted to `HEAD` | Result |
|---|---|
| `LoginCredentialsService` | **1 of 12 failed** — `delete_bank_clearsAccountLinkage` (wanted-but-not-invoked) |
| `BankCredentialService` + `OwnAccountTransferService` | **4 of 14 failed** — 3 delete-linkage cases + `crossBank_certIsSuppressed` |
| `AccountRegistrationService` (re-link line removed as a probe) | **4 of 5 failed** in `AccountRegistrationServiceCredentialRelinkTest`. The pre-existing `AccountRegistrationServicePrnSeqAndWhitelistTest` stayed **green** — direct proof the gap was real and unguarded. |
| all restored | **PASS**, then the full suite 2775/2775 |

`ClearBankCertSeqQueryContractTest` is a **forward guard, not a fail-before reproduction**: the query
it asserts is new, so reverting the repository makes the test fail to compile rather than fail to
assert. It exists to catch a future regression — a dropped `@Modifying`, a dropped `biz_key`
predicate (which would unlink other tenants' accounts in the live JEX DB), or a widened WHERE.

### Regression Review

- Areas checked: every `BankCertInfo` delete call site
  (`grep -rn "bankRepo.delete|bankCertInfoRepo.delete"` → exactly the two now fixed); the other five
  `BankCertInfoRepository` consumers (`AccountService`, `OatNxisasService`, `CardTransactionsService`,
  `CardTransactionSyncService`, `OwnAccountTransferService`) are read-only w.r.t. the linkage; the
  single `certBySeq` resolution site in `OwnAccountTransferService`; `AccountRegistrationService`
  (the only writer of `BANK_CERT_SEQ`, unchanged, and the cure path for existing dangling rows).
- Result: no other call site resolves or clears the linkage. Card Transactions resolves its own cert
  per sync (`findFirstByBizKeyAndBankCdOrderBySeqNoAsc` — already bank-scoped) and is unaffected.
  Full-suite green confirms no collateral impact.
- **Recovery path verified end-to-end** (the NULL is only safe if the write-back works): both trees
  write `BANK_CERT_SEQ` on the new-account AND existing-account branches (legacy
  `fnsh_0101_001_c001_act.jsp:257`/`:321`; refactor `AccountRegistrationService:195`/`:164`);
  `createBankWithAccounts` cannot create a credential with zero accounts; and the account picker does
  **not** exclude already-registered accounts (`applyScrapeResult` maps the engine list verbatim,
  `SelectBankAccountsDialog` has no already-registered filter or disable) — so an existing row can
  always be re-ticked and re-pointed. See `## Implementation` §3.
- **Legacy coexistence re-verified against every legacy reader of the column.** `ACCT_MSTR_R003`,
  `R004`(+`_CNT`), `R010`(+`_CNT`), `R001_CNT` all `LEFT OUTER JOIN BANK_CERT_INFO`, so a `NULL`
  never hides an account row; `ACCT_MSTR_R001` does not join at all. The single INNER JOIN —
  `ACCT_MSTR_R008` (NAPAS target list, `trns_0001_005_r001`) — drops the row for a `NULL` seq **and
  equally for a dangling seq**, so behaviour is unchanged. The only case where dangling differs from
  `NULL` is a stale seq that coincidentally matches another bank's credential, which is precisely the
  wrong-bank hazard being removed. Legacy never reads the old value to restore anything — it always
  overwrites it.

### Manual Verification

- Environment: LOCAL
- Steps: **not performed** — the app was not started and no DB was reached for this change.
- Result: **NOT RUN.** Needs the SIT scenarios in `## Known Limitations / Follow-up`.

## Test Evidence

Test result:
[Bank Credential Delete — Dangling Account Linkage — Test Result](../testing/results/bank-credential-delete-dangling-linkage-test-result-2026-08-19.md)

```
Final test result:    PASS
Regression coverage:  PASS (fail-before/pass-after executed on both delete paths + the guard)
Manual / SIT:         NOT RUN
```

## Result

Deleting a bank credential now clears the account linkage it owned, on **both** delete endpoints, in
the same transaction as the delete. `ACCT_MSTR.BANK_CERT_SEQ IS NULL` therefore honestly means "not
registered against a credential", and cert resolution can no longer cross banks.

**The reported Shinhan rows are not retroactively cured by this change** — it is forward-looking. Their
`BANK_CERT_SEQ = 1` still points at the credential deleted before the fix shipped. The cure for those
rows remains a **data** action: Settings → Login Credentials → Shinhan → **Select Bank Accounts**,
which rewrites `BANK_CERT_SEQ`.

The recovery path is unchanged from legacy and now regression-guarded: re-registering the credential
and re-picking the accounts rewrites `BANK_CERT_SEQ` to the new seq on both the insert and the update
branch, and a credential cannot be created without at least one account being linked.

The frontend half (per-account gate, already shipped) is
`wecms-web/docs/bugs/own-account-transfer-credential-gate-per-account-parity-2026-08-19.md`; its two
open follow-ups — dangling linkage on delete, and cert resolved without `bank_cd` — are both closed by
this record.

## Known Limitations / Follow-up

1. **Existing dangling rows are not back-filled** (unchanged from `## Out of Scope`). A one-off DBA
   script under `docs/sql/2026/` is possible but is a **data change against the live JEX DB** and needs
   its own decision. Until then the per-tenant cure is the Account Picker re-run above.
2. **SIT verification not performed.** Two scenarios to run once deployed: (a) delete a bank
   credential that owns accounts → confirm those accounts show `BANK_CERT_SEQ IS NULL` and Own
   Account Transfer disables Transfer for them; (b) re-register the credential and re-pick the
   accounts → confirm Transfer re-enables. Recorded in the two features'
   `test-scenarios.md` (`D1b`/`D1c` and `SA-CERT-1`…`SA-CERT-3`).
3. **The `BigDecimal` scale assumption in `loadBankCerts` is untouched.**
   `wantedSeqs.contains(b.getSeqNo())` compares `BigDecimal`s, which is scale-sensitive
   (`18` ≠ `18.0`). Every observed `SEQ_NO` on the reported tenant carries scale 0, matching
   `BigDecimal.valueOf(long)`, so this is currently correct — but it is a latent trap if a legacy row
   ever lands with a non-zero scale. Left alone per `feedback_preserve_existing_code`; noted here so
   the next person who touches that method sees it.
4. **The `BANK_CD` guard is silent to the user.** A cross-bank hit logs `WARN` server-side and renders
   as an ordinary "credentials were deleted" state. That is the safe default, but it makes a genuinely
   mis-linked account indistinguishable from an unlinked one in the UI. Splitting the message is low
   value today (the audit's finding B1 reached the same conclusion) — revisit only if the WARN is ever
   seen in a real environment.
