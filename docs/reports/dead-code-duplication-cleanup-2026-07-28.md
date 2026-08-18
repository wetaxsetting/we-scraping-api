# Dead-Code & Duplication Cleanup — OperatingHours / BatchSchedule / Dispatch

**Scope:** `wecms-api/src/main/java/.../domain/payment/dispatch/**`, `.../domain/operation/scheduler/`,
`.../domain/payment/haengsung/{batch,dispatch}/`, and the five per-module `*DispatchExecutor` classes.
**Date:** 2026-07-28 · **Branch:** `wecms-ui-fixes` · **Method:** whole-tree reference count per class,
per method, and per constant across `src/main` + `src/test`, with static-import and Spring-wiring checks.

> **Nothing in this document has been changed.** Per workspace rule §5 (*Required analysis before
> modifying a copied legacy table*) and `feedback_preserve_existing_code.md`, this is analysis only —
> each item below needs sign-off before removal. Items touching legacy-compat surfaces (repository
> finders over legacy tables, legacy `WCConst` ports) are called out explicitly.

---

## 1. Summary

| Category | Confirmed dead | Notes |
|---|---|---|
| Unused **services** | **1** (`TransferDispatchExecutor`, 398 LOC) | Superseded by 5 per-module executors |
| Unused **repositories** (whole interface) | **0** | All 14 dispatch repositories are injected |
| Unused **repository methods** | **5** | Derived finders, never called |
| Unused **constants** | **12 of 14** in `BulkTransferConstants` | 5 of them re-declared locally elsewhere |
| Unused **DTOs** | **0** | All EFS + callback DTOs are wired |
| Unused **enum constants** | **0** | `ProcStat` / `ProcStep` / `ApiTranTp` fully used |
| **Duplicate logic** clusters | **8** | ~14 copy-pasted helpers across 5 executors + 4 result services |
| False positives (Spring-wired, **keep**) | 11 classes | Section 6 |

`OperatingHoursService`, `OperatingHoursDecision`, `TbBatchScheduleInfo/Dtl` (+ their `*Id` and
repositories) are **all live** — reached from `RealtimeDispatchService.evaluateRealtime` and
`BulkReceptionService.evaluateBulk`. No dead classes in the OperatingHours / BatchSchedule area.
`haengsung/batch/` and `haengsung/dispatch/` are also fully live.

---

## 2. Unused services

### 2.1 `TransferDispatchExecutor` — orphaned `@Service` (HIGH confidence)

`dispatch/service/TransferDispatchExecutor.java` (398 LOC)

Its own javadoc still says *"Shared executor invoked by every payment module's dispatch service
(Internal Transfer / Vendor / Expense / Payroll)"*, but that is no longer true. Each module grew a
dedicated executor and none of them delegates back:

| Module | Executor actually injected | Injection site |
|---|---|---|
| Expense | `ExpenseDispatchExecutor` | `ExpensePaymentService.java:168` |
| Vendor | `VendorDispatchExecutor` | `VendorPaymentService.java:168` |
| Payroll | `PayrollDispatchExecutor` | `PayrollPaymentService.java:161` |
| Overseas | `OverseasDispatchExecutor` | `OverseasPaymentService.java:108` |
| Internal Transfer | `InternalTransferDispatchExecutor` | `InternalTransferDispatchService.java:77` |
| — | **`TransferDispatchExecutor`** | **none** |

Evidence:
- Zero `private final TransferDispatchExecutor` / `transferDispatchExecutor` anywhere in `src/main`.
- No bean-name or qualifier reference in any `.java` / `.yml` / `.properties`.
- All 12 remaining `src/main` occurrences are **javadoc or comment prose** — e.g.
  `VendorDispatchExecutor.java:48` explicitly records *"`TransferDispatchExecutor` file path diverged
  from legacy in six places"*, which is why the per-module copy was written.
- The only executable references are its own test,
  `TransferDispatchExecutorRealtimeTest.java:40,56,60`.

It is still annotated `@Service`, so Spring instantiates it on every startup — a live bean that
nothing can ever call.

**Action:** delete `TransferDispatchExecutor.java` + `TransferDispatchExecutorRealtimeTest.java`.
No cascade: its collaborators (`TbPayTargetRepository`, `TbPayFileMstRepository`,
`TbPayFileDataRepository`, `AcctMstrRepository`, `LegacyFunctionRepository`) are all heavily used
elsewhere.

**Before deleting**, confirm the divergences noted in `VendorDispatchExecutor.java:48` were carried
into all five per-module executors — this class is the last in-tree copy of the original shared
dispatch path, and once removed the legacy `PymnHelper.java:2142-2369` mapping survives only in the
five copies. If it is being kept deliberately as a reference implementation, mark it
`@Deprecated` + drop `@Service` rather than leaving a phantom bean.

