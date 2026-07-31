# Payment Dispatch — Full Repository / Query Re-verification

**Date:** 2026-07-30 · **System:** Refactor (`wecms-api`) · **Branch:** `wecms-ui-fixes` (uncommitted working tree)
**Scope:** every query method on the dispatch rails — `domain/payment/dispatch/repository/**` (13 repositories)
plus `domain/payment/dispatch/DispatchCountRepository`.
**Method:** each method re-read against its legacy source of truth (JEX `WECMS_ETC` IDO XML, batch-side
`WECMS_BATCH_ETC` IDO XML, and the four legacy batch programs), then against its live callers, then
against the query-contract tests that pin it. Live-data checks run read-only against JEX DEV
(`postgres_dev_server_jexframe`).
**Companions:** [../testing/integration-test-audit.md](../testing/integration-test-audit.md) ·
[../architecture/payment-dispatch-tenant-isolation.md](../architecture/payment-dispatch-tenant-isolation.md) ·
[../architecture/bulk-dispatch-concurrency-fix.md](../architecture/bulk-dispatch-concurrency-fix.md)

---

## 1. Verdict

| | |
|---|---|
| Query methods re-verified | **31** across 14 repositories |
| Match legacy | **30** → **31** after the Q-1 fix |
| Confirmed divergence | **1** — `DispatchCountRepository.countByBatch` (**Q-1**, §5) · **FIXED 2026-07-30** |
| Coverage gaps found in the query-contract tests | **2** — both closed here (§4) |
| Deprecated unbounded finders still caller-free | **4 / 4** ✔ |
| Mutating queries on the dispatch rails | **3**, all pinned ✔ |

**Q-1 was a real, data-proven defect on a read path.** The 2026-07-30 follow-up pass fixed it
(§5.1) — a query-only correction with no schema change, no wire-shape change and no consumer change.
Everything else came back clean or was closed in the original pass.

---

## 2. Mutating queries (the money-path writes)

Enumerated by `grep -rn "@Modifying" domain/payment/dispatch/` — there are exactly three, and all
three are pinned by `DispatchScanTenantScopeContractTest` (tenant-scoped, fully key-qualified,
state-guarded, `int` return) **and** `DispatchClaimQueryContractTest` (conditional-UPDATE shape).

| Method | Transition | Legacy source | Pinned |
|---|---|---|---|
| `TbPayTargetRepository.claimForDispatch` | `'9' → '6'` guarded on `'9'` | `TB_PAY_TARGET_U001` (`NAPAS_REAL_TIME_BATCH`) made conditional | ✔ ✔ |
| `TbPayFileMstRepository.claimForReception` | `'9' → '6'` guarded on `'9'` | `TB_PAY_FILE_MST_U001` (`FILE_DEPOSIT_TRANSFER_BATCH1:453-454`) made conditional (H-4) | ✔ ✔ |
| `TbPayFileMstRepository.failClaimedPayFile` | `'6' → '3'` guarded on `'6'` | `TB_PAY_FILE_MST_U001` fail write (H-3) | ✔ |

No fourth mutation exists on these rails; no `@Modifying` was added or lost since the tenant-isolation
pass. **The H-4 atomic claim is unchanged and remains verified.**

---

## 3. Deprecated unbounded finders — still caller-free

The four finders the H-2 pass superseded carry "do not add new callers" notes. Re-verified by
`grep -rn "\.<method>(" src/main src/test`:

| Finder | Production callers | Test references |
|---|---|---|
| `TbPayTargetRepository.findByProcStatAndProcDttmLessThan` | **0** | 0 |
| `TbPayFileMstRepository.findByProcStatAndProcDttmLessThan` | **0** | 0 |
| `TbFirmTranRepository.findByTranTpAndProcStat` | **0** | 1 — a `never()` regression guard in `ResultCheckBoundedScanTest:184` |
| `TbFirmFileMstRepository.findByTranDtAndProcStepAndProcStat` | **0** | 0 |

Still safe to delete; still deliberately left in place (they are the exact legacy selections and the
natural probe for operator diagnosis). No new caller crept in.

---

## 4. Bounded scheduled sweeps — two coverage gaps found and closed

Four live scheduled readers select rows by state on the dispatch rails. Only two of them were in the
query-contract test's `boundedSweeps()` list, so the other two could have silently lost their bound,
their ordering or their SQL age cutoff without failing anything.

