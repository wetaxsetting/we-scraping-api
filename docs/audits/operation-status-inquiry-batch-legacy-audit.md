# Operation Status Inquiry Batch Legacy Audit

> **System:** JEX (live WABOOKS / WeCMS) — **legacy audit only, no code modified.**
> **Scope:** the two scheduled batch tasks `OPRT_0001_001` / `OPRT_0010_001` ("Batch Operation Status Infor").
> **Companion docs:** on-demand (web) flow → [docs/jex/features/operation-status-inquiry/business-flow.md](../../../docs/jex/features/operation-status-inquiry/business-flow.md); refactor parity (web) → [operation-status-inquiry-parity.md](operation-status-inquiry-parity.md).
> **Reference pattern:** Exchange Rate Inquiry batch refactor (`ExchangeRateBatchScheduler` / `ExchangeRateBatchService`, flag-gated OFF).
> **Date:** 2026-06-30.

---

## 1. Summary

"Operation Status Inquiry" (거래처 휴폐업 상태 / Trạng thái MST) caches, per tenant + per Vietnamese tax code, the **GDT (Tổng cục Thuế) business operating status** of each counterparty. The data is owned by GDT and surfaced through the Coocon **OSMC smart-gateway** (service `BI09` / API id `TRCO_TRCO001_00003`). WeCMS only stores the **last poll** in `OPRT_STTS_INFO`.

There are two scheduled batch tasks, both named "Batch Operation Status Infor", both authored 2025-10-07, both taking a single optional `BIZ_KEY` input:

| ID | Role | Verb |
|---|---|---|
| `OPRT_0001_001` | **Seed / discovery** — find every counterparty tax code used by F/M tenants and **INSERT** any not yet tracked. | create-only |
| `OPRT_0010_001` | **Refresh** — re-poll every **already-tracked** tax code and **UPDATE** its `COMPANYSTATUS`. | update-only |

Both are thin two-layer JEX tasks: a **BAT** wrapper (`batch.jex.wabooks.oprt.*`) that forwards to a **BCS** implementation (`bcs.jex.wabooks.oprt.*`) where all logic lives. They share the same scrape API, the same tenant filter (`busi_stts='N'`, `del_yn='N'`, `prdt_type in ('F','M')`), and the same per-tenant transaction shape.

`OPRT_0010_001` is the batch the request calls "the Operation Status Inquiry **update**" — it is the recurring refresh job analogous to the Exchange Rate Inquiry batch. `OPRT_0001_001` is its companion seeder. **Neither is yet ported to the Spring refactor** (only the on-demand web equivalents `inquire` / `create` / `bulkUpdate` exist in `OperationStatusService`).

**PASS/FAIL:** **PASS** — both legacy classes located, the full flow / data-source / DB / status / error behaviour traced from source, and a refactor recommendation produced. Items that cannot be confirmed from source alone (scheduler cadence, GDT response field nuances) are flagged **Needs confirmation**.

---

## 2. Legacy Batch Tasks

| Batch Task ID | Class Name | Purpose | Active/Unused | Notes |
|--------------|------------|---------|---------------|-------|
| `OPRT_0001_001` | `batch.jex.wabooks.oprt.OPRT_0001_001` → `bcs.jex.wabooks.oprt.OPRT_0001_001` | **Seed.** Scan `BUSA_DOC_MSTR` for distinct counterparty tax IDs (`hand_reg_yn='N'`) across all F/M tenants, poll GDT, **INSERT** new `OPRT_STTS_INFO` rows (skips existing). | **Active** (`BCS.OPRT_0001_001.xml useYn=Y`). Bootstraps the tracked set. | Source of tax codes = `BUSA_DOC_MSTR_R035` (e-invoice/document master). Create-only — never updates an existing row. |
| `OPRT_0010_001` | `batch.jex.wabooks.oprt.OPRT_0010_001` → `bcs.jex.wabooks.oprt.OPRT_0010_001` | **Refresh / update.** For every existing `OPRT_STTS_INFO` row (per tenant), re-poll GDT and **UPDATE** `COMPANYSTATUS` + `MOD_DTM`. | **Active** (`BCS.OPRT_0010_001.xml useYn=Y`). The recurring "update" job. | Source of tax codes = `OPRT_STTS_INFO_R001` (the already-tracked set). Update-only — never inserts. |

**Difference between the two (both are used, as a pair):**

