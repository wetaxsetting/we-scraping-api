# Bank Transaction Sync scrapes every account at a bank with one arbitrary credential

**Date:** 2026-08-27
**Type:** BUG FIX
**Current Work Classification:** BUG
**Finding Classification:** BUG — refactor-only divergence from legacy JEX
**Investigation Status:** COMPLETE
**Approval Status:** APPROVED — Sonny, 2026-08-27 ("Please fix it to correct.")
**Implementation Status:** COMPLETE
**Test Status:** PASS
**Status:** DEPLOYED TO SIT — behaviour NOT yet exercised
**Repository:** wecms-api

> **Process note (honest record).** The user's message carried explicit implementation intent
> (§17c class B/C), and implementation began before this record was created — contrary to §2a,
> which makes the record a hard precondition. The record was written immediately after the code
> change and before any commit. Recorded here rather than back-dated.

## Problem

Account Inquiry (VND / FCY) > Bank Transaction Sync logs in with a credential chosen by **bank
code**, not by the credential each account is actually registered under. A tenant holding more
than one login at the same bank therefore has some accounts scraped with the wrong login ID and
password.

Raised by Sonny 2026-08-27 while reviewing the Vietcom iB@nking / DigiBiz guard work:

> *"If user add Login Credentials with Vietcom VCB iB@nking … then in page that get Login
> Credentials … Account Inquiry (VND), Account Inquiry (FCY), Own Account Transfer … Because all
> the Credentials (ID and PWD) not the same."*

## Current Behavior (before the fix)

`BankTransactionSyncService` resolved the credential with
`credentialService.loadForScrapingByBank(acct.getBankCd())` (:534), which is
`findFirstByBizKeyAndBankCdOrderBySeqNoAsc` — **the lowest `SEQ_NO` registered at that bank**.
`loginGroupKey` (:1036) returned `key.bankCd()` alone, so every account at one bank was merged
into a single login group and reused one engine session.

The method's javadoc asserted the premise outright:

> *"multiple accounts at one bank share the same registered credential row, so resolving by
> `BANK_CD` is enough"*

That is false whenever a tenant registers more than one login at a bank — a different sub-type
(`NXISAS_GB_SEQ_NO`), or a different bank-side user level (preparer vs approver login), which
legacy stores as separate `BANK_CERT_INFO` rows because the find-or-create key includes
`CERT_INFO_CON`.

**Live evidence — shared dev DB `wecmsdevdb`, tenant `VN_VNM0004_000001`:**

| `seq_no` | `nxisas_gb_seq_no` | Sub-type | Login ID | Accounts bound |
|---|---|---|---|---|
| 1 | 1 | VCB DigiBiz | `…S07` | **0** |
| 2 | 0 | VCB iB@nking | `…E111` | **3** |

`ORDER BY seq_no ASC` picks **seq 1 (DigiBiz)**, so all three iB@nking accounts were scraped with
the DigiBiz ID/PW — and `NxisasBankConfig.find(bankCd, 1)` resolved the **DIGIBIZ** module/class
instead of CORPORATE. Repeated failed logins feed the bank's 3-strike lockout.

## Expected Behavior

Resolve the credential per account, through `ACCT_MSTR.BANK_CERT_SEQ`, and group the login
session by credential — matching legacy.

## Verified Root Cause

Legacy JEX resolves per account and groups per credential; the refactor did neither.

| Step | Legacy evidence |
|---|---|
| Binding stamped at Add-and-Save (account picker) | `fnsh_0101_001_c001_act.jsp:257` (insert) · `:321` (update) |
| Credential find-or-create keyed incl. sub-type + login ID | `fnsh_0101_001_c001_act.jsp:168` — the OSMC branch. `CommHelper.isOSMCVersion()` returns `true` in **both** its branches, so this is the branch that always runs. |
| **Account Inquiry real-time inquiry groups by `BANK_CERT_SEQ`** | `nxisas_tranList_osmc.js:265-279` — `fn_groupBy(_orgData, item => [item.BANK_CERT_SEQ])`, then `loginID` / `loginPWD` / `nxisasGbSeqNo` read from that credential |
| Own Account Transfer resolves the same way | `trns_0001_007.ts:1917-1918` — `getCertInfo(bank_cert_seq)` → `getModuleInfo(bank_cd, cert.NXISAS_GB_SEQ_NO)` |
| Account list SQL joins on the pointer | `IDO.ACCT_MSTR_R003.xml:158-160`, `R004`, `R008`, `R010` |

The refactor's own OAT path was already correct
(`OwnAccountTransferService.resolveCertForAccount:1010`) — only the sync orchestrator diverged.

## Scope

`BankTransactionSyncService` only: the login-group key and the credential resolution.

## Out of Scope

