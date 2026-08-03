# `wecms.dispatch.operating-hours.zone` — Decision

**Date:** 2026-07-29 · **System:** Refactor (`wecms-api`)
**Decision:** `SUPPORTED OVERRIDE` — retained, validated at bean creation, restricted to two values, and consistency-tested against the request-time gate.

---

## 1. Decision

```text
SUPPORTED OVERRIDE
```

The property stays, because legacy evidence shows the two zones produce **genuinely different and both
legitimate** behaviour, and the choice depends on a deployment fact (which database clock the batch
reads) that is not fixed across environments. It is not left ambiguously configurable: the accepted
value set is exactly two entries, anything else aborts startup, and the default is the business zone.

## 2. Legacy evidence

| Aspect | Finding | Evidence |
|---|---|---|
| Where the window compare happens | **In SQL, on the database clock** — not in the JVM | `IDO.TB_BATCH_SCHEDULE_INFO_R001.xml`: `CASE WHEN BATCH_START_TM > TO_CHAR(NOW(),'HH24MISS') THEN 'N' WHEN BATCH_END_TM < TO_CHAR(NOW(),'HH24MISS') THEN 'E' ELSE 'Y' END AS EXE_DIV`; same in `IDO.TB_BATCH_SCHEDULE_DTL_R001.xml` |
| Time columns | `bpchar(6)` `HHmmss`, `NOT NULL` | `V96__tb_batch_schedule_info.sql`, `V95__tb_batch_schedule_dtl.sql` |
| Comparison semantics | lexicographic string compare, strict `>` / `<` | the SQL above |
| Batch-side pickup date | `TO_CHAR(now(),'yyyymmdd')` — also the DB clock | `IDO.TB_PAY_TARGET_R001.xml`, `IDO.TB_FIRM_TRAN_R001.xml` |
| JVM timezone | not consulted for the gate at all | no `Calendar`/`TimeZone` in the `EXE_DIV` path |
| Business timezone | Vietnamese bank cut-offs (ICT, UTC+07) | `CutoffValidator.VIETNAM_ZONE`; `TB_FIRM_BANK_CODE`-driven cut-off config |
| **Actual DB clock** | JEX DEV frame DB reports **`Asia/Seoul` (UTC+9)** | recorded in `OperatingHoursService` Javadoc from a prior DEV inspection |
| Operator override in legacy | **none** — there is no property; the zone *is* whatever the DB server runs | absence across `wecms_batch.properties`, `*.batch`, `web.xml` |

**The consequence, stated plainly.** Legacy evaluates a stored `090000-153000` window on a UTC+9 clock,
so on the JEX DEV DB it behaves as 09:00–15:30 **KST** = 07:00–13:30 **ICT**. The stored numbers mean
Vietnamese business hours, but legacy compares them against a Korean clock. That is a **legacy defect
arising from an operational assumption** (single co-located DB whose zone nobody re-checked), not an
intentional design.

**Classification:** `legacy operational assumption unsafe in refactor`. Copying it bug-for-bug would
make the dispatch-time gate contradict the refactor's own request-time gate (`CutoffValidator`, ICT) —
a payment accepted at 15:00 ICT would be cut-off-failed 2 h early at dispatch.

## 3. Why not `FIXED`

`FIXED` (hard-code ICT, delete the property) was the tempting option and is rejected on evidence:

- The refactor is documented to run **against the live JEX DB in all environments**
  (`project_uat_prod_live_jex_db_target`). If a legacy JEX batch is still processing the same
  `TB_BATCH_SCHEDULE_*` rows on that Seoul-clock DB, the two systems must agree, or the same file gets
  opposite verdicts depending on which one picks it up. Reproducing legacy exactly is then the *correct*
  behaviour, and removing the knob would leave no way to do it.
- The coexistence question is explicitly open and PO/Mr. Kang-gated. A decision that forecloses one of
  the two viable answers should not be taken inside an audit.

So the knob is a **coexistence tool**, not a convenience. Its default is the business-correct zone; the
override exists solely to reproduce legacy when a shared DB demands it.

## 4. Enforcement implemented

| Control | Implementation |
|---|---|
| Default is the business zone | `@Value("${wecms.dispatch.operating-hours.zone:Asia/Ho_Chi_Minh}")` with an in-code field default, so an **unset** property can never abort startup |
| Invalid value fails at bean creation, not per payment | `@PostConstruct validateOperatingHoursZone()` |
| Accepted set is closed | `ACCEPTED_ZONES = {Asia/Ho_Chi_Minh, Asia/Seoul}` — membership is checked **before** any parse, so a zone that merely *resolves* (`America/New_York`, `UTC`, `Asia/Bangkok`) is rejected too |
| Not in any profile YAML | absent from all five yaml files, asserted by test |

> **Correction (2026-07-29).** An earlier revision of this document claimed the accepted set was already
> enforced. **It was not** — `validateOperatingHoursZone()` only checked that the value was a
> *resolvable* IANA zone, so `America/New_York` would have been accepted and would have silently moved
> the window. The closed-set check was added when that gap was found, and
> `OperatingHoursZoneConsistencyTest.unsupportedZoneFailsAtStartup` now pins it across six values
> including the realistic typo and the plausible-but-wrong `UTC`.

