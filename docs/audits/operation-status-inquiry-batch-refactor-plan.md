# Operation Status Inquiry Batch — Refactor Implementation Plan

> **System:** WeCMS refactor (Spring Boot, `wecms-api`). **Status: PLAN ONLY — no code written.**
> **Source audit:** [operation-status-inquiry-batch-legacy-audit.md](operation-status-inquiry-batch-legacy-audit.md).
> **Legacy targets:** `bcs.jex.wabooks.oprt.OPRT_0001_001` (seed) + `bcs.jex.wabooks.oprt.OPRT_0010_001` (refresh/update).
> **Reference pattern:** `ExchangeRateBatchScheduler` + `ExchangeRateBatchService` (flag-gated OFF, ShedLock, per-job `@Transactional`).
> **Date:** 2026-06-30.

---

## 0. Goal & guardrails

Port the two legacy scheduled batch jobs into the refactor as a **flag-gated, OFF-by-default** scheduled pair that reuses the existing on-demand Operation Status assets. The batch is "`OperationStatusService` per-tenant logic, run across every F/M tenant, on a schedule." Nothing ships ON until cutover (§6).

Guardrails (all from the audit + repo rules):
- **No new schema / migration** — every touched table is already in the inventory and mapped.
- **Funnel rule** — `EXT_TRAN_CALL_HIS.seq_no` only via `LegacyFunctionRepository` → `wb_get_next_val`.
- **Parity** — F/M tenant filter; create-only seed; update-only refresh; no notification writes; no `NXSIAS_MODULE_TRAN_LOG_HIS`; no `System.out`.
- **Coexistence** — legacy JEX batch still owns `OPRT_STTS_INFO` on the shared DB; the refactor batch must not double-write until the env owns it.

---

## 1. Scheduler design — `OperationStatusBatchScheduler`

New class: `domain/operation/scheduler/OperationStatusBatchScheduler.java`. Mirrors `ExchangeRateBatchScheduler` exactly.

```java
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "wecms.operation-status.batch",
        name   = "enabled",
        havingValue   = "true",
        matchIfMissing = false)               // OFF by default — bean does not exist unless flag=true
public class OperationStatusBatchScheduler {

    private static final String VIETNAM_TZ_ID = "Asia/Ho_Chi_Minh";
    private final OperationStatusBatchService batchService;

    /** OPRT_0001_001 — seed/discovery. CRON PLACEHOLDER until batch-admin confirmed (Risk R-1). */
    @Scheduled(cron = "${wecms.operation-status.batch.seed-cron:0 0 2 * * ?}", zone = VIETNAM_TZ_ID)
    @SchedulerLock(name = "oprt-status-seed",
                   lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void seed()    { batchService.seedAllTenants(); }

    /** OPRT_0010_001 — refresh/update. CRON PLACEHOLDER until batch-admin confirmed (Risk R-1). */
    @Scheduled(cron = "${wecms.operation-status.batch.refresh-cron:0 0 3 * * ?}", zone = VIETNAM_TZ_ID)
    @SchedulerLock(name = "oprt-status-refresh",
                   lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void refresh() { batchService.refreshAllTenants(); }
}
```

Design notes:
- **Flag-gated OFF by default** — `@ConditionalOnProperty(... matchIfMissing=false)`. When `wecms.operation-status.batch.enabled` is absent/false, the bean is never created → **no cron fires**. The *service* bean (§2) is always present so the on-demand path / DI keep working regardless of the gate (same split as FX).
- **ShedLock** — `@SchedulerLock` co-located with `@Scheduled` (ShedLock runs in scheduler-proxy mode via `@EnableSchedulerLock` in `SchedulerLockConfig`; the lock must annotate the scheduled method, not the delegate). `shedlock` table already exists (refactor-owned infra in the inventory). `lockAtMostFor=PT30M` covers a slow multi-tenant GDT sweep; `lockAtLeastFor=PT1M` prevents rapid re-fire. Two distinct lock names so seed and refresh never block each other.
- **Cron placeholder** — both crons are externalised with a placeholder default (`seed-cron` 02:00 ICT, `refresh-cron` 03:00 ICT — arbitrary, clearly provisional). **These are not the confirmed legacy cadence** (Risk R-1 / OQ-1 in the audit). Anchored to `Asia/Ho_Chi_Minh` to match every other WeCMS batch. Replace the defaults once `TB_BATCH_SCHEDULE_INFO` / `TB_BATCH_SCHEDULE_DTL` are read on the live system.
- **No `@Scheduled` on the service** — triggers live only on this gated class (same rule as `ExchangeRateBatchService`).

