# WECMS Batch — Operating-Hours Gate (TB_BATCH_SCHEDULE_INFO / _DTL) Audit

> **Type:** Audit + implementation record. Original audit 2026-07-06; **implemented 2026-07-06** (see §12a).
> **Date:** 2026-07-06 · **Author aid:** Claude Code
> **Related:** [msg-api-alert-implementation-plan.md](msg-api-alert-implementation-plan.md) · [firm-banking-dispatch.md](firm-banking-dispatch.md) · [legacy-jex-mapping.md](legacy-jex-mapping.md)
> **Verdict (one line):** **IMPLEMENTED AND WIRED** — `OperatingHoursService` computes `EXE_DIV` over the existing `TB_BATCH_SCHEDULE_INFO/DTL` entities/repositories and is wired into `RealtimeDispatchService` (NAPAS) and `BulkReceptionService` (BATCH1); the 4 deferred cutoff / no-criteria alerts now fire through `OpsAlertService`. Flag-gated OFF, no schema change, no MSG_API transport change, full test suite green. **⚠ `TB_BATCH_SCHEDULE_INFO/_DTL` must be seeded before enabling real dispatch — an empty INFO table resolves to NO_CRITERIA and blocks dispatch.** Historical audit sections (§1–§11) below describe the pre-implementation state; the implementation record is §12a.

---

## 1. Summary

The legacy JEX firm-banking batches gate every dispatch on **bank / channel operating-hours windows** stored in `TB_BATCH_SCHEDULE_INFO` (per bank) and `TB_BATCH_SCHEDULE_DTL` (per bank × other-bank-network channel). A per-row SQL `CASE` computes `EXE_DIV ∈ {N, E, Y}` (before-start / after-end / in-window). `Y` → dispatch; `E` → **cutoff** (`거래 마감`); a missing schedule row (or `N` in some paths) → **no execution criteria** (`수행 기준 없음`) / skip.

In the Spring refactor the **data layer is fully present** (both tables, both `@Entity` classes with matching columns, both Spring Data repositories, and the two legacy `BATCH_ID` constants). **No service reads them** — there is no `EXE_DIV` computation, no `OperatingHoursService`, and no wiring into the dispatch flow. This is a **documented deferral**, not an oversight.

The 4 deferred alerts (`대량이체 Batch 거래 마감` / `수행 기준 없음`, `실시간지급이체 Batch 거래 마감` / `수행 기준 없음`) are **downstream of this gate** — they can only be wired once the gate produces the `cutoff` (`exeEndYN`) vs `no-criteria` distinction.

---

## 2. Legacy implementation (code evidence)

### 2.1 Callers

| Legacy batch | INFO gate | DTL (channel) gate | Alert site |
|---|---|---|---|
| `FILE_DEPOSIT_TRANSFER_BATCH1` | `.java:271-274` (INFO_R001) | `.java:797-827` `operTimeChk(bankCd, antDivCd)` per channel (DTL_R001) | `.java:424-441` (§424) |
| `NAPAS_REAL_TIME_BATCH` | `.java:112-115` (INFO_R001) | `.java:141-145` inline (DTL_R001) | `.java:207-223` (§223) |

Both files: `WECMS_BATCH DEV/source/WECMS_BATCH/WECMS_BATCH/src/batch/`.

### 2.2 SQL (verbatim, both IDO files)

`WECMS_BATCH DEV/source/WECMS_BATCH/WECMS_BATCH_ETC/xml/service/IDO/`:

**`IDO.TB_BATCH_SCHEDULE_INFO_R001.xml`** — params `BATCH_ID`, `OUT_BANK_CD`:
```sql
SELECT ANT_WHERE_USE_YN, BATCH_START_TM, BATCH_END_TM, REG_DTTM, MOD_DTTM, LAST_EDTR,
       CASE WHEN BATCH_START_TM > TO_CHAR(NOW(), 'HH24MISS') THEN 'N'
            WHEN BATCH_END_TM   < TO_CHAR(NOW(), 'HH24MISS') THEN 'E'
            ELSE 'Y' END AS EXE_DIV
  FROM TB_BATCH_SCHEDULE_INFO
 WHERE BATCH_ID = :BATCH_ID AND OUT_BANK_CD = :OUT_BANK_CD
```