- **Input set differs.** `0001` reads candidate tax codes from `BUSA_DOC_MSTR` (the universe of counterparties that have appeared on documents); `0010` reads from `OPRT_STTS_INFO` (the codes already being tracked).
- **Write verb differs.** `0001` does `findByPk` → if **absent**, `create(...)`; it copies the **full** GDT detail (companyName, companyStatus, officeAdd, representativeFirstName, searchDatetime). `0010` does `findByPk` → if **present**, `update(...)`; it touches **only** `companyStatus` + `modDtm`.
- **Lifecycle.** `0001` grows the tracked set (discovery); `0010` keeps it fresh (maintenance). Run together: `0001` first to register, `0010` repeatedly to refresh.
- **Code is otherwise near-identical** — same tenant query, same `TrcoHelper.insertExtTranModuleTable(...)`, same `WABOOKSOSMCAPIUtil.callApi("TRCO_TRCO001_00003", ...)`, same response parsing, same single per-tenant transaction.

---

## 3. Source Files Found

| File | Purpose |
|------|---------|
| [WECMS DEV/WECMS/src/batch/jex/wabooks/oprt/OPRT_0001_001.java](../../../WECMS%20DEV/WECMS/src/batch/jex/wabooks/oprt/OPRT_0001_001.java) | BAT wrapper (seed). `execute()` builds BCS input, copies domain, delegates to BCS. |
| [WECMS DEV/WECMS/src/batch/jex/wabooks/oprt/OPRT_0010_001.java](../../../WECMS%20DEV/WECMS/src/batch/jex/wabooks/oprt/OPRT_0010_001.java) | BAT wrapper (refresh). Same wrapper shape as `0001`. |
| [WECMS DEV/WECMS/src/bcs/jex/wabooks/oprt/OPRT_0001_001.java](../../../WECMS%20DEV/WECMS/src/bcs/jex/wabooks/oprt/OPRT_0001_001.java) | **BCS implementation (seed)** — all create logic. Lines 63–221. |
| [WECMS DEV/WECMS/src/bcs/jex/wabooks/oprt/OPRT_0010_001.java](../../../WECMS%20DEV/WECMS/src/bcs/jex/wabooks/oprt/OPRT_0010_001.java) | **BCS implementation (refresh)** — all update logic. Lines 64–207, plus private `requestBody(...)` 209–223. |
| [WECMS DEV/WECMS_ETC/xml/service/BAT/BAT.OPRT_0001_001.xml](../../../WECMS%20DEV/WECMS_ETC/xml/service/BAT/BAT.OPRT_0001_001.xml) | BAT service contract. `trTp=U`, in `BIZ_KEY(20)`, no out. |
| [WECMS DEV/WECMS_ETC/xml/service/BAT/BAT.OPRT_0010_001.xml](../../../WECMS%20DEV/WECMS_ETC/xml/service/BAT/BAT.OPRT_0010_001.xml) | BAT service contract. Same shape. |
| [WECMS DEV/WECMS_ETC/xml/service/BCS/BCS.OPRT_0001_001.xml](../../../WECMS%20DEV/WECMS_ETC/xml/service/BCS/BCS.OPRT_0001_001.xml) | BCS contract. `useYn=Y`, `mntLogYn=N`, `tmUseYn=N`, in `BIZ_KEY(20)`. |
| [WECMS DEV/WECMS_ETC/xml/service/BCS/BCS.OPRT_0010_001.xml](../../../WECMS%20DEV/WECMS_ETC/xml/service/BCS/BCS.OPRT_0010_001.xml) | BCS contract. Same. |
| [WECMS DEV/WECMS/src/wabooks/helper/TrcoHelper.java](../../../WECMS%20DEV/WECMS/src/wabooks/helper/TrcoHelper.java) | `insertExtTranModuleTable(...)` (l. 520–533, writes `EXT_TRAN_CALL_HIS`), `requestBody(...)` (l. 401–418, used by `0001`), batch notification helpers (`getBatchTitle`/`getBatchContent`, l. 857–885). |
| [SOURCE_CODED_OSMC/WABOOKS_OSMC/src/wabooks/osmc/WABOOKSOSMCAPIUtil.java](../../../SOURCE_CODED_OSMC/WABOOKS_OSMC/src/wabooks/osmc/WABOOKSOSMCAPIUtil.java) | `callApi(apiId, input, encodeUtf8)` — resolves API config from `WABOOKSDynamicDataPool` (`API_INFM`), POSTs via `HttpUtils`, logs to `API_TRAN_LOG_HIS`. |
| [WECMS DEV/WECMS/src/wabooks/entity/OprtSttsInfo.java](../../../WECMS%20DEV/WECMS/src/wabooks/entity/OprtSttsInfo.java) · [OprtSttsInfoPk.java](../../../WECMS%20DEV/WECMS/src/wabooks/entity/OprtSttsInfoPk.java) | Entity + composite PK `(BIZ_KEY, TAXCODE)`. |
| [WECMS DEV/WECMS/src/wabooks/dao/OprtSttsInfoDAO.java](../../../WECMS%20DEV/WECMS/src/wabooks/dao/OprtSttsInfoDAO.java) | `findByPk` / `create` / `update` / `upsert` / `deleteByPk`. |
| [WECMS DEV/WECMS/src/wabooks/entity/ExtTranCallHis.java](../../../WECMS%20DEV/WECMS/src/wabooks/entity/ExtTranCallHis.java) | Call-history row (svc_id `BI09`, web_svc_id = task id). |
| [WECMS DEV/WECMS_ETC/xml/service/IDO/IDO.BUSI_INFM_R001.xml](../../../WECMS%20DEV/WECMS_ETC/xml/service/IDO/IDO.BUSI_INFM_R001.xml) · [IDO.BUSA_DOC_MSTR_R035.xml](../../../WECMS%20DEV/WECMS_ETC/xml/service/IDO/IDO.BUSA_DOC_MSTR_R035.xml) · [IDO.OPRT_STTS_INFO_R001.xml](../../../WECMS%20DEV/WECMS_ETC/xml/service/IDO/IDO.OPRT_STTS_INFO_R001.xml) | The three read IDOs the BCS uses. |

