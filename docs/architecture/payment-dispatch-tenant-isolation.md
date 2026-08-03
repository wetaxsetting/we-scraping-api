# Payment Dispatch — Tenant Isolation & Sweep Bounding (H-2)

**Date:** 2026-07-29 · **System:** Refactor (`wecms-api`) · **Branch:** `wecms-ui-fixes` (uncommitted)
**Finding:** H-2, originally recorded as *"cross-tenant unbounded scan in the result-check poll"*
**Verdict:** the **cross-tenant half is NOT a defect** (legacy-verified); the **unbounded half was**, and is now fixed on every rail.

---

## 1. Executive summary

H-2 bundled two claims. Tracing legacy separates them, and they land differently:

| Claim | Verdict |
|---|---|
| Sweeps select across tenants → tenant-safety defect | **NOT A DEFECT.** Legacy does exactly this, deliberately. `BIZ_KEY` is a *selected column*, never a predicate. |
| Sweeps are unbounded / filtered in Java after the limit | **CONFIRMED DEFECT.** Fixed on all three rails. |

The safety property that actually matters is **"a system-scoped sweep must never be able to *write* outside a row's own tenant."** That is a property of the **mutations**, not the scan — and every dispatch mutation carries `BIZ_KEY` in its `WHERE`.

## 2. Legacy evidence — sweeps are system-scoped by design

All three legacy pickup queries, read verbatim from
`WECMS_BATCH DEV/source/WECMS_BATCH/WECMS_BATCH_ETC/xml/service/IDO/`:

| Legacy IDO | Predicate | `BIZ_KEY` |
|---|---|---|
| `IDO.TB_PAY_FILE_MST_R001.xml` | `WHERE PROC_STAT = :PROC_STAT AND TRAN_DT = :TRAN_DT` | **selected column only** |
| `IDO.TB_PAY_TARGET_R001.xml` | `WHERE TRAN_DT = TO_CHAR(now(),'yyyymmdd') AND PROC_STAT = '9'` | **selected column only** |
| `IDO.TB_FIRM_TRAN_R001.xml` | `WHERE TRAN_DT = TO_CHAR(now(),'YYYYMMDD') AND PROC_STAT = '6' AND TRAN_TM <= TO_CHAR(now() - INTERVAL '2 minute','hh24miss') AND TRAN_TP = '202'` | **selected column only** |

Not one carries a `BIZ_KEY` predicate. Each *selects* `BIZ_KEY`, and the batch then loops rows and acts
per row with that row's own tenant key — e.g. `FILE_DEPOSIT_TRANSFER_BATCH1.java` passes
`data.getString("BIZ_KEY")` into every subsequent IDO call (`:543`, `:706`, `:727`, `:743`).

**Legacy tenant-isolation mechanism, precisely:** none at the scan; per-row at the action. There is no
per-tenant batch loop, no session/login context in the batch, and no environment-level separation
inside a single deployment.

**Classification:** *intentional redesign is not required* — the refactor already matches legacy here.
Adding `BIZ_KEY` to a sweep would need a tenant list the sweep does not have, and would diverge from
the approved system-batch architecture.

## 3. The real defect — unbounded scans

| Rail | Before | After |
|---|---|---|
| Result-check | `findByTranTpAndProcStat` — every `202`/`'6'` row, all dates, all tenants, into heap; date + age filtered in **Java after** the limit | `findResultCheckCandidates` — bounded `Pageable`, eligible `TRAN_DT` set as a **SQL predicate before** the limit; only the tiered age compare stays in Java (D-1), with a documented redundant guard |
| Stranded recovery — pay-target | `findByProcStatAndProcDttmLessThan` — **no LIMIT, no ORDER BY** | **`findStrandedPayTargets`** *(added here)* — bounded, `ORDER BY procDttm ASC, tranDt, tranSeq, bizKey` |
| Stranded recovery — pay-file | same unbounded finder | **`findStrandedPayFiles`** *(existed; sweep switched over here)* |
| Stuck monitor — pay-file | same unbounded finder | **`findStrandedPayFiles`** *(switched here)*, capped by `stuck-monitor.pay-file-batch-size` (default 200) |
| Stuck monitor — firm-file | already bounded | unchanged (`findStrandedBulkExecutionFiles`) |

**Two of these were live gaps found in this pass.** `findStrandedPayFiles` already existed and its own
Javadoc claimed *"Both consumers (the recovery sweep and the stuck monitor) now use the bounded,
ordered form"* — **neither did**. Both still called the unbounded finder. The Javadoc has been
corrected and both consumers switched.