**`IDO.TB_BATCH_SCHEDULE_DTL_R001.xml`** — params `BATCH_ID`, `OUT_BANK_CD`, `ANT_DIV_CD`: same `EXE_DIV` `CASE`, `FROM TB_BATCH_SCHEDULE_DTL`, extra `AND ANT_DIV_CD = :ANT_DIV_CD`.

**SQL analysis:**
- `EXE_DIV` is a **DB-computed column** comparing `BATCH_START_TM` / `BATCH_END_TM` (`HHMMSS`, 6-char) against `TO_CHAR(NOW(),'HH24MISS')` — **server-local wall-clock**, time-of-day only (no date, no weekday/holiday branch in the SQL; the `<tmUseYn>N</tmUseYn>` jex-framework service-time block is inert here).
- Pure `SELECT` (read-only). One row max (PK = `BATCH_ID, OUT_BANK_CD[, ANT_DIV_CD]`).

### 2.3 Business rules (from the Java, verbatim behavior)

**INFO gate (bank level) — shared key `"NAPAS_REAL_TIME_BATCH"` for BOTH batches:**
- BATCH1 passes `BATCH_ID="NAPAS_REAL_TIME_BATCH"` (`FILE_DEPOSIT_TRANSFER_BATCH1.java:272`) — **not** its own id. NAPAS passes the same (`NAPAS_REAL_TIME_BATCH.java:113`).
- **No INFO row** (`BATCH_START_TM == null`) → `exeErrYN=true; exeYN=false` + `vrfi_msg="No data to perform bulk transfer batch. Please check TB_BATCH_SCHEDULE_INFO."` → **`수행 기준 없음`** (BATCH1 `:284-290`; NAPAS `:125-130`).
- `EXE_DIV='E'` → `exeErrYN=true; exeEndYN=true` → **`거래 마감`** (BATCH1 `:404-407`; NAPAS `:184-187`).
- `EXE_DIV='Y'` + `ANT_WHERE_USE_YN='Y'` → run the DTL/channel gate; else `exeYN=true` (dispatch).

**DTL gate (channel level):**
- **BATCH1** (`operTimeChk`, `:797-827`) iterates channels with `count>0` — `EBANK/VCB/CITAD/NAPAS/SWIFT` (`:299-397`), key `BATCH_ID="FILE_DEPOSIT_TRANSFER_BATCH1"` (`:801`). Per channel: `N` → `continue` (silent skip, no alert); `E` → `exeErrYN+exeEndYN` (cutoff); `Y` → `exeYN=true`. **Missing DTL row → `'Y'` (pass)** (`:814-817`).
- **NAPAS** (inline, `:138-177`), key `BATCH_ID="NAPAS_REAL_TIME_BATCH"` (`:142`), only when `ANT_WHERE_USE_YN='Y'` **and** `ANT_DIV_CD != "INTER"`. `Y` → dispatch; `E` → cutoff; **missing DTL row → `exeErrYN; exeYN=false` (no-criteria)** (`:155-160`) — **diverges from BATCH1** (BATCH1 missing-DTL passes; NAPAS missing-DTL fails).

**Terminal handling + alert (BATCH1 `:411-443`; NAPAS analogous):**
- `exeErrYN` → mark `TB_PAY_FILE_MST` FAIL (`_U001` `PROC_STAT` FAIL + `RS_MSG`) → `MsgSendUtil.SendMsg(...)` with `exeEndYN ? "거래 마감" : "수행 기준 없음"` → `continue`.
- `!exeYN` (channel `N`) → `continue` (silent skip, **no** FAIL, **no** alert).

**How the two Korean phrases are decided (definitive):**
| Phrase | Trigger |
|---|---|
| `… 거래 마감` (cutoff) | `EXE_DIV='E'` at INFO or DTL → `exeEndYN=true` |
| `… 수행 기준 없음` (no criteria) | missing INFO row (both), or missing DTL row (NAPAS only) → `exeErrYN` with `exeEndYN=false` |