---

## 4. Batch Entry Flow

**How it starts.** A JEX batch task is registered as a `BAT` service. The batch engine (JEX Batch Module 6.4.4) invokes the BAT wrapper's `execute(JexData input)` with `input.BIZ_KEY` set (or blank for all-tenant). The BAT class is a pure forwarder:

```
[Batch scheduler] → batch.jex.wabooks.oprt.OPRT_0010_001.execute(input)        BAT.OPRT_0010_001:43-54
   │ util.createBCSData("OPRT_0010_001"); DomainUtil.copyDomain(input, bcsIn)
   ▼
bcs.jex.wabooks.oprt.OPRT_0010_001.execute(bcsIn)                              BCS.OPRT_0010_001:64
```

**Main / execute method.** All work is in the **BCS** `execute(JexData input)` (annotated `@JexDataInfo(id="OPRT_0010_001", type=JexDataType.BCS)`).

**Required parameters.** One optional input only — `BIZ_KEY` (length 20). When blank, the tenant query runs across **all** F/M tenants; when set, it is scoped to that one tenant (`BCS.OPRT_0010_001.xml:28-30`, BCS code l. 71, 79–82).

**Scheduler / batch-admin configuration.** The cron/window is **not** in this repo — it is configured in the **batch admin** (`TB_BATCH_SCHEDULE_INFO` / `TB_BATCH_SCHEDULE_DTL`, both in the PROD active table list) referenced by the user's request ("legacy batch admin"). The BCS XML shows `tmUseYn=N` (no service-time window enforced at the JEX layer) and `maxUse=0`. **Needs confirmation:** exact schedule (frequency, time-of-day) for each task — read from the batch-admin schedule tables on the live system.

**Batch history / log writes.**
- `mntLogYn=N` on both BCS — JEX does **not** auto-write a maintenance/audit log per call (contrast the web `aosv_0001_001_r001` which has `mntLogYn=Y`).
- The BCS itself writes **`EXT_TRAN_CALL_HIS`** once per tenant iteration via `TrcoHelper.insertExtTranModuleTable(util, idoCon, "OPRT_00x0_001")` (BCS l. 146) — `svc_id='BI09'`, `ref_date=today`, `web_svc_id=` the task id, `seq_no` from `wb_get_next_val`.
- `WABOOKSOSMCAPIUtil.callApi(...)` independently writes **`API_TRAN_LOG_HIS`** (before/after the HTTP call) on its own JNDI datasource connection (`WABOOKSOSMCAPIUtil:169-210`).
- **No `BAT_CALL_HIS` write** is performed by the BCS code itself (any batch-engine-level run log would be framework-side — Needs confirmation).
- **Note:** unlike the web `bulkUpdate` flow, the batch BCS does **NOT** write `NXSIAS_MODULE_TRAN_LOG_HIS` and does **NOT** write notification rows (`GENL_NOTI_*`). The `TrcoHelper.getBatchTitle/getBatchContent` notification helpers exist but are **not invoked** by either batch BCS.