**Config keys (no new yml file — add under the base `application.yml` `wecms:` tree, commented OFF):**
```yaml
wecms:
  operation-status:
    batch:
      enabled: false            # OFF by default — flip on only in an env that owns OPRT_STTS_INFO
      seed-cron:    "0 0 2 * * ?"   # PLACEHOLDER — confirm against legacy batch-admin (OQ-1)
      refresh-cron: "0 0 3 * * ?"   # PLACEHOLDER — confirm against legacy batch-admin (OQ-1)
```

---

## 2. Service design — `OperationStatusBatchService`

New class: `domain/operation/service/OperationStatusBatchService.java`. Always present (not gated). Holds the cross-tenant fan-out + the per-tenant transaction boundary.

**Public methods (called by the scheduler, and reusable on-demand):**

| Method | Legacy equivalent | Verb | Source set |
|---|---|---|---|
| `seedAllTenants()` | `OPRT_0001_001` | create-only | distinct `BUSA_DOC_MSTR.TRCO_TAX_ID` per tenant (`hand_reg_yn='N'`) |
| `refreshAllTenants()` | `OPRT_0010_001` | update-only | existing `OPRT_STTS_INFO` rows per tenant |
| `seedTenant(bizKey)` / `refreshTenant(bizKey)` | optional `BIZ_KEY` input | — | single-tenant variants (mirror the legacy optional `BIZ_KEY` param) |

**Fan-out (both jobs):**
```java
List<BusiInfm> tenants = busiInfmRepo.findByDelYnAndBusiSttsAndPrdtTypeIn(
        "N", BusiStts.NORMAL.code(), List.of("F", "M"));   // F/M ONLY — see §5
for (BusiInfm t : tenants) {
    try { refreshTenant(t.getBizKey(), t.getEml()); }       // own @Transactional
    catch (Exception e) { log.warn("OPRT refresh skipped tenant {}", t.getBizKey(), e); }
}
```

**Per-tenant transaction (refactor improvement over legacy):**
- Each `seedTenant(...)` / `refreshTenant(...)` is annotated `@Transactional` — **one transaction per tenant**, not one spanning all tenants.
- This **closes the legacy coarse-rollback risk** (audit §8: a single bad tenant aborts the whole legacy run). The cross-tenant loop catches per-tenant exceptions and continues. Document this as a deliberate, parity-safe divergence (it changes failure isolation only, not the written data).

**`refreshTenant(bizKey, regUserHint)` — body (= `OPRT_0010_001`):**
1. `rows = oprtSttsInfoRepo.findAllByBizKey(bizKey)`; if empty → return (legacy `continue`).
2. `taxCodes = rows.map(taxcode)`.
3. Allocate `seqNo = legacyFunctions.nextExtTranCallHisSeqNo("BI09", today)`; insert `EXT_TRAN_CALL_HIS` pre-call row (`web_svc_id = "OPRT_0010_001"` — see §5 note on the slug).
4. `result = osmcClient.lookup(taxCodes, pad7(seqNo))`.
5. Patch the `EXT_TRAN_CALL_HIS` row with `result.errorCode()` / truncated `errorMessage()`.
6. If `result.isSuccess()`: index details by taxCode; for each existing row whose taxCode matches a returned detail → set `companystatus` + `modDtm` only, `save`. **Do not** touch name/address/representative/searchDatetime (update-only parity).
7. No notification, no NXISAS row (§5).

**`seedTenant(bizKey, regUserEml)` — body (= `OPRT_0001_001`):**
1. `taxCodes = busaDocMstrRepo.findDistinctTrcoTaxIdByBizKeyAndHandRegYn(bizKey, "N")` (**one new repository query — no schema change**, see §3/§4); if empty → return.
2. Allocate `seqNo`; insert `EXT_TRAN_CALL_HIS` (`web_svc_id = "OPRT_0001_001"`).
3. `result = osmcClient.lookup(taxCodes, pad7(seqNo))`; patch the audit row.
4. For each returned detail whose taxCode matches the search: `if (!repo.existsById(new OprtSttsInfoId(bizKey, taxCode)))` → build a **full** `OprtSttsInfo` (taxCode, companyName, companyStatus, officeAdd, representativeFirstName, searchDatetime) with `regId`/`regDtm` stamped from the tenant `EML`, then `save`. **Existing rows skipped** (create-only parity).

**Reuse vs. extract:** the per-tenant refresh body is the same shape as `OperationStatusService.bulkUpdate(...)` minus the `TenantContext` dependency and the tenant loop. **Recommended:** extract a tenant-explicit helper so both the on-demand endpoint and the batch call one code path (avoid duplicating the upsert/audit logic). If extraction is deferred, the batch service replicates the proven `bulkUpdate` steps verbatim and a follow-up consolidates.

---

## 3. Reuse of existing components