---

## 3. Unused repository methods

No repository *interface* is dead. These five **derived finders** have no caller in `src/main` or
`src/test` — declaration line only:

| Repository | Method | Line |
|---|---|---|
| `TbBatchScheduleDtlRepository` | `findByBatchIdAndOutBankCd(String, String)` → `List` | :25 |
| `TbFirmBatApiTranRepository` | `findByTranDtAndBizKeyAndFirmFileNm` | :27 |
| `TbFirmFileMstRepository` | `findByTranDtAndProcStepAndProcStat` | :34 |
| `TbFirmFileMstRepository` | `findByTranDtAndBizKey` | :60 |
| `TbFirmFileRsltRepository` | `findByTranDtAndBizKeyAndFirmFileNmOrderBySeqAsc` | :24 |

Note on the first: `TbBatchScheduleDtlRepository` declares **two** finders and only the three-arg
`findByBatchIdAndOutBankCdAndAntDivCd` is used (`OperatingHoursService.java:138,192`). The two-arg
`List` variant is unreachable.

**Risk: low.** These are read-only derived queries over legacy tables — removing the Java method
changes no DDL, no column mapping, and no migration surface. Rule §1 is not engaged (no table or
column is being renamed or dropped).

**Caveat:** `findByTranDtAndProcStepAndProcStat` looks like the natural entry point for a
result-sweep that `ResultCheckJob` / `StrandedRecoveryJob` would use once those crons are enabled.
Both jobs are currently cron-disabled pending the PO coexistence decision. Check with the batch owner
whether it is a placeholder for that path before deleting — it may be cheaper to keep the four
`TbFirmFile*` finders than to re-derive them.

---

## 4. Unused constants

### 4.1 `BulkTransferConstants` — 12 of 14 constants dead

`dispatch/constant/BulkTransferConstants.java`. There are **no static imports** of this class anywhere;
every use is fully qualified, so the counts below are exact.

| Constant | Uses | Status |
|---|---|---|
| `BATCH_ID_NAPAS_REAL_TIME` | 3 | **live** (`OperatingHoursService:109,139,166`) |
| `BATCH_ID_FILE_DEPOSIT_TRANSFER_1` | 1 | **live** (`OperatingHoursService:193`) |
| `BANK_CD_SHINHAN` | 0 | dead — re-declared in `DispatchPacer:28` |
| `BANK_CD_WOORI` | 0 | dead — re-declared in `DispatchPacer:30`, `RealtimeDispatchService:88`, `RealtimeTransferService:76` |
| `PER_DELAY_DEFAULT` | 0 | dead — re-declared in `DispatchPacer:33` |
| `PER_DELAY_SHINHAN` | 0 | dead — re-declared in `DispatchPacer:34` |
| `PER_DELAY_WOORI` | 0 | dead — re-declared in `DispatchPacer:35` |
| `CURRENCY_CD_VND` | 0 | dead — re-declared as `CURRENCY_VND` in `RealtimeDispatchService:90`, `RealtimeTransferService:78` |
| `ORG_ST_OK` | 0 | dead — re-declared in `ResultCheckService:76` |
| `TAR_FINC_MDA_DIV_ACCT` | 0 | dead — re-declared in `RealtimeDispatchService:85` |
| `TAR_FINC_MDA_DIV_CARD` | 0 | dead, no replacement |
| `ORG_ST_WAIT` | 0 | dead, no replacement |
| `ORG_ST_STOP` | 0 | dead, no replacement |
| `ORG_ST_OUT` | 0 | dead, no replacement |

This is the **duplicate-constants** finding: the class was ported verbatim from legacy
`consts.WCConst`, then six consumers each re-declared the value they needed as a `private static
final` instead of importing it. Every local copy carries a `/** Legacy {@code WCConst.X} */` javadoc,
so the intent was clearly to use the shared class.

**Recommended action (preferred):** do *not* delete the constants — point the six local copies at
`BulkTransferConstants` and delete the local re-declarations. That removes the duplication, keeps the
legacy `WCConst` port intact as the single named source, and is the change that most reduces drift.
`PER_DELAY_*` needs a type note: `BulkTransferConstants` declares them `int`, `DispatchPacer` uses
`long` — widening is safe, but `perBankDelay` returns `long`, so keep the return type.

**Alternative:** if the team prefers per-class constants, delete the 12 unused entries and let
`BulkTransferConstants` shrink to the two `BATCH_ID_*` schedule keys — but that loses the legacy
`WCConst` traceability the file was created for. Recommend the first option.

### 4.2 `AntDivChannel` — public vocabulary used only internally (LOW priority, keep)