---

## 5. External API / WSVC / IDO Flow

**External system.** Coocon **OSMC smart-gateway** → GDT (Vietnam tax authority) public business-registration data (`GLOBAL/VNM/PUBLIC_MASOTHUE` scraping module). Same gateway as the web flow.

**API id.** `TRCO_TRCO001_00003` (svc family `BI09`). Resolved at runtime from `API_INFM` via `WABOOKSDynamicDataPool.getInstance().getAPIInfm("TRCO_TRCO001_00003")` — **the URL, headers, and content-type are DB-config-driven, not hardcoded** (`WABOOKSOSMCAPIUtil.restApi:62-103`). `CONN_TYPE='R'` → REST POST.

**Request URL.** Not in source — comes from the `API_INFM.DETL` row with `FIELD_ID='API_URL'`. **Needs confirmation** from `API_INFM` on the live DB. (Per memory `reference_nxisas_gateway_urls`, the Coocon smart-gateway is `dev-sgw:8443` DEV/SIT, `sgw.webcashvietnam.com` UAT/PROD.)

**Request body.** Built per tenant:

```jsonc
// OPRT_0010_001.requestBody(oprtSttsInfoValue, uniqueKey)   BCS.OPRT_0010_001:209-223
{
  "UniqueKey": "<EXT_TRAN_CALL_HIS.seq_no left-padded to 7 digits>",   // BCS l. 150
  "Input": { "search": [ "<TAXCODE>", "<TAXCODE>", ... ] }             // all tax codes for the tenant
}
```

`0001` uses `TrcoHelper.requestBody(trcoTaxId, uniqueKey)` which pulls `TRCO_TAX_ID` from each `BUSA_DOC_MSTR_R035` row (`TrcoHelper:401-418`); `0010` uses its own private `requestBody(...)` pulling `TAXCODE` from each `OPRT_STTS_INFO_R001` row. `encodeUtf8=false` is passed (l. 152/153).

**Response structure (as parsed).**

```jsonc
{
  "ErrorCode": "00000000",                 // top-level; must == "00000000" to proceed
  "RespData": [                            // one element per search batch
    {
      "ErrorCode": "00000000",             // per-item; "" or "00000000" = ok
      "searchDatetime": "yyyyMMddHHmmss",   // 0001 stores this; 0010 ignores it
      "search": "<tax code searched>",      // matched against detail.taxCode
      "Result": {
        "registrationDetail": [
          {
            "taxCode": "...",
            "companyName": "...",
            "companyStatus": "...",          // the GDT free-text VN status string
            "officeAdd": "...",
            "representativeFirstName": "..."  // 0001 only
          }
        ]
      }
    }
  ]
}
```

Parsing logic: `0010` BCS l. 155–198; `0001` BCS l. 154–211. A detail is applied only when `item.search.equalsIgnoreCase(detail.taxCode)` (BCS l. 176) — i.e. the response row must echo the searched code.

**IDO / WSVC.** Batch tasks use **BAT/BCS** service contracts (no WSVC). The three IDOs consumed:

| IDO | Used by | Returns |
|---|---|---|
| `BUSI_INFM_R001` | both | tenant master rows (filtered by dynamic `biz_key`/`busi_stts='N'`/`del_yn='N'`/`prdt_type in ('F','M')`) |
| `BUSA_DOC_MSTR_R035` | `0001` | `DISTINCT TRCO_TAX_ID FROM BUSA_DOC_MSTR WHERE NULLIF(TRIM(TRCO_TAX_ID),'') IS NOT NULL` + dynamic `biz_key=? AND hand_reg_yn='N'` |
| `OPRT_STTS_INFO_R001` | `0010` | all `OPRT_STTS_INFO` columns + dynamic `biz_key=?` |

---

## 6. Database Tables and SQL