- **Legacy `BANK_CERT_INFO_R005` bank-wide account list.** Every credential of a bank shows that
  bank's whole account list (`IDO.BANK_CERT_INFO_R005.xml:40-42` filters `BIZ_KEY` + `BANK_CD`
  only, no `BANK_CERT_SEQ`). The refactor copied this faithfully — a **legacy** defect, so
  changing it diverges from legacy and needs a PO decision. **Open.**
- **Prepare-level vs approve-level as a modelled concept.** No column exists for it in
  `BANK_CERT_INFO`, and an account can be bound to exactly one credential. Supporting both levels
  on one account is a new capability, not a bug fix — PO decision required.
- The two card auto-link fallbacks (`CardTransactionsService:589`,
  `CardTransactionSyncService:208`) — bank-level by design, used only when the request omits the
  seq.
- Re-pointing SIT data (an operator action), and the pencil/edit path not re-linking accounts
  (legacy behaves the same — `comm_0218_004_u001` is a password change only).

## Implementation

**1. `loginGroupKey` is now `BANK_CD + "#" + BANK_CERT_SEQ`** — three overloads: `(String, Long)`,
`(AcctMstr)`, and `(NxisasScrapeSession, AccountKey)` which reads the binding back from
`ACCT_MSTR` for the login-success path (the wire `AccountKey` carries only `bankCd` + `acctNo`).
Grouping in `start()` uses the `byKey` map already built at :233 — **no wire-contract change**.

**2. `resolveCredentialForAccount(AcctMstr)`** replaces the bank-level call:

- `BANK_CERT_SEQ` present → `loadForScraping(seq)`.
- `BANK_CERT_SEQ` null → falls back to `loadForScrapingByBank`, **logged as ambiguous**. Verified
  safe on the dev DB: 17 accounts are unbound, none at Vietcom, and all at banks with a single
  sub-type.
- Credential owned by another bank, or resolving to nothing → `WBE2050`, refusing to send bank
  A's login to bank B (same stance as `OwnAccountTransferService.resolveCertForAccount`).

**3. `BankCredentialService.loadForScrapingByBank` javadoc corrected** — it asserted the false
premise. Now documented as a fallback for unbound accounts only. No behaviour change.

## Files Changed