---

## 3. Database schema usage — Spring (code evidence)

| Table | Migration file | Inventory | PROD active list |
|---|---|---|---|
| `TB_BATCH_SCHEDULE_INFO` | `src/main/resources/db/migration/V96__tb_batch_schedule_info.sql` | `docs/references/database/legacy-table-inventory.md:128` | ✅ (root CLAUDE.md) |
| `TB_BATCH_SCHEDULE_DTL` | `src/main/resources/db/migration/V95__tb_batch_schedule_dtl.sql` | `…inventory.md:127` | ✅ |

> **Minor observation (out of scope):** these files reuse version numbers `V95`/`V96` that CLAUDE.md's historical migration table also lists for Chart-of-Accounts. Flyway is **disabled** (`ddl-auto=validate`, schema external — `project_flyway_disabled_ddl_validate`), so this collision is inert (no auto-apply). Flagged for hygiene only; not part of this audit's remit.

**Entities** (`domain/payment/dispatch/entity/`) — columns match legacy 1:1:
- `TbBatchScheduleInfo` (`:28`) — `BATCH_ID`, `OUT_BANK_CD`, `ANT_WHERE_USE_YN`, `BATCH_START_TM`, `BATCH_END_TM`, `REG_DTTM`, `MOD_DTTM`, `LAST_EDTR` (+ `TbBatchScheduleInfoId`).
- `TbBatchScheduleDtl` (`:29`) — `BATCH_ID`, `OUT_BANK_CD`, `ANT_DIV_CD`, `BATCH_START_TM`, `BATCH_END_TM`, `REG_DTTM`, `LAST_EDTR` (+ `TbBatchScheduleDtlId`).
- **`EXE_DIV` is NOT modeled** — it is a SQL-computed column in legacy; the refactor entities carry only the raw `BATCH_START_TM`/`BATCH_END_TM`, so `EXE_DIV` must be computed in Java.

**Repositories** (`domain/payment/dispatch/repository/`) — derived finders only, return the row (not `EXE_DIV`):
- `TbBatchScheduleInfoRepository.findByBatchIdAndOutBankCd(batchId, outBankCd)`
- `TbBatchScheduleDtlRepository.findByBatchIdAndOutBankCdAndAntDivCd(...)` + `findByBatchIdAndOutBankCd(...)`

**Constants already staged** — `BulkTransferConstants.java:52-53`:
```java
public static final String BATCH_ID_NAPAS_REAL_TIME = "NAPAS_REAL_TIME_BATCH";
public static final String BATCH_ID_FILE_DEPOSIT_TRANSFER_1 = "FILE_DEPOSIT_TRANSFER_BATCH1";
// :50 comment: "Used as the lookup key into TB_BATCH_SCHEDULE_INFO / _DTL (EXE_DIV window)."
```

---

## 4. Spring implementation status

| Layer | Status | Evidence |
|---|---|---|
| Tables | ✅ Done | `V95`/`V96`; inventory `:127-128` |
| Entities (+Id) | ✅ Done | `TbBatchScheduleInfo/Dtl(.Id)` — columns match legacy |
| Repositories | ✅ Done | `findByBatchIdAndOutBankCd[AndAntDivCd]` |
| `BATCH_ID` constants | ✅ Done | `BulkTransferConstants:52-53` |
| **`EXE_DIV` computation** | ❌ **Missing** | grep for `EXE_DIV` / `OperatingHours` / `operTimeChk` in `src/main/java` → **no business-logic hit** (only comments/TODOs) |
| **Operating-hours service** | ❌ **Missing** | no `OperatingHoursService` / `…Validator` class exists |
| **Wiring into dispatch** | ❌ **Missing** | see deferral notes below |
| **Repositories consumed anywhere** | ❌ **No** | no service/scheduler injects either repository |