| Table / IDO | Read/Write | Purpose |
|-------------|------------|---------|
| `BUSI_INFM` (via `BUSI_INFM_R001`) | Read | Tenant universe. Filter: `busi_stts='N'` (NORMAL), `del_yn='N'`, `prdt_type in ('F','M')`, optional `biz_key`. Per-row `BIZ_KEY` + `EML` feed the loop. |
| `BUSA_DOC_MSTR` (via `BUSA_DOC_MSTR_R035`) | Read (**`0001` only**) | Distinct counterparty tax IDs per tenant where `hand_reg_yn='N'` and `trco_tax_id` non-blank. The discovery source. |
| `OPRT_STTS_INFO` (via `OPRT_STTS_INFO_R001`) | Read (**`0010` only**) | Existing tracked rows per tenant — the codes to refresh. |
| `OPRT_STTS_INFO` (via DAO `create`/`update`) | **Write** | `0001`: `create(...)` (INSERT, full detail). `0010`: `update(...)` (UPDATE `COMPANYSTATUS`+`MOD_DTM` only). PK `(BIZ_KEY, TAXCODE)`. |
| `EXT_TRAN_CALL_HIS` (via DAO `create`) | **Write** | One row per tenant iteration — `svc_id='BI09'`, `ref_date=LocalDate.now()`, `web_svc_id='OPRT_00x0_001'`, `seq_no` from `wb_get_next_val`. Its `seq_no` is the GDT request `UniqueKey`. |
| `API_TRAN_LOG_HIS` | **Write** | OSMC util logs the raw HTTP request/response (`user_id='SYSTEM'`). Separate JNDI connection, not in the IDO transaction. |
| `SYS_ENV_SET_INFM` / `API_INFM` (`WABOOKSDynamicDataPool`) | Read | API endpoint config for `TRCO_TRCO001_00003`. |

**SQL / IDO files.** `IDO.BUSI_INFM_R001.xml`, `IDO.BUSA_DOC_MSTR_R035.xml`, `IDO.OPRT_STTS_INFO_R001.xml` (+ DAO-generated `OPRT_STTS_INFO_C001` / `_U001` for the writes).

**Key columns updated.**
- `0001` INSERT: `BIZ_KEY`, `TAXCODE`, `COMPANYNAME`, `COMPANYSTATUS`, `OFFICEADD`, `REPRESENTATIVEFIRSTNAME`, `SEARCHDATETIME`, `REG_ID`(=tenant `EML`)/`REG_DTM`, `MOD_ID`/`MOD_DTM` (DAO `create` mirrors REG→MOD).
- `0010` UPDATE: `COMPANYSTATUS`, `MOD_DTM` only (`existingData.setCompanyStatus(...)`, `setModDtm(utcNow)`, BCS l. 186–188).

**Status code mapping.** None inside the batch — `COMPANYSTATUS` is stored **verbatim** as the GDT Vietnamese free-text string. (The web layer collapses it into active/inactive badges; the refactor `OperationStatusService.resolveStatusCd` maps it to a 3-state code — see §7.) There is **no controlled enum** on the WeCMS side.

**Error code handling.**
- Top-level: proceed only if `bigDataResponse.ErrorCode == "00000000"` (BCS l. 158); otherwise the response is ignored (no write, no throw).
- Per-item: proceed only if `errorCode == "00000000"` or blank (BCS l. 168).
- IDO errors: `DomainUtil.isError(idoOut)` → `throw new JexTransactionRollbackException(idoOut)` (BCS l. 100–102, 126–128) → rolls back the tenant transaction.

**Commit / rollback behavior.**
- One transaction per BCS run, opened with `idoCon.beginTransaction()` (BCS l. 73) and closed with `IdoUtils.commitAndCloseQuietly(idoCon)` (BCS l. 204) **after the whole tenant loop**.
- An IDO read/error inside the loop throws `JexTransactionRollbackException` → the **entire run rolls back** (all tenants in that invocation), since there is a single connection/transaction spanning every tenant.
- `API_TRAN_LOG_HIS` writes are on a separate JNDI connection and are **not** rolled back with the IDO transaction.
- **Risk:** a single bad tenant aborts the whole batch run (no per-tenant savepoint). See §8.

---

## 7. Status Mapping Logic

The batch stores the GDT status **as-is** — there is no legacy status enum. Mapping happens downstream (web display / refactor). For reference, the refactor `OperationStatusService.resolveStatusCd` (`wecms-api/.../operation/service/OperationStatusService.java:267-275`) collapses the VN free text:

| Legacy `COMPANYSTATUS` (raw GDT VN text, examples) | Meaning | Updated Target Column | Notes |
|--------------|---------|----------------------|-------|
| `NNT đang hoạt động (đã được cấp GCN ĐKT)` | Active — certificate issued | `OPRT_STTS_INFO.COMPANYSTATUS` (verbatim) | refactor code → `active_certificate_issued` |
| `NNT đang hoạt động` / `... (được cấp thông báo MST)` | Active | `COMPANYSTATUS` | refactor → `active...` (contains "đang hoạt động") |
| `NNT ngừng hoạt động và đã đóng MST` / `... chấm dứt hiệu lực MST` | Suspended / closed | `COMPANYSTATUS` | refactor → `suspended` (contains "ngừng hoạt động") |
| `NNT tạm nghỉ kinh doanh có thời hạn` / `tạm ngừng` | Temporarily suspended | `COMPANYSTATUS` | refactor → `suspended` |
| anything unrecognised | Unknown | `COMPANYSTATUS` | refactor → `not_registered` (defensive fallback) |

**Batch-specific:** `0001` writes the full snapshot (status + name + address + representative + searchDatetime); `0010` overwrites **only** `COMPANYSTATUS` (+ `MOD_DTM`) and leaves name/address/representative/searchDatetime untouched. There is **no** pending/processing/completed/failed state machine — "status" = "last GDT string", and every poll overwrites it.

---

## 8. Error Handling / Retry Logic

- **Which statuses are "failed"?** A poll is treated as success only when both the top-level and per-item `ErrorCode` are `00000000` (or per-item blank). A non-zero code → the response branch is skipped; the row is **left unchanged**. No failure row is recorded by the batch (unlike the web `bulkUpdate`, which writes a `NXSIAS_MODULE_TRAN_LOG_HIS` failure row).
- **No retry.** Neither task retries a failed GDT call. The next scheduled run is the de-facto retry.
- **No date-range rule.** The batch always polls the *current* status; there is no from/to window. (`EXT_TRAN_CALL_HIS.ref_date` is just today.)
- **Transaction granularity is coarse.** A single IDO connection/transaction spans the entire tenant loop; any `JexTransactionRollbackException` (IDO read error) aborts the whole run. There is no per-tenant try/catch or savepoint.
- **Defensive / debug residue.** Both BCS contain `System.out.println("ITEM>>>>>> " + item)` (BCS `0001` l. 163, `0010` l. 164) — a parity/cleanliness note for the refactor (the JEX rule bans `System.out.println` in production paths). Refactor must use structured logging.
- **Product gate.** Only `prdt_type in ('F','M')` tenants are processed (BCS l. 89/90). `H` (HAENGSUNG) and others are excluded by design.
- **`0001` extra guard.** If a tenant has zero `BUSA_DOC_MSTR_R035` rows it `continue`s (skips, no API call) — BCS `0001` l. 142–144. `0010` likewise skips a tenant with zero `OPRT_STTS_INFO` rows (l. 142–144).

---

## 9. Full Process Flow

`OPRT_0010_001` (the **update / refresh** job) — step by step:

```
1. Batch starts
   BAT.OPRT_0010_001.execute(input{BIZ_KEY?}) → forwards to BCS.OPRT_0010_001.execute
   idoCon = createIDOConnection(); idoCon.beginTransaction()                         BCS:69-73

2. Select target tenants
   BUSI_INFM_R001 WHERE busi_stts='N' AND del_yn='N' AND prdt_type IN ('F','M')
        [+ biz_key=? when input.BIZ_KEY set]                                          BCS:75-99
   → busiRows  (one iteration per tenant)

3. For each tenant: select tracked tax codes
   OPRT_STTS_INFO_R001 WHERE biz_key=?                                                BCS:117-125
   → if empty, continue (skip tenant)                                                 BCS:142-144

4. Call inquiry API (once per tenant, all that tenant's codes batched)
   EXT_TRAN_CALL_HIS.create(svc_id=BI09, web_svc_id='OPRT_0010_001', seq_no)          BCS:146
   body = { UniqueKey: pad7(seq_no), Input:{ search:[TAXCODE...] } }                  BCS:148-151, 209-223
   resp = WABOOKSOSMCAPIUtil.callApi("TRCO_TRCO001_00003", body, false)              BCS:153
        → POST to OSMC gateway (URL from API_INFM); writes API_TRAN_LOG_HIS

5. Parse response & update status
   if resp.ErrorCode == "00000000":                                                   BCS:158
     for each RespData[] item where item.ErrorCode in {"00000000",""}:                BCS:162-168
       for each Result.registrationDetail[] where item.search == detail.taxCode:      BCS:172-176
         existing = OprtSttsInfoDAO.findByPk(biz_key, detail.taxCode)                  BCS:182
         if existing != null:                                                          BCS:185
           existing.companyStatus = detail.companyStatus; existing.modDtm = utcNow()   BCS:186-187
           OprtSttsInfoDAO.update(existing)   // UPDATE COMPANYSTATUS, MOD_DTM         BCS:188

6. Write logs/history
   EXT_TRAN_CALL_HIS (step 4, pre-call) + API_TRAN_LOG_HIS (OSMC util).
   (No NXSIAS_MODULE_TRAN_LOG_HIS, no GENL_NOTI_* in the batch path.)

7. Commit or rollback
   IdoUtils.commitAndCloseQuietly(idoCon)   // after ALL tenants                       BCS:204
   On IDO error anywhere in the loop → JexTransactionRollbackException → whole run rolls back.
```