There is now **no live caller** of either unbounded finder in `src/main`; the remaining mentions are
comments. Both are retained for ad-hoc diagnosis with a "do not add new callers" note.

### Why oldest-first ordering is load-bearing (the N-1 lesson)

A cap without a deterministic order lets the database return any page, so a capped sweep can revisit
the same rows forever. Worse, a cap with the age filter applied *after* it is a starvation bug: old
rows sort to the front of every page, consume the whole window, and are then discarded — the sweep
processes nothing, permanently. That is exactly what happened on the result-check rail (N-1). Every
sweep now applies its age cutoff in SQL, before the limit, and orders oldest-first so a truncated pass
drains its backlog instead of starving its tail.

## 4. Where tenant safety actually lives — the mutations

Every mutating query on the dispatch rails, verified:

| Mutation | Guard | Tenant-scoped |
|---|---|---|
| `TbPayTargetRepository.claimForDispatch` | `AND t.procStat = '9'` | ✅ `t.bizKey = :bizKey` |
| `TbPayFileMstRepository.claimForReception` | `AND p.procStat = '9'` | ✅ `p.bizKey = :bizKey` |
| `TbPayFileMstRepository.failClaimedPayFile` | `AND p.procStat = '6'` | ✅ `p.bizKey = :bizKey` |

All three are fully key-qualified (`tranDt` + `tranSeq`/`fileSeqNo`), return `int`, and contain no
`LIKE`/`%`. This matters concretely because **`TRAN_SEQ` and `FILE_SEQ_NO` are drawn from global
sequences** (`firm_seq_no` / `firm_file_seq_no`, per `project_wb_get_next_val_funnel`) and therefore
repeat across tenants — without `BIZ_KEY` these updates could match a different tenant's row outright.

## 5. Tests added

`repository/DispatchScanTenantScopeContractTest` (8 tests):

| Test | Pins |
|---|---|
| `sweepsAreBounded` | every sweep takes a `Pageable` |
| `sweepsAreOrderedOldestFirst` | `ORDER BY … procDttm ASC`, and *not* `DESC` |
| `ageCutoffIsASqlPredicate` | `procDttm < :cutoff` and `IS NOT NULL` are in SQL, not Java |
| `sweepsFilterOnAnExactStatus` | exact `procStat`, no `LIKE`/`%` |
| `everyMutationIsTenantScoped` | `bizKey = :bizKey` on all three mutations |
| `everyMutationIsFullyKeyQualified` | `tranDt` + rail-appropriate sequence |
| `everyMutationIsStateGuarded` | expected-current-state guard + `int` return |

**Scope limit, stated honestly.** These are static query-contract assertions — the idiom this repo
already uses (`ScdulPymnSumrSearchStatusFilterTest` documents *why*: no test database). They prove the
SQL says what it must. They do **not** execute PostgreSQL, so the following remain **outstanding**, not
done:

```text
tenantA_cannotReadTenantBPendingRows
tenantA_cannotClaimTenantBRow
tenantA_cannotUpdateTenantBResult
sameSequenceDifferentTenant_isIndependent   (partially covered in-memory by
                                             BulkReceptionConcurrentClaimTest)
globalSweep_preservesTenantIsolation
```

These need a DB-backed `@DataJpaTest`; see
[../testing/payment-dispatch-integration-tests.md](../testing/payment-dispatch-integration-tests.md).

## 6. Files changed

| File | Change |
|---|---|
| `repository/TbPayTargetRepository.java` | **+`findStrandedPayTargets`** (bounded, ordered, documented non-tenant-scoped with legacy citation); deprecation note on the unbounded finder |
| `service/StrandedRecoveryService.java` | both sweeps switched to bounded finders; `+recovery.batch-size` (default 50) with a clamp; cap-hit warning |
| `monitor/StuckPayFileMonitorService.java` | pay-file sweep switched to `findStrandedPayFiles`; `+stuck-monitor.pay-file-batch-size` (default 200); stale Javadoc corrected |
| `scheduler/StuckPayFileMonitorJob.java` | stale Javadoc reference corrected |
| `repository/TbPayFileMstRepository.java` | Javadoc corrected (the "both consumers now use it" claim was false) |

New properties are additive with safe in-code defaults and appear in no `application*.yml`, so an unset
variable cannot abort startup.

## 7. Remaining

- **P2** DB-backed tenant-visibility tests (§5) — blocked on test-DB infrastructure.
- **P3** `TbFirmTranRepository.findByTranTpAndProcStat` and the two `findByProcStatAndProcDttmLessThan`
  finders are now caller-free. Deleting them is safe but was left out of a money-path change; they
  carry "do not add new callers" notes.
