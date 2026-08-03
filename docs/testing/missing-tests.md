# Payment Dispatch — Test Coverage Audit & Missing Tests

**Scope:** `wecms-api/src/main/java/com/kosign/wecms/domain/payment/dispatch/**` plus the
dispatch-adjacent gates in `payment/internaltransfer/service/Cutoff*` and the five
per-module dispatch executors.
**Date:** 2026-07-28 · **System:** Refactor (wecms-api)

> **See also [fixture-data-conventions.md](fixture-data-conventions.md).** A missing test is the honest
> failure mode; the dangerous one is a *present, passing* test whose fixture data cannot exercise the
> condition it names. Three defects here were hidden that way — including the `DispatchPacer` constants
> below, which had no assertion in either direction until 2026-08-01.

> ## ⚠️ SUPERSEDED 2026-07-29 — read [integration-test-audit.md](integration-test-audit.md) first
>
> This was a **static, unit-level** gap audit. It has been overtaken by work landed on
> 2026-07-28/29 and re-verified by a follow-up integration/dead-code audit. Treat this file as the
> historical record of what was missing on 2026-07-28, not as a current work list.
>
> **Its baseline figures are stale.** It reported 118 production files / 10,750 LOC / 27 test files /
> 204 tests; the tree now holds **127 / 12,789 / 40 / 383**.
>
> **Two of its findings were factually wrong and are retracted:**
>
> 1. **P1-8 (Payroll VCB VND) — WRONG.** It recommended a
>    `payrollVcbVnd_routesToFilePath_notApi` test. Payroll **rejects VCB VND outright**
>    (`WBE_PAYROLL_VCB_VND_NOT_SUPPORTED`, `PayrollDispatchExecutor:213-214`, legacy `:179-182`) and
>    has **no API rail at all** — `PayrollDispatchExecutor` never injects
>    `RealtimeDispatchRegistrar`. The recommended test would have asserted behaviour that must not
>    exist. Both Payroll VCB guards are in fact tested (`vcbVnd_blocked`, `vcbUsdOtherBank_blocked`).
> 2. **The Testcontainers baseline — WRONG.** It reported "Testcontainers 9 files"; that grep matched
>    **javadoc prose**. `build.gradle` has no Testcontainers dependency, and per `CLAUDE.md` that is
>    deliberate.
>
> **P1-3 (null schedule times) is resolved as a NON-ISSUE,** not a defect: `BATCH_START_TM` /
> `BATCH_END_TM` are `NOT NULL` in the legacy JEX DDL (`V95`/`V96`), so a present row cannot carry a
> null bound and legacy's `BATCH_START_TM == null` check is a row-absence test — exactly equivalent
> to the refactor's `Optional.empty() → NO_CRITERIA`. Pinned by `BatchScheduleNotNullContractTest`.
> No PO/DBA decision needed. See integration-test-audit.md §10.1.
>
> **Closed since:** P0-1 (scheduler disabled-by-default — `SchedulerConfigurationContractTest`,
> `SchedulerCronAndLockIdentityTest`) · P0-3 (callback IP filter — `CallbackIpWhitelistFilterTest`,
> 25 tests, and the H-1 fail-closed fix) · P0-4 (default-profile egress safety —
> `DispatchExternalClientWiringTest`) · P1-1 / P1-2 (zone + window boundaries — new
> `OperatingHoursServiceTest` cases; invalid-zone fail-fast now also fixed in production) · P1-4
> (`AntDivChannelTest`, 15 tests) · P1-8..P1-11 (VCB — now covered across all six paths) · P1-12
> (BATCH2 retry — implemented as `BulkExecutionRetryService`, legacy trigger set matched exactly) ·
> P1-13 (`DispatchPacer`) · P2-6 (claim atomicity — `DispatchClaimQueryContractTest`) · P3-2
> (`TransferDispatchExecutor` — deleted, deletion verified).
>
> **Closed 2026-07-29 (second pass):** P2-1 + P2-2 (`VietnameseText` and the tone-strip/byte-truncation
> composition) — **NOT A DEFECT**, legacy-verified byte-for-byte (same two 134-char mapping literals,
> same algorithm, same UTF-8 charset, same truncate→strip order per
> `FILE_DEPOSIT_TRANSFER_BATCH1:638` → `:667`); 14 tests added incl. the length-drift guard, emoji,
> combining vs precomposed, and byte-boundary truncation. P0-2 (cross-gate zone) — **decided**:
> `SUPPORTED OVERRIDE`, two accepted values, startup-validated
> ([../architecture/operating-hours-timezone-decision.md](../architecture/operating-hours-timezone-decision.md)).
>
> **Closed 2026-07-30:** P2-3 (split totals) — `BulkSplitTotalReconciliationTest`, legacy
> `FILE_DEPOSIT_TRANSFER_BATCH1:463-686` + `IDO.TB_PAY_FILE_DATA_R001/_R002` traced; Σ trailer / MST /
> 00301-audit / 00301-request counts and amounts all reconcile to the pre-split total across 10
> row/limit shapes, with every row emitted exactly once. P3-1 (`DispatchResultResolver`) —
> `DispatchResultResolverTest`, legacy `IDO.SCDUL_PYMN_SUMR_R006` + `IDO.OVRS_PYMN_SUMR_R007` traced
> field by field; **6 divergences recorded, none changed** (integration-test-audit.md §11.1).
>
> **Still open from this document:** P2-4
> (`presentChannels` derivation) · P1-14 (gateway
> timeout/malformed-response behaviour) · two zone-consistency tests
> (`requestGate_andDispatchGate_useSameZone`, `utcDate_andBusinessTime_areNotMixed`).
>
> **New finding not in this document — found, then fixed:** the bulk file rail could
> **double-dispatch**. Its duplicate guard was a non-atomic read-then-check, and the
> `TB_FIRM_FILE_MST` PK collision it relied on is detected at commit, i.e. *after* the SFTP upload and
> the EFS 301 call. `P0`. **Fixed 2026-07-29** by an atomic pre-egress claim
> (`TbPayFileMstRepository.claimForReception`) committed before any egress, restoring the ordering
> legacy uses (`FILE_DEPOSIT_TRANSFER_BATCH1` commits at `:696`, uploads at `:700`). 22 tests added,
> including a real two-thread race that fails without the fix. Full design, legacy evidence, failure
> matrix and residual risks:
> [../architecture/bulk-dispatch-concurrency-fix.md](../architecture/bulk-dispatch-concurrency-fix.md).