**Deferral is explicit and documented (not forgotten):**
- `BulkReceptionService.java:104-108` — *"the operating-hours / cut-off gate (legacy `:269-447`, `TB_BATCH_SCHEDULE_*`) … intentionally NOT ported (Mr. Kang: DB 24h, inert; pending PO)"*.
- `BulkTransferValidator.java:27` — same note.
- `RealtimeDispatchService.java:112-113` — *"there is no batch cutoff/criteria gate here. TODO: revisit if a batch-level cutoff gate is ever ported (OQ-4)."*

**Classification: PARTIALLY PORTED — data layer complete, business logic intentionally deferred.**

---

## 5. Missing functionality

1. **`EXE_DIV` computation in Java** — compare `batchStartTm` / `batchEndTm` (`HHmmss`) against the current time-of-day; return `N` / `E` / `Y`. (Legacy uses server-local `NOW()`; refactor convention is ICT `Asia/Ho_Chi_Minh` — see §7 caveat.)
2. **`OperatingHoursService`** — INFO lookup (shared `NAPAS_REAL_TIME_BATCH` key) + DTL lookup (per-channel), reproducing the two missing-row semantics (INFO-missing → no-criteria; DTL-missing → **`Y` for BATCH1**, **no-criteria for NAPAS**).
3. **Wiring** into `BulkReceptionService.receive` (before the `PROC_STAT='6'` mark) and `RealtimeDispatchService.dispatch` (before the 202).
4. **Per-channel counts** for BATCH1 (`eBankCnt/vcbCnt/citadCnt/napasCnt/swiftCnt`) to drive the DTL loop — legacy derives these from the file's detail rows; `BulkReceptionService` already aggregates `BulkValidationCounts` (`payFileDataRepository.aggregateValidationCounts`), which may or may not expose per-channel counts (to be verified during implementation).
5. **The 4 deferred alerts** (§424 / §223 families) — only wireable once the gate yields `cutoff` vs `no-criteria`.

---

## 6. Migration design (proposed — NOT implemented)

### 6.1 Legacy flow → Spring equivalent

| Legacy | Spring equivalent |
|---|---|
| `TB_BATCH_SCHEDULE_INFO_R001` (SQL `EXE_DIV`) | `TbBatchScheduleInfoRepository.findByBatchIdAndOutBankCd` + Java `EXE_DIV` |
| `TB_BATCH_SCHEDULE_DTL_R001` | `TbBatchScheduleDtlRepository.findByBatchIdAndOutBankCdAndAntDivCd` + Java `EXE_DIV` |
| `operTimeChk(bankCd, antDivCd)` | `OperatingHoursService.channelExeDiv(batchId, outBankCd, antDivCd)` |
| INFO/DTL null-row semantics | explicit in the service (INFO→NO_CRITERIA; DTL→`Y` for BATCH1, NO_CRITERIA for NAPAS) |
| `exeEndYN` / `exeErrYN` flags | a small result enum, e.g. `{ DISPATCH, CUTOFF, NO_CRITERIA, SKIP }` |

### 6.2 Services / schedulers affected

- **New:** `OperatingHoursService` (+ an `ExeDiv` enum) under `domain/payment/dispatch/service/` (or `common/firmbanking/`). Read-only; no `@Transactional` needed for the lookups.
- **Modified:** `BulkReceptionService.receive` (gate before dispatch; on CUTOFF/NO_CRITERIA → mark `TB_PAY_FILE_MST` FAIL + alert + return); `RealtimeDispatchService.dispatch` (gate before the 202; on CUTOFF/NO_CRITERIA → skip/fail + alert).
- **Unblocks:** the 2 alert TODOs already in those files (§424, §223) — reuse the existing `OpsAlertService.notify(...)` (already implemented, flag-gated).

### 6.3 Database + transaction flow

- Two read-only PK lookups per pay-file/target. No writes to the schedule tables (master data).
- Gate runs **before** the `PROC_STAT='6'` in-process mark; a CUTOFF/NO_CRITERIA path writes only the existing `TB_PAY_FILE_MST` FAIL update (legacy parity) inside the existing transaction. No new tables, no schema change.

### 6.4 Expected behavior (parity targets)