`OPRT_0001_001` (the **seed** job) differs only at steps 3 & 5:
- **Step 3:** reads `BUSA_DOC_MSTR_R035` (`biz_key=? AND hand_reg_yn='N'`, distinct non-blank `TRCO_TAX_ID`) instead of `OPRT_STTS_INFO_R001`.
- **Step 5:** `if existing == null` → `create(...)` a full new row (companyName, companyStatus, officeAdd, representativeFirstName, searchDatetime, reg user = tenant EML). Existing rows are skipped (never updated).

---

## 10. WeCMS Refactor Recommendation

**Can the logic be reused?** Yes — it is structurally identical to the already-ported **Exchange Rate Inquiry batch** and the web Operation Status flow. The refactor already has: the `OprtSttsInfo` entity + `OprtSttsInfoRepository`, the `OsmcGdtClient` bridge, the `ExtTranCallHisRepository`, and `OperationStatusService.bulkUpdate(...)` (which is the per-tenant refresh logic, minus the cross-tenant loop). The batch is essentially "`bulkUpdate` over every F/M tenant, on a schedule, gated off by default".

**Recommended structure (mirror `ExchangeRateBatchScheduler` / `…BatchService`):**

```
domain/operation/
├── service/OperationStatusBatchService.java     // the work; @Transactional per tenant
│      ├─ seed(bizKey?)     ← OPRT_0001_001  (discover from BUSA_DOC_MSTR, create-only)
│      └─ refresh(bizKey?)  ← OPRT_0010_001  (re-poll OPRT_STTS_INFO, update-only)
└── scheduler/OperationStatusBatchScheduler.java // @Scheduled + @SchedulerLock + @ConditionalOnProperty
```

