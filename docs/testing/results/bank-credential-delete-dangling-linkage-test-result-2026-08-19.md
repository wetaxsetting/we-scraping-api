# Bank Credential Delete — Dangling Account Linkage — Test Result

**Date:** 2026-08-19
**Type:** TEST RESULT
**Status:** PASS
**Repository:** wecms-api
**Related Record:** [Bank Credential Delete Leaves a Dangling `ACCT_MSTR.BANK_CERT_SEQ`](../../bugs/bank-credential-delete-leaves-dangling-acct-linkage-2026-08-19.md)
**Issue:** —

## Scope

Verification of three coupled backend changes:

1. `AcctMstrRepository.clearBankCertSeq(...)` — the bulk `UPDATE … SET BANK_CERT_SEQ = NULL` scoped to
   `BIZ_KEY + BANK_CERT_SEQ`.
2. Both credential-delete paths calling it inside their existing transaction —
   `LoginCredentialsService.delete` (the endpoint the Settings UI uses) and
   `BankCredentialService.delete` (the banking-tree endpoint).
3. The `BANK_CD` equality guard on Own Account Transfer's per-row cert resolution
   (`OwnAccountTransferService.resolveCertForAccount`).
4. **Regression coverage for the re-link back** — `AccountRegistrationService` writing
   `BANK_CERT_SEQ` when a credential is (re-)registered. **No production code changed for this item**;
   it closes a pre-existing coverage gap that items 1-2 turned into a real risk, since clearing the
   pointer makes that write the only route back to a working Transfer button.

## Environment

**LOCAL only.** No database was reached and the application was never started — every test below is a
headless JVM test. **SIT / UAT not exercised**; deployment is manual and has not happened.

## Code / Build Under Test

- Branch: `main` (working tree)
- Commit: **NOT COMMITTED** — working-tree contents only
- Build/artifact: Gradle 8.14.4, Java 21 (Temurin), Spring Boot 3.5.13; JUnit 5 + Mockito + AssertJ
- API URL/environment: **not used** — no HTTP, no DB. `@ExtendWith(MockitoExtension.class)` /
  plain-Mockito service tests plus one reflection-based query-contract test.

## Automated Tests

| Test / Command | Result | Evidence |
|---|---|---|
| `./gradlew compileJava` | **PASS** | no output, no warnings |
| `./gradlew compileTestJava` | **PASS** | one pre-existing unchecked-operations note in `WithdrawalAccountListServiceTest` — predates this change |
| `./gradlew test --tests "…banking.repository.ClearBankCertSeqQueryContractTest" --tests "…banking.bankcredentials.service.*" --tests "…banking.ownaccounttransfer.*"` | **PASS** | 9 classes, **71 executed, 71 passed, 0 failed, 0 errors** |
| `./gradlew test --tests "…setting.logincredentials.service.LoginCredentialsServiceTest"` | **PASS** | **12 executed, 12 passed, 0 failed** (10 pre-existing + 2 added here) |
| `./gradlew test --tests "…banking.service.AccountRegistrationService*"` | **PASS** | **8 executed, 8 passed, 0 failed** (5 new re-link cases + 3 pre-existing) |
| `./gradlew test` (full suite) | **PASS** | **331 classes, 2775 executed, 2755 passed, 20 skipped, 0 failed, 0 errors** |

The full-suite run is not ceremonial: `AcctMstrRepository` is shared across Banking, Payment, and the
report services, so a broader regression run is warranted per `CLAUDE.md` §15i.

### Per-class breakdown of the new/changed tests

| Class | Cases | Result |
|---|---|---|
| `ClearBankCertSeqQueryContractTest` (**new**) | 6 | PASS |
| `BankCredentialServiceDeleteLinkageTest` (**new**) | 4 | PASS |
| `OwnAccountTransferServiceCertBankGuardTest` (**new**) | 4 | PASS |
| `LoginCredentialsServiceTest` (**+2**) | 12 total | PASS |
| `AccountRegistrationServiceCredentialRelinkTest` (**new**) | 5 | PASS |
| `AccountRegistrationServicePrnSeqAndWhitelistTest` (untouched, re-run) | 3 | PASS |
| `OwnAccountTransferServiceTest` (untouched, re-run) | 32 | PASS |

### What each new case pins

**`ClearBankCertSeqQueryContractTest`** — the query itself, asserted from the annotation because this
project ships no Testcontainers and no embedded DB (`CLAUDE.md` §15c), so the UPDATE cannot be executed:

| # | Case | Pins |
|---|---|---|
| 1 | `@Modifying` present | without it Spring Data never runs the UPDATE, and the caller-level test would still pass because it stubs the call |
| 2 | returns `int` | the unlinked count stays observable/loggable |
| 3 | single `UPDATE … SET a.bankCertSeq = null` | the pointer goes to NULL, not to 0 and not to another seq |
| 4 | **`a.bizKey = :bizKey` predicate present** | **the dangerous one** — losing it would unlink accounts in *every* tenant against the live JEX DB, and nothing else in the suite would notice |
| 5 | `a.bankCertSeq = :bankCertSeq` predicate present | only the deleted credential's accounts are touched |
| 6 | `MOD_ID` / `MOD_DTM` stamped | bulk JPQL bypasses `@PreUpdate`, so the audit columns must be explicit |

**`BankCredentialServiceDeleteLinkageTest`** — the banking-tree caller: the clear happens, with the
tenant and the seq of the row actually deleted; the seq crosses as a `long` (a `BigDecimal` scale
mismatch would silently match nothing); order is credential-then-linkage; and an unknown /
cross-tenant seq throws without touching `ACCT_MSTR` at all.

**`OwnAccountTransferServiceCertBankGuardTest`** — same-bank cert resolves normally (login id + strictly
decrypted password project); a cert owned by a **different** bank projects every cert field as null
while the row still renders; a seq pointing at a deleted credential projects null; an account with no
`BANK_CERT_SEQ` never queries the credential table at all.

**`LoginCredentialsServiceTest` (+2)** — the Settings path clears the linkage for the deleted seq, and
the GDT branch never touches `ACCT_MSTR` (GDT credentials own no account linkage).

**`AccountRegistrationServiceCredentialRelinkTest`** — the write-back, which had **no coverage at all**
before this pass:

| # | Case | Pins |
|---|---|---|
| 1 | existing account with `BANK_CERT_SEQ = NULL` → new seq | **the recovery path** — the exact state the delete fix leaves behind |
| 2 | existing account with a **stale** seq → overwritten | the documented cure for the already-dangling reported rows |
| 3 | reactivation (ACTV_YN set to Y) + `REG_ID`/`REG_DTM` preserved, `MOD_*` bumped | legacy `fnsh_0101_001_c001` patches, never re-creates |
| 4 | brand-new account carries the seq on INSERT | the other legacy branch (`:257`) |
| 5 | **all** picked accounts re-linked, not just the first | a loop bug would strand 3 of the tenant's 4 Shinhan rows |

## Regression Tests

**Did a regression test cover the original defect? YES.**

```
Test file:          src/test/java/com/kosign/wecms/domain/setting/logincredentials/service/LoginCredentialsServiceTest.java
Test case:          delete(bank): clears ACCT_MSTR.BANK_CERT_SEQ for the deleted seq (no dangling pointer)
Original failure protected: deleting a bank credential left every linked ACCT_MSTR row pointing at the
                    removed SEQ_NO. Re-registering minted a NEW seq without re-linking, so Own
                    Account Transfer disabled Transfer with "Bank login credentials were deleted.
                    Please register Login Credentials again." while the credential was visibly
                    present in bank_cert_info.
Result:             PASS after the fix, FAIL before it (executed — see below)
```

### Fail-before / pass-after — actually executed

Run on the real working tree, reverting source files to `HEAD` with the new tests in place:

| Step | Reverted | Command | Observed |
|---|---|---|---|
| 1 | `LoginCredentialsService.java` | `--tests "…LoginCredentialsServiceTest"` | **1 of 12 FAILED** — `delete(bank): clears ACCT_MSTR.BANK_CERT_SEQ…` (Mockito wanted-but-not-invoked). The GDT case still passed, correctly — it asserts a *negative*. |
| 2 | `BankCredentialService.java` + `OwnAccountTransferService.java` | contract + linkage + guard classes | **4 of 14 FAILED** — the 3 `BankCredentialServiceDeleteLinkageTest` positive cases plus `crossBank_certIsSuppressed` |
| 3 | `AccountRegistrationService.java` — the single line `existing.setBankCertSeq(bankCertSeq)` commented out as a probe | `--tests "…AccountRegistrationServiceCredentialRelinkTest"` | **4 of 5 FAILED**. Case 4 (INSERT branch) correctly stayed green — the probe only removed the UPDATE branch. Crucially, the pre-existing `AccountRegistrationServicePrnSeqAndWhitelistTest` **stayed green**, which is direct proof the re-link was unguarded before this pass. |
| 4 | all restored (`git diff` on `AccountRegistrationService.java` empty — byte-identical to `HEAD`) | same targeted runs | **PASS** |
| 5 | all restored | `./gradlew test` | **PASS — 2775/2775** |

No git history was modified — `git checkout --` on tracked files, restored from a scratchpad copy.

**One honest exception.** `ClearBankCertSeqQueryContractTest` is a **forward guard, not a fail-before
reproduction.** The query it asserts is new, so reverting `AcctMstrRepository` makes the test fail to
*compile* rather than fail to *assert*. It was therefore verified only in the pass-after direction. Its
purpose is future regression: a dropped `@Modifying`, a dropped `biz_key` predicate, or a widened
`WHERE` would each break a specific case in it.