| Sweep | Driver | Was pinned | Now |
|---|---|---|---|
| `TbPayTargetRepository.findStrandedPayTargets` | `StrandedRecoveryService:159` | ✔ | ✔ |
| `TbPayFileMstRepository.findStrandedPayFiles` | `StrandedRecoveryService:184`, `StuckPayFileMonitorService:221,359` | ✔ | ✔ |
| `TbFirmFileMstRepository.findStrandedBulkExecutionFiles` | `StuckPayFileMonitorService:420` | **✗ GAP** | ✔ added to `boundedSweeps()` |
| `TbFirmTranRepository.findResultCheckCandidates` | `ResultCheckService:291` | **✗ GAP** | ✔ pinned by three new dedicated tests |

`findStrandedBulkExecutionFiles` already satisfied the whole existing contract (`Pageable`,
`ORDER BY procDttm ASC`, `procDttm < :cutoff` and `procDttm IS NOT NULL` as SQL predicates, exact
`procStat`, no `LIKE`/`%`) — it was simply absent from the list, so it joins it directly.

`findResultCheckCandidates` has a legitimately different bound shape and gets its own tests:

- `resultCheckScanIsBoundedAndOrderedOldestFirst` — takes a `Pageable`; orders
  `tranDt ASC, tranTm ASC, tranSeq ASC`; no `DESC` anywhere.
- `resultCheckScanBoundsTheDateInSql` — `t.tranDt IN :tranDts` must stay a **SQL** predicate. This is
  the N-1 regression verbatim: with the date filter applied in Java *after* the `LIMIT`, a row parked
  at `202`/`'6'` from an older date sorts to the front of every page, consumes the whole batch window
  and is then discarded, so the sweep processes nothing, permanently. Legacy's own
  `IDO.TB_FIRM_TRAN_R001` (batch-side copy) also has `TRAN_DT` in the `WHERE` clause.
- `resultCheckScanFiltersOnExactStateAndType` — exact `tranTp` + `procStat`, no wildcard.

Its **age** rule deliberately stays in `ResultCheckService.selectAged` (the tiered
`RQ_DTTM`-first-then-`TRAN_DT`/`TRAN_TM` rule, documented D-1); folding it into SQL would reintroduce
the UTC/ICT boundary bug. That is a documented divergence, not a gap, and the new tests do not
contradict it.

---

## 5. `Q-1` · `DispatchCountRepository.countByBatch` reports 0 / 0 for every file-route batch — **FIXED 2026-07-30**

**Severity `P2`** (read/display path — no money moves, but the Result list lies to the operator).
**Status: FIXED — query-only correction, no schema change.** See §5.1 for the applied change.

### What legacy does

`IDO.SCDUL_PYMN_SUMR_R007.xml` computes `SUC_CNT` / `ERR_CNT` / status per batch. It picks the route
with an `EXISTS` over `TB_PAY_FILE_DATA` keyed on the **master**:

```sql
WHEN EXISTS (SELECT 1 FROM TB_PAY_FILE_DATA TPFD
              WHERE TPFD.BIZ_KEY     = SPS.BIZ_KEY
                AND TPFD.TRAN_DT     = SPS.PAY_TRAN_DT
                AND TPFD.FILE_SEQ_NO = SPS.PAY_FILE_SEQ_NO)   -- SPS = SCDUL_PYMN_SUMR (master)
```

and on the file route joins the per-line result with a **cast on `PAY_TRAN_SEQ`**:

```sql
AND CAST(TPFD.SEQ AS VARCHAR) = SPMD.PAY_TRAN_SEQ             -- SPMD = ..._DETL (line)
```