- INFO missing → NO_CRITERIA → FAIL + `수행 기준 없음` alert.
- INFO `E` → CUTOFF → FAIL + `거래 마감` alert.
- INFO `Y` + `ANT_WHERE_USE_YN='Y'` → per-channel DTL; channel `N` → silent skip; channel `E` → CUTOFF; DTL-missing → BATCH1 pass / NAPAS NO_CRITERIA.
- INFO `Y` + `ANT_WHERE_USE_YN != 'Y'` → dispatch.

---

## 7. Impact analysis

- **Scope:** additive — a new read-only service + gate calls in 2 existing orchestrators. No schema/DTO/endpoint change.
- **Risk:** LOW–MEDIUM. It **adds a gate that can block dispatch** — but dispatch is already flag-gated OFF (`WECMS_EFS_ENABLED`/`WECMS_SFTP_ENABLED` default false), so no live impact until dispatch is enabled. The gate should itself be inert-safe (e.g. treat a totally-empty schedule table as legacy's documented behavior, not a hard block) — legacy note says "DB 24h, inert" (`BulkReceptionService:106`), i.e. the seeded windows currently span all day.
- **Time-zone caveat (must resolve at implementation):** legacy `EXE_DIV` uses PostgreSQL `NOW()` (server-local). The refactor computes times in `Asia/Ho_Chi_Minh` elsewhere (`RealtimeDispatchService` ICT). Computing `EXE_DIV` in Java-ICT vs SQL-`NOW()` must be reconciled, or the window edges shift. (Doing the `CASE` in a repository `@Query` against `NOW()` would match legacy exactly but reintroduces DB-side time logic.)
- **Divergence to preserve:** the INFO shared-key (`NAPAS_REAL_TIME_BATCH` for both batches) and the **asymmetric missing-DTL semantics** (BATCH1 pass vs NAPAS no-criteria) are real legacy behavior — must be reproduced, not "cleaned up".
- **Data dependency:** requires `TB_BATCH_SCHEDULE_INFO/DTL` master rows to be seeded/migrated for the target banks × channels (currently no seed migration observed for these two tables — confirm before enabling).

---

## 8. Can the 4 deferred alerts be implemented once this is ported?

**Yes — this gate is exactly their prerequisite.** Evidence:
- `대량이체 Batch 거래 마감` / `수행 기준 없음` (BATCH1 §424) — emitted only inside the `exeErrYN` branch that **only this gate sets** (`FILE_DEPOSIT_TRANSFER_BATCH1.java:411-441`). Currently a TODO in `BulkReceptionService`.
- `실시간지급이체 Batch 거래 마감` / `수행 기준 없음` (NAPAS §223) — same, driven by `exeEndYN`/`exeErrYN` from the NAPAS INFO/DTL gate (`NAPAS_REAL_TIME_BATCH.java:184-223`). Currently a TODO in `RealtimeDispatchService`.

The alert transport (`OpsAlertService.notify`) is **already implemented and tested** (see the MSG_API plan). So porting the operating-hours gate is the **only** remaining work to close these 4 alerts — no new alert infrastructure is required.

---

## 9. Recommendation

1. **Track as a separate feature** — `batch-operating-hours-gate` — distinct from MSG_API. Rationale: MSG_API (alert transport) is complete and independent; this is a **dispatch pre-flight gate** touching different services (`BulkReceptionService`, `RealtimeDispatchService`) and the schedule tables. The only coupling is one-directional: the gate *consumes* the already-built `OpsAlertService`.
2. **Sequence with the operating-hours-gate PO decision** — the deferral notes cite "pending PO" (`BulkReceptionService:106`). Do not implement until the PO confirms the gate is wanted for the refactor (legacy currently runs 24h windows / inert).
3. **Bundle the 4 deferred alerts into the same MR** as the gate — they are one unit of work (the gate produces the state; the alert reports it).
4. **Resolve the time-zone question first** (§7) — decide Java-ICT vs a DB-side `@Query` with `NOW()` before writing `EXE_DIV`.
5. **Confirm master-data seeding** for `TB_BATCH_SCHEDULE_INFO/DTL` (banks × channels) — the gate is meaningless without rows, and a missing INFO row means "no criteria" (fail), which would block all dispatch if the table is empty.

---

## 10. Is this a separate feature from MSG_API?

**Yes.** MSG_API / `WGVN_MSG_SEND_001` = **ops-alert transport** (done, flag-gated, tested). This audit's subject = **operating-hours gating** (`EXE_DIV` over `TB_BATCH_SCHEDULE_INFO/DTL`) — a dispatch pre-flight decision, partially ported (data layer only). They intersect at exactly one point: the gate, once ported, calls the existing `OpsAlertService` to emit the 4 cutoff/no-criteria alerts that were deferred in the MSG_API work. Keep them as two features; implement the gate second, reusing MSG_API's transport.

---

## 11. Final status table

| Item | Status | Evidence |
|---|---|---|
| Legacy usage understood | ✅ | INFO/DTL IDO SQL + `FILE_DEPOSIT_TRANSFER_BATCH1.java:271-443,797-827`, `NAPAS_REAL_TIME_BATCH.java:112-223` |
| Tables in refactor | ✅ Done | `V95`/`V96`, inventory `:127-128` |
| Entities + repos + constants | ✅ Done | `TbBatchSchedule*` + repos + `BulkTransferConstants:52-53` |
| `EXE_DIV` / operating-hours service | ✅ **Done** | `OperatingHoursService` (§12a) |
| Wiring into dispatch | ✅ **Done** | `RealtimeDispatchService` + `BulkReceptionService` gates (§12a) |
| 4 deferred alerts | ✅ **Wired** | fire via `OpsAlertService` from both gates (§12a) |
| Intentional deferral? | — (superseded) | was "pending PO"; now implemented |
| Separate feature from MSG_API? | ✅ Yes | different services; reuses `OpsAlertService`; MSG_API transport untouched |

**Bottom line:** the operating-hours gate is now **implemented and wired** — see the implementation record in **§12a**. It reuses the existing schedule entities/repositories and the `OpsAlertService` transport; it is flag-gated OFF and changes no schema and no MSG_API transport. §1–§11 above are the original (pre-implementation) audit and are retained for traceability.

---

## 12a. Implementation record (2026-07-06) — IMPLEMENTED AND WIRED

The operating-hours gate is **implemented and wired**, superseding the "partially ported / deferred" state described in §1–§11.

### What was added / changed

| File | Change |
|---|---|
| `domain/payment/dispatch/service/OperatingHoursDecision.java` | **New** — enum `{ DISPATCH, CUTOFF, NO_CRITERIA, SKIP }`. |
| `domain/payment/dispatch/service/OperatingHoursService.java` | **New** — computes `EXE_DIV` and returns a decision. `evaluateRealtime(outBankCd, antDivCd)` (NAPAS) and `evaluateBulk(outBankCd, presentChannels)` (BATCH1). Reads the existing `TbBatchScheduleInfoRepository` / `TbBatchScheduleDtlRepository`. |
| `domain/payment/dispatch/service/RealtimeDispatchService.java` | **Wired** — gate runs before the 204/202; CUTOFF/NO_CRITERIA → pay-target FAIL + alert + return; SKIP → leave WAIT. |
| `domain/payment/dispatch/service/BulkReceptionService.java` | **Wired** — gate runs after pre-flight validation, before the `PROC_STAT='6'` mark; CUTOFF/NO_CRITERIA → pay-file FAIL + alert + return; SKIP → leave WAIT. Adds `presentChannels(details)` (distinct `ANT_DIV_CD` → channel name) + `NO_CRITERIA_MSG`. |
| `test/.../OperatingHoursServiceTest.java` | **New** — 12 tests (the 9 required cases + INTER short-circuit + N-beats-E precedence + no-channel SKIP). |
| `test/.../RealtimeDispatchServiceTest.java` · `BulkReceptionReceiveTest.java` · `BulkReceptionServiceValidationTest.java` · `BulkReceptionServiceTest.java` | **Updated** — new constructor arg + `DISPATCH` default stub; added the 4 cutoff/no-criteria alert tests + skip tests. |

### The 4 deferred alerts — now wired (through `OpsAlertService`, verbatim Korean)

| Alert | Site |
|---|---|
| `대량이체 Batch 거래 마감.` (BATCH1 cutoff) | `BulkReceptionService` gate → `opsAlertService.notify(...)` |
| `대량이체 Batch 수행 기준 없음.` (BATCH1 no-criteria) | same |
| `실시간지급이체 Batch 거래 마감.` (NAPAS cutoff) | `RealtimeDispatchService` gate → `opsAlertService.notify(...)` |
| `실시간지급이체 Batch 수행 기준 없음.` (NAPAS no-criteria) | same |

MSG_API transport (`OpsAlertService` / `EFS_MSG` / `MsgSendRequest`) is **reused unchanged** — no core transport change.

### Legacy rules preserved
INFO shared key `NAPAS_REAL_TIME_BATCH` for both batches; INFO missing → NO_CRITERIA; `EXE_DIV` E → CUTOFF, N → SKIP (silent), Y → DISPATCH; NAPAS DTL missing → NO_CRITERIA (and `INTER` skips DTL); BATCH1 DTL missing → pass/DISPATCH; BATCH1 channel precedence any-N → SKIP, else any-E → CUTOFF, else DISPATCH. Channel codes verified from the R005 IDO: `E`→EBANK, `V`→VCB, `C`→CITAD, `N`→NAPAS, `S`→SWIFT.

### Documented deviations
- **Timezone:** `EXE_DIV` compares against **`Asia/Ho_Chi_Minh` (ICT)** wall-clock (the project-wide dispatch time zone), where legacy used the DB server-local `NOW()`. Same lexicographic `HHmmss` comparison; documented deviation. **Quantified 2026-07-28 (§12b):** the legacy DB server zone is `Asia/Seoul` (UTC+9), so the deviation is a real **2-hour** shift, and the zone is now a configurable property rather than a hardcoded constant.
- **NAPAS alert field label:** the legacy `TRAN_SEQ :` line was populated with `TRAN_DT` (a copy-paste bug); the refactor uses the actual `tranSeq` value under that label. Display-only; no business change.

### Guarantees
- **Feature-flag controlled:** dispatch remains gated by `WECMS_EFS_ENABLED` / `WECMS_SFTP_ENABLED` (Mock EFS/SFTP by default); alerts remain gated by `WECMS_DISPATCH_ALERT_ENABLED=false`. The gate itself does no live work until those are enabled.
- **No schema change** (existing `V95`/`V96` tables + entities; no new tables/columns). **No SQL/YAML change** in this implementation (the gate computes `EXE_DIV` in Java over the existing repository finders).
- **Fire-and-forget alerts:** each `notify(...)` is `@Async` + swallow; an alert failure can never block or fail dispatch (the gate calls it after the FAIL write).

### Tests
`compileJava` + `compileTestJava` clean; **full test suite green — 0 failures, 0 errors.** New/updated suites: `OperatingHoursServiceTest` 12, `RealtimeDispatchServiceTest` 19, `BulkReceptionReceiveTest` 12, `BulkReceptionServiceValidationTest` 9.

### ⚠ Required before enabling real dispatch (unchanged from §7/§9)
**`TB_BATCH_SCHEDULE_INFO` and `TB_BATCH_SCHEDULE_DTL` must be seeded** (per bank × channel) before enabling real dispatch. **An empty `TB_BATCH_SCHEDULE_INFO` resolves to `NO_CRITERIA` and will block (FAIL) every dispatch.** No seed migration ships for these two tables yet; seed the operating windows (legacy runs effectively 24h) as part of any enablement.

> **Superseded 2026-07-28 (§12b):** the seed now exists as a manual DBA script —
> [`Claude Code (AI)/sql/tb_batch_schedule_seed_refactor.sql`](../../../../Claude%20Code%20%28AI%29/sql/tb_batch_schedule_seed_refactor.sql) —
> and has been **applied to the refactor DEV DB** (3 INFO + 12 DTL verified). SIT / UAT / PROD still pending.

---

## 12b. Follow-up hardening (2026-07-28) — two confirmed defects fixed

Verified read-only against the JEX DEV and refactor DBs. Full record:
[../../deployment/wecms-batch-schedule-seed-review.md §17](../../deployment/wecms-batch-schedule-seed-review.md).

### Defect A — timezone divergence was 2 hours, not hypothetical

`SELECT current_setting('TimeZone')` on the JEX frame DB returns **`Asia/Seoul`** (UTC+9). Legacy's
`TO_CHAR(NOW(),'HH24MISS')` therefore reads a `090000-153000` window as 09:00–15:30 **KST** =
07:00–13:30 Vietnam time. The refactor evaluated in ICT — a two-hour disagreement on every window edge
against a shared UAT/PROD JEX DB.

ICT is retained as the default: the windows are Vietnamese bank cut-offs, and `CutoffValidator.VIETNAM_ZONE`
is already `Asia/Ho_Chi_Minh`, so a KST dispatch gate would fail payments the request-time gate had just
accepted. The zone is now explicit and overridable:

```java
// OperatingHoursService
@Value("${wecms.dispatch.operating-hours.zone:Asia/Ho_Chi_Minh}")
private String operatingHoursZone = "Asia/Ho_Chi_Minh";
```

In-code default + absent from every YAML ⇒ an unset property cannot abort startup (audit H-1). Set it to
`Asia/Seoul` for bug-for-bug legacy parity if a co-hosted legacy batch ever demands it.

### Defect B — `ANT_DIV_CD` width inconsistency failed real payments

`TB_PAY_TARGET.ANT_DIV_CD` is `bpchar(5)` and carries **both** widths. Live JEX DEV distribution:
`CITAD` 39 · `NAPAS` 30 · `INTER` 28 · **`'E'` 2 · `'S'` 2**. The four 1-char rows:
`(970424,'E')` ×2 → `PROC_STAT='3'` **FAIL** (2025-09-18); `(970424,'S')` ×2 → `PROC_STAT='9'`
**stranded** (2025-09-19).

Cause: `OperatingHoursService` used the raw column value as the schedule key, so `'N'` never matched the
`'NAPAS'` row → `NO_CRITERIA` → FAIL. It also never stripped bpchar padding. Every other consumer already
treated the widths as equivalent (`TransferNetwork.parse`), so this was an internal inconsistency.

Fix: new [`AntDivChannel`](../../../src/main/java/com/kosign/wecms/domain/payment/dispatch/constant/AntDivChannel.java)
is the single canonical vocabulary —

| Method | Consumer | Behaviour |
|---|---|---|
| `fromStorageCode` | `BulkReceptionService.presentChannels` | strict `E/V/C/N/S` → name; `null` otherwise (legacy `ETC`, ungated) |
| `canonical` | `OperatingHoursService.evaluateRealtime` | trims padding, expands 1-char aliases, passes known names, returns unknown codes **verbatim** so they still fail closed |

`BulkReceptionService`'s private `CHANNEL_BY_ANT_DIV` map was removed in favour of the shared class. Bulk
behaviour is unchanged (`TB_PAY_FILE_DATA.ANT_DIV_CD` is `bpchar(1)`).

**Not changed on purpose:** an unknown code (e.g. HAENGSUNG's unresolved `'T'`) is never coerced onto a
channel — it keeps failing loudly. **Still a PO decision:** `'S'`→`SWIFT` / `'E'`→`EBANK` resolve to keys
with no realtime schedule row, so they still fail; whether those channels should be dispatchable on the
NAPAS realtime rail is routing, not windows.

### Verification
`./gradlew compileJava compileTestJava` clean · `./gradlew test` **1935 tests, 0 failures, 0 errors** ·
`OperatingHoursServiceTest` **12 → 17** tests (added: 1-char NAPAS alias, 1-char CITAD alias,
bpchar-padded value, padded `INTER` short-circuit, unknown code fails closed).