| Component | Reuse | Change needed |
|---|---|---|
| `OperationStatusService` (`domain/operation/service`) | The per-tenant refresh/upsert + audit pattern (`bulkUpdate`, `create`, `resolveStatusCd`, `stampAudit`, `generateUniqueKey`, `truncate`). | Optional: extract a `TenantContext`-free helper so batch + endpoint share one path. No behavioural change to the endpoint. |
| `OsmcGdtClient.lookup(taxCodes, uniqueKey)` | The GDT bridge call + response parsing (`OsmcGdtResult` / `OsmcGdtRegistration` / `OsmcGdtSearchOutcome`). Already models `TRCO_TRCO001_00003` / `BI09`. | **None.** Used as-is. |
| `OprtSttsInfoRepository` | `findAllByBizKey(bizKey)` (refresh source); `existsById(OprtSttsInfoId)` (seed dup guard); `findById` / `save`. | **None.** |
| `ExtTranCallHisRepository` | Pre-call audit row insert + post-call patch. | **None.** |
| `LegacyFunctionRepository` | `nextExtTranCallHisSeqNo("BI09", refDate)` → `wb_get_next_val` (funnel rule). | **None** (helper already exists, used by `OperationStatusService`). |
| `BusiInfmRepository` | `findByDelYnAndBusiSttsAndPrdtTypeIn("N", BusiStts.NORMAL.code(), List.of("F","M"))` — exact F/M fan-out finder. | **None** (method exists; pass `["F","M"]`, NOT `["F","M","H"]`). |
| `BusaDocMstrRepository` (`domain/einvoice/repository`) | Seed tax-code source. | **One new query method** — `findDistinctTrcoTaxIdByBizKeyAndHandRegYn(...)` (or `@Query`). No new table/entity (BUSA_DOC_MSTR already mapped). |
| `BusiStts` enum (`common/enums`) | `NORMAL.code()` = `"N"` for the filter. | **None** (confirm enum/code present; add only if missing — legacy `busi_stts='N'`). |

---

## 4. Database mapping

**No new schema. No new migration.** All tables in the legacy-table-inventory (audit §10).

| Table | R/W | Job | Columns touched |
|---|---|---|---|
| `BUSI_INFM` | Read | both | filter `del_yn='N'`, `busi_stts='N'`, `prdt_type IN ('F','M')`; read `BIZ_KEY`, `EML` |
| `BUSA_DOC_MSTR` | Read | seed | distinct `TRCO_TAX_ID` where `biz_key=?` AND `hand_reg_yn='N'` AND `trco_tax_id` non-blank |
| `OPRT_STTS_INFO` | Read | refresh | `findAllByBizKey` (all cols, for taxCode list) |
| `OPRT_STTS_INFO` | **Write** | seed = **INSERT** | `BIZ_KEY, TAXCODE, COMPANYNAME, COMPANYSTATUS, OFFICEADD, REPRESENTATIVEFIRSTNAME, SEARCHDATETIME, REG_ID(=EML)/REG_DTM, MOD_ID/MOD_DTM` |
| `OPRT_STTS_INFO` | **Write** | refresh = **UPDATE** | `COMPANYSTATUS`, `MOD_DTM` **only** |
| `EXT_TRAN_CALL_HIS` | **Write** | both = INSERT + patch | `SVC_ID='BI09'`, `REF_DATE=today`, `SEQ_NO`(wb_get_next_val), `WEB_SVC_ID`(task id), then `RSLT_CD`/`ERR_CONT` |
| `API_TRAN_LOG_HIS` | (Write) | both | written inside the gateway client leg (transport audit) — not the refactor's responsibility to add; matches legacy OSMC util |

**Status mapping:** store `COMPANYSTATUS` **verbatim** (no enum). The 3-state `resolveStatusCd` collapse stays a read/display concern in `OperationStatusService.toRow` — the batch does **not** classify on write (legacy parity).

---

## 5. Parity rules (must hold)