| File | Change |
|---|---|
| `domain/banking/nxisas/BankTransactionSyncService.java` | grouping by credential; `resolveCredentialForAccount`; 3 `loginGroupKey` overloads |
| `domain/banking/bankcredentials/service/BankCredentialService.java` | javadoc correction on `loadForScrapingByBank` (no behaviour change) |
| `src/test/.../BankTransactionSyncServiceLoginGroupingTest.java` | **+5 regression tests** + `stubBoundAccount` / `stubCredentialBySeq` / `capturedLogins` helpers |
| `src/test/.../BankTransactionSyncService{Async,FixedDeposit,LoginGrouping,VpProfile}Test.java` | explicit `getBankCertSeq() → null` on unbound stubs (see Known Limitations #1) |

## API Impact

**None.** `TransactionsSyncRequest.AccountKey` is unchanged (`bank_cd` + `acct_no`); the binding
is read server-side from `ACCT_MSTR`. No paired `wecms-web` change required.

## UI Impact

None directly. Behavioural effect: sync on an account bound to a non-lowest-`SEQ_NO` credential
now uses the correct login instead of failing at the bank.

## Database Impact

**NONE.** No schema, function, index, constraint or data change. Reads the existing
`ACCT_MSTR.BANK_CERT_SEQ` column.

## Integration Impact

The NXiSAS engine now receives one LOGIN per credential instead of one per bank. For a tenant
with a single credential per bank — every tenant on the dev DB except Vietcom — the call pattern
is byte-identical.

## Verification

### Automated Tests

- Test:   `./gradlew test --tests "com.kosign.wecms.domain.banking.nxisas.*"`
- Result: **PASS** — 77 tests, 0 failed
- Test:   `./gradlew test --tests "com.kosign.wecms.domain.banking.*"`
- Result: **PASS**
- Test:   `./gradlew compileJava`
- Result: **PASS**

### Regression Test

Test file:          `src/test/java/com/kosign/wecms/domain/banking/nxisas/BankTransactionSyncServiceLoginGroupingTest.java`
Test cases:         `twoCredentialsSameBank_oneLoginPerCredential_eachWithItsOwnIdAndPassword` ·
                    `boundAccount_usesItsOwnCredential_notTheLowestSeqAtTheBank` ·
                    `twoAccountsOnTheSameCredential_stillLoginOnce` ·
                    `crossBankBinding_isRefused_ratherThanScrapedWithTheWrongBanksLogin` ·
                    `unboundAccount_fallsBackToTheBankLevelLookup`
Behavior protected: an account is scraped with the credential it is registered under; one engine
                    login per credential; a cross-bank binding is refused.

**Fail-before-fix verified:** reverting both changed lines made **4 of the 5** new tests fail
(the 7 pre-existing tests in the suite stayed green); restoring the fix returned 11/11.

### Regression Review

- Areas checked: the four other `BankTransactionSync*` suites, `NxisasBankConfigTest`,
  `NxisasMaskTest`, `NxisasResultCodesTest`, and the whole `domain.banking` package.
- Result: unaffected — full `domain.banking` run green.

### Manual Verification

- Environment: LOCAL
- Steps: unit tests + read-only DB queries against `wecmsdevdb` for the credential / binding
  evidence in "Current Behavior".
- Result: **NOT RUN** for a live engine round-trip — the NXiSAS gateway is not reachable from
  local R&D. SIT verification pending deployment.

## Test Evidence

Test result:
[Bank Transaction Sync Per-Credential Resolution — Test Result](../testing/results/bank-transaction-sync-per-credential-resolution-api-test-result-2026-08-27.md)

Final test result:    PASS (77 tests in the nxisas package, 0 failed)
Regression coverage:  **YES** — 5 new tests; fail-before-fix verified on 4 of them.

## Result

Implemented and verified locally. **READY FOR MANUAL COMMIT** — not committed, pushed, or
deployed.

## Known Limitations / Follow-up

1. **Mockito returns `0L`, not `null`, for a `Long` getter.** Four existing suites stubbed
   `AcctMstr` without `getBankCertSeq()`, so the mock reported seq `0` — a real binding — and
   diverted into the by-seq lookup. They now stub `null` explicitly. Worth remembering: any
   future `Long`-returning getter has the same trap.
2. **Unbound accounts still guess.** 17 rows on the dev DB have `BANK_CERT_SEQ IS NULL` with
   `HAND_REG_YN='N'`. None are at a multi-credential bank today, so the fallback is safe now —
   but the real cure is re-registering them via Settings > Login Credentials.
3. **The per-credential Bank Account list is still wrong** — see Out of Scope. This is the half
   of the confusion the user is most likely to hit next.
4. **SIT verification not run.** Needs a deploy plus a Vietcom account bound to each of the two
   credentials to exercise the two-login path end to end.

## Deployment + post-deploy status (2026-08-27)

Committed `354959a`, merged `bb14eaa`, pushed to `sit` under explicit human authorization
(Sonny, 2026-08-27 — a one-off approval for this deployment; §16c).

**The fix is deployed but its behaviour has NOT been exercised.** No Bank Transaction Sync has run
on the new build, so the per-credential resolution has never actually chosen a credential in
anger. `Test Status: PASS` refers to the unit suite only — do not read it as SIT verification.

To close it: run a sync on a Vietcom account and read the `bank-sync` log. The new code emits the
resolved credential, and the ambiguous-fallback path logs a distinct warning
(`account …/… has no BANK_CERT_SEQ — falling back to the bank-level credential lookup`), so the log
line alone distinguishes the bound path from the fallback.

### Related: the Vietcom accounts were re-bound to DigiBiz the same day

The paired frontend record
([own-account-transfer-vietcom-corporate-transfer-guard-2026-08-27.md](../../../wecms-web/docs/issues/own-account-transfer-vietcom-corporate-transfer-guard-2026-08-27.md))
shows both Vietcom accounts moved from the iB@nking credential (`seq 2`) to DigiBiz (`seq 1`).

That **changes what a sync on this tenant proves**. Before the re-bind, the bug's signature was
visible (accounts bound to `seq 2`, bank-level lookup returning `seq 1`). Now both the bound value
and the lowest-`SEQ_NO` value are `seq 1`, so **old and new code would pick the same credential for
these accounts** — a passing Vietcom sync no longer discriminates between them.

⚠️ **Do not treat a green Vietcom sync as proof this fix works.** A discriminating test needs a
tenant with two credentials at one bank where the account is bound to the **higher** `SEQ_NO` — the
condition the unit test `boundAccount_usesItsOwnCredential_notTheLowestSeqAtTheBank` pins
synthetically. On live data that condition no longer exists here.

### Unexplained observation, carried forward

A pre-deploy SIT screenshot showed `Last Refresh: Success · 4hr ago` on an account still bound to
iB@nking (`seq 2`), under the **old** bank-level build — which would have used the DigiBiz login.
That is not what this record's analysis predicts. Candidates, none verified: a stale status from an
older run; a different refresh path; or DigiBiz genuinely reaching those accounts (which the later
re-bind makes plausible). **Not evidence for or against the fix** — recorded so it is not
rediscovered as a surprise.
