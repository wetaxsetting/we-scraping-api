# Payment Dispatch — Integration-Test Audit & Dead-Code Verification

**Date:** 2026-07-29 · **System:** Refactor (`wecms-api`) · **Branch:** `wecms-ui-fixes` (uncommitted working tree)
**Companions:** [../architecture/payment-dispatch-call-graph.md](../architecture/payment-dispatch-call-graph.md) ·
[missing-tests.md](missing-tests.md) ·
[../reports/dead-code-architecture-review.md](../reports/dead-code-architecture-review.md) ·
[../architecture/architecture-review.md](../architecture/architecture-review.md)

---

## 1. Executive summary

The dispatch package is **very heavily unit-tested and essentially untested at the integration
level**. 383 `@Test` methods cover 127 production classes, and 36 of the 40 dispatch test files are
pure Mockito unit tests. Repo-wide there is **exactly one real `@SpringBootTest`** (and it is not a
dispatch test), **zero `@DataJpaTest`**, and **zero Testcontainers** — the last is a deliberate
project constraint, not an omission (`CLAUDE.md`: *"Not currently in build.gradle … Testcontainers.
Add them in the sprint where the first feature actually needs them."*).

That distribution is not automatically wrong — most dispatch logic is decision logic, and Mockito
tests it well. But it leaves three junctions structurally unverifiable, and one of them hides a
confirmed defect:

> **H-4 confirmed, and worse than documented.** The bulk file rail's duplicate guard is a
> non-atomic read-then-check (`BulkReceptionService.receive:177-186`). The class Javadoc and
> `StrandedRecoveryService`'s Javadoc both claim the `TB_FIRM_FILE_MST` PK makes a re-run
> idempotent. **It does not, in the concurrent case.** The firm-file name is fully deterministic, so
> the PK *would* collide — but `firmFileMstRepository.save(mst)` is a plain `save()` and there is no
> query between it and the egress calls to trigger a Hibernate auto-flush, so the INSERT is deferred
> to commit. Commit happens **after** `sftpClient.upload(...)` **and after** the EFS 301 call. Two
> concurrent drivers therefore both upload the file and both issue 00301; the PK violation is
> detected afterwards. `StrandedRecoveryService` is a live second driver of exactly this path.
>
> **✅ FIXED 2026-07-29.** The `'9'→'6'` transition is now a single conditional UPDATE
> (`TbPayFileMstRepository.claimForReception`) committed in its own short transaction before any file
> is built, uploaded or dispatched — restoring the ordering legacy relies on
> (`FILE_DEPOSIT_TRANSFER_BATCH1` commits at `:696`, uploads at `:700`). 22 tests added, including a
> real two-thread race; with the claim disabled 4 of 7 race tests fail. Full design, legacy evidence
> and failure matrix: **[../architecture/bulk-dispatch-concurrency-fix.md](../architecture/bulk-dispatch-concurrency-fix.md)**.

Everything else the brief asked to verify came back clean or already-closed. Both parity decisions
resolved conclusively **against** the previous audit's suspicion:

- **Null schedule times — no divergence.** The columns are `NOT NULL` in the legacy JEX DDL, so the
  case is unreachable and legacy's `BATCH_START_TM == null` check is a row-absence test, exactly
  equivalent to the refactor's `Optional.empty() → NO_CRITERIA`.
- **BATCH2 retry — parity gap now closed.** Legacy's retry trigger set is reproduced exactly.
- **`TransferDispatchExecutor` — confirmed dead and already deleted**; deletion verified complete
  against the full reachability list, with two dangling javadoc links fixed here.

The previous audit ([missing-tests.md](missing-tests.md)) has been substantially overtaken by work
landed on 2026-07-28/29 — and **two of its findings were wrong**; both are corrected in §10.4.

**Verdict (2026-07-29, updated): `NOT PRODUCTION-READY` — but the P0 is now closed.** H-4 was fixed
the same day with an atomic pre-egress claim (§6.1); what remains gating a non-local deploy is the
`P1` H-2 scan and the outstanding coexistence sign-off, neither of which is a duplicate-money risk.
Local R&D is unaffected either way: every egress is flag-disabled by default and that is now
test-enforced.

## 2. Scope

Audited: `domain/payment/dispatch/**` (127 files), the five per-module `*DispatchExecutor` classes,
`domain/payment/internaltransfer/service/Cutoff*`, all four callback/scheduler/recovery/monitor
surfaces, `application*.yml` × 5, `build.gradle`, and the legacy baseline
`WECMS_BATCH DEV/source/WECMS_BATCH/WECMS_BATCH/src/batch/` (4 programs) + `consts/WCConst.java` +
`IDO.TB_BATCH_SCHEDULE_{INFO,DTL}_R001.xml` + migrations `V95`/`V96`.

Not audited: everything outside the dispatch path; the six non-dispatch `@Scheduled` classes
(covered by [../audits/scheduler-legacy-parity-audit.md](../audits/scheduler-legacy-parity-audit.md)).

**Working-tree note (material).** This reads the **uncommitted** tree: 114 changed paths, 48
untracked. `TransferDispatchExecutor` and its test are `D`; `AntDivChannel`, `common/lock/`,
`dispatch/monitor/`, `BulkExecutionRetry*`, `IpAllowList` are new. Counts from any document dated
2026-07-28 or earlier are stale.

## 3. Baseline (recalculated, not reused)

| Metric | Value |
|---|---|
| Dispatch production files / LOC | **127 / 12,789** |
| Dispatch test files | **40** |
| Dispatch `@Test` + `@ParameterizedTest` | **383** |
| Repo-wide test files | **273** |
| Repo-wide `@Test` + `@ParameterizedTest` | **2,155** |
| `compileJava` + `compileTestJava` | **exit 0** |
| Real `@SpringBootTest` repo-wide | **1** (`VasAccountIntegrationTest` — not dispatch) |
| `@DataJpaTest` / `@WebMvcTest` / Testcontainers | **0 / 0 / 0** |
| `ApplicationContextRunner` files | **6** (5 pre-existing + 1 added here) |
| Docker daemon available | **No** (`dockerDesktopLinuxEngine` pipe absent) |

> The prior audit's figures (118 files / 10,750 LOC / 27 test files / 204 tests) are stale, and its
> "Testcontainers 9 files" line was wrong — that grep matched **javadoc prose**, not a dependency.
> `build.gradle` contains no Testcontainers artifact.

### Runtime defaults (all four profiles verified)

Every dispatch flag defaults off and every cron to Spring's never-fire `-`:
`efs.enabled=false`, `sftp.enabled=false`, `alert.enabled=false`, `realtime-dispatch=false`,
`recovery.enabled=false`, `stuck-monitor.enabled=false`, `bulk-execution-retry.enabled=false`;
`result-check.cron`/`recovery.cron`/`stuck-monitor.cron`/`bulk-execution-retry.cron` = `-`.
Base `application.yml` declares no `wecms.dispatch` block at all, so an unprofiled run inherits the
in-code defaults, which are the safe ones. `callback.protection` is `ENFORCE` in sit/uat/prod and
`DISABLED` in local (deliberate, startup-logged); `allowed-ips` in sit/uat/prod is a bare
`${WECMS_CALLBACK_ALLOWED_IPS}` with no inline default, so a missing variable **aborts startup**
rather than failing open.

## 4. Existing integration-test inventory

Classified by what each test actually wires, not by filename.

| Class | Dispatch files | Tests | Verdict |
|---|---|---|---|
| unit (Mockito) | 36 | 344 | strong decision-logic coverage |
| component (servlet) | 1 — `CallbackIpWhitelistFilterTest` | 25 | real filter + `MockHttpServletRequest`/`MockFilterChain` |
| controller-slice | 1 — `BulkCallbackRoutingTest` | 2 | `MockMvcBuilders.standaloneSetup`, delegation only |
| external-contract-sim | 1 — `RestClientEfsApiClientTest` | 3 | `MockRestServiceServer` |
| wiring/context | 1 — `DispatchExternalClientWiringTest` *(added here)* | 10 | `ApplicationContextRunner` |
| repository integration | **0** | 0 | no DB available |
| service integration | **0** | 0 | — |
| end-to-end internal flow | **0** | 0 | — |

Dispatch-relevant wiring tests outside the package: `SchedulerConfigurationContractTest` (7),
`SchedulerCronAndLockIdentityTest` (10) — both real, both added 2026-07-29, both covering the
dispatch jobs' disabled-by-default and lock-identity contracts.

## 5. Flow-by-flow coverage matrix

Legend: ✅ verified · ⚠️ partial (asserted through mocks on both sides of the junction) · ❌ absent.

### Flow A — Realtime dispatch lifecycle

| Step / property | State | Evidence |
|---|---|---|
| approval gate → dispatch | ✅ | `*DispatchApprovalGateTest` × 4 |
| after-commit hand-off registered | ⚠️ | `RealtimeDispatchRegistrarTest` asserts registration; no real commit |
| operating-hours gate (3 outcomes) | ✅ | `RealtimeDispatchServiceTest` (20), `OperatingHoursServiceTest` (25) |
| **claim is a conditional atomic UPDATE** | ✅ *(added)* | `DispatchClaimQueryContractTest` — query text, `@Modifying`, `int` return, tenant scope (both rails since 2026-07-29) |
| **second claim affects zero rows** | ⚠️ | caller behaviour: `g2_claimLost_isNoOp_noTransfer` (stubbed count). True concurrency needs a DB |
| key generation via `wb_get_next_val` | ⚠️ | funnel asserted by inspection; `DispatchKeyGeneratorTest` still absent |
| EFS request construction + 204 pre-step | ✅ | `RealtimeTransferServiceTest` (12) |
| `G999` / transport uncertainty → pending | ✅ | `g999_returns_pending_and_does_no_synchronous_203`, `noResponse_returnsPending` |
| success/failure status persistence | ✅ | `DispatchResultTxServiceTest` (5) |
| **no partial commit of money state** | ⚠️ | pre-conditions only — `REQUIRES_NEW` rollback is untestable without a tx manager (stated in that test's own Javadoc) |
| result-page resolution | ❌ | `DispatchResultResolver` (268 LOC) still has **no test** |

### Flow B — Callback lifecycle

| Step / property | State | Evidence |
|---|---|---|
| allowed IP passes / disallowed → 403 | ✅ | `CallbackIpWhitelistFilterTest` (25) |
| empty allow-list **fails closed** | ✅ | H-1 fixed: `protection=ENFORCE` default rejects |
| `trustForwardedFor=false` ignores spoofed XFF | ✅ | same |
| `trustForwardedFor=true` → first trimmed hop, **and only from a trusted proxy** | ✅ | `trustedProxies` gate added |
| request validation | ✅ | `BulkReceptionResultServiceTest`, `BulkExecutionResultServiceTest2` |
| duplicate callback idempotent | ✅ | `duplicate_callback_does_not_double_execute_00302` |
| malformed callback does not partially update | ✅ | `malformedResultFile_throws_andDoesNotAdvanceState` |
| audit row survives parse rollback | ⚠️ | `DispatchApiTranAuditWriter` is `REQUIRES_NEW`; asserted by annotation, not execution |
| 00302-failure ops alert | ✅ | `BulkExecutionAlertParityTest` (15) |
| secrets never logged | ⚠️ | `RestClientEfsApiClientTest` asserts the RC-only log line; no repo-wide log assertion |

### Flow C — Bulk file lifecycle

| Step / property | State | Evidence |
|---|---|---|
| **default profile uses only the mock SFTP client** | ✅ *(added)* | `DispatchExternalClientWiringTest` |
| **live/mock SFTP + EFS beans mutually exclusive** | ✅ *(added)* | same — all four flag combinations |
| **no external call possible by default** | ✅ *(added)* | same — offline stand-ins throw on every operation |
| operating-hours evaluation | ✅ | `BulkReceptionReceiveTest` (17) |
| split calculation + per-file H/D/T | ✅ | `eachSplitFile_hasCorrectHeaderDataTrailerCount`, `split_partitionsBySeqValueRange_*` |
| **split totals reconcile to the original batch** | ❌ | still no test that per-file trailer counts/amounts **sum** to the pre-split total |
| SFTP failure → both rows FAIL, no API call | ✅ | `sftpUploadFailure_marksBothFailed_andDoesNotCallApi` |
| 01301 → 00302 → 01302 chain | ✅ | `BulkReceptionResultServiceTest` (12) |
| duplicate callbacks harmless | ✅ | see Flow B |
| sibling failure stops execution | ✅ | `laterSplitFailure_stopsBatch_earlierFileStandsCommitted`, `siblingFailStop_*` |
| total mismatch fails safely | ✅ | `mismatched_totals_fail_the_file_and_do_not_execute_00302` |
| mixed `ANT_DIV_CD` → correct channel set | ⚠️ | `AntDivChannelTest` (15) covers the mapping; the **derivation** of `presentChannels` from detail rows is still unasserted |
| unknown channel code follows legacy rule | ✅ | `AntDivChannelTest` — unknown → `null` → ungated (`ETC_CNT`) |
| **Vietnamese tone-strip + byte truncation cannot split a multibyte char** | ❌ | `VietnameseText` still has **no test**; the composition with the 180-byte truncation is unasserted |
| **duplicate `receive()` cannot double-dispatch** | ✅ *(fixed 2026-07-29)* | atomic pre-egress claim + real two-thread race: `BulkReceptionConcurrentClaimTest` (7), `BulkDispatchClaimTxServiceTest` (7). Was **H-4** — see §6.1 |

### Flow D — ResultCheck and recovery

| Step / property | State | Evidence |
|---|---|---|
| both jobs disabled by default; crons resolve to `-` | ✅ | `SchedulerConfigurationContractTest`, `SchedulerCronAndLockIdentityTest` |
| recovery needs cron **and** `enabled=true` | ✅ | `StrandedRecoveryServiceTest.disabled_isNoOp` + the cron test |
| default profiles cannot arm either sweep | ✅ | both above, across all four profiles |
| scheduler zone is `Asia/Ho_Chi_Minh` | ✅ | `SchedulerCronAndLockIdentityTest` |
| lock names + `lockAtMostFor` present, distinct | ✅ | `dispatchJobs_useDistinctLockIdentitiesExceptTheSharedResultCheck` |
| two nodes cannot run the same sweep | ⚠️ | ShedLock annotation asserted; no two-node test (needs a DB) |
| delay/grace boundary strictness | ✅ | `ResultCheckSelectionBoundaryTest` (15), `ResultCheckBoundedScanTest` (18) |
| **stored `PROC_DTTM` and cutoff share a timezone** | ⚠️ | boundary tests use literals; no test constructs the row through the real writer |
| two sweeps → one effective dispatch | ⚠️ | API rail: relies on the atomic claim (now contract-pinned). File rail: **not true** (H-4) |
| step-0/1 firm files are observe-only, not re-driven | ✅ | `StuckFirmFileMonitorTest` (9), `StuckPayFileMonitorServiceTest` (21) |
| ResultCheck delegation failure does not abort the sweep | ✅ | `StrandedRecoveryResultCheckLockTest` (4) |

### Flow E — Request-time vs dispatch-time cut-off consistency

| Step / property | State | Evidence |
|---|---|---|
| default zones agree | ⚠️ | both are ICT by construction; no test asserts the *equality* across the two classes |
| accepted request not later rejected by dispatch | ❌ | still no cross-gate test |
| exact start time is open | ✅ | `windowBoundaries_startIsInclusive_endIsInclusive` |
| exact end time follows legacy | ✅ | same + `windowBoundaries_oneMinuteEitherSide_ofBothEdges` |
| one second past end → CUTOFF | ✅ | `exeDiv("001500","235500","235959") == E` |
| VCB aliases + rails resolve | ✅ | `AntDivChannelTest`, `vcb_hasNoInfoRow_bulkAndRealtime_areNoCriteria`, `CutoffValidatorTest` |
| VCB absent SWIFT rail is safe | ⚠️ | `CutoffConfigProviderTest` covers required-rail validation generically; no VCB-SWIFT-specific null-rail test |
| **invalid zone fails at bean creation, not per payment** | ✅ *(fixed + tested)* | §8.1 |
| `Asia/Seoul` override explicitly tested and documented | ✅ | `sameStoredWindow_yieldsDifferentDecisionsInIctVsKst`, `operatingHoursZone_defaultsToIct_andIsOverridable`, `bothSupportedZones_passValidation` |

**Can the two gates contradict each other?** Yes — but only under an explicit opt-in. Both default to
ICT, so out of the box they agree. Setting `wecms.dispatch.operating-hours.zone=Asia/Seoul` (an
offered legacy-parity knob) makes `OperatingHoursService` evaluate 2 h ahead of `CutoffValidator`,
so a payment accepted at 15:00 ICT is CUTOFF-failed at dispatch. This is now **documented and
pinned by a test** rather than contradicted by a Javadoc claim, and is classified **P2 (configuration
hazard)** rather than a production defect, because no shipped profile sets it.

## 6. Findings

### 6.1 `P0` · H-4 — the bulk file rail can double-dispatch; the documented PK guard fires too late

> **STATUS: ✅ FIXED 2026-07-29.** An atomic conditional claim
> (`TbPayFileMstRepository.claimForReception`) now takes the `'9'→'6'` transition and **commits before
> any egress**, restoring the transaction ordering legacy depends on
> (`FILE_DEPOSIT_TRANSFER_BATCH1`: insert `:559` → commit `:696` → upload `:700` → 00301 `:724`).
> Race-verified: with the claim disabled 4 of 7 threads-level tests fail. The analysis below is kept as
> the record of what was wrong. Design, full legacy evidence, state machine, failure matrix and
> residual `P2`:
> **[../architecture/bulk-dispatch-concurrency-fix.md](../architecture/bulk-dispatch-concurrency-fix.md)**.

**Confirmed (pre-fix).** `BulkReceptionService.receive` gated on a read-then-check:

```java
TbPayFileMst pay = payFileMstRepository.findByTranDtAndBizKeyAndFileSeqNo(...)  // :177
if (!ProcStat.WAIT.code().equals(pay.getProcStat())) { return; }                // :182-186
```

Two drivers can both pass. The stated mitigation is the `TB_FIRM_FILE_MST` PK — and the name *is*
fully deterministic (`"S_" + vanOrgCd + "_" + fileType + "_" + fileSeq + "_" + tranDt`, `:360`) over
a PK of `(tranDt, bizKey, fileSeqNo, firmFileNm)`, so a duplicate would collide. **But the collision
is detected too late:**

| Order inside the per-split `REQUIRES_NEW` transaction | Line |
|---|---|
| `firmFileMstRepository.save(mst)` — plain `save()`, INSERT deferred | `:381` |
| `firmFileDataRepository.save(fd)` × N — also deferred (no query ⇒ no auto-flush) | `:432` |
| `sftpClient.upload(localPath, remotePath)` — **egress** | `:470` |
| `issueReception(...)` → EFS 301 — **egress** | `:485` |
| commit — *this* is where the PK violation surfaces | — |

Hibernate's `FlushModeType.AUTO` flushes before **query** execution, not before `persist`/`merge`;
there is no query in that window. So both drivers upload the bulk file to the bank's SFTP server and
both issue the 00301 reception instruction; one then fails at commit.

**Reachability is concrete, not theoretical.** `StrandedRecoveryService.recoverPayFiles` re-drives
`bulkReceptionService.receive(...)` for any pay-file still at `PROC_STAT='9'` older than the grace
window (default 30 min) — and a file whose SFTP upload is merely slow is exactly such a row. Both
`BulkReceptionService`'s and `StrandedRecoveryService`'s Javadoc assert the opposite safety property
("the WAIT gate + firm-file PK make a re-run idempotent"), so the invariant is documented but not
held. The API rail got this right (`claimForDispatch`); the file rail did not.

**Not fixed here** — deliberately. An atomic claim on the file rail is a money-path behaviour change
that has to decide the claim's status transition, its interaction with the existing `failBoth`
paths, and its legacy comparison. That needs PO / Mr. Kang sign-off, not an unilateral edit under an
audit. **Recommended fix:** add
`TbPayFileMstRepository.claimForReception(bizKey, tranDt, fileSeqNo, procDttm)` as a conditional
`UPDATE … SET proc_stat='6' … WHERE proc_stat='9'` returning `int`, call it in place of the
read-then-check, and treat `0` as the existing no-op; then extend
`DispatchClaimQueryContractTest` to cover it. Currently mitigated only by both crons being off.

### 6.2 `P1` · Zero repository/service/end-to-end integration tests on the dispatch path

Three junctions cannot be verified by a Mockito test of either side: the
`afterCommit` hand-off, real transaction rollback of `REQUIRES_NEW` boundaries, and concurrent
claim behaviour. Each is currently asserted through mocks on both sides, which verifies the
*contract as written* and cannot catch the contract being wrong. `DispatchResultTxServiceTest`'s own
Javadoc states this limitation explicitly.

This is a *known, accepted* project constraint (no Testcontainers by policy), not an oversight — but
it is the reason H-4 survived 383 tests, and it should be recorded as accepted risk rather than
silently carried. The cheapest lift that would have caught H-4 is a single `@DataJpaTest`-class
concurrency test over the two claim paths.

### 6.3 `P2` · Cross-gate zone divergence is configurable

See Flow E. Both default to ICT; the `Asia/Seoul` knob breaks the invariant its own Javadoc claims
is impossible. Now documented and test-pinned. Recommend either removing the knob or adding a
startup warning when the two zones differ.

### 6.4 `P2` · Bulk-path gaps carried forward from the previous audit (still open)

- Split trailer totals never reconciled against the pre-split batch total.
- `VietnameseText` has no test at all; its `TONED`/`PLAIN` strings are index-paired by hand, and a
  one-character drift would silently mis-map every Vietnamese name with no exception.
- `presentChannels` derivation from detail rows is unasserted (the mapping function now is).
- `DispatchResultResolver` (268 LOC, feeds every Result page) has no test.

### 6.5 `P3` · Two dangling javadoc `{@link}`s to the deleted executor — **fixed here** (§8.2)

## 7. Tests added

All new; 43 tests, all green.

| Class | Tests | What it pins |
|---|---|---|
| `dispatch/van/client/DispatchExternalClientWiringTest` | 10 | Default profile selects the offline EFS/SFTP clients only; enabled selects the live ones only; the pairs are mutually exclusive in **all four** flag combinations; offline stand-ins **throw** on every operation (so "mock active" cannot be mistaken for "call succeeded"); a **malformed** flag registers *neither* client so the app refuses to boot |
| `dispatch/repository/DispatchClaimQueryContractTest` | 4 | The G2 claim is `@Modifying`, returns `int`, is a single conditional `UPDATE … WHERE t.procStat='9'` that transitions to `'6'`, is fully key-qualified and tenant-scoped with no `LIKE`/`%` |
| `dispatch/entity/BatchScheduleNotNullContractTest` | 4 | `BATCH_START_TM`/`BATCH_END_TM` stay `nullable=false` on both schedule entities — the precondition that makes Decision A a non-divergence |
| `OperatingHoursServiceTest` *(+2 to existing)* | 25 | `invalidZone_failsFastAtBeanCreation_notOncePerPayment`, `bothSupportedZones_passValidation` |

Notable behaviour discovered while writing them: a **malformed** boolean flag
(`WECMS_EFS_ENABLED=yes`) satisfies neither `@ConditionalOnProperty` branch, so **no** client bean is
registered and startup fails. That is fail-safe and is now pinned; my first draft asserted a fallback
to the mock and correctly failed.

### 7.1 Tests added 2026-07-30 (the four carried §11 items)

All new; no production code changed by this pass.

| Class | What it pins | Legacy read first |
|---|---|---|
| `dispatch/service/BulkSplitTotalReconciliationTest` | Σ per-file trailer counts/amounts == the pre-split batch total, and the same for Σ `TB_FIRM_FILE_MST.REQ_CNT`/`REQ_AMT`, Σ `TB_FIRM_BAT_API_TRAN.BAT_CNT`/`BAT_AMT` and Σ 00301 request `data_cnt`/`data_amt`+`data_dcm_amt` — across 10 row/limit shapes spanning the split boundary (below, at, one over, exact multiple, limit 1, no-split). Every row emitted **exactly once** in both the D records and `TB_FIRM_FILE_DATA` (partition-edge drop/duplicate guard). Plus cent-level `BigDecimal` precision, the null-`TRAN_AMT` counted-but-adds-zero rule, and the sparse-`SEQ` non-invariant | `FILE_DEPOSIT_TRANSFER_BATCH1:463-467` (`calculateBatchDivision:932-961`), `:470-471/:506-507/:525-526` (`START_SEQ`/`END_SEQ`), `:552-555` (MST from R002), `:594-687` (`tempSeq`/`tempAmt` → trailer); `IDO.TB_PAY_FILE_DATA_R001.xml` + `_R002.xml` (`SEQ BETWEEN`) |
| `dispatch/service/DispatchResultResolverTest` | Route selection on both rails; `PROC_STAT` coalesce; `RS_MSG` API-only; `rsltDttm`; lines spanning two `PAY_TRAN_DT` values; the plain-string `SEQ` CAST join; duplicate-`SEQ` first-wins; Overseas keyed by `OVRS_PYMN_DETL_NO` while joining on `PAY_TRAN_SEQ`; empty/defensive inputs. **6 divergences recorded, none changed** — §11.1 | `IDO.SCDUL_PYMN_SUMR_R006.xml`, `IDO.OVRS_PYMN_SUMR_R007.xml`, `PymnHelper.pymn_0002_002_r001:3050-3067` |
| `dispatch/service/RealtimeTransportFailureNoResendTest` | The 202 leg does not resend: exactly one `EFS_202` send on a null envelope / `G999` / unverifiable `VV`, never a 203 or 204, no internal retry; the dispatch seam attempts one transfer and parks both rows at `'6'`; a parked `'6'` is not re-dispatchable (read gate) and a lost claim stops a second 202 (atomic gate); the recovery sweep selects `'9'` only — with a positive `'9'`-is-re-driven counter-test so that cannot pass vacuously | `NAPAS_REAL_TIME_BATCH:567-589` (`무응답 경우 다음으로` → `TB_PAY_TARGET_U001 = PROC_ST_REQ`, `sleep`, `continue`; **no** `TB_FIRM_TRAN_U004` in that branch) |
| `dispatch/repository/DispatchScanTenantScopeContractTest` *(extended)* | `findStrandedBulkExecutionFiles` joins the pinned sweep list (it was a live scheduled sweep outside the contract); three new tests pin `findResultCheckCandidates` — bounded, ordered `tranDt`/`tranTm`/`tranSeq` ASC with no `DESC`, `tranDt IN` as a **SQL** predicate (the N-1 regression), exact `tranTp`+`procStat` | `IDO.TB_FIRM_TRAN_R001.xml` (batch-side copy) |

Full query-by-query re-verification record, including the `Q-1` defect it surfaced:
[../audits/dispatch-repository-query-reverification.md](../audits/dispatch-repository-query-reverification.md).

## 8. Production defects fixed

### 8.1 Invalid operating-hours zone failed once per payment instead of at startup

- **Issue:** `wecms.dispatch.operating-hours.zone` was resolved by `ZoneId.of(...)` inside
  `nowHms()`, i.e. on every gate evaluation.
- **Root cause:** the property was read per-invocation and never validated at bean creation.
- **Why it happened:** the field carries an in-code default, so the *unset* case was safe and was
  documented as such ("an unset property can never abort startup"); the *misspelled* case was never
  considered.
- **Legacy behaviour:** n/a — legacy evaluated `EXE_DIV` in SQL using the DB server clock and had no
  zone property. This is refactor-only configuration surface.
- **Refactor behaviour before fix:** `Asia/Hochiminh` (missing underscores) threw
  `DateTimeException` from the operating-hours gate on **every** dispatch. Both dispatch registrars
  catch and log per-row failures so one bad row cannot block siblings — so the payment silently
  stayed at `PROC_STAT='9'` with only a log line. A configuration typo presented as "payments never
  dispatch", indefinitely.
- **Implemented solution:** a `@PostConstruct validateOperatingHoursZone()` that resolves the zone
  once and rethrows as `IllegalStateException` naming the property and the bad value. The zone is
  deliberately **not** cached, so `nowHms()` still reads the field and the existing override test
  (which sets the field reflectively) keeps working; `ZoneId.of` is a cached map lookup, not a parse.
  No behavioural change for any valid value.
- **Verification:** `invalidZone_failsFastAtBeanCreation_notOncePerPayment` (asserts the exception
  type, message content and `DateTimeException` cause) and `bothSupportedZones_passValidation`
  (`Asia/Ho_Chi_Minh` + `Asia/Seoul`). Full `OperatingHoursServiceTest` 25/25 green.

### 8.2 Two dangling javadoc `{@link}` references to the deleted `TransferDispatchExecutor`

- **Issue:** `TbPayTarget:18-19` and `TbPayFileMst:18-19` carried
  `{@link com.kosign.wecms.domain.payment.dispatch.service.TransferDispatchExecutor}`.
- **Root cause:** the class was deleted 2026-07-28; five other `{@link}`s were repaired, these two
  were missed (the operating-hours-flow doc claims all links were repaired — that claim was
  inaccurate for these two).
- **Why it happened:** the references are split across two source lines, so a single-line grep for
  `{@link …TransferDispatchExecutor}` does not match them.
- **Legacy behaviour:** n/a — documentation only.
- **Refactor behaviour before fix:** `javac` ignores unresolvable `{@link}`, so compilation passed;
  a `javadoc` build would fail on an unresolvable reference, and readers were pointed at a deleted
  class as if it were the live path.
- **Implemented solution:** both retargeted to name the five active executors as `{@code}`, with a
  one-line note that the shared executor was deleted and a pointer to the dead-code review.
- **Verification:** `compileJava` exit 0; `grep -rn "TransferDispatchExecutor" src/` now returns
  only `{@code}`/comment prose, no `{@link}`.

**No other production code was changed.** In particular H-4 (§6.1) was **not** fixed — see the
rationale there.

## 9. Dead-code conclusion

### `TransferDispatchExecutor` — **DELETED** (already deleted in the working tree; deletion verified correct and complete)

Checked against the brief's full reachability list — constructor/field/setter injection, `getBean`,
bean-name lookup, reflection, service loaders, registrars, strategy maps, collection injection,
`@Qualifier`, `@Primary`, `@Bean` factories, XML, SpEL, `@Scheduled`, event/async listeners, callback
registries, test-only usage, javadoc-only references, package scanning, indirect
interface/superclass calls, reachable REST paths, and legacy migration adapters. Every mechanism came
back negative; the per-mechanism evidence table is in
[../architecture/payment-dispatch-call-graph.md](../architecture/payment-dispatch-call-graph.md) §4.

Classification: **(5) completely dead.** All five modules inject their own executor (§1 of the call
graph), and its removal deleted two *wrong* behaviours rather than losing any
(`BANK_PRTN_ID_PLACEHOLDER = "DEFAULT"`; a `resolveAntDivCd` that defaulted blank codes to `INTER`).

Post-deletion proof: `compileJava` + `compileTestJava` exit 0; no production reference remains; the
two dangling javadoc links are fixed (§8.2); the five active executors cover all supported
products/rails; full suite green (§12).

## 10. Legacy-parity decisions

### 10.1 Decision A — null schedule times · **RESOLVED: no divergence, case unreachable**

Legacy fails closed on a null bound:

```java
// NAPAS_REAL_TIME_BATCH.java:125-130   ("대상 은행 존재여부 확인" — check the target bank exists)
if (idoOutBSIR001.get("BATCH_START_TM") == null) { exeErrYN = true; exeYN = false; }
// → TB_PAY_TARGET.PROC_STAT = WCConst.PROC_ST_FAIL ('3') + "수행 기준 없음" alert
```

The refactor's `exeDiv` null-guards both bounds and falls through to `Y` → `DISPATCH`, which reads
like a divergence. It is not:

- **`V96__tb_batch_schedule_info.sql`**: `batch_start_tm bpchar(6) NOT NULL`, `batch_end_tm bpchar(6) NOT NULL`
- **`V95__tb_batch_schedule_dtl.sql`**: identical
- Entities declare `nullable = false` on all four columns
- DDL is copied verbatim from the legacy JEX DDL, which is the schema source of truth

A present row therefore cannot carry a null bound. Legacy's check is a **row-absence** test — the JEX
IDO layer returns a `JexData` whose columns read null when zero rows matched, and legacy has no other
way to distinguish "no schedule configured". The refactor expresses the same thing as
`Optional.empty() → NO_CRITERIA`, which produces legacy's fail-closed outcome. `exeDiv`'s null-guards
are unreachable defensive code.

(The SQL agrees and is irrelevant: `CASE WHEN BATCH_START_TM > TO_CHAR(NOW(),'HH24MISS')` would yield
`EXE_DIV='Y'` for a null bound because `NULL > x` is `NULL` — but legacy's Java null-check
short-circuits before `EXE_DIV` is read.)

**Classification: obsolete legacy behaviour / equivalent.** No code change. Pinned by
`BatchScheduleNotNullContractTest` so relaxing the mapping fails the build and points at this
analysis. **No PO/DBA decision required.**

### 10.2 Decision B — dropped BATCH2 retry loop · **RESOLVED: parity gap closed 2026-07-29**

Legacy `FILE_DEPOSIT_TRANSFER_BATCH2.java` (337 LOC, cron every 60 s), read end-to-end:

| Dimension | Legacy | Refactor (`BulkExecutionRetryService`) |
|---|---|---|
| Selection | `TB_FIRM_FILE_MST_R001` `TRAN_DT`=today, `PROC_STEP='1'`, `PROC_STAT='6'`; `executeList`, **no LIMIT** | same predicate + grace window, ordered, batch-capped (`findBulkExecutionRetryCandidates`) |
| Retry trigger | empty/null HTTP body (`:201-203`) **or** `RC ∈ {BT01, G999, C999}` (`:231-233`, *"무응답일 경우"*) → `continue` | `FirmBankingResultCd.isBulkExecutionRetryable` = `{BT01, G999, C999}` + null/blank → **exact match** |
| Max attempts | **unbounded** within the same `TRAN_DT` (date rollover ends it); no attempt counter | bounded `max-attempts` (default 3), counted from `TB_FIRM_BAT_API_TRAN` 00302 rows |
| Delay / pacing | the 60 s cron interval; no per-request sleep in BATCH2 | cron interval + `grace-seconds` (default 120) |
| Terminal success | `RC ∈ {0000, BT00}` → `PROC_STEP='2'` | same (`isBulkExecutionSuccess`) |
| Terminal failure | any other RC → ops alert + `PROC_STEP='2'`, `PROC_STAT='3'` + `TB_PAY_FILE_MST` FAIL | same |
| Exhaustion | n/a (never exhausts) | alert, **leave at `PROC_STEP='1'`** — marking FAIL would wrongly imply the bank rejected the batch |
| Can retry re-send money? | **Yes** — 00302 is the execution instruction; legacy re-sends whenever the response was absent/uncertain | same, bounded |
| Gateway idempotency | not provable from this repository | unchanged |

**Classification: intentional redesign, now implemented, with documented deliberate divergences**
(bounded attempts, grace window, batch cap, exhaustion semantics). The parity *gap* that existed at
the time of the previous audit is closed. **Residual condition — RESTATED 2026-07-29:** *not* the
coexistence question (DEV/SIT are DB-separated; UAT is an **Ops** DB-target check). The sweep must stay
OFF **everywhere** because it is the K9 **Option A** periodic sweep Mr. Kang was offered and **declined**
in favour of *"no periodic sweep; monitoring + manual"*.
**[CONFIRM: Mr. Kang — reopen K9]** → [legacy-batch2-ownership-verification.md](../audits/legacy-batch2-ownership-verification.md).
Both gates (`bulk-execution-retry.enabled=false`, cron `-`) are verified off in all four profiles.

The requested pinning test for the 202 leg (*"transport failure does not resend automatically"*)
remains **absent** — the 203 leg has
`ResultCheckPollTest.transportFailure_leavesOriginalUntouched_but203RowRecordedFail`, the 202 leg has
no equivalent. Carried to §11.

### 10.3 Decision C — `TransferDispatchExecutor` · **RESOLVED: DELETED** (§9)

### 10.4 Corrections to the previous audit ([missing-tests.md](missing-tests.md))

Two of its findings were **wrong** and are retracted:

- **P1-8 was factually incorrect.** It recommended
  `payrollVcbVnd_routesToFilePath_notApi`. Payroll **rejects VCB VND outright** —
  `WBE_PAYROLL_VCB_VND_NOT_SUPPORTED` ([PayrollDispatchExecutor:213-214](../../src/main/java/com/kosign/wecms/domain/payment/payrollpayment/service/PayrollDispatchExecutor.java#L213-L214), legacy `:179-182`) — and has **no API rail at all** (it never injects
  `RealtimeDispatchRegistrar`), so the recommended test would have asserted behaviour that must not
  exist. Both guards are tested (`vcbVnd_blocked`, `vcbUsdOtherBank_blocked`).
- **The Testcontainers baseline was wrong** — see §3.

Its P1-3 (null schedule times) is resolved as a **non-issue** (§10.1), not a defect. Everything else
it raised is either closed by the 2026-07-28/29 work or carried in §6.4/§11.

## 11. Remaining blockers

### Code blockers

| # | Item | Severity |
|---|---|---|
| 1 | ~~**H-4** — atomic claim for the bulk file rail~~ **CLOSED 2026-07-29** — `claimForReception` implemented, 22 tests, race-verified. Residual `P2`: `buildAndDispatchFile` still holds one transaction across the SFTP upload + 301 call, where legacy commits first (`:696`→`:700`); tracked in [../architecture/bulk-dispatch-concurrency-fix.md](../architecture/bulk-dispatch-concurrency-fix.md) §16 | ~~`P0`~~ → `P2` |
| 2 | ~~**H-2** — cross-tenant unbounded scan~~ **RESOLVED 2026-07-29.** Split into two findings: the *cross-tenant* half is **NOT a defect** (legacy-verified — all three legacy pickup IDOs select across tenants, `BIZ_KEY` is an output column; tenant safety lives in the mutations, all of which carry `bizKey`). The *unbounded* half **was** real and is now fixed on every rail — two live consumers were still calling the unbounded finder despite its Javadoc claiming otherwise. See [../architecture/payment-dispatch-tenant-isolation.md](../architecture/payment-dispatch-tenant-isolation.md) | ~~`P1`~~ → **closed** |
| 3 | No repository/service/e2e integration tests (§6.2) — **still open, and now the only remaining P1.** Escalated with four costed options: [payment-dispatch-integration-tests.md](payment-dispatch-integration-tests.md) | `P1` **BLOCKED (PO)** |
| 4 | ~~`VietnameseText`~~ **CLOSED 2026-07-29 — NOT A DEFECT** (legacy-verified byte-for-byte; 14 tests). ~~split-total reconciliation~~ **CLOSED 2026-07-30** — `BulkSplitTotalReconciliationTest`, legacy `FILE_DEPOSIT_TRANSFER_BATCH1:463-686` + `IDO.TB_PAY_FILE_DATA_R001/_R002` traced; Σ per-file trailer / MST / 00301-audit / 00301-request counts + amounts all reconcile to the pre-split total across 10 row/limit shapes, every row emitted exactly once, cent-level precision, null-amount and sparse-`SEQ` cases pinned. ~~`DispatchResultResolver`~~ **CLOSED 2026-07-30** — `DispatchResultResolverTest` (41 tests), legacy `IDO.SCDUL_PYMN_SUMR_R006` + `IDO.OVRS_PYMN_SUMR_R007` traced field by field; **6 divergences recorded, none changed** (see §11.1). Remaining in this row: `presentChannels` derivation | `P2` |
| 5 | ~~202-leg "transport failure does not resend" pinning test~~ **CLOSED 2026-07-30** — `RealtimeTransportFailureNoResendTest`, legacy `NAPAS_REAL_TIME_BATCH:567-589` (`무응답 경우 다음으로` → `TB_PAY_TARGET_U001 = '6'`, `sleep`, `continue`) traced. Pins all three layers: the transport leg sends the 202 **exactly once** on a null envelope / `G999` / unverifiable `VV` and never a 203 or 204; the dispatch seam attempts one transfer and parks both rows at `'6'`; and no sweep re-drives a parked `'6'` (the recovery scan is `ProcStat.WAIT` only, with the positive `'9'`-is-re-driven counter-test so the assertion cannot pass vacuously) | `P2` |
| 6 | ~~Cross-gate zone decision~~ **DECIDED 2026-07-29: `SUPPORTED OVERRIDE`** — two accepted values, startup-validated, default = business zone. Two consistency tests remain outstanding. See [../architecture/operating-hours-timezone-decision.md](../architecture/operating-hours-timezone-decision.md) | `P2` |
| 7 | Bulk transaction-boundary parity — legacy order proven, **blocked on a state-model decision** (no `UNCERTAIN` state exists and one may not be added without PO approval). See [../architecture/bulk-file-transaction-boundary.md](../architecture/bulk-file-transaction-boundary.md) | `P2` **DECISION REQUIRED** |

### 11.1 `DispatchResultResolver` — legacy trace, 2026-07-30

Writing the resolver's first tests required reading its two legacy queries end to end. Six divergences
from them fell out.

> **Updated 2026-07-30.** **DRR-1 and DRR-2 are FIXED** (see the two rows below and §11.2).
> **DRR-3, DRR-4, DRR-5 and DRR-6 are deliberately NOT fixed** and carried as four separate
> decisions — §11.3. They remain pinned *as current behaviour* in `DispatchResultResolverTest`, each
> assertion naming the legacy value it differs from, so a future fix trips an explained test rather
> than an anonymous one.

Legacy sources: `WECMS DEV/WECMS_ETC/xml/service/IDO/IDO.SCDUL_PYMN_SUMR_R006.xml`
(← `PymnHelper.pymn_0002_002_r001:3050-3067` ← `pymn_0010_002_r001_act.jsp`) and
`IDO.OVRS_PYMN_SUMR_R007.xml` (← `pymn_0008_002_r001_act.jsp:186`).

| # | Field | Legacy | Refactor | Assessment |
|---|---|---|---|---|
| **DRR-1** ✅ **FIXED** | `proc_dttm` (both paths) | the **master request** datetime — `SPS.TRNF_DTM` (R006:89) / `SPS.TRNF_DT` (R007:75) — a *different column* from `RSLT_DTTM` (R006:96-100 / R007:77) | was the dispatch row's `PROC_DTTM`, so `proc_dttm == rslt_dttm` always | each field now projects its own legacy column. §11.2 |
| **DRR-2** ✅ **FIXED** | Overseas `rs_msg` | `TPFM.RS_MSG` (R007:80) | was hardcoded `null` | now sourced from the pay-file master; blank normalised to null. §11.2 |
| **DRR-3** | Overseas `proc_stat` | `TPFM.PROC_STAT`, **no** `COALESCE(…,'3')` (unlike R006) | R006's coalesce applied on the overseas path too, so a *blank* status becomes `'3'` | narrow — dispatch writers always set a status |
| **DRR-4** | route granularity | decided **per line** by an `EXISTS` over `TB_PAY_FILE_DATA` | decided once, from `master.payFileSeqNo != null` | agree for every batch whose lines all have a matching `TB_PAY_FILE_DATA` row — which is what the executors write |
| **DRR-5** | `rs_cd` blank form | `TRIM(COALESCE(RS_CD,''))` → `""` | `null` | **not observable** — `countByStatus` and the frontend `deriveResultStatus` treat blank uniformly |
| **DRR-6** | File-path `proc_stat` coalesce | `COALESCE(TPFM.PROC_STAT,'3')` → `'3'` | `null` for a stored **NULL**: `mstOpt.map(::getProcStat).map(::coalesce)` short-circuits on the first `map`, so only a *blank* is coalesced | a present `TB_PAY_FILE_MST` row with `PROC_STAT IS NULL` buckets as *in progress* where legacy says *failed*. The API path is unaffected (it calls `coalesceProcStat` directly) |

### 11.2 DRR-1 + DRR-2 — applied 2026-07-30

**DRR-1.** `DispatchResultResolver` now projects the two datetimes from their own legacy columns on all
three paths:

| Path | `procDttm` | `rsltDttm` |
|---|---|---|
| SCDUL API | `master.trnfDtm` (R006:89) | `TB_PAY_TARGET.PROC_DTTM` (R006:99) |
| SCDUL File | `master.trnfDtm` (R006:89) | `TB_PAY_FILE_MST.PROC_DTTM` (R006:98) |
| Overseas | `master.trnfDt` (R007:75) | `TB_PAY_FILE_MST.PROC_DTTM` (R007:77) |

A line with no dispatch row still carries `procDttm` — legacy's `PROC_DTTM` is a plain master column
that no join can suppress — with every other field null. `DispatchResult.EMPTY` is therefore no longer
returned from the API path's no-shadow-keys branch.

**Companion edit — the ERP write-back had to move to `rsltDttm()`.**
`HaengsungDispatchResultWriteBackService:226-236` computed the Oracle `END_DATETIME` from
`result.procDttm()`. Its legacy source is **not** R006/R007 but
`SCDUL_PYMN_SUMR_DETL_R003` / `OVRS_PYMN_SUMR_DETL_R004`, whose `PROC_DTTM` column is the
**dispatch row's** value (`R003:92-94` — `CASE … THEN TPFM.PROC_DTTM ELSE TPT.PROC_DTTM END`), i.e.
what the resolver now calls `rsltDttm`. Switching that read to `rsltDttm()` keeps the stored
`END_DATETIME` **byte-identical** to what the service already wrote — without it, DRR-1 would have
silently changed a timestamp persisted to the HAENGSUNG ERP. No other consumer reads either datetime
for anything but the Result-page DTOs (verified by grep over `src/main`: 5 result services pass both
through verbatim; the download services read neither).

**DRR-2.** Overseas `rs_msg` now comes from `TB_PAY_FILE_MST.RS_MSG`. Source precedence is
unambiguous — the pay-file master is the only table on that path with an `RS_MSG` column
(`TB_PAY_FILE_DATA` has `RS_CD` only; there is no `TB_FIRM_TRAN` row without an API rail). A genuinely
null master message stays null; a blank one is normalised to null, matching how the API path already
treats `TFT.RS_MSG`. The **SCDUL File path still emits null** — legacy R006 selects `NULL` outright
there — pinned by `drr2_isScopedToOverseas_scdulFilePathUnchanged`. This also aligns the ERP
write-back's overseas detail message with legacy `R004`, which likewise selects `TPFM.RS_MSG`.

Tests: `DispatchResultResolverTest` grew to 53 tests — five DRR-1 cases (domestic API, domestic File,
overseas, independent nulls, multi-detail ×2) and five DRR-2 cases (failure, success, null/blank ×3
parameterised, absent-master fallback, SCDUL-scope guard).

### 11.3 DRR-3 … DRR-6 — four separate decisions, deliberately NOT fixed

Out of scope for the 2026-07-30 fix pass and recorded individually so each can be decided on its own
merits. All four stay pinned as current behaviour.

| # | Decision needed | Why it was left | Blast radius if fixed |
|---|---|---|---|
| **DRR-3** | Should the Overseas path drop the R006 `COALESCE(PROC_STAT,'3')`? Legacy R007 has none, so a *blank* stored status should pass through as blank, not become `'3'` (failed). | Narrow — the dispatch writers always set a status, so a blank is a data-anomaly path only. Removing the coalesce would also make the Overseas and SCDUL paths behave differently for the same input, which needs a deliberate call. | `resolveOverseas` only; changes an anomaly row from *failed* to *blank* on the Overseas Result detail. |
| **DRR-4** | Should route selection be per-line (legacy's `EXISTS` over `TB_PAY_FILE_DATA`) instead of once from `master.payFileSeqNo != null`? | The two agree for every batch the executors write (all lines of a file-route batch have a matching `TB_PAY_FILE_DATA` row). Making it per-line means loading `TB_PAY_TARGET`/`TB_FIRM_TRAN` on the file path too — extra queries on every Result-detail render for a case that does not occur. | Both `resolve` paths; adds two bulk lookups per file-route render. |
| **DRR-5** | Should blank `rs_cd` be `""` (legacy `TRIM(COALESCE(…,''))`) rather than `null`? | **Not observable.** `countByStatus` and the frontend `deriveResultStatus` treat blank and null identically, so no rendered value changes. Recorded for completeness only. | Wire-level `null` → `""` across all Result details; zero visible effect. |
| **DRR-6** | Should the File-path `PROC_STAT` coalesce also fire for a stored `NULL`? `mstOpt.map(::getProcStat).map(::coalesce)` short-circuits on the first `map`, so only a *blank* is coalesced; legacy R006's `COALESCE(TPFM.PROC_STAT,'3')` gives `'3'` for both. | Same anomaly-only reach as DRR-3, and it interacts with DRR-3 — on the Overseas path the short-circuit accidentally *matches* legacy R007. Fixing DRR-6 without deciding DRR-3 would break that accidental agreement. | `resolveFilePath`; a present `TB_PAY_FILE_MST` row with `PROC_STAT IS NULL` moves from *in progress* to *failed*. |

**[CONFIRM: PO / Mr. Jung]** — DRR-3 and DRR-6 should be decided together (they interact). DRR-4 is a
performance-vs-fidelity call. DRR-5 needs no decision unless a future consumer starts distinguishing
`null` from `""`.

### Deployment / configuration blockers

- `WECMS_CALLBACK_ALLOWED_IPS` must be provisioned in sit/uat/prod — startup aborts without it (that
  is intended).
- `wecms.dispatch.callback.trusted-proxies` must name the terminating proxy wherever
  `trust-forwarded-for=true` (defaults `true` in sit/uat/prod with an **empty** proxy list, so XFF is
  currently ignored — correct, but means a real proxy deployment needs the list filled).
- All dispatch flags/crons must stay off until the items below clear.

### PO decisions

- **[CONFIRM: PO]** H-4 fix approval (money-path behaviour change).
- ~~**[CONFIRM: PO]** `Q-1`~~ — **FIXED 2026-07-30.** Query-only correction (master routing + the
  varchar-CAST join on `PAY_TRAN_SEQ`); no schema, wire-shape or consumer change. 34 tests added.
  [../audits/dispatch-repository-query-reverification.md](../audits/dispatch-repository-query-reverification.md) §5 + §5.1
- ~~**[CONFIRM: PO]** `DRR-1` / `DRR-2`~~ — **FIXED 2026-07-30** (§11.2). The ERP write-back was moved
  to `rsltDttm()` in the same change so the persisted Oracle `END_DATETIME` stays byte-identical.
- **[CONFIRM: PO]** `DRR-3` + `DRR-6` (decide together — they interact), `DRR-4`, `DRR-5` — four
  separate decisions, none applied. §11.3.
- ~~**[CONFIRM: Mr. Kang]** BATCH2 coexistence~~ — **superseded 2026-07-29.** Coexistence is resolved
  (DEV/SIT DB-separated; UAT = **Ops** DB-target check) and the 4→1 architecture was already approved.
  Replaced by: **[CONFIRM: Mr. Kang — reopen K9]** — `bulk-execution-retry` is the Option-A periodic
  sweep that was offered and declined. → [legacy-batch2-ownership-verification.md](../audits/legacy-batch2-ownership-verification.md)
- **[CONFIRM: Mr. Kang]** Stranded-recovery coexistence (pre-existing).
- **[CONFIRM: PO]** Keep or remove the `operating-hours.zone` override (§6.3).

### DBA decisions

- None. Decision A needed no DBA input: the DDL already answers it (§10.1).

### Environment-only limitations

- **Docker daemon unavailable** on this host, so any future Testcontainers work cannot be executed
  here. No test currently requires it (0 Testcontainers files), so this did **not** limit this audit.
- Gradle repeatedly failed to delete `build/test-results/test/binary/output.bin` (a Windows file
  lock), forcing `--stop` + `cleanTest` and causing Gradle to write results into
  `build/test-results/test_stale_*`. A local build annoyance only; results are complete and were read
  from those directories.

## 12. Test / build evidence

All commands run from `c:/WECMS PROJECT/wecms-api`, JDK 21 (Temurin 21.0.10), Gradle 8.14.4.

| # | Command | Exit | Result |
|---|---|---|---|
| 1 | `./gradlew compileJava compileTestJava` | 0 | `BUILD SUCCESSFUL` |
| 2 | `./gradlew cleanTest test --tests "*DispatchExternalClientWiringTest" --tests "*DispatchClaimQueryContractTest" --tests "*BatchScheduleNotNullContractTest" --tests "*OperatingHoursServiceTest"` | 0 | `BUILD SUCCESSFUL` — **43 tests, 0 failures, 0 errors, 0 skipped** |
| 3 | `./gradlew cleanTest test` (full suite) | see below | see below |

Targeted run (command 2), per class, from the JUnit XML:

| Class | tests | failures | errors | skipped |
|---|---|---|---|---|
| `DispatchExternalClientWiringTest` | 10 | 0 | 0 | 0 |
| `DispatchClaimQueryContractTest` | 4 | 0 | 0 | 0 |
| `BatchScheduleNotNullContractTest` | 4 | 0 | 0 | 0 |
| `OperatingHoursServiceTest` | 25 | 0 | 0 | 0 |
| **total** | **43** | **0** | **0** | **0** |

Full clean suite (command 3) and the full build:

| Command | Exit | Test classes | Tests | Skipped | Failures | Errors | Result |
|---|---|---|---|---|---|---|---|
| `./gradlew cleanTest test` | **0** | **276** | **2,249** | **20** | **0** | **0** | `BUILD SUCCESSFUL in 2m 21s` |
| `./gradlew build` | **0** | — | — | — | — | — | `BUILD SUCCESSFUL in 26s` (`bootWar`, `war`, `assemble`, `check`) |

**Re-verified after the H-4 fix (2026-07-29, same day):**

| Command | Exit | Test classes | Tests | Skipped | Failures | Errors | Result |
|---|---|---|---|---|---|---|---|
| `./gradlew compileJava` / `compileTestJava` | **0** / **0** | — | — | — | — | — | `BUILD SUCCESSFUL` |
| `./gradlew cleanTest test` | **0** | **277** | **2,248** | **20** | **0** | **0** | `BUILD SUCCESSFUL` |
| `./gradlew build` | **0** | — | — | — | — | — | `BUILD SUCCESSFUL in 4m 15s` |

The class count rose 276 → 277 and the test count moved 2,249 → 2,248 because the H-4 work added three
classes (22 tests) and the three obsolete `save(pay)` assertions were folded into claim assertions. See
[../architecture/bulk-dispatch-concurrency-fix.md](../architecture/bulk-dispatch-concurrency-fix.md) §15
for the per-class breakdown and the flake that was found and fixed in the new test.

Aggregated from all 276 JUnit XML reports in `build/test-results/test/`
(`grep -ho 'tests="…" skipped="…" failures="…" errors="…"' *.xml`); zero files contain a
`<failure>` or `<error>` element.

**All 20 skipped tests are in one class: `VasAccountIntegrationTest`** — the repo's only real
`@SpringBootTest`, `@Disabled` at class level with the reason *"F1 — pending shared integration-test
infrastructure (Testcontainers + JWT mock)"*. This sharpens §1: the number of integration tests that
**actually execute** anywhere in this repository is **zero**. Nothing is skipped due to this host's
missing Docker daemon — the skip is a committed `@Disabled`, so the same 20 skip in CI.

`UP-TO-DATE` was not accepted as evidence: every reported run was preceded by `cleanTest` (and, where
a stale daemon held a file lock, by `./gradlew --stop`).

## 13. Files changed

**Production (3 files, all minimal):**

| File | Change |
|---|---|
| `dispatch/service/OperatingHoursService.java` | `@PostConstruct validateOperatingHoursZone()` + 2 imports (`jakarta.annotation.PostConstruct`, `java.time.DateTimeException`) + Javadoc. No behavioural change for valid config (§8.1) |
| `dispatch/entity/TbPayTarget.java` | dangling `{@link}` → `{@code}` naming the active executors (§8.2) |
| `dispatch/entity/TbPayFileMst.java` | same (§8.2) |

**Tests (4 files — 3 new, 1 extended):**

| File | Change |
|---|---|
| `dispatch/van/client/DispatchExternalClientWiringTest.java` | **new**, 10 tests |
| `dispatch/repository/DispatchClaimQueryContractTest.java` | **new**, 4 tests |
| `dispatch/entity/BatchScheduleNotNullContractTest.java` | **new**, 4 tests |
| `dispatch/service/OperatingHoursServiceTest.java` | +2 tests, +2 static imports |

**Docs (3 files — 2 new, 1 updated):** this file; `../architecture/payment-dispatch-call-graph.md`
(new); `missing-tests.md` (superseded banner + the two retractions).

No migration, no YAML, no `build.gradle`, no `.env*`, no deployment flag was touched.

## 14. Safety statement

- **No commit, no push.** All changes are unstaged working-tree edits. The tree was already dirty
  with 114 changed paths from prior work; nothing pre-existing was reverted, staged, or committed.
- **No SQL executed.** Zero database calls. Decision A was resolved from committed DDL (`V95`/`V96`),
  the entity mappings, and the legacy IDO XML — the `postgres_dev_server_*` MCPs were not used.
- **No live external calls.** No EFS/VAN, bank, SFTP, callback or gateway endpoint was contacted. The
  new wiring test's "enabled" cases assert *bean selection only* and never invoke a client method;
  the live EFS base URL is set to `http://127.0.0.1:1` and is never dialled.
- **No deployment flags enabled.** No scheduler, EFS, SFTP, recovery, realtime-dispatch,
  bulk-execution-retry, stuck-monitor or alert flag was changed. All remain `false` / cron `-` in all
  four profiles, now test-enforced.
- **No secrets exposed.** No API key, `EV`, `VV`, JWT secret, credential, certificate, account number
  or customer datum appears in any file added or changed here, or in this document.
- **No suspected dead code deleted by this pass.** `TransferDispatchExecutor` was already deleted
  before this audit; the audit verified that deletion. The five unused repository finders and 12
  unused constants identified in the dead-code review were **left in place**.