`EBANK`, `VCB`, `CITAD`, `NAPAS`, `SWIFT`, `CODE_EBANK`, `CODE_VCB`, `CODE_CITAD`, `CODE_NAPAS`,
`CODE_SWIFT` have **zero external references** — only `INTER` (`OperatingHoursService:134`),
`canonical()` and `fromStorageCode()` / `toStorageCode()` are called from outside.

**Do not remove.** These are the declared channel vocabulary backing the three mapping tables inside
the class, and they are the documented contract for callers writing to `bpchar(1)`
(`toStorageCode` javadoc). Narrowing them to `private` is possible but has no real benefit and would
break `PayrollDispatchExecutorAntDivCdTest`. Listed here only so the zero-count does not get
re-flagged by the next sweep.

---

## 5. Duplicate logic

Eight clusters, ordered by payoff. The first two are mechanical and low-risk; the rest need a
behaviour check first because the copies have **diverged**.

### 5.1 Byte-identical helpers across the five `*DispatchExecutor` classes

Method bodies hashed after whitespace/comment strip — identical hash means identical code.

| Helper | Copies | Files |
|---|---|---|
| `sameBankGroup(String,String)` | **4 identical** | Expense, InternalTransfer, Payroll, Vendor |
| `engAbrvatNm(String)` | **4 identical** | Expense, InternalTransfer, Payroll, Vendor |
| `resolveAntDivCd(String, ScdulPymnSumrDetl)` | **2 identical** | Expense, Vendor |
| `eq(String,String)` | 4 copies | all executors |
| `trimToNull(String)` | **8 copies** across `src/main` | executors + others |

**Action:** extract to a package-private helper in `dispatch/service/` (e.g. `DispatchFieldSupport`)
or fold `sameBankGroup` / `engAbrvatNm` into the existing bank-code lookup service. `eq` and
`trimToNull` are trivial and already exist in various forms — consolidate on one.

### 5.2 Byte-identical `DispatchCountRow` → `Map<String,long[]>` block in 4 result services

Identical seven-line body at:
- `ExpensePaymentResultService.java:132-138`
- `InternalTransferResultService.java:182-188`
- `PayrollPaymentResultService.java:144-150`
- `VendorPaymentResultService.java:139-145`

```java
List<DispatchCountRow> raw = dispatchCountRepository.countByBatch(bizKey, ids);
Map<String, long[]> out = new HashMap<>(raw.size());
for (DispatchCountRow r : raw) {
    long succ = r.getSuccCnt() != null ? r.getSuccCnt() : 0L;
    long err  = r.getErrCnt()  != null ? r.getErrCnt()  : 0L;
    out.put(r.getScdulPymnNo(), new long[] { succ, err });
}
```

**Action:** move onto `DispatchCountRepository` as a default method, or add a small
`DispatchCountReader` — the four call sites then become one line each. Zero behaviour change.

### 5.3 Near-identical, **diverged** helpers — inspect before merging

| Helper | Copies | Divergence |
|---|---|---|
| `resolveBankPrtnId` | 5, **all different** | 291–365 chars; Overseas is the outlier (365) |
| `resolveOwnerNm` | 5, **all different** | 226–529 chars; Overseas is much shorter (226) |

These are *not* safe to merge blindly. Each was tuned per module against its legacy JSP counterpart —
see `project_overseas_payment_excel_download_parity_gap.md` and the per-module parity audits. Merging
them requires reading the five legacy flows first (workspace north-star rule: *understand the full
legacy flow before writing any code*). **Recommend: leave as-is, or merge only after a parity audit.**
Flagged so the divergence is documented rather than accidental.

### 5.4 Duplicated date/zone constants

`DT8` / `TM6` / `DTM14` `DateTimeFormatter`s are re-declared in all five executors, and
`ZoneId.of("Asia/Ho_Chi_Minh")` appears in **34 files** under `src/main`.

**Action:** one shared `WecmsTime` (or reuse the existing zone constant if one already exists in
`common/`). Note this interacts with the timezone work in `project_banking_datetime_tz_locale.md` /
`project_batch_schedule_tz_antdiv_fixes.md` — `OperatingHoursService` deliberately takes its zone from
the overridable `wecms.dispatch.operating-hours.zone` property and **must not** be folded into a
hardcoded shared constant.

### 5.5 Duplicated `"VND"` literal

`CURRENCY_VND` is declared twice (`RealtimeDispatchService:90`, `RealtimeTransferService:78`), `VND`
once more (`FxConverter:35`), plus `BulkTransferConstants.CURRENCY_CD_VND` unused — and there are
**65 bare `"VND"` string literals** under `domain/payment`. Same fix as §4.1.

### 5.6 `OperatingHoursService.evaluateRealtime` / `evaluateBulk` shared prologue

Both methods open with the same 12-line block: load `TbBatchScheduleInfo` by
`(BATCH_ID_NAPAS_REAL_TIME, outBankCd)` → `null` ⇒ `NO_CRITERIA` → `exeDiv` → `E` ⇒ `CUTOFF` →
`N` ⇒ `SKIP` (`:108-120` and `:165-177`).