| Rule | Implementation |
|---|---|
| **No notification writes** | Do NOT call any `GENL_NOTI_*` writer / `TrcoHelper.getBatchTitle/Content`. Legacy batch BCS never writes them (audit §4/§8). |
| **No `NXSIAS_MODULE_TRAN_LOG_HIS` writes** | The batch path writes only `EXT_TRAN_CALL_HIS`. Do NOT call `writeNxisasAudit(...)` (that is the *web* `bulkUpdate` behaviour, absent from the legacy batch). |
| **No `System.out`** | Use SLF4J (`@Slf4j`). The legacy `System.out.println("ITEM>>>>>>")` debug lines are NOT reproduced. |
| **F/M tenant filter** | `findByDelYnAndBusiSttsAndPrdtTypeIn("N","N",List.of("F","M"))` — `H`/others excluded. Do NOT pass `H` (that's the FX finder's set, not OPRT's). |
| **Create-only seed** | `seedTenant` guards with `existsById(...)` and only inserts; existing rows untouched. |
| **Update-only refresh** | `refreshTenant` updates `COMPANYSTATUS` + `MOD_DTM` only; never inserts a missing row, never overwrites name/address/representative/searchDatetime. |
| **Funnel** | `EXT_TRAN_CALL_HIS.seq_no` via `LegacyFunctionRepository.nextExtTranCallHisSeqNo` only — no `@Query(nativeQuery=true)` calling `wb_get_next_val` elsewhere. |
| **`WEB_SVC_ID` value** | Open: legacy writes the literal task id (`OPRT_0001_001` / `OPRT_0010_001`) into `EXT_TRAN_CALL_HIS.WEB_SVC_ID`. The refactor slug rule prefers kebab slugs, BUT `EXT_TRAN_CALL_HIS` rows coexist with live JEX on the shared DB. **Decision needed (OQ in §6):** keep the legacy task id for cross-system audit continuity, or use a slug (`operation-status-batch-seed` / `…-refresh`). Default recommendation: **keep the legacy task id** for these audit rows only (coexistence), document as the exception. |

---

## 6. Risks & confirmations needed

| ID | Risk / question | Impact | Action before build/enable |
|---|---|---|---|
| **R-1 / OQ-1** | **Legacy schedule cadence** is not in the repo (lives in `TB_BATCH_SCHEDULE_INFO` / `_DTL`). The plan ships placeholder crons. | Wrong cadence = under/over-polling GDT. | Read the batch-admin schedule tables on the live system (or PO confirm). Replace `seed-cron` / `refresh-cron` defaults before enabling. **Blocks enable, not build.** |
| **R-2 / OQ-2** | **`API_INFM` / gateway reachability** — GDT URL/headers come from DB config; a headless server may hit Coocon engine-origin/licensing limits (per memory `reference_nxisas_local_engine_origin`). | Batch silently no-ops (`WBE_OSMC_NOT_CONFIGURED` / transport error) if the gateway is unreachable. | Confirm `wecms.osmc.*` (url/api-key/api-id) is set for the target env and that the server can reach the Coocon smart-gateway headlessly. **Blocks enable.** |
| **R-3 / OQ-5** | **Product-type scope** — legacy batch is F/M only; confirm HAENGSUNG (`H`) stays excluded. | Including `H` would poll out-of-scope tenants. | PO confirm (memory `feedback_product_type_set_fmh`). Keep `List.of("F","M")`. |
| **R-4 / OQ-7** | **Double-write at cutover** — refactor + live JEX both writing `OPRT_STTS_INFO` on the shared DB. | Race / duplicate audit rows. | Keep `enabled=false` everywhere until an env genuinely owns this batch; at cutover, **disable the legacy JEX batch in that env first**, then flip the flag. Same coexistence model as FX. |
| **R-5** | **`EXT_TRAN_CALL_HIS.WEB_SVC_ID` value** (slug vs legacy task id). | Cross-system audit traceability. | Decide per §5; default = keep legacy task id for these coexistence audit rows. |
| **R-6** | **`BIZ_KEY` input source** (all-tenant vs parameterised) — legacy accepts optional `BIZ_KEY`. | Determines whether prod runs all-tenant. | Confirm with batch-admin/PO; the plan supports both (`*AllTenants` + `*Tenant(bizKey)`). |

---

## 7. Build sequence (when authorised)

1. Add `BusaDocMstrRepository.findDistinctTrcoTaxIdByBizKeyAndHandRegYn(bizKey, "N")` (query only).
2. Add `OperationStatusBatchService` (`seed*/refresh*`, per-tenant `@Transactional`, reuse OSMC/audit helpers; ideally extract the shared tenant-explicit helper from `OperationStatusService`).
3. Add `OperationStatusBatchScheduler` (gated, ShedLock, placeholder crons).
4. Add `wecms.operation-status.batch.*` config (OFF) to `application.yml`.
5. Unit tests mirroring `ExchangeRateBatchServiceTest` / `…SchedulerTest`: gate-off = no bean; seed create-only + dup-skip; refresh update-only (status+modDtm); F/M filter; per-tenant isolation (one bad tenant doesn't abort the run); no NXISAS/notification writes.
6. Leave `enabled=false`; do **not** enable until R-1, R-2, R-4 are closed.

No schema migration in any step. No frontend change (batch is server-only; the existing Operation Status pages already render the data).

---

## PASS/FAIL

**PASS** — implementation plan created covering scheduler design (gated OFF, ShedLock, placeholder cron), service design (`seed`/`refresh`, per-tenant transaction), reuse of all five existing components (+ one new repository query, no schema), full DB mapping (no new schema), parity rules, and risks/confirmations. No code modified. Plan file: `wecms-api/docs/audits/operation-status-inquiry-batch-refactor-plan.md`.