This is the same route rule and the same join `IDO.SCDUL_PYMN_SUMR_R006.xml` uses (the query the
refactor's `DispatchResultResolver` replaces, and which the resolver reproduces correctly).

### What the refactor does

`DispatchCountRepository.countByBatch` instead:

1. routes **per line** on `SPMD.PAY_FILE_SEQ_NO` — the **detail** column, not the master's; and
2. joins `TPFD.SEQ = SPMD.PAY_SEQ` — a different column, and without the cast.

Both columns are always `NULL`:

- On JEX DEV, `SCDUL_PYMN_SUMR_DETL` has **0 of 544** rows with `PAY_SEQ` and **0 of 544** with
  `PAY_FILE_SEQ_NO`; the master carries the file key instead (`SCDUL_PYMN_SUMR.PAY_FILE_SEQ_NO`
  non-null on 46 of 408 batches, ≈ 11 % file-route).
- The refactor's own dispatch executors write it that way **on purpose** — every one of them sets
  `line.setPayFileSeqNo(null)` and `line.setPaySeq(null)` and stamps only `line.payTranSeq` plus
  `master.payFileSeqNo` (`InternalTransferDispatchExecutor:276-277,319`;
  `ExpenseDispatchExecutor:198-199,241`; `PayrollDispatchExecutor:184,284`;
  `OverseasDispatchExecutor:215,378`). That matches legacy data exactly — the executors are right.

So the file-route `WHEN` branch is **unreachable**, and every file-route line falls through to the
API `WHEN` branch (`PAY_FILE_SEQ_NO IS NULL AND PAY_TRAN_SEQ IS NOT NULL`), where it has no
`TB_PAY_TARGET` row — `TPT.PROC_STAT` is `NULL`, so it counts as neither success nor error.

### Live proof (JEX DEV, read-only)

Running both count expressions side by side over the batches whose master carries a
`PAY_FILE_SEQ_NO` returns a divergence on **every** one of them. First 20 rows:

| `SCDUL_PYMN_NO` | legacy succ / err | refactor succ / err |
|---|---|---|
| 128, 129, 130, 206, 209, 214, 244, 253, 254, 274, 286, 287, 364, 367, 370 | 0 / 1 | **0 / 0** |
| 208 | 0 / 2 | **0 / 0** |
| 131, 132 | 0 / 3 | **0 / 0** |
| 291 | 0 / 4 | **0 / 0** |
| 372 | **1** / 0 | **0 / 0** |

### Impact

`DispatchCountRepository.countByBatch` feeds the per-batch **succ / err chips on the Result list** for
Internal Transfer, Vendor, Expense and Payroll (`docs/features/payment/internal-transfer/transfer-result-flow.md`
§Search). For a file-route batch the chips therefore read `0 / 0` — the batch looks untouched even when
the bank reported failures. The Result **detail** page is unaffected: it goes through
`DispatchResultResolver`, which uses the master's `PAY_FILE_SEQ_NO` and the `PAY_TRAN_SEQ` join, i.e.
legacy's rule. So detail and list disagree with each other today.

The API-route path is correct and unaffected (≈ 89 % of DEV batches).

### Proposed fix (one query, no schema change, no new column)

Align `countByBatch` with `R007`/`R006`: route on the **master's** `PAY_FILE_SEQ_NO` (the query already
joins `SCDUL_PYMN_SUMR_DETL`, so it needs the master joined in or the route passed as a parameter), and
join `CAST(TPFD.SEQ AS VARCHAR) = SPMD.PAY_TRAN_SEQ` instead of `TPFD.SEQ = SPMD.PAY_SEQ`. No entity,
DTO, endpoint contract or table changes; the wire shape (`succ`, `err`) is unchanged — only the values
become correct.

**Paired frontend impact:** none structurally — the chips already render whatever the API returns; they
simply start showing the real numbers for file-route batches. No wire-shape change (`succ` / `err` are
unchanged fields), so no `wecms-web` edit is required.

### 5.1 Applied change (2026-07-30)

Query-only, in `DispatchCountRepository.countByBatch`. Three edits:

1. **Joined the master.** `JOIN SCDUL_PYMN_SUMR SPS ON SPS.BIZ_KEY = SPMD.BIZ_KEY AND
   SPS.SCDUL_PYMN_NO = SPMD.SCDUL_PYMN_NO` — an **INNER** join, matching legacy's
   `FROM SCDUL_PYMN_SUMR SPS`. A `LEFT JOIN` would let an orphaned detail row be classified on the
   wrong route.
2. **Route from the master, via legacy's `EXISTS`.** Each aggregate's outer `CASE` now opens with the
   verbatim legacy probe over `SPS.BIZ_KEY` / `SPS.PAY_TRAN_DT` / `SPS.PAY_FILE_SEQ_NO`, replacing
   the per-line `SPMD.PAY_FILE_SEQ_NO IS NOT NULL` / `IS NULL` tests.