**Note:** `evaluateBulk` reads the INFO header with `BATCH_ID_NAPAS_REAL_TIME` but its DTL rows with
`BATCH_ID_FILE_DEPOSIT_TRANSFER_1` (`:193`). That mismatch may be intentional legacy parity
(`FILE_DEPOSIT_TRANSFER_BATCH1.java:269-448`) — **verify against the legacy source before extracting
the prologue**, because a naive extraction would invite "fixing" it. Low priority; the duplication is
small and the risk of touching it is higher than the gain.

### 5.7 `BulkExecutionResultService` / `BulkReceptionResultService` ack + audit paths

Both build a `FirmBankingResultCd`-coded ack and write a `DispatchApiTranAuditWriter` row with the
same `SUCC` / `MSG_SUCC` / `ETC_ERR` shape (`BulkExecutionResultService:266-276`,
`BulkReceptionResultService:296-297`). Partially shared already via `DispatchApiTranAuditWriter`.
Low payoff — the two callbacks have genuinely different branch sets. **Recommend: no action.**

### 5.8 `BulkExecutionResultServiceTest2` — suspicious filename

`src/test/.../callback/service/` contains `BulkExecutionResultServiceTest2.java` with no
`BulkExecutionResultServiceTest.java` beside it. The `2` suffix suggests a rename that never
completed. **Action:** rename to `BulkExecutionResultServiceTest` (cosmetic, no behaviour change).

---

## 6. Looks dead, is NOT — do not delete

These have zero or near-zero *code* references but are reached through Spring wiring. Listing them so
the next automated sweep does not re-flag them.

| Class | Refs | Why it is live |
|---|---|---|
| `EfsClientConfig` | **0** | `@Configuration` producing the `efsRestClient` bean, injected by `@Qualifier("efsRestClient")` at `RestClientEfsApiClient:42` |
| `StrandedRecoveryJob` | **0** | `@Component` + `@Scheduled`; cron defaults to `"-"` (never fires) pending the PO/legacy-coexistence decision — intentionally inert, not dead |
| `ResultCheckJob` | 2 (javadoc) | Same pattern; cron disabled by default per `project_batch_event_driven_tobe_recovery.md` |
| `BulkExecutionResultController` | 0 | `@RestController` — HTTP entry point |
| `BulkReceptionResultController` | 0 | `@RestController` — HTTP entry point |
| `MockSftpClient` / `JschSftpClient` | 3 / 2 | `@ConditionalOnProperty` pair on `wecms.dispatch.sftp.enabled` |
| `MockEfsApiClient` / `RestClientEfsApiClient` | 5 / 3 | `@ConditionalOnProperty` pair on `wecms.dispatch.efs.enabled` |
| `CallbackIpWhitelistFilter` | 2 | Registered in `SecurityConfig:93` |
| `OpsAlertAsyncConfig` | 2 | `@Async(OpsAlertAsyncConfig.EXECUTOR)` at `OpsAlertService:125` |
| `AntDivChannel` internals | 0 ext | See §4.2 |

Also verified live and **not** listed as cleanup: every `Efs2xx/3xx` request+response DTO,
`EfsCallbackEnvelope`, `MsgSendRequest`, `TbVanApiInfo` + repository, `DispatchCountRow`
(4 consumers), `ProcStatKey`, all `ProcStat` / `ProcStep` / `ApiTranTp` enum constants, and all
`haengsung/batch` + `haengsung/dispatch` classes.

---

## 7. Suggested order of work

| # | Item | Risk | Needs approval |
|---|---|---|---|
| 1 | §5.2 — extract the `DispatchCountRow` block | none | no |
| 2 | §5.1 — extract `sameBankGroup` / `engAbrvatNm` / `resolveAntDivCd` / `eq` / `trimToNull` | none (identical bodies) | no |
| 3 | §5.8 — rename `BulkExecutionResultServiceTest2` | none | no |
| 4 | §4.1 — point the 6 local constant copies at `BulkTransferConstants` | low | no |
| 5 | §3 — remove the 5 unused repository finders | low | **yes** — batch owner, re §3 caveat |
| 6 | §2.1 — remove `TransferDispatchExecutor` + its test | medium | **yes** — PO / batch owner |
| 7 | §5.4 — consolidate zone/formatter constants | medium | **yes** — overlaps open TZ work |
| 8 | §5.3, §5.6 — diverged helpers | high | **yes** — needs a legacy parity audit first |

Items 1–4 are pure refactors with no contract, DTO, or wire-format change, so §4 of the workspace
DB rules (API/WEB change coordination) is not engaged — **no `wecms-web` change is required for any
item in this document**.