### Scope of the override — GATE ONLY (this is the important caveat)

Verified by reading every source file under `domain/payment`: the property is honoured by
**`OperatingHoursService` alone**. Twelve other dispatch classes hold a hardcoded
`private static final ZoneId ICT = ZoneId.of("Asia/Ho_Chi_Minh")` — `RealtimeDispatchService`,
`RealtimeTransferService`, `BulkReceptionService`, `ResultCheckService`, `StrandedRecoveryService`,
`BulkReceptionResultService`, `BulkExecutionResultService`, `StuckPayFileMonitorService`,
`OpsAlertService`, `DepositorInquiryService`, `FirmTranInquiryAuditService`, `RecipientLookupService` —
as does `CutoffValidator.VIETNAM_ZONE`.

So setting `Asia/Seoul` shifts **only the window decision**. Every `PROC_DTTM` / `RQ_DTIME` /
`TRAN_TM` stamp, the request-time cut-off gate, the recovery grace window and the monitor stay on ICT.

That is deliberate and narrow — it reproduces a Seoul-clock legacy batch's *verdict* without
re-timestamping the ledger — but it means **the override is not a system-wide timezone switch and must
never be described as one.** `allOperatingHoursConsumers_useOneValidatedZoneSource` asserts both halves:
exactly one class binds the property, and each of the twelve stays ICT-pinned and does not read it.

**Consequence for §7:** because the override is gate-only, using it in a mixed legacy/refactor
deployment aligns the *window* but leaves timestamps in ICT. The cleaner answer to coexistence is
single-owner-per-environment (one system owns dispatch), not a zone flip.

**Why fail-fast matters here specifically.** Before the guard, `ZoneId.of(...)` ran inside `nowHms()` on
every gate evaluation. A typo (`Asia/Hochiminh`) threw `DateTimeException` *per dispatch*, and both
dispatch registrars catch-and-log per-row failures so one bad row cannot block its siblings — so every
affected payment silently sat at `PROC_STAT='9'` with only a log line. A configuration typo presented as
"payments never dispatch", indefinitely. Refusing to start is the safe failure on a money path.

## 5. Zone-consistency requirement

`CutoffValidator` (request-time) and `OperatingHoursService` (dispatch-time) **must** evaluate in the
same zone, or an accepted payment can be rejected later. Both default to `Asia/Ho_Chi_Minh`, so the
invariant holds out of the box. Setting the override to `Asia/Seoul` **deliberately breaks it** — that
is the point of the knob (it realigns the dispatch gate with a Seoul-clock legacy batch) and it is now
an explicit, documented, test-pinned divergence rather than a Javadoc claim that it "cannot happen".

`ResultCheckService` is a separate concern and correctly so: its date bound is **UTC** (matching the
`TRAN_DT` the executors stamp) while its age compare is ICT. That is the D-1 fix, not a mixed-zone bug —
the two are different quantities.

## 6. Tests

Present and green in `OperatingHoursServiceTest` (25 tests):

| Required test | Status |
|---|---|
| `defaultZone_matchesLegacyBusinessZone` | ✅ `operatingHoursZone_defaultsToIct_andIsOverridable` |
| `invalidZone_failsAtStartup` | ✅ `invalidZone_failsFastAtBeanCreation_notOncePerPayment` |
| `exactStart_isOpen` | ✅ `windowBoundaries_startIsInclusive_endIsInclusive` |
| `exactEnd_matchesLegacy` | ✅ same (end inclusive, matching SQL's strict `<`) |
| `oneSecondAfterEnd_isClosed` | ✅ `exeDiv("001500","235500","235959") == E` |
| `midnightBoundary_matchesLegacy` | ✅ `nearMidnightWindow_doesNotWrap` |
| `asiaSeoulOverride_isEitherRejectedOrExplicitlySupported` | ✅ `sameStoredWindow_yieldsDifferentDecisionsInIctVsKst` + `bothSupportedZones_passValidation` |
| `requestGate_andDispatchGate_useSameZone` | ⚠️ **outstanding** — no test asserts the equality of `CutoffValidator.VIETNAM_ZONE` and the `OperatingHoursService` default across the two classes |
| `utcDate_andBusinessTime_areNotMixed` | ⚠️ **outstanding** — the D-1 UTC-date/ICT-time split is reasoned in Javadoc and covered indirectly by `ResultCheckSelectionBoundaryTest`, but not asserted as a zone invariant |

Two tests remain outstanding; both are cheap and neither changes production behaviour. Recorded in
[../testing/missing-tests.md](../testing/missing-tests.md).

## 7. Operational guidance

| Environment | Setting | Rationale |
|---|---|---|
| local / SIT | leave unset (→ ICT) | no legacy batch competing |
| UAT / PROD | **decide with the coexistence answer.** ICT if the refactor owns dispatch; `Asia/Seoul` if a legacy JEX batch still processes the same rows on the Seoul-clock DB | the two must agree |

**[CONFIRM: Mr. Kang / PO]** — which system owns dispatch per environment. Until answered, the dispatch
flags stay off and the default (ICT) applies.