3. **Fixed the two file-route joins.** `TPFM` and `TPFD` now key on `SPS.PAY_TRAN_DT` /
   `SPS.PAY_FILE_SEQ_NO`, and the per-line result joins
   `CAST(TPFD.SEQ AS VARCHAR) = SPMD.PAY_TRAN_SEQ` instead of `TPFD.SEQ = SPMD.PAY_SEQ`.

Unchanged: the success/error predicates, the `GROUP BY` grain, the projection aliases
(`scdul_pymn_no` / `succ_cnt` / `err_cnt`), the `DispatchCountRow` projection, all four consumer
services, and the tenant scope (`SPMD.BIZ_KEY = :bizKey` plus `BIZ_KEY` equality on every join —
load-bearing because `FILE_SEQ_NO` and `TRAN_SEQ` both come from **global** sequences and repeat
across tenants). No fan-out: every `LEFT JOIN` is on its table's full PK and the master join on the
master's PK.

**Tests added**

| Class | Tests | Covers |
|---|---|---|
| `dispatch/DispatchCountQueryContractTest` | 18 | master joined + INNER; route `EXISTS` on the master key, twice; **regression guard** that `SPMD.PAY_FILE_SEQ_NO` and `SPMD.PAY_SEQ` never reappear; the varchar-CAST join; every legacy classification arm verbatim; per-line `SUM(CASE …)` grain; `ELSE 0` zero-result behaviour; tenant scope + the multi-tenant global-sequence rationale; read-only; no fan-out |
| `dispatch/DispatchCountLegacyParityRegressionTest` | 16 | executable models of **legacy R007**, the **fixed** query and the **pre-fix** query over shared fixtures. Every case asserts `FIXED == LEGACY`; file-route cases also assert `PRE_FIX != LEGACY` and `PRE_FIX == 0/0`. Cases: batch-372 shape (1/0), all-failed (0/3), mixed per-line RS_CD (2/2), blank RS_CD → error, in-progress → 0/0, varchar-CAST semantics, API all-success / all-error / mixed / zero-result, API-unaffected-by-the-fix, two tenants sharing a `FILE_SEQ_NO`, two tenants sharing a `TRAN_SEQ`, empty tenant borrows nothing, two batches in one tenant, and a model-distinctness sanity check |

**Verification limitation, stated plainly.** `countByBatch` is a native query over four tables, three
of them not entity-mapped, and this project ships no Testcontainers and no embedded DB by policy — so
**the SQL itself was not executed by a test**. The contract test proves the SQL *says* the legacy
thing; the parity test proves the legacy rule *yields* the legacy numbers (including the batch-372
figures taken from the live DEV comparison above). Executing the real query against PostgreSQL is
still outstanding and is tracked with the other DB-blocked items in
[../testing/payment-dispatch-integration-tests.md](../testing/payment-dispatch-integration-tests.md).

---

## 6. Everything else — verified, no change needed

### 6.1 `TbPayFileDataRepository.aggregateValidationCounts` — verbatim-equivalent subset ✔

The native query reproduces 8 of the 15 columns of legacy `IDO.TB_PAY_FILE_DATA_R005.xml`. Verified
this is complete, not a shortfall: `FILE_DEPOSIT_TRANSFER_BATCH1:129-143` reads 12 columns into locals
but only **branches** on nine —

| Legacy local | Used in a branch | Refactor projection |
|---|---|---|
| `eBankCnt`, `eBankOutCnt`, `vcbCnt`, `vcbOutCnt`, `napasCnt`, `ccyCnt`, `exchangeTranCnt` | yes | present |
| `swiftOutCnt` (`:148`) and `failChnlCnt` (`:153`) | yes | one column — the two R005 expressions are **byte-identical** (`ANT_DIV_CD` neither `'S'` nor blank) |
| `citadCnt`, `swiftCnt` | **no** — assigned, never read | absent (dead in legacy too) |
| `citadOutCnt`, `napasOutCnt`, `succChnlCnt`, `etcCnt` | commented out in legacy | absent |

`ETC_CNT` being absent is the same rule `AntDivChannelTest` pins: an unmapped `ANT_DIV_CD` is legacy
`ETC` and is deliberately **not** operating-hours-gated.