- **Flag-gated OFF** exactly like FX: `@ConditionalOnProperty(prefix="wecms.operation-status.batch", name="enabled", havingValue="true", matchIfMissing=false)`. Rationale is identical — the refactor shares the live JEX DB, and the **legacy** batch already maintains `OPRT_STTS_INFO`; a second writer would double-write/race. (See memory `project_haengsung_exchange_rate_sync`, `project_batch_refactor_real_impl`.)
- **`@SchedulerLock`** (ShedLock — `shedlock` table is already a refactor-owned infra table in the inventory) anchored to `Asia/Ho_Chi_Minh`, cadence from batch-admin (Needs confirmation, §11 OQ-1).
- **Reuse, don't re-port:** factor the per-tenant body out of `OperationStatusService.bulkUpdate` so both the on-demand endpoint and the batch call the same method. Tenant loop comes from `BusiInfmRepository` filtered `busi_stts='N' AND del_yn='N' AND prdt_type IN ('F','M')`.
- **Per-tenant transaction** (refactor improvement over legacy's single cross-tenant tx): wrap each tenant in its own `@Transactional` so one bad tenant cannot abort the whole run (closes the §8 coarse-rollback risk). Document this as a deliberate, parity-safe divergence.
- **No `System.out.println`** — use SLF4J. **No notification writes** (parity: legacy batch doesn't write `GENL_NOTI_*`). **No `NXSIAS_MODULE_TRAN_LOG_HIS`** in the batch path (parity).
- **Key generation** for `EXT_TRAN_CALL_HIS.seq_no` must route through `LegacyFunctionRepository` → `wb_get_next_val` (funnel rule; the web `inquire`/`bulkUpdate` already use `nextExtTranCallHisSeqNo`).

**Tables that receive the updated status (all in the legacy-table-inventory — confirmed):**

| Table | Write | Inventory |
|---|---|---|
| `OPRT_STTS_INFO` | INSERT (seed) / UPDATE COMPANYSTATUS+MOD_DTM (refresh) | ✅ line 101 |
| `EXT_TRAN_CALL_HIS` | INSERT (one per tenant per run) | ✅ line 84 |
| `BUSI_INFM` | read (tenant universe) | ✅ line 55 |
| `BUSA_DOC_MSTR` | read (seed source) | ✅ line 46 |
| `NXSIAS_MODULE_TRAN_LOG_HIS` | (not written by batch; web only) | ✅ line 100 |
| `API_TRAN_LOG_HIS` | written by the OSMC client leg | ✅ (active list) |

No new table is required — **no migration needed** for the batch itself.

**API dependencies / credentials required:**
- The Coocon OSMC smart-gateway (`TRCO_TRCO001_00003` / `BI09`) — the refactor's `OsmcGdtClient` (`wecms.osmc.*` config). Same gateway/host as the web flow; for non-PROD must override off the live Coocon default (per `OperationStatusService` Javadoc).
- Per memory `reference_nxisas_local_engine_origin` / `reference_nxisas_gateway_urls`: the GDT scrape path is Coocon-served; verify the batch can reach it headlessly (no browser-origin NXiSAS engine licensing surprise — Needs confirmation, OQ-2).

---

## 11. Open Questions / Required Confirmation

- **OQ-1 (schedule cadence).** The cron/window for `OPRT_0001_001` and `OPRT_0010_001` is **not in this repo** — it lives in the batch-admin tables `TB_BATCH_SCHEDULE_INFO` / `TB_BATCH_SCHEDULE_DTL` on the live system. Read these (or confirm with PO) before setting the refactor `@Scheduled` cron. **Needs confirmation.**
- **OQ-2 (GDT gateway reachability).** Confirm `API_INFM` row for `TRCO_TRCO001_00003` (URL/headers) and that a headless server batch can call it (Coocon engine origin/licensing). **Needs confirmation.**
- **OQ-3 (BIZ_KEY input source).** In production, does the scheduler pass a specific `BIZ_KEY` or run all-tenant (blank)? Affects whether the refactor loops all F/M tenants or is parameterised. **Needs confirmation.**
- **OQ-4 (response field nuances).** `0001` reads `searchDatetime` + `representativeFirstName`; `0010` ignores them. Confirm the GDT `RespData` shape matches the refactor `OsmcGdtClient` parsing (it was modelled from the web flow `TRCO_TRCO001_00003` — should align). **Likely OK; verify.**
- **OQ-5 (product gate `F`/`M`).** The batch excludes `H`/others. Confirm this is still the intended tenant scope for the refactor (HAENGSUNG explicitly out). Per memory `feedback_product_type_set_fmh`. **Confirm with PO.**
- **OQ-6 (run-log expectation).** Legacy batch writes only `EXT_TRAN_CALL_HIS` (+ `API_TRAN_LOG_HIS` via the client). Confirm no `BAT_CALL_HIS` / dashboard run-log row is expected for parity. **Needs confirmation.**
- **OQ-7 (seed vs refresh ownership at cutover).** If/when the refactor *owns* this batch (flag on), confirm both jobs move together and the legacy batch is disabled in that environment to avoid double-write. **PO decision.**

---

## 12. Final Conclusion

- Both batch classes located and traced end-to-end from source (BAT wrapper → BCS implementation → IDO/DAO → OSMC gateway → DB).
- `OPRT_0010_001` is the **active "Operation Status Inquiry update" batch** (refresh / update-only); `OPRT_0001_001` is its **active seed** companion (discover / create-only). Both are in use.
- Data source = Coocon OSMC `TRCO_TRCO001_00003` (`BI09`); store = `OPRT_STTS_INFO` (PK `BIZ_KEY+TAXCODE`); call log = `EXT_TRAN_CALL_HIS` + `API_TRAN_LOG_HIS`. All touched tables are in the refactor legacy-table-inventory — **no new schema**.
- The logic is reusable and maps cleanly onto the existing refactor assets (`OperationStatusService`, `OsmcGdtClient`, `OprtSttsInfoRepository`). Recommended refactor: a flag-gated, ShedLock-guarded `OperationStatusBatchScheduler` + `OperationStatusBatchService` (`seed` + `refresh`) mirroring `ExchangeRateBatchScheduler`, **OFF by default**, **per-tenant transaction**, no `System.out`, no notification/NXISAS writes (parity).
- Implementation is **not started** in this audit (audit-only, per scope). Schedule cadence + gateway reachability are the key Needs-confirmation items before any build.

**PASS/FAIL: PASS.**
**Audit file created at:** `wecms-api/docs/audits/operation-status-inquiry-batch-legacy-audit.md`