## Baseline

| | Count |
|---|---|
| Dispatch source files | 118 (10,750 LOC) |
| Dispatch test files | 27 (5,294 LOC) |
| Dispatch `@Test` methods | 204 |

The dispatch package is **well tested overall** — the EFS envelope crypto, the 201/202/203/301/302
happy and error paths, the G2 atomic claim, bulk split, callback de-duplication and the
`OperatingHoursService` decision matrix all have real assertions. This document lists only the gaps
that survived verification against the source. It is not a call to raise a coverage percentage.

Findings are ordered by risk to real money movement.

---

## P0 — Untested safety invariants on the money path

### P0-1 · Scheduler "disabled by default" is asserted by zero tests

`ResultCheckJob` and `StrandedRecoveryJob` are both documented as **disabled by default for
coexistence safety** — the refactor must never co-process live `PROC_STAT` rows alongside the JEX
batch on the shared DB, because refactor-originated rows are indistinguishable from JEX-originated
ones (both draw the same global `firm_seq_no`). The guard is `cron` defaulting to `"-"`
([ResultCheckJob.java:39](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/scheduler/ResultCheckJob.java#L39),
[StrandedRecoveryJob.java:32](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/scheduler/StrandedRecoveryJob.java#L32)),
plus for recovery a second `wecms.dispatch.recovery.enabled=false` gate.

Neither job has a test file. A property rename, a yaml typo, or a "helpful" default in a profile
would silently arm a sweep against live JEX rows with nothing failing.

**Add** `scheduler/DispatchSchedulerWiringTest` — copy the in-repo `ApplicationContextRunner`
precedent at
[HaengsungScheduledPaymentImportJobWiringTest.java](../../src/test/java/com/kosign/wecms/domain/payment/haengsung/batch/HaengsungScheduledPaymentImportJobWiringTest.java):

- `resultCheckCron_defaultsToNeverFire` — resolved `@Scheduled` cron is `"-"` with no properties set
- `recoveryCron_defaultsToNeverFire`
- `recoveryService_isInertEvenWhenCronSet` — cron set + `recovery.enabled` unset → `recoverOnce()` no-ops
- `bothJobs_declareIctZone` — `zone = "Asia/Ho_Chi_Minh"` on both `@Scheduled`
- `bothJobs_declareSchedulerLock` — lock name + `lockAtMostFor` present (two nodes cannot sweep concurrently)

### P0-2 · The "two cut-off gates cannot contradict each other" claim is unverified

[OperatingHoursService.java:60-85](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/service/OperatingHoursService.java#L60-L85)
justifies its deliberate divergence from legacy (evaluating in ICT, not the DB's `Asia/Seoul`) with:

> matches `CutoffValidator.VIETNAM_ZONE`, so the request-time gate and this dispatch-time gate
> cannot contradict each other — a payment accepted at 15:00 ICT is not then failed by a
> KST-evaluated cut-off.

No test asserts this. Worse, the zone is **configurable** — setting
`wecms.dispatch.operating-hours.zone=Asia/Seoul` for legacy bug-for-bug parity (an explicitly
offered option) immediately creates the contradiction the javadoc says is impossible: a payment
`CutoffValidator` accepts at 15:00 ICT is 17:00 KST and gets CUTOFF-failed at dispatch.

**Add** `service/DispatchGateConsistencyTest`:

- `zonesAgree_byDefault` — `OperatingHoursService` default zone equals `CutoffValidator.VIETNAM_ZONE`
- `acceptedAtRequestTime_isNotCutoffAtDispatchTime` — for each bank/rail, a time inside the
  `CutoffValidator` window yields `DISPATCH`, not `CUTOFF`
- `seoulOverride_isDocumentedAsBreakingTheInvariant` — pin the divergence explicitly so the
  behaviour is a recorded decision rather than a surprise

### P0-3 · `CallbackIpWhitelistFilter` — no tests on the inbound money callback

[CallbackIpWhitelistFilter.java](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/callback/CallbackIpWhitelistFilter.java)
gates the 01301/01302 EFS callbacks. Zero tests. Untested branches:

- `emptyAllowList_passesThrough` — the R&D default; confirm it is intentional and visible
- `pathOutsidePrefix_isNotFiltered`
- `allowedIp_passes` / `disallowedIp_gets403`
- `trustForwardedFor_false_ignoresSpoofedXffHeader` ← **the actual security assertion.** With
  `trustForwardedFor` on, `X-Forwarded-For` is client-controlled; the test that matters is that it
  is ignored when the flag is off.
- `trustForwardedFor_true_takesFirstHopOnly` — `xff.split(",")[0]`, whitespace trimmed

### P0-4 · No test that the default profile cannot make a live gateway/SFTP call

`MockEfsApiClient` throws on every `send` so "no live EFS request can leave by accident", and
`MockSftpClient` is the offline SFTP stand-in. `MockSftpClient` has **zero** references anywhere in
`src/test`. Neither mutual-exclusion guard (`@ConditionalOnProperty` with `matchIfMissing = true`
vs `havingValue = "true"`) is tested.

**Add** `van/client/EfsClientWiringTest` + `sftp/SftpClientWiringTest`:

- `defaultProfile_activeClientIsMock_andThrowsOnSend`
- `efsEnabled_activeClientIsRestClient`
- `neverBothBeans` — the inverse conditions cannot double-register

---

## P1 — OperatingHours

Existing: 17 tests covering INFO/DTL levels, N/E/Y, the NAPAS `INTER` short-circuit, BATCH1
`N`-beats-`E` precedence, `ANT_DIV_CD` canonicalisation, bpchar padding, and unknown-code
fail-closed. This is the best-covered class in the package. The gaps are at its edges.

### P1-1 · The timezone code path is never executed

Every test calls the package-private `evaluate*(…, nowHms)` seam. The public two-arg overloads and
`nowHms()` — the only code that reads `wecms.dispatch.operating-hours.zone` and calls `ZoneId.of` —
are never invoked. Consequently untested:

- default resolves to `Asia/Ho_Chi_Minh`
- the `Asia/Seoul` override actually changes a decision (the documented legacy-parity escape hatch)
- **an invalid zone string throws `DateTimeException` per dispatch call, not at startup.** The
  javadoc says "an unset property can never abort startup" — true, but a *typo'd* one
  (`Asia/Hochiminh`) aborts every dispatch instead, one payment at a time. Nothing pins this.

**Add** to `OperatingHoursServiceTest`: `defaultZone_isIct`, `seoulOverride_shiftsTheWindow`,
`invalidZone_failsFastNotPerDispatch` (and consider validating the zone at construction).

### P1-2 · Window boundary equality

`exeDiv` uses strict `>` / `<`
([OperatingHoursService.java:220-228](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/service/OperatingHoursService.java#L220-L228)),
so `now == startTm` → `Y` (open) and `now == endTm` → `Y` (open). Neither boundary is asserted. At
15:30:00 sharp this is exactly the off-by-one that reaches production.

**Add:** `nowExactlyAtStart_isOpen`, `nowExactlyAtEnd_isOpen`, `nowOneSecondPastEnd_isCutoff`.

### P1-3 · NULL `BATCH_START_TM` on a *present* row — possible divergence

The class javadoc states the legacy rule as "INFO missing → NO_CRITERIA (legacy
`BATCH_START_TM == null`)". The implementation returns `NO_CRITERIA` only when the **row** is
absent; a row that exists with a null `BATCH_START_TM` falls through `exeDiv`'s null guards to `Y`
→ **DISPATCH**. Legacy, as documented, would have failed it.

Untested either way, and the two readings differ on whether a misconfigured schedule row dispatches
or fails. **Verify against the JEX DDL/data and get a PO ruling before writing the test** — then
pin whichever is correct with `infoPresentWithNullStartTm_…`. Same question for a null `BATCH_END_TM`.

### P1-4 · `AntDivChannel.fromStorageCode` is completely untested

[AntDivChannel.java](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/constant/AntDivChannel.java)
has no test file. `canonical` is exercised indirectly through the realtime tests;
`fromStorageCode` — the **strict mapping the entire bulk path depends on** — is not exercised at all.

**Add** `constant/AntDivChannelTest`:

- `fromStorageCode_mapsEVCNS` — `E→EBANK, V→VCB, C→CITAD, N→NAPAS, S→SWIFT`
- `fromStorageCode_nullBlankAndPadding_returnNull`
- `fromStorageCode_lowercaseIsAccepted`
- `fromStorageCode_unknownCode_returnsNull` ← encodes the deliberate legacy rule that `ETC_CNT`
  rows are **not** operating-hours gated. Today that rule lives only in a comment.
- `canonical_vs_fromStorageCode_divergeOnUnknownCodes` — `canonical` returns the value verbatim
  (fail-closed at lookup), `fromStorageCode` returns null (ungated). The two behave differently on
  purpose; no test says so.

---

## P1 — Timezone

Existing: one exemplary test —
`VendorDispatchExecutorTest.apiPath_tranDtAndRegTm_areUtc_notIct_nearMidnightBoundary`.

### P1-5 · Seven dispatch classes hardcode ICT with no injectable clock

`ResultCheckService`, `RealtimeDispatchService`, `BulkReceptionService`, `RealtimeTransferService`,
`StrandedRecoveryService`, `BulkExecutionResultService`, `BulkReceptionResultService` each declare
`private static final ZoneId ICT = ZoneId.of("Asia/Ho_Chi_Minh")` and call `LocalDateTime.now(ICT)`
directly. `TransferDispatchExecutor` takes an injected `clock` — the right pattern exists in the
package and simply was not applied.

The values these stamp (`RQ_DTIME`, `TRAN_DT`, `PROC_DTTM`, DT8/TM6) are **not testable at a
boundary**, and `TRAN_DT` is a PK component and the partition key for every downstream lookup. A
dispatch at 23:59:59 vs 00:00:01 ICT writes a different key.

**Add:** inject `Clock` into each (mirroring `TransferDispatchExecutor`), then one
`…_nearMidnightBoundary_stampsIctDate` test per class. Given memory
`project_banking_datetime_tz_locale.md` (14-char `_DTM` UTC→VN double-shift bugs already found and
fixed elsewhere), this is a known-recurring defect class in this codebase.

### P1-6 · Zone agreement between a written timestamp and the window that reads it

Two aged-row windows compare a locally computed ICT string against a stored `PROC_DTTM`:

- `ResultCheckService.RESULT_CHECK_DELAY_MINUTES = 2`
- `StrandedRecoveryService`: `LocalDateTime.now(ICT).minusMinutes(graceMinutes).format(DTTM14)`

If the stored column was written in UTC and the cutoff is computed in ICT, a 2-minute window
silently becomes a 7-hour one (and a 30-minute grace becomes 7.5 hours). `ResultCheckPollTest
.freshRow_withinDelayWindow_isSkipped` passes with mocked strings, so it cannot catch this.

**Add:** `resultCheck_agedWindow_comparesSameZoneAsProcDttmWriter` and
`recovery_graceWindow_comparesSameZoneAsProcDttmWriter` — construct the row through the real writer
path, not a literal.

**Also add:** `recovery_rowExactlyAtGraceCutoff_isNotEligible` (`LessThan` is strict) and
`graceMinutesZero_behaviour`.

### P1-7 · Non-requirement worth recording

`Asia/Ho_Chi_Minh` is a fixed UTC+07 with no DST, and `Asia/Seoul` is fixed UTC+09. **No DST tests
are needed** for either. Stated here so the absence is not later mistaken for an oversight.

---

## P1 — VCB

Existing: `BulkTransferValidator` VCB-network rules (2 tests), `CutoffValidator` VCB CITAD base /
≥500M large tier / internal-transfer exemption (4), Expense + Vendor + Internal executor VCB
routing (~10).

### P1-8 · Payroll and Internal Transfer lack the "VCB never takes the API path" assertion

Per memory `project_vcb_file_transfer_switch.md`, JEX moved VCB (`970436`) off the Transfer API onto
File-based dispatch for **both** VND and USD. Vendor and Expense pin this
(`vcbVnd_routesToFilePath_notApi`, `vcbVnd_otherBank_notRejected_usesFilePath`).

- `PayrollDispatchExecutor*Test` — **no VCB test at all**
- `InternalTransferDispatchExecutorBankChnlDivTest` — has `vcbVnd_carriesUploadedAntDivCd_…` but
  never asserts the path choice

A VCB VND payroll going out on the API path is a live-money divergence from JEX with no test
standing in the way. **Highest-value VCB gap.**

**Add:** `payrollVcbVnd_routesToFilePath_notApi`, `payrollVcbUsd_routesToFilePath`,
`internalTransferVcbVnd_routesToFilePath_notApi`.

### P1-9 · VCB USD → other-bank reject not covered for Payroll / Internal Transfer

The guard is tested in `ExpenseDispatchExecutorTest.vcbUsdOtherBank_isRejected`,
`VendorDispatchExecutorTest.vcbUsdOtherBank_isRejected` and `ExpensePaymentExcelParser` (G6).
Confirm whether Payroll and Internal Transfer can carry VCB USD at all — if they can, both the
tests **and possibly the guard itself** are missing.

### P1-10 · VCB is absent from the realtime operating-hours tests

`OperatingHoursServiceTest` exercises NAPAS / CITAD / INTER / EBANK; `VCB` appears only in the bulk
`n-beats-E` precedence case. **Add** `realtime_oneCharVcbAlias_resolvesToVcbRow` (`V` → `VCB`).

### P1-11 · VCB's absent SWIFT rail

`CutoffConfigProvider.REQUIRED_RAILS` gives VCB `NAPAS/CITAD/CITAD_LARGE` — deliberately no SWIFT,
and `CutoffConfigSnapshot` documents that an absent rail is `null` "on purpose". No test asserts a
VCB **SWIFT** lookup returns a bypass rather than throwing. A VCB overseas payment reaching a null
rail is a plausible NPE. **Add** `vcbSwiftRail_isAbsent_andBypassesRatherThanThrows`.

---

## P1 — Retries

**Thinnest area in the audit — because there is almost no implementation to test.** Verified by
grep: no `@Retryable`, no `RetryTemplate`, no attempt loop anywhere in `payment/`. The only recovery
mechanisms are (a) `G999` → pending → result-check, and (b) the opt-in stranded sweep.

### P1-12 · The dropped BATCH2 retry loop is an undocumented, untested behaviour change

[FirmBankingResultCd.java:17-23](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/constant/FirmBankingResultCd.java#L17-L23)
records that legacy had a **BATCH2 retry loop** driven by gateway status tokens that are "NOT
defined in `WCConst` and are not invented here" (`TODO(K3)/TODO(B5)`). The refactor therefore does
not retry where legacy did. That is a legacy-parity divergence with no test and no PO decision
recorded in `docs/`.

**Add** a test that pins the *chosen* behaviour so it is deliberate rather than accidental:
`transportFailureOn202_doesNotResend_leavesRowForResultCheck`. The 203 leg has an equivalent
(`ResultCheckPollTest.transportFailure_leavesOriginalUntouched_but203RowRecordedFail`); the 202 leg
— the one that moves money — does not. **Also:** raise the retry-parity question with the PO.

### P1-13 · `DispatchPacer` has no test file — **CLOSED 2026-08-01**

> **Closed** by [`DispatchPacerTest`](../../src/test/java/com/kosign/wecms/domain/payment/dispatch/service/DispatchPacerTest.java):
> all three constants pinned, plus a statelessness test that guards the D-A per-call shape (a Woori call
> must not leak into the next). `pause()` itself is deliberately not exercised — it really sleeps.
> Original entry follows.


[DispatchPacer.java](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/service/DispatchPacer.java)
carries the pacing constants (Woori `970457` = 3000 ms, Shinhan `970424` = 100 ms — both verified legacy
values — and **default = 3000 ms**, deliberately NOT legacy's 100, per D-A 2026-08-01).
`ResultCheckPollTest` verifies the pacer is *invoked* — against a mock, so the delay values
themselves are unverified. Cheap to fix, and these constants exist precisely because the Woori
gateway rate-limits.

**Add** `service/DispatchPacerTest`:

- `perBankDelay_wooriIs3000_shinhanIs100_defaultIs100`
- `pause_swallowsInterrupt_restoresFlag_andDoesNotThrow` — the documented "pacing can never abort
  the scan" guarantee
- `zeroDelay_doesNotSleep`

### P1-14 · HTTP timeout / malformed-response behaviour is untested

`EfsProperties` (connect 3 s, read 20 s) and `EfsClientConfig` have no tests.
`RestClientEfsApiClientTest`'s 3 tests cover path selection, form encoding and unknown-key
rejection — not a timeout, not a 5xx, not a non-JSON body, and not an empty body (which throws
`IllegalStateException("Empty EFS response body")`, unasserted).

For a money path the unanswered question is: **what does the caller see when the gateway hangs for
21 seconds mid-transfer, and does the payment end up pending or failed?**

**Add** to `RestClientEfsApiClientTest` (MockRestServiceServer / a socket stub):

- `readTimeout_surfacesAsPendingNotFailed` — and assert which one the transfer services treat it as
- `http500_doesNotLeakEvVvOrApiKeyIntoTheException`
- `emptyBody_throwsIllegalState`
- `nonJsonBody_throws_withoutIncludingRawBody` (the code deliberately omits the body from the
  message because it may carry `EV`/`VV` — untested)
- `timeoutsAreBoundFromProperties`

### P1-15 · Stale concurrency javadoc on `DispatchKeyGenerator`

[DispatchKeyGenerator.java:15-22](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/service/DispatchKeyGenerator.java#L15-L22)
describes a `MAX+1` race whose `DataIntegrityViolationException` "the calling service catches and
surfaces as a retryable error". The method bodies no longer do `MAX+1` — both delegate to
`wb_get_next_val` global sequences, so the described race cannot occur, and no caller was found
that catches `DataIntegrityViolationException`.

**Action:** correct the javadoc, and add `service/DispatchKeyGeneratorTest` asserting both methods
funnel through `LegacyFunctionRepository` (the `project_wb_get_next_val_funnel` rule) and never
compute a key locally.

---

## P2 — Bulk

Existing: strong — 17 reception tests (split, SFTP failure, audit-before-call, per-split MST/data
rows), 12 callback tests (duplicate callback, sibling fail-stop, total mismatch, no-response),
9 validation tests, 15 `FixedLengthCodec` tests.

### P2-1 · `VietnameseText` has no test — and its two mapping strings are hand-paired

[VietnameseText.java](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/file/VietnameseText.java)
maps by **index** between two hand-copied literals (`TONED` / `PLAIN`). If they ever drift by one
character, every Vietnamese name in every bulk file silently maps to the wrong letter — no
exception, no failed record, just wrong recipient names at the bank.

**Add** `file/VietnameseTextTest`:

- `tonedAndPlainStringsAreSameLength` ← one line, catches the entire class of drift
- `stripsDiacritics_forRepresentativeNames`
- `passesThroughAsciiAndPunctuationUnchanged`
- `nullAndEmpty_returnEmptyString` (note: returns `""`, not `null` — untested contract)

### P2-2 · Tone-strip + byte-truncation composition

`BulkReceptionService` applies `VietnameseText.replaceTone(byteTruncate(addr, 180))` for
`RCPT_USE_ADDR`. Truncating **bytes** can split a multi-byte character mid-sequence; the order of
the two operations decides whether that is possible. No test covers the composition.

**Add:** `rcptUseAddr_at180ByteBoundary_neverSplitsAMultiByteChar` and
`rcptUseAddr_exactly180Bytes_isNotTruncated`.

### P2-3 · Split trailer totals are not reconciled against the original

`eachSplitFile_hasCorrectHeaderDataTrailerCount` checks each file in isolation. Nothing asserts that
the split files' **trailer counts and amounts sum back to the pre-split total** — the classic bulk
defect (a dropped or double-counted line at a partition edge).
`mismatched_totals_fail_the_file_and_do_not_execute_00302` covers the reception-result comparison,
not the build side.

**Add:** `splitFiles_trailerCountsAndAmountsSumToOriginal` (property-style, several counts around
the split limit).

### P2-4 · Channel-set derivation feeding `evaluateBulk`

`BulkReceptionService:529` maps each detail row's 1-char `ANT_DIV_CD` into the channel set passed to
the operating-hours gate. `BulkReceptionReceiveTest` covers the gate's three outcomes but never the
**input** — no test that a file with mixed `E`/`V`/`C` rows produces `{EBANK, VCB, CITAD}`, or that
an unmapped code is dropped (the `ETC_CNT`-is-ungated rule from P1-4).

**Add:** `presentChannels_derivedFromDetailAntDivCds`, `unmappedAntDivCd_isExcludedFromTheGate`.

### P2-5 · `BulkTransferValidator` edges

- `ccy == 1` boundary (the check is `ccy > 1`) — untested
- all-zero counts → pass — untested
- null `fundTp` — untested
- the documented **unreachable** legacy branch (`failChnlCnt>0`, byte-identical to `swiftOutCnt`)
  — no test pins that only `MSG_OVRS_ANT_DIV` can fire for overseas, which is what makes the
  omission safe

---

## P2 — Realtime

Existing: strong — 20 dispatch tests + 12 transfer tests covering both gates, the G2 atomic claim,
the full Woori 204 FX pre-step, `G999` pending, and VV-invalid-on-success.

### P2-6 · The G2 claim's atomicity is asserted only through a mock

`g2_claimLost_isNoOp_noTransfer` stubs "0 rows claimed". That proves the *caller* handles the
outcome; it does not prove the repository method is a conditional `UPDATE … WHERE PROC_STAT='9'`
rather than a read-then-write. The entire no-double-payment argument — and the stranded-recovery
design that leans on it — rests on that query.

**Add** a repository-level test on the update query (`@DataJpaTest` or a query-text assertion):
`claimQuery_isConditionalUpdateOnProcStat9` and `secondClaim_affectsZeroRows`.

### P2-7 · `VanApiKeyService` untested

Services assert `missing_api_key_propagates_b2_failure_without_calling_gateway` using a mocked
throw. The class that actually throws
([VanApiKeyService](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/van/client/VanApiKeyService.java))
has no test. Its whole purpose is to fail before crypto runs on a money-moving path with an
unprovisioned key.

**Add:** `missingRow_throwsIllegalState_withVanOrgCdInMessage`, `presentRow_returnsKey`,
`keyIsNeverLogged`.

---

## P2 — Recovery

Existing: 6 tests (disabled no-op, re-drive aged targets/files, result-check delegation, per-row
failure isolation).

### P2-8 · Re-drive idempotency is asserted only through mocks

`enabled_redrivesAgedPayTargets_viaDispatch` verifies `dispatch(...)` was called. Nothing verifies
that **two sweeps over the same row are harmless** — which is the entire justification for the
design ("the G2 atomic claim de-dupes"). See also P2-6.

**Add:** `twoConsecutiveSweeps_overSameRow_produceOneEffectiveDispatch`.

### P2-9 · The K9 scope decision is unprotected

Firm files stranded mid-step (`TB_FIRM_FILE_MST` step `0`/`1`) are **deliberately logged only, never
auto-driven** — re-driving a partially-processed bulk file safely needs the 01301/01302 state
machine, explicitly out of scope. No test asserts they are not driven, so a future change could
quietly enable it.

**Add:** `strandedFirmFilesAtStep0Or1_areLoggedNotRedriven`.

### P2-10 · Delegation-failure containment

`recoverOnce` wraps `resultCheckService.runOnce()` in `catch (Exception) → log.warn`. There is
`disabled_doesNotDelegateToResultCheck`, but no test that a **throwing** delegation still lets the
sweep return normally.

**Add:** `resultCheckDelegationThrows_sweepStillCompletes`.

---

## P3 — Largest untested class, and one probably-dead one

### P3-1 · `DispatchResultResolver` (268 LOC) — no test file

The biggest untested class in the package. It feeds the **Result pages** for the whole SCDUL_PYMN
family plus Overseas, and encodes real legacy semantics:

- `PROC_STAT` coalesces to `"3"` (failed) when null/blank — verbatim legacy
  `SCDUL_PYMN_SUMR_R006.xml` behaviour, so a missing status **displays as failed**
- API path vs File path selection on `payFileSeqNo` presence
- `RS_MSG` is always null on the File path (legacy parity)
- `rsltDttm = procDttm` on both paths
- lines spread across two `pay_tran_dt` values when a batch crosses the cut-off boundary
- Overseas keys by `OVRS_PYMN_DETL_NO` while joining on `PAY_TRAN_SEQ`
- `Collectors.toMap` merge `(a, b) -> a` — a duplicate `SEQ` silently keeps the first

**Add** `service/DispatchResultResolverTest` covering each bullet, especially
`nullProcStat_coalescesToFailed` and `linesSpanningTwoTranDts_areAllResolved`.

### P3-2 · `TransferDispatchExecutor` (398 LOC) appears to be unwired

Only `TransferDispatchExecutorRealtimeTest` (2 tests) constructs it. Grep found **no injection site
in `src/main`** — every reference is javadoc. It looks superseded by the four per-module executors
(`InternalTransfer` / `Vendor` / `Expense` / `Payroll`), each of which has its own tests.

**Do not write tests for it until this is resolved.** Confirm live-or-dead; if dead, delete it
rather than test it — a 398-line money-moving `@Service` that nothing calls is a hazard, and the
stale references in `TbPayTarget`/`TbPayFileMst`/`DispatchAutoReceiveRegistrar` javadoc point future
readers at it as if it were the active path.

### P3-3 · Low-value remainders

Listed for completeness; write only if they are cheap:

| Class | Note |
|---|---|
| `FirmBankingResultCd` | Constants only. `isSuccess` is a one-liner worth one test. |
| `EfsProperties` | Default endpoint map — one test that all 7 keys resolve to `/WGVN_*.jct`. |
| `DispatchCountRepository` / `DispatchCountRow` | Projection plumbing. |
| `OpsAlertAsyncConfig` | Thread-pool config. |

---

## Summary

| Area | Existing coverage | Verdict |
|---|---|---|
| OperatingHours | 17 tests, decision matrix complete | **Good** — gaps at the zone path (P1-1), boundaries (P1-2), null columns (P1-3), and the untested `fromStorageCode` input (P1-4) |
| Timezone | 1 boundary test, in one module | **Weak** — 7 classes hardcode ICT with no injectable clock (P1-5); zone agreement across write/read windows unverified (P1-6) |
| VCB | ~16 tests across validator, cut-off, 3 executors | **Good but uneven** — Payroll and Internal Transfer lack the file-path assertion (P1-8) |
| Realtime | 32 tests | **Strong** — the G2 claim's atomicity is mock-only (P2-6) |
| Bulk | 53 tests | **Strong** — `VietnameseText` untested (P2-1), split totals unreconciled (P2-3) |
| Cut-off | 30 tests | **Strong** — but the cross-gate invariant is unverified and configurably breakable (P0-2) |
| Retries | ~0 | **Absent by design** — the dropped legacy BATCH2 retry loop needs a PO decision and a pinning test (P1-12); `DispatchPacer` and gateway timeouts untested (P1-13, P1-14) |
| Recovery | 6 tests | **Adequate** — the disabled-by-default coexistence guard has zero tests (P0-1) |

**Three items warrant a decision, not just a test:**

1. **P1-3** — a schedule row with a null `BATCH_START_TM` currently dispatches; the documented
   legacy rule says fail. Needs verification against JEX data + a PO ruling.
2. **P1-12** — legacy's BATCH2 retry loop was dropped. Undocumented parity divergence.
3. **P3-2** — `TransferDispatchExecutor` has no caller. Confirm and delete, or wire and test.