### 6.2 The resolver's read queries ✔

`TbPayTargetRepository.findByBizKeyAndTranDtAndTranSeqIn`,
`TbFirmTranRepository.findByBizKeyAndTranDtAndTranSeqIn`,
`TbPayFileDataRepository.findByBizKeyAndTranDtAndFileSeqNo` and
`TbPayFileMstRepository.findByTranDtAndBizKeyAndFileSeqNo` reproduce the R006 join keys exactly
(`BIZ_KEY` + the line's own `PAY_TRAN_DT` + `PAY_TRAN_SEQ` on the API rail; master
`PAY_TRAN_DT`/`PAY_FILE_SEQ_NO` on the file rail). All are tenant-scoped. Now executed by
`DispatchResultResolverTest`, including the two-`PAY_TRAN_DT` batch that proves the per-date grouping.

### 6.3 Bulk-flow finders ✔

| Method | Legacy IDO | Note |
|---|---|---|
| `TbFirmFileMstRepository.findByTranDtAndFirmFileNmAndProcStepAndProcStat` | `TB_FIRM_FILE_MST_R004` | verbatim key set |
| `…findForExecutionResult` | `TB_FIRM_FILE_MST_R006` (v20250910) | `substring(FIRM_FILE_NM,3)` prefix-agnostic match + `VAN_ORG_CD` + `FILE_SEQ` scope — audit C3/M-2 |
| `…findByTranDtAndBizKeyAndFileSeqNo` | `TB_FIRM_FILE_MST_R003` | sibling-file gate for the split-file 00302 hold |
| `…findByTranDtAndOutBankCdAndBankPrtnId` | `TB_FIRM_FILE_MST_R002` | `MAX(FILE_SEQ)+1` round computation |
| `TbFirmFileDataRepository.findByTranDtAndFileSeqNoAndFirmFileNmOrderBySeqAsc` | `TB_FIRM_FILE_DATA_R001` | ordered by `SEQ` |
| `TbFirmFileRsltRepository.findByTranDtAndBizKeyAndFirmFileNmOrderBySeqAsc` | `TB_FIRM_FILE_RSLT_R001` | ordered by `SEQ` |
| `TbFirmBatApiTranRepository.findByTranDtAndBizKeyAndFirmFileNm` / `existsBy…AndApiTranTp` | `TB_FIRM_BAT_API_TRAN_R001` | the `exists` form is the duplicate-callback guard |
| `TbBatchScheduleInfoRepository` / `TbBatchScheduleDtlRepository` finders | `TB_BATCH_SCHEDULE_{INFO,DTL}_R001` | `NOT NULL` bounds pinned by `BatchScheduleNotNullContractTest` (Decision A) |
| `TbFirmConfRepository.findById` / `TbVanApiInfoRepository.findByVanOrgCd` | `TB_FIRM_CONF_R001` / `TB_VAN_API_INFO_R001` | PK reads |

### 6.4 Not re-litigated

The system-scoped (non-tenant-predicated) shape of the sweeps was resolved in the tenant-isolation
pass: legacy's three pickup IDOs all select across tenants with `BIZ_KEY` as an output column, and
tenant safety lives in the mutations (§2). Unchanged, still correct, not reopened here.

---

## 7. Outcome

**Original pass (2026-07-30, verification only)**

- **Code changed:** none. The two coverage gaps were test-side.
- **Tests changed:** `DispatchScanTenantScopeContractTest` — `findStrandedBulkExecutionFiles` added to
  the pinned sweep list, plus three new tests for `findResultCheckCandidates`.

**Follow-up pass (2026-07-30, Q-1 fix)**

- **Code changed:** `DispatchCountRepository` (the query + its Javadoc) — §5.1.
- **Tests added:** `DispatchCountQueryContractTest` (18), `DispatchCountLegacyParityRegressionTest` (16).
- **Not changed:** no schema, no entity, no DTO, no endpoint contract, no consumer service, no
  scheduler, no flag, no transaction boundary, no `PROC_STAT` value, no payment-execution path. The
  query is read-only (`SELECT` only, asserted by `isReadOnly`).
- **Open item:** the DB-backed execution test for this query — blocked on test-DB infrastructure,
  tracked in [../testing/payment-dispatch-integration-tests.md](../testing/payment-dispatch-integration-tests.md).