## Test Scenarios

| Scenario | Expected | Actual | Result |
|---|---|---|---|
| Delete a bank credential owning 4 accounts | `BANK_CERT_INFO` row removed; 4 accounts unlinked; count logged | as expected (stubbed repo returned 4) | **PASS** (Mockito) |
| Delete a bank credential owning 0 accounts | delete succeeds, 0 unlinked, no error | as expected | **PASS** |
| Delete a GDT credential | `GDT_CERT_INFO` removed; `ACCT_MSTR` untouched | as expected | **PASS** |
| Delete an unknown / cross-tenant seq | business error; nothing deleted; nothing unlinked | as expected | **PASS** |
| Account whose seq belongs to another bank | cert fields all null; row still listed; Transfer gated | as expected | **PASS** |
| Re-register + re-pick accounts after a delete | `BANK_CERT_SEQ` rewritten to the NEW seq on the existing rows; Transfer re-enables | as expected (Mockito, both NULL and stale starting states) | **PASS** |
| Same behaviour against the real DB, on SIT | account rows show `BANK_CERT_SEQ IS NULL` after delete, then the new seq after re-picking | **not run** | **NOT RUN** — see `## Not Tested` |

## API Verification

**NOT APPLICABLE — no contract change.** Both delete endpoints keep their request shape, `ApiResponse`
envelope, and error codes; `/source-accounts` keeps all five cert fields (only *when* they project as
null changed, and null was already the handled case). No controller was modified, so no `@WebMvcTest`
was added.

## Database Verification

**NOT RUN — no database was reached.** The change is a data-behaviour change with **no schema change**:
no DDL, no Flyway migration (Flyway is disabled; `ddl-auto: validate`), and no `.sql` artifact for the
code path. The statement is issued by the application inside the credential-delete transaction.

The one DB-side item that *would* need an artifact — back-filling the tenant's already-dangling rows —
is explicitly **out of scope** in the implementation record and was not written: it is a data change
against the live JEX DB and needs its own decision.

## Security / Permission Verification

**PASS, by review.**

- **Tenant isolation** is the load-bearing property here and is asserted twice: the query carries
  `a.bizKey = :bizKey` (contract test case 4) and the caller passes the tenant of the row it just
  loaded through the already-tenant-checked `findByBizKeyAndSeqNo`. A cross-tenant seq throws before
  `ACCT_MSTR` is touched (`delete_unknownSeq_touchesNothing`, `verifyNoInteractions`).
- **Credential exposure narrows, never widens.** The `BANK_CD` guard can only *suppress* cert
  projection; it cannot cause a credential to be projected that was not projected before. A foreign
  bank's login id and password can no longer reach the wire or the engine.
- Authorization annotations on both delete endpoints are untouched.

## Performance / Concurrency Verification

**PASS, by review.** The clear is one indexed bulk `UPDATE` per delete — it replaces nothing and adds
no N+1 (the guard reuses the already-batch-loaded `certBySeq` map, so it issues no query). Both callers
run inside the existing `@Transactional` boundary, so the credential row and its linkage commit or roll
back together. `clearAutomatically = true` / `flushAutomatically = true` keep the persistence context
consistent with the bulk write. No new locking, no new external call.

## Existing Failures

**None.** The full suite is green: 0 failures, 0 errors. The 20 skipped tests are pre-existing
`@Disabled` cases unrelated to this change. The only pre-existing warning is the unchecked-operations
note in `WithdrawalAccountListServiceTest`, which predates this work.

## New Failures

**None.**

## Not Tested

| Check | Why |
|---|---|
| SIT / UAT behaviour | Deployment is manual and has not happened (`CLAUDE.md` §16). |
| Real `UPDATE` execution + affected-row count | No Testcontainers, no embedded DB in this project. Covered statically by the query-contract test. |
| The reported Shinhan tenant end-to-end | Requires the live JEX DB. Both Postgres MCP servers were disconnected during this session, so no DB read was possible either. |
| Rendered UI after a delete | Frontend concern; the paired `wecms-web` record covers the gate, and its own tests are green. |
| Cross-bank collision in real data | Needs a tenant where a stale seq has actually been re-occupied by another bank. The guard is asserted in jsdom-free unit form instead; a real occurrence would log `WARN`. |

## Final Result

**PASS** (LOCAL scope, as defined above).

## Recommendation

**READY FOR MANUAL COMMIT**, then **READY FOR SIT** for the two scenarios in the implementation
record's follow-up item 2. Note that this fix is forward-looking — the reported Shinhan rows still need
the **data** action (Settings → Login Credentials → Shinhan → Select Bank Accounts), not a deploy.
